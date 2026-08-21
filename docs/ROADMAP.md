# Cookie — Roadmap & Fix-Liste

Stand: 2026-07-31. Ersetzt keine der Detail-Dokumente (`cookie-game-design.md`,
`CLAUDE.md`), sondern bündelt alle bekannten offenen Baustellen an einer
Stelle: Bugs, Aufräumarbeiten, Build/Deployment, Design-Doc-Pflege.

Priorität grob absteigend pro Abschnitt. Abgehakt = erledigt, nicht löschen
(Historie), sondern Häkchen setzen und ggf. Datum/Commit ergänzen.

---

## 0. Sofort (Sicherheit / Datenintegrität)

- [x] **`@Version`-Spalten fehlten/waren NULL, brach "Start Spiel" komplett
  (2026-08-08).** `MarketStockEntity.version` (primitiv `long`) wurde in
  einer frueheren Session ergaenzt (Race-Condition-Fix, siehe 7.1), aber
  Hibernates `ddl-auto=update` generierte
  `alter table market_stock add column version bigint not null` OHNE
  Default — schlaegt auf einer Tabelle mit bestehender Zeile IMMER fehl
  (Postgres verweigert NOT NULL ohne Default bei vorhandenen Zeilen, siehe
  auch die `lifetime_*`-Felder in `UserEntity` fuer denselben bereits bekannten
  Fallstrick). Ergebnis: `market_stock` blieb dauerhaft ohne die Spalte,
  jeder Zufalls-Preis-Tick (alle 2s) warf `column ... does not exist`.
  Gleiches Muster bei `PlayerBuildingEntity.version` — dort war die Spalte
  zwar vorhanden, aber alle 21 bestehenden Zeilen hatten `NULL` (primitives
  `long` kann das nicht aufnehmen → `PropertyAccessException` beim Laden),
  was `GameController#initializeGame` (`buildingService.ensurePreBuiltBuildings`/
  `getBuildings`/`getTotalCap`) crashen liess — **"Start Spiel" warf für
  jeden Spieler mit bestehenden Gebäuden einen 500er.**
  **Fix (lokal angewendet):** `ALTER TABLE market_stock ADD COLUMN version
  bigint NOT NULL DEFAULT 0`, `UPDATE player_buildings SET version = 0
  WHERE version IS NULL` + `ALTER COLUMN version SET NOT NULL`.
  **Achtung: der Live-Beta-Server hat vermutlich denselben Bug** (gleiche
  Entity-Historie) — dieselben zwei SQL-Fixes muessen dort manuell nachgezogen
  werden, ddl-auto=update repariert das nicht von selbst.

- [x] **Neuer `EffectType`-Enum-Wert brach "Dev Start" komplett (2026-08-09).**
  Gleiche Problemklasse wie die `@Version`-Spalten oben: Hibernates
  `ddl-auto=update` legt beim ersten Anlegen der Spalte `skill_nodes.effect_type`
  eine Postgres-CHECK-Constraint an, die nur die zu dem Zeitpunkt bekannten
  Enum-Werte erlaubt (`skill_nodes_effect_type_check`) — und aktualisiert diese
  Constraint bei einem späteren `ddl-auto=update`-Lauf NIE, auch wenn
  `EffectType` neue Werte bekommt (hier: `WAGE_INTEREST_REDUCTION` für den
  neuen DISPO-Skillzweig). `SkillTreeService#seedTree()` versuchte beim
  Boot die neuen DISPO-Knoten einzufügen, Postgres lehnte die INSERTs mit
  `violates check constraint "skill_nodes_effect_type_check"` ab →
  `skillTreeService`-Bean konnte nicht initialisiert werden, ganzer Boot
  bricht ab ("Dev Start ... FEHLER").
  **Fix (lokal angewendet):** `ALTER TABLE skill_nodes DROP CONSTRAINT
  IF EXISTS skill_nodes_effect_type_check;` (App-seitige Validierung über
  den Java-Enum reicht, die DB-Constraint war ohnehin nur redundante
  Absicherung).
  **Achtung: der Live-Beta-Server hat denselben Bug**, sobald dieses Release
  dort deployed wird (gleiche Entity-Historie) — denselben SQL-Fix dort
  manuell nachziehen, ddl-auto=update repariert das nicht von selbst. Bei
  jedem künftigen neuen `EffectType`-Wert wieder relevant, falls die
  Constraint zwischenzeitlich (z.B. durch DB-Neuaufsetzen) neu entsteht.
  **Strukturell erledigt (2026-08-10):** `SkillNodeEffectEntity.effectType`
  ist im Zuge des Skillbaum-Fundament-Umbaus auf einen reinen `String` ohne
  `@Enumerated` umgestellt — Postgres legt für diese Spalte nie wieder eine
  CHECK-Constraint an, dieser Fallstrick kann für `effectType` nicht mehr
  auftreten (Details `docs/cookie-game-design.md` §9). `nodeTier`
  (`PASSIVE`/`NOTABLE`/`KEYSTONE`) ist bewusst weiter ein typed Enum mit
  demselben einmaligen Constraint-Risiko, dort aber unkritisch (stabiles
  3-Werte-Set). Live-Server-Nachzug für den *alten* `skill_nodes.effect_type`
  bleibt trotzdem nötig, falls dort noch nicht deployed — siehe
  `docs/plans/2026-08-10-done-skillbaum-wheel-keystones.md`.

- [x] **Rathaus-Abrechnung zeigte immer "Noch keine Abrechnung", obwohl
  Lohn abgebucht wurde (2026-08-09).** `WageLedgerEntity#breakdownJson`
  hatte `@Lob` auf einem `String`-Feld — Hibernate mappt das bei Postgres
  nicht auf eine normale `text`-Spalte, sondern auf den Large-Object-Typ
  `oid` (Zeiger auf `pg_largeobject`, ein eigenes Postgres-Subsystem für
  sehr große Blobs, hier für ein paar hundert Zeichen JSON komplett
  überdimensioniert). Postgres-Large-Objects dürfen nur innerhalb einer
  expliziten Transaktion gelesen werden — `WageService#getWageHistory()`
  ist aber ein reiner, nicht-transaktionaler Read. Jeder Aufruf von
  `GET /api/v1/farm/wage-history/{userId}` warf serverseitig
  `PSQLException: Large Objects may not be used in auto-commit mode`
  (500er), das Frontend (`RathausDialog.vue#selectBillingTab`) fing den
  Fehler ab und zeigte still den Leer-Zustand — sah für den Spieler aus wie
  "Feature tut einfach nichts", nicht wie ein Fehler.
  **Fix:** `@Lob` entfernt, stattdessen `columnDefinition = "text"` (ganz
  normale Spalte, kein Large-Object-Subsystem). DB-Spalte musste händisch
  nachgezogen werden (Hibernate `ddl-auto=update` ändert den Typ einer
  bestehenden Spalte nie): `ALTER TABLE wage_ledger DROP COLUMN
  breakdown_json; ALTER TABLE wage_ledger ADD COLUMN breakdown_json text;`
  — vorhandene Test-Einträge verloren dabei ihre Aufschlüsselung (Dev-DB,
  unkritisch). **Live-Server:** falls dort noch nicht deployed, betrifft es
  nicht; falls doch, denselben DROP/ADD-Fix nachziehen.
  **Merke für künftige `String`-Spalten mit viel Text:** nie `@Lob` bei
  Postgres, immer `columnDefinition = "text"` direkt am `@Column`.

- [x] **Keine echte Steam-Auth-Verifizierung (kritisch vor Public/Early-Access).**
  — behoben 2026-08-14. War: `app.dev-mode=false` schaltete nur die
  Admin-Token-Pflicht scharf, alle Gameplay-Endpunkte nahmen `steamId`
  ungeprüft entgegen. **Fix:** `SteamAuthInterceptor` erzwingt bei
  `devMode=false` auf jedem `/api/v1/**`-Call (außer `/config`, `/auth/**`,
  `/admin/**`) eine gültige Session + Owner-Match zwischen Session und
  behaupteter `userId`/`steamId`. Session entsteht aus einem echten
  Steam-Ticket (`electron/main.js` → `steamworks.js
  auth.getAuthTicketForWebApi` → `POST /api/v1/auth/steam` →
  `SteamAuthService.verifyTicket` gegen `ISteamUserAuth
  /AuthenticateUserTicket`). `app.dev-mode=true` unverändert (kein Check,
  `DEV_PLAYER_001`, MCP-Testserver funktioniert weiter). Details/Architektur:
  `docs/plans/2026-08-14-done-steam-auth-produktion.md`.

- [x] **Browser-Zugang (ohne Electron) — Steam OpenID-Login.**
  — behoben 2026-08-14, zusammen mit obigem Punkt. `LandingView.vue`
  (Button war schon vorbereitet, nur `disabled`) → `GET /api/v1/auth/steam
  /login` → Steam-OpenID-Redirect → `GET /api/v1/auth/steam/callback`
  verifiziert (`check_authentication`) und prüft zusätzlich
  `SteamAuthService.ownsGame()` (`ISteamUser/CheckAppOwnership`) — OpenID
  bestätigt nur Identität, nicht Kauf, das war hier bewusst als eigener
  Schritt mit eingebaut, nicht zurückgestellt. Bei Erfolg Redirect zurück
  zum Frontend mit Session-Token. Details:
  `docs/plans/2026-08-14-done-steam-auth-produktion.md`.

- [x] **Negative-Amount-Exploit im Markt (kritisch).** — behoben 2026-08-02.
  `MarketService.performAction()` prüfte `amount` nie auf `> 0`. Ein `BUY`
  mit negativem `amount` machte die Kosten negativ → Cookie-Duplizierung;
  ein `SELL` mit negativem `amount` erzeugte Ressourcen aus dem Nichts.
  **Fix:** `amount <= 0` wird jetzt serverseitig in `performAction()`
  abgelehnt (400), zusätzlich `@Positive` auf `ResourceDto.amount` +
  `@Valid` durchgereicht als zweite Verteidigungslinie. Bonus: das
  Marktpreismodell wurde in derselben Session komplett auf ein
  Constant-Product-AMM umgebaut (kein linearer Preiseinfluss mehr) —
  Details jetzt in `cookie-game-design.md` Abschnitt 6.
  Betrifft: `MarketService.java`, `ResourceDto.java`,
  `MarketRequestDto.java`, `MarketController.java`,
  neu: `exception/GlobalExceptionHandler.java` (fängt seither auch alle
  anderen `IllegalArgumentException`/`IllegalStateException`-Validierungsfehler
  im ganzen Backend sauber als 400 statt als rohen 500 ab).

- [x] **Swagger UI + dev API-Tester öffentlich erreichbar (kritisch).** —
  behoben 2026-08-03, direkt beim ersten produktiven Rollout aufgefallen.
  `/swagger-ui/index.html` und `/v3/api-docs` waren ungeschützt live und
  erlaubten es jedem, die komplette API — inklusive `/api/v1/admin/market/reset`
  — direkt im Browser zu erkunden und auszuführen. Zusätzlich lag unter
  `static/index.html` ein alter manueller "API Tester" mit Buttons zum
  Anlegen/Löschen beliebiger User und Markt-Buy/Sell für jede beliebige
  `userId`, ganz ohne Auth auf der Seite selbst.
  **Fix:** `springdoc.swagger-ui.enabled=false` + `springdoc.api-docs.enabled=false`
  in `application.properties`; `static/index.html` entfernt und durch eine
  harmlose Coming-Soon-Seite ersetzt (kein API-Zugriff von dort). Die
  Admin-Endpunkte selbst verlangten schon vorher korrekt den Admin-Token —
  das Problem war die Auffindbarkeit/Bedienbarkeit über Swagger, nicht
  fehlende Prüfung im Endpunkt. Details: `docs/DEPLOYMENT.md`.

---

## 1. Offene GitHub Issues — Status-Check

Ergebnis einer Code-Verifikation gegen die Issue-Beschreibungen. Vier der
fünf länger offenen Issues sind entweder gelöst oder durch Rework
überholt — sollten geschlossen bzw. präzisiert werden, damit das Issue-Board
wieder den Ist-Zustand widerspiegelt.

| Issue | Titel | Verdikt | Aktion |
|---|---|---|---|
| [#19](https://github.com/RedConcrete/Cookie/issues/19) | Markt-Server-Logik unklar | **Größtenteils erledigt**, aber der Negative-Amount-Bug (Abschnitt 0) ist genau die Art Lücke, die das Issue meinte | Nach Fix von Abschnitt 0 schließen, mit Verweis auf Commit |
| [#21](https://github.com/RedConcrete/Cookie/issues/21) | Ressourcenauswahl kaputt | **Überholt.** Die im Issue gemeinte alte UI (`MarketTable.vue`, `TradePanel.vue`) wird nirgends mehr importiert — totes Code. Aktuelles `MarketView.vue` hat pro Ressource eigene Buy/Sell-Buttons, Chart-Hover hebt die passende Zeile korrekt hervor (`PriceChart.vue` → `hover-resource` Event) | Issue schließen; toten Code entfernen (Abschnitt 2) |
| [#22](https://github.com/RedConcrete/Cookie/issues/22) | Upgrade-Kauf clientseitig (Security) | **Erledigt.** `UpgradeService.buyUpgrade()` ist `@Transactional`, berechnet Kosten serverseitig neu, prüft Guthaben/Max-Level. Frontend zeigt nur Server-Werte an, wendet nichts lokal an | Issue schließen |
| [#24](https://github.com/RedConcrete/Cookie/issues/24) | Markt-Graph unübersichtlich | **Vermutlich erledigt** durch den Graph-Rework (Serien-Toggle, %-Modus, Zoom/Pan mit Limits, Label-Overlap-Fix in `PriceChart.vue`) | Visuell im Browser gegenchecken, dann schließen |
| [#14](https://github.com/RedConcrete/Cookie/issues/14) | Linux-Build kaputt | **Nicht abschließend geklärt.** `mvnw` ist in Git mit `100755` committet (korrekt). `steamworks.js@0.4.0` bringt vorkompilierte Linux/Win/Mac-Binaries mit (kein Cross-Compile nötig). Root Cause vermutlich Build-Maschine (fehlende `electron-builder`/AppImage-Systemdeps oder Wine-Interferenz bei `build:all`), nicht das Repo selbst | `npm run build:linux` mit `--publish=never` einmal isoliert (ohne `build:win` parallel) laufen lassen, echten Fehler einsammeln, dann neu bewerten |

Zusätzlich offen, nicht im Scope dieser Session geprüft:
[#27](https://github.com/RedConcrete/Cookie/issues/27) (Steam-Lib-Bild),
[#25](https://github.com/RedConcrete/Cookie/issues/25) (Lautstärke-Settings-Popup),
[#23](https://github.com/RedConcrete/Cookie/issues/23) (dynamische Button-Freischaltung nach Fortschritt),
[#20](https://github.com/RedConcrete/Cookie/issues/20) (SSL/HTTPS Server↔Client),
[#18](https://github.com/RedConcrete/Cookie/issues/18) (Tooltip-Beschreibungen),
[#17](https://github.com/RedConcrete/Cookie/issues/17) (Asset-Credits),
[#15](https://github.com/RedConcrete/Cookie/issues/15) (Marktverlauf-Hover-Highlight — technisch schon in `PriceChart.vue` vorhanden, prüfen ob damit erledigt).

---

## 2. Aufräumarbeiten

- [ ] **Unity-Reste entfernen** (aus `CLAUDE.md` übernommen, weiterhin offen):
  - 153 `.meta`-Dateien, überwiegend unter `frontend/src/assets/`
  - 51 `.sfk`-Dateien, alle unter `frontend/src/assets/Sounds/RPGsounds/OGG/`
  - Vor dem Löschen kurz gegenchecken, ob irgendein Build-Schritt sie
    referenziert (sollte nicht der Fall sein — reine Unity-Editor-Artefakte)
- [x] **Toten Frontend-Code entfernt:** `MarketTable.vue`/`TradePanel.vue` —
  bereits am 2026-08-03 in Commit `b7904ac` gelöscht (durch
  `MarketView.vue`/`MarketDialog.vue` ersetzt), Roadmap-Eintrag war nur
  nicht abgehakt (siehe #21 oben)
- [ ] **`frontend/src/assets` durchsehen** (214 MB) auf ungenutzte
  Sprites/Sounds aus der alten Unity-Optik — insbesondere nach dem
  Pixel-Art-Rework (`2142ecc`, `493354a`) dürften alte Sprite-Sets aus dem
  vorherigen Look verwaist sein
- [ ] Große Audio-Assets im Build prüfen (`ElevatorMusic-*.wav` ~16.9 MB,
  mehrere `.mp3` >3–5 MB) — für Steam-Distribution ok, aber ggf. auf OGG/
  niedrigere Bitrate umstellen, wenn Downloadgröße relevant wird
- [x] **Markt-Preisgraph folgte nach Zoom/Pan nie wieder der Live-Kante
  (2026-08-19).** `PriceChart.vue`s Zoom- UND Pan-Handler setzten beide
  `userHasMoved=true` — danach blieb das X-Fenster für immer an der Zoom-
  /Pan-Zeit eingefroren, neue Marktdaten liefen zwar weiter in
  `fullHistory` ein (`rebuildChart()` läuft unconditional), tauchten aber
  nie mehr im sichtbaren Bereich auf → sah wie "Graph aktualisiert sich
  nicht mehr" aus, besonders beim Rauszoomen. **Fix:** `onZoomComplete`
  setzt `userHasMoved` nicht mehr — nur ein echtes Pan (Wegziehen in die
  Vergangenheit) gilt jetzt als Signal "User schaut sich bewusst einen
  fixen Zeitpunkt an" und stoppt die Live-Verfolgung; Zoom (rein/raus)
  behält die gewählte Fensterbreite, rutscht aber weiter mit der Live-
  Kante mit (siehe `watch(marketStore.history, ...)`, das den bisherigen
  `viewWidth` beibehält und nur `max` auf die neueste Zeit nachzieht).
- [ ] **`MarketView.vue`s Verkaufsvorschau zeigt den flachen Config-
  `sellFeeRate` statt der effektiven, level-/skill-abhängigen Gebühr**
  (`netPayout()`, `frontend/src/views/MarketView.vue`) — der tatsächliche
  Verkauf serverseitig (`MarketService.performAction`) rechnet schon
  korrekt mit `BuildingService#getEffectiveSellFeeRate`, nur die
  Vorschau im Dialog nicht. Gefunden beim Fix des analogen Hof-Popup-Bugs
  (siehe unten), bewusst nicht mitgezogen (anderer Fix-Ort, braucht die
  effektive Rate im Dialog-Kontext statt am Gebäude-DTO — z. B. über
  `getBuildings()`s neues `markt.feeRate` im MarketView nachladen).
- [ ] **Gebäude-Szenen auf Fruitpunch24-Palette migrieren** (siehe
  `cookie-game-design.md` Abschnitt 8). Alle aktuellen Gebäude-SVGs
  (`frontend/src/assets/buildings/*.svg`) sind Platzhalter und nutzen
  keine der 24 Palette-Farben — bewusst so, werden im Rahmen des
  UI-Rebuilds nach und nach durch palette-konforme Pixel-Art ersetzt.
  Neue Referenz-Assets liegen bereits unter
  `frontend/src/assets/buildings/StorageBuildng/` (Lager-Gebäude,
  Testbilder, noch nicht verdrahtet).
- [x] **Markt-Hover-Popup zeigte statische 8%-Marktgebühr (2026-08-19).**
  `BUILDING_INFO.markt.rows`/`overlayRate` in `buildingInfo.js` waren
  hardcoded und obendrein falsch (echter Default `MarketConfig.sellFeeRate`
  ist `0.15`, nicht `0.08`), während die echte Gebühr mit Markt-Level sinkt
  (`BuildingService#getEffectiveSellFeeRate`, −2%/Stufe über Stufe 1) und
  durch den DISPO-Skillbaum-Zweig (`MARKET_FEE_REDUCTION`) weiter reduziert
  werden kann. Gleiches Bug-Muster wie bei den Produktionsgebäuden
  (Lohn/Ertrag, 2026-08-09 gefixt).
  **Fix:** `PlayerBuildingDto` bekommt ein neues `feeRate`-Feld, von
  `BuildingService#toDto` nur für `markt` per
  `getEffectiveSellFeeRate(userId, level, marketConfig.getSellFeeRate())`
  befüllt (neue dritte Overload, nimmt direkt das Markt-Level statt der
  Owned-Map). `FarmGridView.vue`s `buildings`-Computed baut Popup-Zeile +
  Overlay-Badge für `markt` jetzt live aus `owned.feeRate` statt aus den
  statischen `info.rows`, analog zum bestehenden Live-Branch für
  Produktionsgebäude. Statischer Fallback-Text in `buildingInfo.js` auf den
  korrekten Basiswert `15 %` korrigiert (nur noch als kurzer Lade-Flash
  sichtbar). Details: `docs/plans/2026-08-19-done-markt-gebuehr-live.md`.
  **Bewusst nicht mit angefasst:** `MarketView.vue`s Verkaufsvorschau
  (`netPayout`) nutzt weiterhin den flachen Config-Satz statt des
  effektiven — gleiche Bug-Klasse, aber eigener Fix-Ort (Dialog-Kontext
  statt Gebäude-DTO), als Folge-Punkt offen.

---

## 3. Design-Dokument aktualisieren

- [x] Erledigt 2026-08-02: `cookie-game-design.md` komplett neu geschrieben
  als Ist-Stand-Referenz statt Juni-Plan. Alle Systeme (Hof-Grid, Bürger,
  AMM-Markt, Rezepte/Backen, Upgrades, Net Worth, Prestige, Season,
  Pixel-Art-UI) beschreiben jetzt den tatsächlichen Code. Bekannte
  Diskrepanzen (Gebäude fehlen in Net Worth, Season-Reset löscht keine
  Gebäude, tote Legacy-Frontend-Dateien) stehen dort jetzt explizit in
  einem eigenen Abschnitt 12 statt implizit unter "offen" zu verschwinden.

---

## 4. Verbleibende Feature-Phasen (laut Design-Doc Abschnitt 15)

Mit Punkt 3 oben neu bewertet, ist praktisch die gesamte Phase 1–6
implementiert. Was laut Design-Doc noch offen oder nur teilweise
spezifiziert ist:

- [x] **Lokalisierung DE/EN** — erledigt 2026-08-06. Frontend auf
  `vue-i18n` umgestellt (Composition API, ein JSON-Locale-Paar pro
  Komponente unter `frontend/src/i18n/locales/{de,en}/`), Sprachumschalter
  in `SettingsDialog.vue`, Auswahl persistiert in `localStorage`. Texte aus
  reinen JS-Datenmodulen (`buildingInfo.js`: Gebäude-/Ressourcennamen)
  über Key-Referenzen + Resolver-Funktion (`buildingTitle()`/
  `resourceLabel()`) angebunden, da diese Module keinen eigenen
  i18n-Kontext haben. Aktuell nur Deutsch/Englisch befüllt, Struktur ist
  auf weitere Sprachen ausgelegt (neuer `locales/<code>/`-Ordner genügt).
  Backend-seitige Texte (z. B. Fehlermeldungen, Gebäude-/Item-Namen aus
  der DB) bleiben deutsch — nicht Teil dieser Umstellung.
- [x] **Custom Pixel-Scrollbar + Lade-Animation** — erledigt 2026-08-06.
  Native `::-webkit-scrollbar`-Optik ließ sich nicht auf den exakten
  3D-Block-Look der Slider (`.sd-slider`) bringen (Browser begrenzt Border/
  Bevel auf der echten Scrollbar); stattdessen neue Komponente
  `frontend/src/components/pixel/PixelScrollBox.vue` — versteckt die native
  Scrollbar, zeichnet Track/Thumb selbst (identischer Border/Bevel wie die
  Slider), inkl. Drag-to-Scroll und Klick-auf-Track. Ersetzt die alte
  `.px-scroll`-Utility-Klasse (aus `pixel.css` entfernt) an allen 12
  Stellen, die vorher scrollten. Neue Lade-Anzeige
  `frontend/src/components/pixel/LoadingIndicator.vue` (drehendes
  Cookie-Icon + Punkte-Animation `...`→`..`→`.`→`..`) ersetzt den reinen
  "Lade..."-Text an allen 7 Ladeanzeigen.
  **Für neue scrollende Dialoge:** `PixelScrollBox` verwenden statt
  nativem `overflow:auto` — braucht vom Elternelement eine definierte Höhe
  (fixe `height`, oder `flex:1 1 auto;min-height:0` in einer Flex-Column
  mit `max-height` auf dem Container). Wichtige Falle dabei: `flex:1`
  (Kurzform, `flex-basis:0%`) kollabiert auf 0 Höhe, wenn der Container nur
  `max-height` statt `height` hat — `flex:1 1 auto` (Basis vom Inhalt)
  verwenden, siehe `SettingsDialog.vue` als Vorbild.
- [x] **Musik-Player in den Einstellungen (2026-08-21).** Einstellungen
  hatten nur Lautstärke/Mute, kein Skip. Erste Version war nur ein Skip-
  Button direkt neben dem Musik-Regler — Nutzer-Feedback: sollte ein
  eigener Bereich sein, mit Songnamen und Vor/Zurück statt nur Vorwärts.
  **Umgesetzt:** neue eigene `PixelSection` "Musik-Player" unter Lautstärke,
  zeigt den Namen des laufenden Tracks (`useAudio.js`s `GAME_TRACKS`/
  `MENU_TRACK` haben jetzt feste Anzeigenamen statt der Vite-Hash-URL) +
  Vor-/Zurück-Buttons. `useAudio.js`s Track-Engine dafür umgebaut: eigene
  `history`/`historyPos`-Liste getrennt vom Shuffle-Bag (`shuffled`/
  `trackIdx`) — `nextTrack()` läuft zuerst durch schon bekannte History-
  Einträge (falls per "Zurück" dorthin gesprungen wurde), zieht erst am
  Ende einen neuen Track aus dem Bag; neues `prevTrack()` springt einfach
  einen History-Eintrag zurück. `musicMode` von einer internen `let` auf
  einen reaktiven `ref` umgestellt, damit die Buttons im Hauptmenü-Modus
  (nur ein exklusiver, geloopter Track, nichts zum Wechseln) sichtbar
  deaktiviert sind statt still nichts zu tun.
  **Nebenbei gefixt:** der erste Skip-Button-Entwurf zeigte einen
  Timeline-Balken im Hover-Tooltip (bekannter offener Punkt, siehe
  `NestedTooltip`-Eintrag unten) und ragte durch einen klassischen
  Flexbox-Bug (`<input type=range>` ohne `min-width:0`) über den
  Dialogrand hinaus — beides behoben, bevor der Button durch den
  Musik-Player-Bereich ersetzt wurde.
- [x] **Einstellungen: "Controller-Zoom-Geschwindigkeit"-Label überlappte
  den Regler (2026-08-21).** `.sd-slider-label` war fest `168px` +
  `white-space:nowrap` — passte für kurze Labels ("Musik", "Speed"), aber
  die längeren Kamera-Labels ("Zoom-Geschwindigkeit (Controller)" DE,
  "Controller zoom speed" EN) liefen über die 168px hinaus und ohne
  `overflow:hidden` sichtbar in den Slider rein statt abgeschnitten zu
  werden. **Fix:** Label-Spalte auf `190px` verbreitert, `nowrap`
  entfernt (bricht jetzt bei Bedarf zweizeilig um statt zu überlappen),
  Dialog selbst um 50px breiter (`420px`→`470px`, wie gewünscht) für mehr
  Luft insgesamt.
- [x] **Prestige-UI entfernt (2026-08-06).** Dialog blieb bei Live-Tests dauerhaft
  im Lade-Zustand hängen — DevTools-Network zeigte aber einen sauberen 200-OK-
  Request mit korrektem JSON-Body, Backend (`PrestigeService`/`GameController`)
  ist also nicht die Ursache; der eigentliche Frontend-Bug (`loading` wird nach
  erfolgreicher Antwort nicht auf `false` gesetzt) wurde nicht mehr weiter
  verfolgt, da das Prestige-System laut Spieler ohnehin nicht in der aktuellen
  Form bleibt, sondern später neu gebaut wird. Deshalb komplett aus der Spieler-UI
  entfernt statt gefixt: HUD-Button + `<PrestigeDialog>` aus `FarmGridView.vue`
  raus, `PrestigeDialog.vue`/`PrestigeView.vue` gelöscht, dazugehörige
  Locale-Dateien (`prestigeDialog.json`/`prestigeView.json`, de+en) gelöscht.
  **Bewusst NICHT angetastet:** Backend (`PrestigeService`, `GameController`,
  `UserEntity`-Felder `prestigeLevel`/`totalPrestiges`), `GameBalanceConfig`
  inkl. der zugehörigen Felder im `AdminDialog.vue` (Marktdaten-Panel), und
  `playerStore.prestigeMultiplier` (bleibt bei Default `1`, fließt weiterhin in
  die Ernte-Formel in `FarmGridView.vue` ein, `loadPrestigeMultiplier()` läuft
  unverändert beim Player-Init und funktioniert nachweislich). Beim Neubau des
  Prestige-Systems: diese Altlasten (Backend-Endpoints, Admin-Felder,
  `prestigeMultiplier`-Anbindung) sind noch da und wiederverwendbar, statt von
  Null anzufangen.
  **Konkretisierte Neubau-Richtung (2026-08-07):** kein flacher Multiplikator
  mehr auf alles (aktuelles `prestigeMultiplierPerLevel`-Modell), sondern
  gezielt auf einzelne Ressourcen wirkend, um unterschiedliche Spielweisen zu
  unterstützen (ähnliches Prinzip wie `targetResource` im Skill-Baum, siehe
  Abschnitt 9 im Design-Doc). Deshalb auch die `PRESTIGE ×1.00`-Kachel wieder
  aus dem neuen Statistik-Dialog entfernt (`StatsView.vue`,
  `PlayerStatsDto#prestigeMultiplier` samt Backend-Berechnung in
  `NetWorthService#getStats()` raus) — hätte ein Modell gezeigt, das eh bald
  nicht mehr stimmt. Kommt neu rein, sobald das tatsächliche System steht.
- [ ] **Balancing** — alle Platzhalter-Zahlen (sellFeeRate aktuell `0.05`
  fix im Code, Prestige-Schwelle/Multiplikator, Rezept-Mengen/Output/
  Backzeit, Skill-Baum-Effektwerte/Skill-Punkt-Kostenkurve) sind nie in
  einer echten Testphase durchgespielt worden. Nächster sinnvoller Schritt
  vor Early-Access: ein bis zwei interne Testrunden, dann Werte in
  `MarketConfig`, `RecipeEntity`-Seeds, `SkillNodeEntity`-Seeds nachziehen
- [ ] **Kosmetik-System** — Design-Doc Abschnitt 11 lässt bewusst offen,
  was "freigeschaltete Kosmetik" konkret bedeutet (Titel? Rahmen? Icons?).
  `PlayerCosmeticEntity` als Datenmodell vorgesehen, aber ohne konkrete
  Inhalte nicht sinnvoll baubar — Design-Entscheidung nötig, bevor hier
  Code entsteht
- [ ] **Season-Automatisierung** — aktuell rein manuell ausgelöst
  (`AdminController`). Falls das Spiel produktiv läuft, überlegen ob ein
  Scheduler (`SeasonScheduler`, analog `MarketScheduler`) mit konfigu-
  rierbarem Intervall sinnvoller ist als "Dev drückt manuell einen Knopf"
- [ ] **Echtes Steam-Avatar im Profil.** Seit 2026-08-04 zeigt `PlayerProfileView.vue`
  den echten Steam-Anzeigenamen (`steamworks.js` `localplayer.getName()`, wird bei
  jedem Login serverseitig auf `UserEntity.displayName` resynct). Das Profilbild
  ist aber weiterhin ein Pixel-Icon-Platzhalter — `steamworks.js@0.4.0` hat keine
  Avatar-API (auch nicht auf `main` im Repo geprüft, Stand 2026-08-04). Für ein
  echtes Bild: Steam Web API (`ISteamUser/GetPlayerSummaries/v2`) serverseitig
  mit einem Web-API-Key (https://steamcommunity.com/dev/apikey) aufrufen,
  `avatarfull`-URL am `UserEntity` cachen. Key als Server-Secret, nie committen.
- [ ] **Steam-Deck-Controller-Steuerung.** Angefragt 2026-08-04 nach erstem
  Test auf echtem Steam Deck. Teilweise umgesetzt:
  - [x] **Linker Stick pannt die Kamera** — erledigt 2026-08-05.
    `FarmGridView.vue` liest `navigator.getGamepads()` im selben rAF-Loop wie
    WASD (`camTick`), gleiche Geschwindigkeit/Clamping/Settings-Anbindung
    (`useCameraControls`) wie die Tastatur. Aktiviert sich automatisch über
    `gamepadconnected` (Browser meldet das erst nach echter Eingabe am
    Controller — passt zu "wenn ein Controller genutzt wird"), kein
    Deck-spezifischer Erkennungs-Code nötig dafür.
  - [x] **Hotkey-Badges zeigen automatisch das passende Controller-Symbol** —
    erledigt 2026-08-17 (`docs/plans/2026-08-17-done-controller-hotkey-icons.md`).
    Neuer Composable `useInputMethod.js` erkennt per rAF-Polling auf
    `navigator.getGamepads()`, ob zuletzt Tastatur/Maus oder Controller
    benutzt wurde, plus Controller-Familie (Xbox/PlayStation/generisch) aus
    `Gamepad.id`. `ShortcutSlot.vue` zeigt dann statt Text-Badge ein
    `ControllerButtonIcon.vue` (farbiges Label je Familie, keine neuen
    SVG-Assets). B/Circle schließt jetzt global Dialoge, Y/Triangle
    zentriert die Kamera — vorher hatte ein reiner Controller-Spieler dafür
    gar keine Eingabe. Live-Test mit echtem Pad steht noch aus (kein
    Browser/Gamepad in der Dev-Umgebung verfügbar).
  - **Erkennung:** `steamworks.js` bringt `isSteamRunningOnSteamDeck()`
    fertig mit (`client.d.ts`) — automatischer Check beim Start in
    `electron/main.js`, Ergebnis per IPC (analog `steam-auth`) ans Frontend
    durchreichen, z.B. `steam-deck-mode` Event oder Teil des bestehenden
    `steam-auth`-Payloads.
  - [x] **Fadenkreuz-/Cursor-Modus + Skilltree-Gamepad-Parität** — Code
    steht (2026-08-17, `docs/plans/2026-08-17-open-controller-crosshair-
    cursor.md`), **noch nicht live mit Controller getestet**. Neuer
    Composable `useGamepadCursor.js` (`mode`: `'fixed'`/`'free'`) +
    `GamepadCursor.vue` (Ring-Cursor-Overlay, palette-konforme Neufassung
    der Playwright-Trailer-Vorlage aus `docs/steam-website/trailer/
    record-clips.js`, kein Asset). `FarmGridView.vue`: A hit-testet
    Gebäude-Bounding-Boxes (`fixed`-Modus, kein Dialog offen) bzw. klickt
    generisch per `elementFromPoint().click()` (jeder offene Dialog außer
    Skilltree). R3 (nicht L3 wie ursprünglich geplant — User-Entscheidung)
    togglet `fixed`↔`free`. `SkillTreeView.vue` hat jetzt dieselbe
    Stick-Pan/D-Pad-Zoom/A-Klick-Logik in einer eigenen kleinen rAF-Schleife
    (eigene Pan/Zoom-Welt, teilt sich aber `useGamepadCursor`s Zustand mit
    FarmGridView). Zoom-Geschwindigkeit jetzt einstellbar
    (`useCameraControls.js` `zoomSpeed`, Settings-Slider) statt
    hartkodierter Konstante.
  - [ ] **Settings-Hotkey-Liste als zwei beschriftete Spalten** (Tastatur/
    Maus links, Controller rechts) statt der aktuellen Buttons ohne klare
    Spalten-Beschriftung — Feedback aus dem 2026-08-17-Testlauf.
  - [x] **`NestedTooltip.vue`s 1s-Appear/Close-Timer entfernt, spielweit
    (2026-08-21).** Sollte sofort erscheinen/verschwinden statt verzögert
    — mehrere Nutzer-Beschwerden über den sichtbaren Timeline-Balken
    (Musik-Skip-Button, Sprachumschalter, Skilltree-Schließen-Button).
    Neuer `instant`-Prop auf `NestedTooltip.vue` (kein Fill-/Drain-Balken,
    kein Delay) zunächst nur in `SettingsDialog.vue` verwendet, dann auf
    Ansage ("im ganzen Spiel nicht mehr genutzt werden") auf **alle**
    verbleibenden `NestedTooltip`-Stellen ausgeweitet (`ResourceBar.vue`,
    `MainMenuView.vue`, `PriceChart.vue`, `NetWorthDialog.vue`,
    `PlayerProfileView.vue`, `StatsDialog.vue`, `BuildingDetailDialog.vue`,
    `SkillTreeDialog.vue`, `SkillTreeAdminDialog.vue`, `FarmGridView.vue`)
    inkl. des rekursiven inneren Tooltips für `tt-highlight`-Begriffe in
    `NestedTooltip.vue` selbst. **Bekannte Nebenwirkung:** der verschachtelte
    Erklär-Tooltip in `ResourceBar.vue` ("Verkaufswert" → Erklärung) ist
    dadurch praktisch unerreichbar — das Popup schließt sofort beim
    Verlassen des auslösenden Elements, die Maus hat keine Zeit mehr, in
    den Popup zum verlinkten Begriff zu wandern. Bewusst so in Kauf
    genommen (explizite Nutzer-Ansage), falls das stört: eigener Fix nötig
    (z. B. den Erklärtext direkt inline statt als verschachtelten Tooltip).
    **Zusätzlicher Positionierungs-Bug beim Ausrollen gefunden:** Popup
    vom "Gebäude bauen"-Button (unten rechts) erschien oben links im
    Fenster statt daneben. Ursache: `.tooltip-trigger` (der `<span>`, den
    `NestedTooltip` um seinen Slot-Inhalt legt) ist ein normales
    `display:inline`-Element ohne eigene Größe — ist der eingebettete
    Button selbst `position:absolute`/`fixed` (build-fab, cam-center,
    alle `px-close`-Schließen-Buttons), trägt er nichts zur Box des Spans
    bei, der kollabiert auf 0×0 an Position (0,0). `getBoundingClientRect()`
    für die Popup-Position lief bisher auf dem Span selbst — jetzt auf
    dessen erstem echten Kind-Element (`e.currentTarget.firstElementChild`),
    das ist immer der tatsächlich sichtbare Trigger-Inhalt. Behebt das für
    alle betroffenen Stellen auf einmal, nicht nur den Bau-Button.
    **Danach noch verbleibendes Problem:** selbst mit korrekter Trigger-
    Position kann das Popup bei Triggern nah am Bildschirmrand (z. B.
    Bau-Button unten rechts) über den Viewport hinausragen, je nach
    Textlänge. Erster Ansatz (`clampToViewport()`, nach dem Rendern per
    `nextTick` die echte Größe messen und `posX`/`posY` zurückklemmen)
    verursachte sichtbares Flackern — Popup rendert kurz an der falschen
    Stelle, springt dann ruckartig um. **Ersetzt durch Vorab-Seitenwahl:**
    `positionPopup()` entscheidet synchron in `onTriggerEnter`, noch vor
    dem ersten Frame, anhand der Trigger-Position + der bekannten
    `max-width:340px`/geschätzten Höhe (200px, großzügig), ob das Popup
    rechts/links bzw. oben/unten ankert (`anchorLeft`/`anchorRight`/
    `anchorTop`/`anchorBottom`-Refs, immer nur eine Seite pro Achse
    gesetzt, CSS wächst von der jeweils richtigen Seite aus) — kein
    Messen-dann-Verschieben mehr, Position steht von Anfang an fest, kein
    Flackern.
  - [ ] Y/Center-Bindung fürs Skilltree-eigene "Kamera zentrieren" (aktuell
    nur FarmGridView).
  - [x] **Spieler-Skillbaum-Suche zentriert + Pfad-Wegweiser zum Treffer
    (2026-08-21).** Zwei Nutzer-Beschwerden: Suchbox saß oben rechts statt
    wie im Admin-Editor zentriert; beim Suchen war nicht erkennbar, wie
    man vom aktuellen Fortschritt zum Treffer kommt. `SkillTreeView.vue`s
    `.stv-search-box` jetzt `top:14px;left:50%` (zentriert, **oberhalb**
    des Skillpunkte-Badges — `.stv-points-badge` dafür auf `top:64px`
    verschoben, explizit vom Nutzer so priorisiert). Neuer
    `searchPathEdgeKeys`-Computed: Multi-Source-BFS ab allen bereits
    allozierten Knoten gleichzeitig (nicht nur ab root) zu jedem
    Suchtreffer, markiert die kürzeste Kanten-Kette dorthin mit neuer
    `.stv-edge-search-path`-Klasse (grüner Puls-Glow, gleiches Muster wie
    der bestehende Knoten-Treffer-Glow, nur auf `stroke`/`drop-shadow`
    statt `box-shadow`).
  L1/R1-Gebäude-Zyklus siehe §7.2 Backlog weiter unten.
- [x] **Passiver Skill-Baum ersetzt Upgrade-System (2026-08-06).** Das alte
  3-Upgrade-Regal (`boost_harvest`, `boost_harvest_speed`, `boost_bake`) war
  kaum ein Cookie-Sink und bot keine echte Wahl. Komplett ersetzt durch
  einen Path-of-Exile-artigen Passiv-Baum (18 Knoten + Wurzel, 4 Zweige:
  MILK/BAKING/MARKET/CORE) mit PoE-Konnektivitätsregel — Details:
  `cookie-game-design.md` Abschnitt 9. Alte `UpgradeEntity`/
  `PlayerUpgradeEntity`/`UpgradeService`/`UpgradeController` samt Frontend
  (`UpgradeDialog.vue`/`UpgradeShopView.vue`) vollständig entfernt,
  `upgradeValue` überall zu `skillTreeValue` umbenannt (Net Worth, DTOs,
  `NetWorthHistoryEntity`). Zwei bewusst zurückgestellte Folgepunkte:
  - [x] **Anti-Cheat-Re-Verifikation für Skill-Allokationen (2026-08-21,
    live beobachtet statt nur theoretisch).** Der Allokations-Endpunkt
    prüfte die Konnektivität nur beim Freischalten selbst — keine
    Re-Validierung bestehender `player_skill_nodes`-Zeilen danach. Live im
    Admin-Editor reproduziert: Node/Edge löschen kann einen bereits
    alloziierten, entfernteren Knoten vom Baum abschneiden (Screenshot:
    grüner Knoten nur noch über nicht-allozierte Nachbarn verbunden), ohne
    dass irgendetwas das verhindert oder meldet. `allocateNode`/
    `deallocateNode` selbst sind korrekt (geprüft: Allokation verlangt
    direkten alloziierten Nachbarn, Respec blockiert per `reachableFromRoot`-
    BFS, falls das Entfernen einen ANDEREN Knoten abschneiden würde) — die
    Lücke war ausschließlich der Admin-Editor-Pfad, der Edges/Nodes ändert,
    ohne bestehende Spieler-Allokationen dagegen neu zu prüfen.
    **Fix:** neue `SkillTreeService#repairDisconnectedAllocations()` —
    sammelt pro Spieler alle `player_skill_nodes`, prüft per
    `reachableFromRoot()` echte Erreichbarkeit über den aktuellen
    Kantenbestand, entfernt nicht mehr erreichbare Zeilen und erstattet je
    einen Skillpunkt (kein Spielerfehler, war ein Admin-Edit). Automatisch
    aufgerufen nach `DELETE .../skilltree/nodes/{id}` und
    `DELETE .../skilltree/edges/{id}` (die beiden topologie-verkleinernden
    Admin-Aktionen) — Response trägt jetzt zusätzlich
    `repairedAllocations`. Neuer manueller Endpunkt
    `POST /admin/skilltree/repair` + Button "Reparieren" im Admin-Editor-
    Toolbar, um bereits VOR diesem Fix entstandene kaputte Zustände
    einmalig nachträglich zu bereinigen (der Auto-Trigger greift nur bei
    zukünftigen Löschungen).
    **Konkreter Fall live nachverfolgt:** der zuerst gemeldete "Fair Pay"-
    Knoten (`sugar_2`) war KEIN Validierungs-Bug — `repairDisconnectedAllocations`
    lieferte beim ersten Aufruf korrekt `0`, weil eine falsche Extra-Kante
    `root-sugar_2` existierte (Root direkt mit `sugar_2` verbunden, vorbei
    am eigentlich vorgesehenen `sugar_1` — vermutlich versehentlich im
    Connect-Modus des Admin-Editors gesetzt). Root gilt immer als
    alloziert, die Konnektivitätsprüfung war also technisch korrekt
    erfüllt, nur die Kante selbst war Datenmüll. Nach `DELETE
    .../skilltree/edges/root-sugar_2` griff der Auto-Repair sofort
    (`repairedAllocations: 1`). Stichprobe über den ganzen Baum
    (Root-Kanten-Anzahl, branch-übergreifende Kanten) zeigte keine
    weiteren solchen Streukanten — nur die zwei bekannten Design-Bridges
    (`bridge_bake_market`) und der vom Nutzer selbst angelegte Test-Node
    `"12"`.
    **Ursache an der Quelle geschlossen:** `createSkillEdge` lehnt jetzt
    jede neue Kante ab, die Root involviert (400) — die 11 Branch-Start-
    Kanten kommen ausschließlich aus dem Seed (`buildEdges()`), nie mehr
    aus dem Editor. Verhindert, dass derselbe Bypass für einen beliebigen
    anderen Knoten erneut entstehen kann ("muss bei jeder Node so sein,
    nicht nur bei der einen" — Nutzer-Vorgabe).
  - [ ] **Prestige-Bonuspunkte.** Ursprünglich angedacht: Prestige gibt
    +3 Skill-Punkte on top des normalen Resets. Bewusst außerhalb des
    Scopes beim Erstbau des Skill-Baums (Prestige-UI ist ohnehin gerade
    aus dem Frontend entfernt, siehe Eintrag oben) — beim Prestige-Neubau
    mit einplanen.
- [x] **Skill-Baum-UI-Nachbesserungen nach erstem Live-Test (2026-08-06).**
  Direktes Feedback beim ersten Ausprobieren im Browser:
  - Tooltip-Popups öffneten sich immer oben rechts in der Ecke statt am
    gehoverten Knoten. Ursache: `PixelInfoPopover` positioniert sich über
    `getBoundingClientRect()` seines eigenen Wrapper-Divs (`pip-wrap`) — die
    Knoten-Koordinaten wurden aber nur auf den inneren `<button>` gelegt,
    nicht auf den Popover-Wrapper selbst. Da `pip-wrap` (kein eigenes Layout,
    `width:100%`) dadurch für alle 19 Knoten am gleichen Fleck (oben,
    volle Breite, Höhe 0) im Dokumentfluss landete, zeigte jedes Popup zur
    selben Stelle. **Fix:** Positionierung (`position:absolute;left;top`)
    jetzt direkt per `:style` auf `<PixelInfoPopover>` selbst statt auf den
    Button (Vue reicht `style`/`class` an die Root-Node der Kind-Komponente
    durch, inkl. Scoped-CSS-Attribut vom Elternteil).
  - Skill-Punkte-Kauf-Leiste war eine volltransparente Kopfzeile über dem
    Baum — jetzt ein schwebendes, zentriertes HUD-Element über der Canvas
    (`.stv-buy-hud`), kein Platz mehr vom Canvas abgezogen.
  - Zentrieren-Button war nur ein winziges, unbeschriftetes Icon in der Ecke
    der alten Kopfzeile — jetzt wie in `FarmGridView.vue`s Kamerasteuerung
    ein deutlich sichtbarer Button mit Text-Hinweis, unten links über der
    Canvas.
  - Skill-Punkt-Kosten deutlich angehoben (`skillPointBaseCost` 50→150,
    `skillPointCostGrowth` 1.15→1.4, siehe Balancing-Punkt oben) — war zu
    billig/flach für den Haupt-Cookie-Sink. Im Gegenzug alle Knoten-
    Effektwerte auf ca. ein Drittel reduziert (z. B. `milk_4` +30%→+10%,
    `bake_4` +12%→+5%) — der Baum soll vom Sammeln vieler Punkte über
    längere Spielzeit leben, nicht von wenigen Käufen mit riesigem
    Einzeleffekt. Beide Änderungen live per Admin-API auf den laufenden
    Dev-Server angewendet (kein Neustart nötig, `GameBalanceConfig` +
    `SkillNodeEntity` sind zur Laufzeit editierbar) und im Java-Seed-Code
    nachgezogen, damit künftige Frisch-Installationen dieselben Werte
    bekommen.
  - Zusätzlich beim Nachbessern gefunden: `NetWorthHistoryEntity`-Umbenennung
    (`upgradeValue`→`skillTreeValue`) ließ Hibernates `ddl-auto=update` an
    einer bereits befüllten `networth_history`-Tabelle scheitern (`ALTER
    TABLE ADD COLUMN ... NOT NULL` auf Zeilen mit Bestandsdaten schlägt
    fehl) — der alle-30s-Snapshot-Job crashte seitdem endlos im Hintergrund.
    Tabelle ist reine Verlaufshistorie (DB laut Vereinbarung disposable) →
    einmalig per `DROP TABLE networth_history;` bereinigt, Hibernate legt
    sie beim nächsten Start korrekt neu an. **Für künftige Spalten-
    Umbenennungen an schon befüllten Tabellen:** entweder vorher
    `DROP TABLE`/-Spalte, oder Feld erst mit `@Column(nullable=true)`
    einführen und in einem zweiten Schritt auf `NOT NULL` umstellen, sonst
    bricht `ddl-auto=update` an der Bestandsdaten-Migration ab.
- [x] **HUD-Rechtsseite in Dropdown-Menü zusammengefasst + Admin-Dialog
  entfernt (2026-08-06).** Die einzelnen HUD-Buttons (DEV-Reset, Admin,
  Skill-Baum, Rangliste, Avatar/Profil) sind jetzt ein einziger
  Hamburger-Button (`FarmGridView.vue`), der ein Pixel-Art-Dropdown öffnet:
  Profil, Skill-Baum, Rangliste, Einstellungen, dazu DEV-Reset nur im
  Dev-Modus. Schließt bei Klick außerhalb (`mousedown`-Listener auf
  `document`) oder nach Auswahl eines Eintrags. `AdminDialog.vue` +
  zugehörige Locale-Dateien (`adminDialog.json` de/en) komplett gelöscht —
  der Spieler-seitige Zugriff auf Live-Balance-Config/Skill-Node-CRUD war
  als versehentlich klickbarer Button zu riskant für die Beta.
  **Bewusst nicht angetastet:** die Backend-Endpunkte selbst
  (`AdminConfigController`, `AdminController`) bleiben bestehen und weiter
  per `curl`+Admin-Token nutzbar — nur der UI-Zugang ist weg.
- [x] **Skillpunkt-Stern überlappte die obere HUD-Leiste (2026-08-20).**
  `.hud-skillpoint-star` positioniert sich `top: calc(100% + 8px)` relativ
  zu `.hud-menu-wrap` — der Wrapper war aber nur so hoch wie der
  Hamburger-Button selbst und saß (via `.hud`s `align-items:center`)
  vertikal zentriert in der höheren HUD-Leiste, statt bündig an deren
  Unterkante. Der Stern-Button ragte dadurch sichtbar in die Leiste rein.
  **Fix:** `.hud-actions` + `.hud-menu-wrap` bekommen `align-self:stretch`
  (Wrapper spannt sich jetzt über die volle Zeilenhöhe auf), Hamburger-
  Button bleibt über `display:flex;align-items:center` auf dem Wrapper
  selbst zentriert — `top:100%` zeigt jetzt auf die echte HUD-Unterkante.
  **Nur per Code-Review gefixt, nicht im Browser gegengetestet** (Sandbox
  ohne Display) — kurz visuell verifizieren.
- [x] **Skill-Baum Admin-Editor: Nodes draggen + Verbindungen setzen/löschen
  (2026-08-10).** Neuer `isDev`-Menüpunkt "SKILL-BAUM ADMIN" im
  Hamburger-Menü öffnet `SkillTreeAdminDialog.vue` (eigenständige
  Fullscreen-Canvas, Pan/Zoom wie der Spieler-Baum). Node per Drag
  verschieben speichert x/y sofort per `PUT /admin/skilltree/nodes/{id}`;
  "Verbinden"-Modus + Klick auf zwei Nodes erstellt eine Kante per neuem
  `POST /admin/skilltree/edges`; Klick auf eine Kante löscht sie per
  neuem `DELETE /admin/skilltree/edges/{id}`. Details/Plan:
  `docs/plans/2026-08-10-open-skillbaum-admin-editor.md`.
  **Offene Erweiterungen fürs Tool** (noch nicht gebaut):
  - [x] **Werte-Editor: Effekte (2026-08-12).** Info-Panel im selben Dialog
    zeigt bei ausgewähltem Node jetzt die Effekt-Liste und macht sie
    editierbar — pro Effekt ein Dropdown für `effectType` (Labels
    wiederverwenden `skillTreeView`s bestehende i18n-Keys), ein Dropdown
    für `targetResource` (inkl. "(global)" für `null`), ein Vorzeichen-
    Toggle-Button (grün/rot) + Zahlenfeld für den Betrag, Löschen pro
    Effekt-Zeile, "+ Effekt" zum Hinzufügen, "Speichern" schickt den
    kompletten Node per `PUT /admin/skilltree/nodes/{id}` (Endpoint konnte
    das schon). Getestet gegen die laufende API (Wert geändert, Effekt
    hinzugefügt, per GET verifiziert, wieder zurückgesetzt).
  - [x] **Werte-Editor: Name/Beschreibung/Branch/Tier + Icon (2026-08-19).**
    Info-Panel hat jetzt Name DE/EN, Beschreibung DE/EN, Branch/Tier
    (Dropdowns) und ein frei wählbares Icon (Dropdown, "(automatisch)"
    fällt auf die bestehende Branch-Ableitung zurück) — alles per
    "Speichern" persistiert. Neues nullable `SkillNodeEntity.icon`-Feld,
    auch im Spieler-Baum (`SkillNodeStatusDto`/`SkillTreeView.vue`)
    berücksichtigt. Dabei gefunden und mitgefixt: Effekt-Editor ließ für
    JEDEN Effekttyp jede Ressource wählen, obwohl `BAKE_OUTPUT`,
    `MARKET_FEE_REDUCTION`, `WAGE_INTEREST_REDUCTION`, `STORAGE_CAP_BONUS`,
    `BUILDING_BUFFER_BONUS` serverseitig NUR global abgefragt werden
    (`getEffectTotal(..., targetResource=null)`) — eine gesetzte Ressource
    auf einem dieser Typen war ein stiller Dead-Node ohne Fehlermeldung.
    Ressourcen-Dropdown für diese Typen jetzt gesperrt + Hinweistext.
    Zusätzlich `window.prompt`/`window.confirm` (Node-ID, Lösch-Bestätigung)
    durch auto-generierte IDs bzw. neue wiederverwendbare
    `PixelConfirmDialog.vue`-Komponente ersetzt (native Browser-Popups
    passten nicht zum Pixel-Art-Design). Details:
    `docs/plans/2026-08-19-done-skillbaum-admin-node-editor-ausbau.md`.
    **Live-DB-Test steht noch aus** (Sandbox ohne Postgres).
  - [x] **Neue Nodes erstellen/löschen (2026-08-19).** Editor konnte bisher
    nur bestehende (geseedete) Nodes verschieben/verbinden. Neu:
    `POST /admin/skilltree/nodes` (frei wählbare ID, Duplikat → 409,
    effectType-Validierung wie beim bestehenden `PUT`) und
    `DELETE /admin/skilltree/nodes/{id}` (Root-Node geschützt → 400,
    Node mit Spieler-Allokation geschützt → 409, referenzierende Edges
    werden mitgelöscht). Frontend: neuer "+ Node"-Toolbar-Modus, Klick auf
    leere Canvas-Fläche legt Node an aktuellem Weltpunkt an (ID per
    Prompt), Lösch-Button im Info-Panel mit Bestätigung. Details:
    `docs/plans/2026-08-13-done-skillbaum-admin-nodes-crud.md`.
    **Live-DB-Test steht noch aus** (Sandbox ohne Postgres-Zugriff) — vor
    dem nächsten Einsatz einmal mit laufendem Dev-Stack durchklicken.
  - [ ] **Ganzen Baum als JSON exportieren/importieren** — damit sich ein
    Skill-Baum für die nächste Season vorab (lokal, ohne Live-Server)
    planen und danach vor Season-Start auf den Server hochladen lässt,
    statt live am Produktiv-Baum rumzuklicken. Export: ein Endpoint, der
    alle Nodes+Edges als ein JSON-Dokument liefert (Snapshot). Import:
    Gegenstück, das dieses JSON in `skill_nodes`/`skill_edges` einspielt
    (vermutlich: vorhandene Zeilen ersetzen statt nur upsert-missing wie
    `seedTree()`, sonst kommen alte Nodes nie raus — braucht eigene
    Transaktion/Validierung, nicht einfach `seedTree()` wiederverwenden).
    Passt zeitlich am besten neben den Season-Reset-Admin-Endpoint (siehe
    `docs/cookie-game-design.md` Abschnitt 9). Plan:
    `docs/plans/2026-08-21-open-skillbaum-export-import-sharing.md`.
  - [ ] **Spieler-Builds als Code teilen/importieren** — eigene Node-
    Allokation (nicht die Baum-Struktur) als Code exportieren, andere
    Spieler importieren ihn für denselben Build. Braucht Server-Validierung
    (nie Client-Node-IDs vertrauen) + Kosten-Modell-Entscheidung fürs Bulk-
    Respec. Plan: `docs/plans/2026-08-21-open-skillbaum-export-import-sharing.md`.
  - [x] **Nodes klonen (2026-08-19).** Neuer "Klonen"-Button im Info-Panel
    (neben "Knoten löschen"), fragt neue ID per Prompt ab, übernimmt Name/
    Branch/Tier/Effekte (tiefe Kopie) der Quell-Node mit `+40/+40`
    Positions-Versatz, immer `root: false`, keine Kanten. Reiner
    Frontend-Feature auf Basis des bestehenden
    `POST /admin/skilltree/nodes`-Endpoints, kein Backend-Change nötig.
    Details: `docs/plans/2026-08-13-done-skillbaum-admin-node-clone.md`.
- [x] **Auto-Verkauf bei vollem Lager entfernt (2026-08-07).** Bisher wurde
  Überschuss beim Hover-Ernten (`UserService#harvest`) und bei passiver
  Produktion (`PassiveIncomeService#collectBuilding`, damals noch `creditUser`
  vor dem Ansammeln-Redesign weiter unten) automatisch zum aktuellen
  Marktpreis in Cookies umgewandelt, sobald das Lager voll war — fühlte sich
  wie ein unsichtbarer Dauer-Verkauf an, nicht wie eine bewusste
  Spielerentscheidung. Jetzt wird Überschuss schlicht nicht mehr
  gutgeschrieben (weder Ressource noch Cookies). Frontend-Feedback dazu:
  Hover-Ring auf Produktionsgebäuden wird rot statt grün
  (`BuildingFrame.vue`, neuer `blocked`-Prop), kurzes Popover ("Lager voll")
  beim Hover, Gebäude optisch gedimmt wie bei nicht bezahlbarem Lohn
  (`.building-idle`, `FarmGridView.vue`s neuer `isStorageFull`-Computed).
  Alte "Auto-Verkauf"-Beschreibung aus `LagerDialog.vue` und
  `buildingInfo.js` (Gebäude-Hover-Popup) entfernt, war ohnehin größtenteils
  nur Text ohne echte Anbindung im letzteren Fall.
  **Folgearbeit:** ein echter Ausgleich für volles Lager als größere
  Mechanik im Skill-/Passiv-Baum (Abschnitt 9 im Design-Doc) statt des
  pauschalen automatischen Verkaufs — umgesetzt 2026-08-10 als
  STORAGE-Branch, `docs/plans/2026-08-10-open-skillbaum-lager-branch.md`
  (noch nicht committed).
- [x] **Start-Balance-Bug: Lager sofort überfüllt (2026-08-07).** Jeder neue
  Spieler startete mit 1000 von jeder der 6 Rohstoff-Ressourcen (6000
  insgesamt) bei nur 1100 Lagerkapazität — schon vor dem ersten Klick 5-fach
  überfüllt (`player.initial-sugar` etc. in `application.properties` standen
  auf 1000 statt 0, vermutlich ein Leftover). **Fix:** Startressourcen auf 0,
  Start-Cookies 100→400 (reicht für genau eines der drei günstigsten
  Produktionsgebäude, nicht für alle), plus 1 kostenloser Skill-Punkt bei
  Accounterstellung (`player.initial-skill-points`, neues `PlayerConfig`-
  Feld) — macht den Skill-Baum von Anfang an Teil der ersten strategischen
  Entscheidung. Details: `cookie-game-design.md` Abschnitt 4.
  **Hinweis für den Live-Server:** falls dort `balance.base-storage-cap`
  jemals per Admin-API auf einen abweichenden Wert gesetzt wurde, überschreibt
  das die neuen `application.properties`-Defaults erst nach einem Neustart
  des Backends (der Wert liegt nur im Speicher, nicht in der DB).
- [x] **Statistik-Dialog (2026-08-07).** Neuer Vollbild-Dialog (Hauptmenü →
  "Statistiken", `StatsDialog.vue`/`StatsView.vue`) mit Wirtschafts-Übersicht,
  Produktions-Tabelle + aktiven Skill-Baum-Boni und Lifetime-Zählern
  (geerntete Menge pro Ressource, Markt-Umsatz gekauft/verkauft). Neue
  `UserEntity`-Felder + `GET /api/v1/players/{steamId}/stats`. Details:
  `cookie-game-design.md` Abschnitt 10.
  **Zurückgestellte Idee aus derselben Session:** Crit-Chance beim Ernten/
  Backen (Chance auf überproportionalen Bonus-Ertrag) — jetzt spezifiziert
  (inkl. passiver Arbeiter-Produktion, nicht nur Backen) in
  `docs/plans/2026-08-10-open-skillbaum-crit-system.md`. Beim Umsetzen an
  Crit-Ausbeute in der Statistik (Lifetime-Zähler) denken.
- [x] **Passive Produktion: Ansammeln + manuell einsammeln statt 5s-Server-
  Tick (2026-08-07).** Bug: `PassiveIncomeScheduler` schrieb alle 5s im
  Hintergrund direkt in `UserEntity`-Ressourcen, für JEDEN je registrierten
  Spieler, unabhängig von Aktivität — das Frontend erfuhr davon nichts, bis
  irgendein anderer API-Call (meist die Hover-Ernte-Sync alle 3s) zufällig
  einen vollen `UserInformationDto`-Snapshot zurückgab und alle 6 Ressourcen
  auf einmal überschrieb. Sah für Spieler wie ein Ressourcen-Sprung beim
  Hovern aus, war aber angesammeltes, unsichtbares Hintergrund-Einkommen.
  **Fix:** jedes Produktionsgebäude sammelt jetzt selbst an
  (`PlayerBuildingEntity#pendingAmount`/`lastSettledAt`), gedeckelt durch
  eine neue gebäudeeigene `storageCapacity` (`BuildingService#BuildingDef`)
  — bei voller Lagerung stoppt die Produktion, bis eingesammelt wird (wie
  eine Miete). `PassiveIncomeScheduler` komplett entfernt; Fortschritt wird
  lazy über `BuildingService#settle()` berechnet, ausgelöst nur bei
  tatsächlichen Ereignissen (Lesen fürs Anzeigen — ohne Persistieren,
  Einsammeln, Arbeiter-/Stufen-Änderung, Lohn-Idle-Wechsel im ohnehin
  laufenden 60s-`WageScheduler`). Einsammeln über neuen Endpoint
  `POST /api/v1/farm/buildings/collect/{userId}/{buildingId}`
  (`PassiveIncomeService#collectBuilding`, ersetzt das alte `creditUser`),
  erreichbar per Klick-Badge direkt auf der Hofkarte (kein Dialog nötig,
  `BuildingFrame.vue`) oder über einen Button im Gebäude-Dialog
  (`BuildingDetailDialog.vue`). `GameBalanceConfig#passiveTickSeconds`
  entfernt (kein fester Tick mehr). Details: `cookie-game-design.md`
  Abschnitt 5.
  **Zurückgestellte Idee aus derselben Session:** ein "Alles einsammeln"-
  Sammel-Button — vom Spieler explizit auf später verschoben, könnte im
  Rathaus-Dialog (und ggf. anderswo) landen, sobald der Bedarf (viele
  Gebäude gleichzeitig voll) tatsächlich auftritt.
- [x] **Lohn skaliert mit Arbeiterzahl + Dispo-Kredit statt Komplett-Idle
  (2026-08-09).** Zwei Bugs gemeldet: (1) Gebäude-Dialog/Hofkarten-Popup
  zeigten Lohn/Ertrag/Hover-Rate aus statischen Mockup-Platzhaltern
  (`buildingInfo.js`), nie an Stufe/Arbeiterzahl gekoppelt — obwohl der
  Hinweistext im Dialog genau das versprach. (2) Backend-seitig war der Lohn
  ohnehin pauschal pro Gebäude, unabhängig von Arbeiterzahl. **Fix:**
  Dialog/Popup lesen jetzt echte Live-Werte aus dem Store,
  `BuildingService#effectiveWage` skaliert mit `Arbeiterzahl ×
  wagePerMinPerWorker` (Default 2 C/min, reproduziert die alte Balance bei
  Stufe-1-Vollbesatzung 1:1). Hover-Rate-Anzeige ebenfalls korrigiert (war
  frei erfunden pro Ressource, real einheitlich laut `UserService#harvest`).
  **Zusätzlich vom Spieler gewünscht:** Dispo-Kredit statt sofortigem
  Komplett-Idle bei zu wenig Cookies (Cookies dürfen ins Minus, 10 %
  Zinsen/Tick, Dispo-Grenze = Lohn×8 als harter Stopp, neuer DISPO-Skill-
  Baum-Zweig zur Zinsreduktion), rote fallende Zahl am Cookie-HUD bei jeder
  Abbuchung, neue Abrechnungshistorie im Rathaus. Details:
  `cookie-game-design.md` Abschnitt 5 + 9.
  **Migrations-Falle dabei gefunden und gefixt:** `SkillTreeService#seedTree()`
  seedete Knoten/Kanten bisher nur `if (count == 0)` — neue Knoten
  (DISPO-Zweig) wären auf der bereits befüllten Dev-/Live-DB nie
  angekommen. Auf Upsert (fehlende IDs nachziehen) umgestellt, siehe
  Abschnitt 0 für den zugehörigen CHECK-Constraint-Bug beim neuen
  `EffectType`-Wert.
  **Nicht mit angefasst:** Markt-Hover-Popup zeigt weiterhin eine statische
  Marktgebühr (siehe Eintrag unter Abschnitt 2, Aufräumarbeiten).
- [ ] **Pixel-Art-Rework — Entscheidung gegenchecken.** Design-Doc
  (Abschnitt 8, Stand 2026-08-02) führt das DOM+CSS-Ergebnis jetzt als
  "fertig, kein Plan mehr" statt als offene Render-Engine-Frage — inferiert
  aus dem Umfang der bisherigen Polish-Arbeit (Pixel-Icons, Sound, Hotkeys,
  frei verschiebbare Gebäude), nicht explizit vom Dev bestätigt. Falls doch
  noch PixiJS/Phaser mit freier Kamera geplant ist: Design-Doc Abschnitt 8
  entsprechend zurückstufen.
- [ ] **Discord-Integration (Idee, 2026-08-20).** Aktuell nur ein
  statischer Einladungs-Link im Hauptmenü (`frontend/src/discord.js`,
  `DISCORD_URL`), keine echte Anbindung. Drei Ausbaustufen, aufsteigender
  Aufwand — können unabhängig voneinander umgesetzt werden:
  - [ ] **Rich Presence.** Electron zeigt im Discord-Status "spielt
    Cookie, Hof-Level X" o. ä. — rein clientseitig (`discord-rpc`-Paket
    im Electron-Main-Prozess), kein Backend-Bezug. Kleinster Aufwand,
    größte Sichtbarkeit — guter Einstiegspunkt.
  - [ ] **Webhooks.** Backend postet Events (Rangliste-Änderungen,
    Season-Reset, Meilensteine) per Discord-Webhook-URL in einen Channel
    des Servers — einfacher Server-seitiger POST, kein Bot-Hosting nötig.
  - [ ] **Bot mit Steam↔Discord-Account-Verknüpfung.** Slash-Commands im
    Discord-Server fragen Spieler-Stats/Rangliste direkt ab. Deutlich
    mehr Aufwand: eigener Bot-Host/-Prozess + OAuth-Linking zwischen
    Discord- und Steam-Account (ähnliches Muster wie die bestehende
    Steam-OpenID-Anbindung, siehe
    `docs/plans/2026-08-14-done-steam-auth-produktion.md`).
- [x] **Net-Worth-Dialog: Layout an den Markt-Dialog angeglichen
  (2026-08-21).** Nutzer-Vorgabe: Aufbau wie `MarketView.vue`.
  **Erster Versuch falsch:** `.mv-chart-row`/`.mv-table` fälschlich als
  "Chart oben, Tabelle darunter" gelesen — tatsächlich ist `.mv-root`
  selbst `flex-direction:row`, Chart-Bereich (66%, mit eigener schmaler
  Toggle-Legende `.mv-legend` NEBEN der Chart-Box) und Tabelle sitzen
  NEBENEINANDER, nicht übereinander (per Nutzer-Korrektur gefunden).
  **Korrekt umgesetzt:** `.nw-layout` bleibt `flex-direction:row`
  (Chart-Bereich links, Breakdown-Liste rechts, wie `mv-root`). Neu:
  `.nw-chart-row` (links, `flex:0 0 62%`, selbst `flex-direction:row`)
  enthält `.nw-chart-box` (Chart + Toolbar mit Reset-Zoom-Button) und
  `.nw-legend` (schmale Spalte, `flex:0 0 100px`, Dataset-Toggles
  untereinander — analog `.mv-legend`). `.nw-bottom` (Breakdown+Stats,
  `PixelScrollBox`) rechts, analog `.mv-table`. Dialog-Größe bewusst
  unverändert (nur Layout angefragt).
  **Folgeauftrag (selber Tag):** Nutzer wollte keinen Vollbild-Dialog
  mehr dafür — "mach es zu einem Popup vom Networth-Button". Umgebaut zu
  einem an `.hud-networth` (FarmGridView.vue) geankerten schwebenden
  Popup statt `px-dialog-overlay`: `Teleport to="body"`, Position
  synchron aus `anchorEl.getBoundingClientRect()` berechnet (feste
  Panel-Maße 820×480, kein Nach-dem-Rendern-Messen — gleiche
  Flacker-Vermeidung wie bei `NestedTooltip.vue`s `positionPopup()`),
  schließt bei jedem Mousedown außerhalb (Klicks im Panel erreichen
  `document` durch `@mousedown.stop` nie). Chart-Datasets zusätzlich auf
  `stepped:true, tension:0` umgestellt (war `tension:0.3`, glatte Kurve)
  — "Graph muss auch wie beim Markt pixelig sein", `PriceChart.vue`
  nutzt exakt dieses Muster bereits. `FarmGridView.vue` braucht dafür
  eine neue `netWorthBtnEl`-Ref + `:anchorEl`-Prop-Weitergabe — diese
  Datei ist mit paralleler Arbeit vermischt, Änderung liegt unkommitiert
  im Arbeitsverzeichnis (siehe Commit-Notiz).
  **Font-Nachzug (selber Tag):** Achsen-Ticks (`ticks.font.family`) und
  das Tip-Label-Canvas-Plugin (`ctx.font`) liefen noch auf generischem
  `monospace`/Browser-Default statt `Silkscreen` wie überall sonst im
  Spiel — `PriceChart.vue` hatte das schon korrekt (`font:{family:
  'Silkscreen'}` auf beiden Achsen, `'bold 10px Silkscreen'` fürs
  Canvas-Plugin), auf `NetWorthDialog.vue` übernommen.
  **Achsen-Clipping (selber Tag):** Y-Achsen-Ticks (z. B. "53") klebten
  direkt am Rand und wirkten abgeschnitten — `.chart-wrap` hatte anders
  als Markt's `.mv-chart-box` (`padding:14px`) gar kein Padding um den
  Canvas. `padding:4px 8px 4px 2px` ergänzt, Canvas schrumpft dadurch
  korrekt nach innen (Chart.js liest `clientWidth`/`clientHeight` des
  Eltern-Containers), Ticks haben jetzt Luft zum Rand.
  **Header raus (selber Tag):** "es soll kein Dialog geben, das ist das
  neue Popup" — `.px-titlebar` (Titel-Text + Schließen-Button) komplett
  entfernt, damit auch `useDraggableDialog`/`onDragStart` (der Header war
  der einzige Drag-Griff). Schließt jetzt nur noch über den bestehenden
  Klick-außerhalb-Handler, kein expliziter ×-Button mehr nötig (Gesamt-
  Net-Worth steht ohnehin schon in der "Gesamt"-Zeile der Breakdown-
  Liste). Popup dadurch kompakter: `.nw-box`-Höhe `480px`→`420px`
  (Titelleisten-Höhe abgezogen).
  **Klick→Hover (selber Tag):** "ich will den onHover-Popup ersetzen
  damit" — die bisherige einfache Hover-Vorschau
  (`PixelInfoPopover`/`netWorthRows` in `FarmGridView.vue`) komplett
  durch dieses Popup ersetzt, kein separater Klick-Dialog mehr.
  `NetWorthDialog.vue` umgebaut zu einem selbstständigen Hover-Popup
  (Trigger per `<slot/>`, wie `PixelInfoPopover.vue`) statt extern per
  `dialog`-State (FarmGridView.vue) gesteuert — `wrapRef` (der Slot-
  Wrapper) ersetzt das vorherige `anchorEl`-Prop, Grace-Delay (250ms)
  beim Verlassen wie bei `NestedTooltip.vue`s Drain-Delay, damit die Maus
  vom Trigger in den Popup wandern kann ohne dass er zuklappt. Chart-
  Daten laden lazy beim ERSTEN Hover (kein Fetch bei jedem HUD-Mount).
  **Fallstrick dabei gefunden:** Popup nutzt weiterhin `v-if`, der Canvas
  wird also bei jedem Schließen/Öffnen neu erzeugt — die Chart.js-Instanz
  muss deshalb bei jedem Wieder-Öffnen neu aufgebaut werden (`initChart()`
  erneut, nicht nur beim allerersten Mal), sonst bleibt der Graph ab dem
  zweiten Hover leer (an den alten, entfernten Canvas gebunden). Neuer
  `toggle()` (via `defineExpose`) fürs Tastatur-/Controller-Hotkey
  (`triggerAction('networth')` in `FarmGridView.vue`, konnte den
  ehemaligen `dialog='networth'`-Pfad nicht mehr nutzen) — da ohne Hover
  kein automatisches Schließen ausgelöst wird, schaltet erneutes Drücken
  einfach wieder zu.

## 5. Build & Deployment (aus `CLAUDE.md` übernommen, weiter aktuell)

- [ ] **Steam-Upload vorbereiten** — Windows-Build testen
  (`npm run build:win`), `app_build_2816100.vdf` mit AppID 2816100 und
  Depots für Windows-Client + Server-Binary anlegen, Upload zuerst auf
  Steam-Branch "beta"
- [x] **Server-Deployment** — erledigt 2026-08-03. Backend + PostgreSQL
  laufen produktiv via Docker Compose hinter einem TLS-terminierenden
  Reverse Proxy, `app.dev-mode=false`, unter `https://cookie.r3dconcrete.de`.
  Details, Env-Var-Referenz und Sicherheitshinweise: `docs/DEPLOYMENT.md`.
- [x] **HTTPS zwischen Client und Server** (Issue #20) — erledigt 2026-08-03,
  im Rahmen des Server-Deployments oben. Let's-Encrypt-Zertifikat über den
  Reverse Proxy, `VITE_API_BASE_URL`/`VITE_WS_URL` in
  `frontend/.env.production` auf `https://`/`wss://` umgestellt.

---

## 6. Kleinkram (aus dieser Session, bereits behoben)

Nur als Gedächtnisstütze, kein offener Task mehr:

- ~~`start.sh` hardcodete einen nicht existenten Linuxbrew-JAVA_HOME-Pfad~~
  → jetzt Auto-Detect via `which java`
- ~~`mvnw` hatte kein Ausführungsrecht auf dieser Maschine~~ → `chmod +x`
  gesetzt (in Git bereits korrekt mit `100755` hinterlegt, war ein rein
  lokales Checkout-Problem)

---

## 7. Freund-Playtest-Feedback (2026-08-07)

Erster echter Multiplayer-Playtest mit Freunden. Klar umrissene Bugs/kleine
Features direkt umgesetzt (unten abgehakt), große/unscharfe Themen als
Backlog dokumentiert.

### 7.1 Umgesetzt

- [x] **Bürgerzahl sprang auf 0 beim Einsammeln.** `PassiveIncomeService`,
  `BakeService`, `MarketService` bauten `UserInformationDto` manuell und
  vergaßen `ownedCitizens`/`workersIdle` (+ `displayName`/`avatarUrl`) zu
  setzen — Jackson serialisierte die fehlenden Felder trotzdem mit
  Default-Werten (`0`/`false`), Frontend übernahm das ungeprüft in den
  Store. Alle drei Stellen ergänzt (siehe `UserService.toDto()` als
  Vorbild). Erklärte nebenbei auch "mehr Einwohner kaufen als Platz ist" —
  der Cap-Check selbst war serverseitig bereits korrekt.
- [x] **Race-Condition beim schnellen Einsammeln mehrerer Gebäude.**
  `@Version` auf `PlayerBuildingEntity` (Lost-Update-Schutz), In-Flight-Guard
  in `FarmGridView.vue#onCollectBuilding` gegen überlappende Requests.
- [x] **Kein Cooldown beim Einsammeln.** Neuer
  `GameBalanceConfig.collectCooldownMs` (Default 150ms, Minimum 100ms
  erzwungen), geprüft in `PassiveIncomeService.collectBuilding()` gegen
  neues `PlayerBuildingEntity.lastCollectedAt`-Feld.
- [x] **Markt-Crash bei ~200 Einheiten durch einen Spieler.** Root Cause:
  `MarketService.performAction()` las den Stock vor dem `marketLock`,
  wodurch schnelle Folge-Trades sich gegenseitig überschreiben konnten
  (Lost Update), der Stock Richtung `STOCK_EPSILON` abdriften und der
  Preis explodieren konnte. Kompletter Trade läuft jetzt unter demselben
  Lock wie die Stock-Aktualisierung; zusätzlich `@Version` auf
  `MarketStockEntity` als zweite Verteidigungslinie. Bewusst NICHT
  angefasst: `initialStock`/Liquiditäts-Tuning — eigener Balancing-Pass
  nach echtem Playtest (siehe Backlog unten).
- [x] **Rundung: nie mehr als 2 Nachkommastellen, aufgerundet.** Neues
  `frontend/src/utils/formatNumber.js` (`fmt`/`fmt2`/`fmtBig`/`roundUp`,
  ceiling-basiert statt kaufmännisch gerundet), ersetzt die ca. 10× im
  Frontend duplizierten lokalen `fmt`/`fmtBig`-Helfer sowie die einzigen
  drei Stellen mit mehr als 2 Nachkommastellen (`ResourceBar`-Marktpreis,
  `LagerDialog`-Ressourcenpreis, `RecipeCard`-Zutatenpreis, vorher 3–4
  Nachkommastellen).
- [x] **Schließen-Button im Backen-Dialog reagierte nicht.**
  `RecipeCard.vue` `.rc-close` fehlte ein `z-index`, `PixelScrollBox`s
  transparente Scroll-Fläche lag im gleichen Stacking-Level *nach* dem
  Button im DOM und fing die Klicks ab.
- [x] **Popups jetzt per Klick schließbar.** Neues generisches
  `frontend/src/composables/useClickOutside.js` (verallgemeinert aus dem
  Hamburger-Menü-Muster in `FarmGridView.vue`), in `PixelInfoPopover.vue`
  eingebaut — schließt sofort per Klick statt nur über den Auto-Drain-Timer.
- [x] **"Lager voll"-Notice und "Einsammeln"-Badge überlappten sich.**
  `BuildingFrame.vue`: Collect-Badge höher gestapelt (`bottom: calc(100% +
  40px)`), Blocked-Notice bleibt näher am Gebäude.
- [x] **Rangliste: redundantes Hover-Popup auf dem Namen entfernt.**
  `LeaderboardView.vue` — natives `title`-Attribut zeigte den ohnehin
  sichtbaren Namen nochmal.
- [x] **Hover-Ernte-Zahl ist jetzt immer im selben, nicht-gelben Ton**
  (`#aea47e`, hellster neutraler Ton der Pflicht-Palette — echtes Weiß gibt
  es dort nicht). Gold/Gelb bleibt für künftige kritische Treffer reserviert
  (siehe Backlog unten).
- [x] **Kamera-Geschwindigkeit:** Standard 480→1200, Maximum 1200→3000
  (`useCameraControls.js`, `SettingsDialog.vue`).
- [x] **Profil-Übersicht zeigt nichts mehr vom Skill-Baum**
  (`PlayerProfileView.vue`: Skill-Knoten-Liste + Skill-Baum-Wert-Kachel
  entfernt).
- [x] **Stern-Indikator im HUD bei verfügbaren Skillpunkten**
  (`FarmGridView.vue`, `.hud-skillpoint-star`), Klick öffnet direkt den
  Skill-Baum.
- [x] **Verbleibende Skillpunkte jetzt immer sichtbar im Skill-Baum**
  (`SkillTreeView.vue`, `.stv-points-badge`, zentriert über der Canvas) —
  vorher nur im Kauf-Popup nach Klick auf die Wurzel sichtbar.
- [x] Verifiziert, **kein Bug**: Gebäude-Lager bei vollem Hauptlager
  (`PassiveIncomeService.java` kreditiert bereits nur bis zur Kapazität,
  Rest bleibt im Gebäude liegen statt verworfen zu werden).
- [x] **Markt-Liquiditäts-Tuning.** `initialStock` ist jetzt eine
  Untergrenze statt eines fixen Werts:
  `max(initialStock, stockPerActivePlayer × aktiveSpielerzahl)`
  (`MarketConfig`, Standard `stockPerActivePlayer = 20000`). "Aktiv" =
  Spielstart innerhalb `activePlayerWindowDays` (Standard 7 Tage,
  `UserEntity.lastActiveAt`, gesetzt in `GameController#initializeGame`).
  `MarketService#recalculateDynamicStockBase` (alle 5 Min) zaehlt neu und
  skaliert Stock+Baseline aller sechs Ressourcen um denselben Faktor mit,
  damit der Spotpreis beim Umschalten nicht springt. Behebt "ein Spieler
  bewegt den Markt allein" aus dem Playtest-Feedback. Zusaetzlich:
  `sellFeeRate`-Doku-Drift gefixt (Code-Default + Design-Doc sagten 5%,
  live liefen 15% — jetzt ueberall 15% dokumentiert). Startwert fuer
  `stockPerActivePlayer` ist eine erste Schaetzung, braucht Fein-Tuning
  mit echten Spielerzahlen (live per Admin-Panel ohne Neustart aenderbar).
  **Nachtrag (2026-08-09, noch vor jedem Deploy gefangen):**
  `cachedActivePlayerCount` ist nicht persistiert und startete bei jedem
  Prozess-Neustart wieder bei 0 -- `recalculateDynamicStockBase`
  interpretierte das faelschlich als echten "0 -> N aktive Spieler"-Sprung
  und skalierte den bereits korrekten DB-Stock bei JEDEM Neustart erneut
  mit dem vollen Faktor (kumulativ). Nach 2-3 Neustarts crashte der Markt
  auf den Preis-Boden (0.01 ueberall). Fix: erster Lauf nach einem
  Prozessstart (`stockBaseInitialized`-Flag) uebernimmt die gezaehlte
  Spielerzahl nur, skaliert aber nichts -- nur echte Aenderungen WAEHREND
  ein Prozess laeuft loesen noch eine Rescale aus. Lokale Dev-DB war
  betroffen, per `/api/v1/admin/market/reset` wieder auf saubere Werte
  gesetzt. War noch nicht deployed, Live-Server also nicht betroffen.
  **Zweiter Nachtrag (2026-08-09):** Nach dem Fix oben trat bei JEDEM Neustart
  weiterhin ein kurzer Preis-Einbruch auf (ein einzelner Tick auf 0.01, dann
  sofortige Selbstkorrektur) -- Ursache war ein Startup-Wettlauf zwischen
  `MarketScheduler#updateMarketPrices` und `recalculateDynamicStockBase`
  (beide `@Scheduled` mit `initialDelay=0`, ohne garantierte Reihenfolge).
  Lief der Preis-Tick zuerst, rechnete er kurz mit `cachedActivePlayerCount`
  im Default-Zustand (0) gegen den bereits korrekt hochskalierten DB-Stock.
  Fix: `cachedActivePlayerCount` wird jetzt synchron per `@PostConstruct`
  (`initializeDynamicStockBaseOnStartup`) initialisiert, bevor Spring
  ueberhaupt mit dem Ausfuehren von `@Scheduled`-Tasks beginnt --
  `recalculateDynamicStockBase` startet jetzt mit `initialDelay=300_000`
  statt `0`. Verifiziert: 10s Preis-Sampling direkt nach Neustart zeigt
  keinen Einbruch mehr.
- [x] **`LagerDialog.vue` überarbeitet (2026-08-09).** Ausgangs-Bugreport:
  "Hover gibt Ressourcen obwohl Lager voll" — die eigentliche Hover-Ernte-
  Deckelung (Client + `UserService#harvest`) war bereits korrekt pro
  Rohstoff gedeckelt, aber die "Gesamtkapazität"-Anzeige im Dialog summierte
  alle 6 Rohstoffe und verglich das gegen den Deckel EINES Rohstoffs — zeigte
  "voll" (rot) an, obwohl einzelne Rohstoffe (und damit deren Hover-Ernte)
  noch lange nicht am Limit waren. Fix: Anzeige-Kapazität ist jetzt
  Deckel × 6. Zusätzlich auf Nutzerwunsch (Skizze) neu gebaut: oben ein
  segmentierter Balken zeigt die %-Aufteilung der aktuell gelagerten Menge
  nach Rohstoff (Farben wie in `MarketView.vue`/`PriceChart.vue`s
  `RESOURCE_COLORS`), daneben Gesamtmenge/-kapazität + freier Platz in %.
  Neue Sektion "Gebäude-Lager" listet alle gebauten Produktionsgebäude mit
  ihrem eigenen (vom Hauptlager unabhängigen) Bestand + Einsammeln-Button
  pro Zeile (`collectBuilding`-API, bereits vorhanden). Bestätigt beim
  Nachschauen: Hauptlager und Gebäude-Lager sind bereits im Backend zwei
  getrennte Speicher (`BuildingService#settle` deckelt nur auf
  `def.storageCapacity()`, unabhängig vom Hauptlager-Füllstand — siehe auch
  den bereits vorhandenen Eintrag weiter oben "Verifiziert, kein Bug:
  Gebäude-Lager bei vollem Hauptlager"), keine Backend-Änderung nötig, nur
  Frontend/Dialog.
- [x] **Hauptlager-Deckel zurück auf gemeinsamen Topf (2026-08-09, noch
  selber Tag wie der Eintrag oben).** Der Lager-Dialog-Umbau oben nahm den
  Deckel-pro-Rohstoff aus dem 2026-08-07-Eintrag ("Auto-Verkauf bei vollem
  Lager entfernt") als gegeben an und baute die neue Balken-Anzeige darauf
  auf (Deckel × 6 als Anzeige-Kapazität). Spieler-Feedback beim Testen:
  "es gibt keine Deckelung, das Lager kann auch ganz voll mit Schokolade
  sein" — der Deckel-pro-Rohstoff sollte gar nicht gelten, das Hauptlager
  soll ein einziger gemeinsamer Topf über alle 6 Rohstoffe sein (das war
  tatsächlich das *ursprüngliche* Verhalten vor dem 2026-08-07-Umbau, der
  hatte das Gegenteil dokumentiert). Zurückgerollt: `UserEntity` bekommt
  `getTotalResources()` (Summe aller 6), `UserService#harvest`,
  `PassiveIncomeService#collectBuilding` und `MarketService#performAction`
  (BUY-Zweig, dort war der Deckel-pro-Rohstoff-Check bisher gar nicht
  erwähnt gewesen — beim Suchen mit auf gefallen) prüfen jetzt alle gegen
  `getTotalResources()` statt gegen den einzelnen Rohstoff. Frontend:
  neuer `playerStore.totalResources`-Computed (Summe der 6 Ressourcen-Refs,
  zentral statt in jedem Consumer einzeln nachgebaut) in `player.js`,
  `isResourceFull()` in `FarmGridView.vue`/`BuildingDetailDialog.vue`/
  `LagerDialog.vue` sowie `localHarvestTick()` (Client-Vorhersage) und
  `MarketView.vue#freeStorageFor` (Max-Kauf-Menge) darauf umgestellt.
  `LagerDialog`s Anzeige-Kapazität ist jetzt `totalResourceCap` direkt
  (nicht mehr × 6). Design-Doc (Abschnitt 4) und Code-Kommentare
  entsprechend nachgezogen.

### 7.2 Backlog (noch offen, nicht in dieser Session umgesetzt)

- [ ] **Fenstermodus + Auflösungs-Einstellung + Maus-Containment.**
  `electron/main.js` erzwingt aktuell `fullscreen: true`, feste
  Fenstergröße (1280×800), kein Windowed-Toggle, keine Resolution-UI.
  **Wichtiger Vorbehalt:** echtes OS-Level-Cursor-Clamping (Maus bleibt
  strikt im Fenster) ist mit reinem Electron nicht ohne Weiteres lösbar —
  die Pointer-Lock-API versteckt den Cursor nur und liefert relative
  Deltas, das ist nicht dasselbe wie "Cursor bleibt sichtbar innerhalb der
  Fenstergrenzen". Braucht eigene Machbarkeits-Recherche vor der Umsetzung.
- [x] **Steam Deck: Pfeil hoch/runter zoomt Kamera** — bereits vorhanden,
  kein neuer Code nötig (`FarmGridView.vue:642–656`, D-Pad-Indizes 12/13
  über die Gamepad API).
- [ ] **Steam Deck: R1/L1 zyklisch durch platzierte Gebäude springen.**
  Datengrundlage vorhanden (`farmLayout.js` `BASE`, `buildingOffsets`,
  `buildingFrameEls` in `FarmGridView.vue`), bestehendes
  `resetView()`/`centerExactlyOnRathaus()` müsste zu einem generischen
  `centerOnBuilding(id)` werden, neue Actions in `useActionHotkeys.js` +
  `triggerAction()`. Prioritätsliste (Rathaus → nächstbestes falls Ziel
  fehlt) muss noch definiert werden.
- [ ] **Skillbaum-Ausbau (mehrere Spielweisen).** Design-Pass abgeschlossen
  (inkl. eines vom User mit Perplexity erstellten Anforderungs-Prompts,
  eingearbeitet), Umsetzung läuft — 8 separate Pläne unter `docs/plans/`,
  unabhängig voneinander umsetzbar (bewusst nicht am Stück), aber mit einer
  Abhängigkeit: `2026-08-10-open-skillbaum-wheel-keystones.md` ist das
  **Fundament** (Mehrfach-Effekte pro Knoten für echte Keystone-Tradeoffs,
  Node-Tiers PASSIVE/NOTABLE/KEYSTONE, i18n-Fix für Knotentexte,
  Cross-Branch-Wheel, geschützte-IDs-Liste), umgesetzt 2026-08-10 (noch
  nicht committed) — die anderen 7 setzen direkt darauf auf:
  `2026-08-10-open-skillbaum-rohstoff-branches.md` (Zucker/Mehl/Eier/
  Butter/Schoko, löst diesen Punkt hier ab), umgesetzt 2026-08-10 (noch
  nicht committed),
  `2026-08-10-open-skillbaum-crit-system.md` (Krit bei Ernte/Passiv-
  Produktion/Backen), `2026-08-10-open-skillbaum-lager-branch.md`
  (STORAGE-Branch, löst den "Ausgleich für volles Lager"-Punkt weiter unten
  ab), umgesetzt 2026-08-10 (noch nicht committed — Layout dabei ein
  zweites Mal auf 11 gleichmäßig verteilte Branches umgestellt, siehe
  `docs/cookie-game-design.md` §9),
  `2026-08-10-open-skillbaum-bau-buerger-branch.md`
  (CONSTRUCTION-Branch, Gebäudekosten/Lohn),
  `2026-08-10-done-skillbaum-respec.md` — **umgesetzt (2026-08-12)**:
  flacher Fix-Preis (`respecCostFlat`), ein Knoten pro Aufruf,
  konnektivitäts-sicher (`reachableFromRoot()` + separater
  `requiresAllPrereqs`-Check für `bridge_bake_market`), gegen einen
  Wegwerf-Testnutzer end-to-end verifiziert,
  `2026-08-10-open-skillbaum-suche-buildplanung.md` — Such-/Filter-UI
  **umgesetzt (2026-08-12)**, in Spieler- **und** Admin-Baum (Toolbar-Suche,
  Treffer pulsieren grün, Rest gedimmt). Tooltip-Erweiterung um
  **Folgeknoten-Namen** (welche Knoten von hier aus als Nächstes erreichbar
  wären) bewusst zurückgestellt, war nicht angefragt — bei Bedarf später:
  `nodeRows(n)` in `SkillTreeView.vue` um einen Lookup über `tree.edges`
  erweitern (kein neuer Backend-Wert nötig, Kanten sind schon geladen).
  `2026-08-10-open-skillbaum-automatisierung.md` (Auto-Sammeln/-Verkauf/
  -Backen + ressourcen-unabhängige Hover-Boni, User-Idee vom 2026-08-10).
  Jede Datei einzeln auf ✅/`-done-` umstellen, sobald umgesetzt.
- [ ] **Rezepte pro Season randomisiert + Entdecken-Minigame.** Komplett
  neues Feature (Rezept-Rotation-Modell im Backend, Minigame-Konzept) —
  eigene Design-Session nötig.
- [ ] **Vollständiger Gelb-Kontrast-Sweep.** Phase 1 hat nur die konkret
  gemeldete Ernte-Zahl-Farbe behoben. Grep fand 60+ weitere Stellen mit
  Gelb/Gold als Textfarbe (`--px-gold-txt`, `--px-cream`, `--px-gold`,
  `--px-green-txt` als `color:`, u.a. in `ResourceBar.vue`,
  `BuildingDetailDialog.vue`, `MarketView.vue`, `main.css`). Für "Schrift
  darf nirgends gelb sein" als generelle Regel braucht es eine visuelle
  Durchsicht im Browser (welche Kombination wirklich unlesbar ist) statt
  blindem Suchen-Ersetzen.

---

## 8. AI-Driven Testing + Balancing (2026-08-11, zusammengeführt 2026-08-14)

- [x] **v0: Balance-Report-Skript** (`frontend/scripts/balance-report.mjs`,
  `npm run balance:report`) — bereits umgesetzt am 2026-08-13, hat u.a.
  `prestigeBaseThreshold` kalibriert (100.000 → 4500).
- [x] **v1: MCP-Server, Single-Dev-Player-Tools** (`tools/mcp-testing-server/`)
  — umgesetzt 2026-08-14. Fünf Tools (`game_get_state`, `market_buy`,
  `market_sell`, `farm_harvest`, `farm_collect_building`), spielt als
  `DEV_PLAYER_001` über die normalen Gameplay-Endpunkte. **Ausdrücklich nur
  Dev-Umgebung** — Leitplanke prüft vor jedem Tool-Call `devMode:true` +
  Localhost-Whitelist gegen `GET /api/v1/config`, verweigert sonst jede
  Aktion. Live verifiziert inkl. Negative-Amount-Exploit-Regressionstest.
- [ ] **v2: Mehrspieler-Simulation & Pentest** — mehrere simulierte
  Dev-Player gleichzeitig für Race-Condition-/Lost-Update-Bugs (z.B.
  `MarketService.performAction`, `PassiveIncomeService.collectBuilding`),
  danach gezielte Exploit-Regressionstests gegen bereits gefixte Bugs.
- [ ] **v3: Dynamische Balance-Validierung** — echte MCP-Agent-Handelsdaten
  als neuer `--dynamic`-Modus ins Balance-Report-Skript einspeisen, reale
  Preisbewegungen/Payback neben die idealisierten Formel-Kurven aus v0
  stellen.
  Details/Architektur zu allen vier Stufen:
  `docs/plans/2026-08-14-open-mcp-ki-testing-balancing.md`.

---

## 9. Spieler-Fusion / Account-Merge (Idee, 2026-08-11)

- [ ] **Mehrere Spieler können sich zu einem gemeinsamen Account
  fusionieren.** Grobkonzept, noch kein Plan/Umsetzung. Eckpunkte laut
  Nutzer-Vorgabe:
  - **Freischaltung:** neuer Keystone-Node im Skillbaum (baut auf dem
    PASSIVE/NOTABLE/KEYSTONE-Fundament aus
    `2026-08-10-open-skillbaum-wheel-keystones.md` auf) — jeder Teilnehmer
    muss diesen Keystone selbst freigeschaltet haben, bevor er fusionieren
    kann. Offen: eigener zentraler Keystone oder pro Branch einer, und wie
    teuer/wo im Baum.
  - **Ablauf:** ein Spieler schickt eine Merge-Anfrage an mehrere andere
    Spieler (braucht neuen Request/Accept-Flow, analog Freundschafts-
    Anfragen — noch kein Endpoint/Entity dafür vorhanden). Erst wenn alle
    Angefragten annehmen, wird fusioniert.
  - **Effekt:** alles wird zusammengelegt — Cookies, Ressourcen, Gebäude,
    Skillpunkte/Skillbaum-Stand, Net Worth der beteiligten Accounts fließen
    in den Ziel-Account. Die ursprünglichen Accounts bestehen danach leer/
    inaktiv weiter (nicht gelöscht).
  - **Permanent**, keine Rückgängig-Funktion (bewusste Entscheidung, kein
    Respec-artiger Trennungs-Mechanismus wie beim Skillbaum).
  - **Steuerung danach:** alle fusionierten Spieler loggen weiterhin mit
    ihrer eigenen Steam-ID ein und steuern live gemeinsam denselben Account
    gleichzeitig (echte geteilte Session, nicht nur einer aktiv) — ähnlich
    wie mehrere Browser-Tabs auf demselben Hof, nur mit mehreren echten
    Spielern.
  - **Noch ungeklärt / vor einem echten Plan zu beantworten:**
    - Aktuell ist die Zuordnung `steamId` → `UserEntity` 1:1 (siehe Auth-
      Punkt in Abschnitt 0). Für Fusion braucht es eine N:1-Zuordnung
      (mehrere SteamIDs → ein gemeinsamer Spielstand) — neue
      Verknüpfungstabelle nötig, betrifft potenziell jeden Endpunkt, der
      aktuell `steamId` als alleinigen Nutzer-Schlüssel nimmt.
      Reihenfolge beachten: erst die Steam-Auth-Verifizierung aus
      Abschnitt 0 sauber lösen, danach an Fusion — sonst baut Fusion auf
      einer ohnehin ungeprüften Identität auf.
    - Concurrency: mehrere Spieler greifen jetzt *dauerhaft* gleichzeitig
      auf denselben Datensatz zu (nicht nur kurze Rennen wie bisher beim
      Einsammeln, siehe Abschnitt 7.1 Race-Condition-Fix) — bestehende
      `@Version`-Locks reichen vermutlich nicht für ein Dauer-Multi-Actor-
      Szenario, eigenes Konzept nötig.
    - Leaderboard/Season-Reset: zählt ein fusionierter Account als ein
      Spieler oder gewichtet nach Teilnehmerzahl? Bleibt Fusion über einen
      Season-Reset hinweg bestehen?
    - UI fehlt komplett: Spielerliste/-suche, Anfrage senden, offene
      Anfragen annehmen/ablehnen, Anzeige "wer gehört zu diesem Account".
    - Skillbaum-Konflikt: was passiert, wenn beide Accounts unterschiedlich
      allokierte, sich gegenseitig ausschließende Notable/Keystone-Pfade
      haben? Zusammenlegen kann gegen die PoE-Konnektivitätsregel
      verstoßen.
  Braucht eigene Design-Session (wie beim Rezept-Rotation-Feature oben) und
  danach einen eigenen Plan unter `docs/plans/`, bevor Code entsteht.

---

## 10. AFK-Timeout → Hauptmenü (Idee, 2026-08-11)

- [x] **Wer den Tab/das Fenster lange offen lässt ohne etwas zu tun, soll
  automatisch zurück ins Hauptmenü fliegen** — Ziel: Server entlasten
  (kein `pollWageStatus`, keine Websocket-Marktupdates, keine sonstigen
  Timer/Polls mehr für Spieler, die eh nicht mehr da sind). Noch offen vor
  einem Plan:
  - Wie wird "keine Aktion" erkannt (Maus/Tastatur/Klick-Events global
    tracken, welcher Timeout in Minuten)? Muss Hover-Ernten (passives
    Halten der Maus über einem Feld) als "Aktivität" zählen oder nicht —
    sonst verhindert genau der Hauptmechanismus des Spiels den Timeout nie.
  - Gibt es aktuell überhaupt eine Hauptmenü-Route/View, zu der man
    zurückspringen kann, oder muss die erst gebaut werden?
  - Welche Timer/Polls genau sollen beim Timeout gestoppt werden (siehe
    `FarmGridView.vue`: `pollWageStatus`/`wagePollTimer`, Websocket-
    Marktverbindung aus `services/websocket.js`, ggf. weitere) und sauber
    wieder hochfahren, wenn der Spieler zurückkommt.
  - Soll der Spieler vorgewarnt werden (z.B. Countdown-Hinweis kurz vorm
    Rausfliegen), oder direkter Sprung ohne Warnung?
  **Umgesetzt (2026-08-11):** 10 Minuten ohne Aktivität (Hover zählt),
  Heartbeat-Mechanismus (`UserEntity#lastHeartbeatAt`,
  `POST /api/v1/users/{id}/heartbeat`), `WageScheduler` überspringt Spieler
  ohne frischen Heartbeat komplett (kein Lohn/Zins-Tick), Frontend
  (`composables/useIdleTimeout.js`) schickt bei Inaktivität zurück ins
  Hauptmenü und räumt Bake-Poll/Markt-Websocket ab. Kein Warn-Dialog (bewusst
  weggelassen, siehe `docs/plans/2026-08-11-open-afk-timeout.md`).

## 11. Balancing-Report-Tool (Idee, 2026-08-13)

- [ ] **Wiederholbares Skript, das Markt-, Gebäude-, Bürger/Lohn- und
  Skill-Punkte/Keystone-Kurven gegen Zielbänder prüft** — aktuell wird an
  `GameBalanceConfig`/`MarketConfig`/Skill-Node-Werten nach Gefühl gedreht,
  ohne systematischen Check auf Payback-Perioden, Keystone-Bonus/Malus-
  Verhältnisse oder Markt-Slippage. Externe Tools (Machinations.io,
  Google Sheets) recherchiert, aber verworfen — würden ein zweites,
  driftendes Wirtschaftsmodell neben dem echten Java-Code bedeuten.
  Stattdessen: `frontend/scripts/balance-report.mjs` liest Live-Werte direkt
  vom Dev-Server, rechnet die echten Formeln nach, markiert Ausreißer.
  Plan: `docs/plans/2026-08-13-open-balance-report-tool.md`.
  **Fortschritts-Simulator ergänzt (2026-08-13):** neues Modul simuliert
  einen Spieler mit genau einem Startgebäude (Check-in alle 12h, echte
  settle()/AMM/Storage-Cap-Formeln, plus 1-2h aktive Hover-Ernte pro
  Check-in — braucht laut `UserService#harvest` keinen Gebäudebesitz, ist
  aber Aktiv-only, kein Lazy-Catchup wie settle()) und misst Tage bis zum
  ersten Prestige-Reset. Fund: `prestigeBaseThreshold` war mit 100.000 ca.
  60-100x zu hoch — selbst nach 20 simulierten Tagen nie erreicht. Erster
  Durchlauf (ohne Hover-Ernte) kam auf 1700, war mit Hover-Ernte
  eingerechnet aber zu niedrig (Spieler kommt aktiv in 1-2 statt 2-3 Tagen
  hin). Auf **4500** kalibriert (`GameBalanceConfig.java`,
  `application.properties`) — trifft Bauernhof (3 Tage) und Plantage
  (2 Tage) gut. Zwei Ausreißer an gegenüberliegenden Enden bleiben, kein
  einzelner globaler Threshold trifft alle vier: Hühnerhof braucht 4.5
  Tage (schwächste Produktionsrate, 0.4 vs. 0.6-0.7 Einheiten/Sek./
  Arbeiter bei den anderen, konsistent in beiden Kalibrierungs-Durchläufen
  aufgefallen), Butterei nur 1.5 Tage (höchster Ressourcenpreis macht den
  Hover-Ernte-Verkauf überproportional stark). Noch offen, kein reines
  Threshold-Problem, braucht Gebäude-spezifisches Feintuning (Rate/Kosten
  je Gebäude statt nur der einen globalen Schwelle). Plantage-Start (380
  Cookies) ist ein echter Softlock ohne
  Hover-Ernte-Grinding (400 Start-Cookies − 380 = 20 Rest, reicht nicht für
  den ersten Bürger à 50) — laut Rücksprache **kein Bug**,
  `PlayerResetService.hardReset()` ist genau für diesen Fall der
  vorgesehene Ausweg (siehe Punkt 12 unten).

## 12. Prestige Passive Tree (Idee, 2026-08-13)

- [ ] **Prestige soll einen eigenen, separaten Passiv-Baum bekommen** (nicht
  den normalen Skill-Baum) — bei Reset gibt's einen "Prestige-Punkt", den
  der Spieler dort dauerhaft investiert. Soll die aktuelle flache
  `prestigeMultiplierPerLevel`-Mechanik (`PrestigeService.calcMultiplier()`,
  +10 % Ernte/Backen pro Stufe, kein Baum) ersetzen. Der erste große
  Prestige-Keystone soll so stark sein, dass der Spieler danach nur noch
  ~halb so lange braucht, um wieder auf den Stand vor dem Reset zu kommen
  — und das soll sich mit jedem weiteren Reset/Keystone wiederholen
  (siehe Diskussion zum Fortschritts-Simulator, Punkt 11).
  **Bestätigt:** Softlocks (z.B. Plantage-Start, siehe Punkt 11) sind
  legitim — `PlayerResetService.hardReset()` (bestehender Endpoint, kein
  Schwellenwert nötig, aber auch keine Belohnung, setzt `prestigeLevel`
  zurück auf 0) ist genau dafür der vorgesehene Fluchtweg, kein Bug zum
  Fixen.
  **Offene Fragen vor einem Plan:**
  - Bleibt `hardReset()` wie heute (kein Prestige-Punkt, `prestigeLevel`→0,
    also auch der neue Baum wird geleert), oder soll der Prestige-Baum
    davon verschont bleiben, weil er ja "dauerhafter Fortschritt" sein
    soll? Aktuell setzt `hardReset()` auch `prestigeLevel`/`totalPrestiges`
    zurück auf 0 — bei einem Punkte-Baum müsste das äquivalent entschieden
    werden.
  - Genau 1 Prestige-Punkt pro `PrestigeService.prestige()`-Aufruf (wie
    aktuell +1 `prestigeLevel`), oder skaliert das mit erreichtem Net
    Worth/Overshoot über der Schwelle?
  - Ersetzt der neue Baum `prestigeMultiplierPerLevel` komplett, oder
    laufen beide parallel (Baum zusätzlich zum flachen Bonus)?
  - Bleibt `prestigeThresholdGrowth` (aktuell ×1.5 pro Stufe) als
    Schwellen-Wachstum bestehen, oder braucht das eigene Abstimmung, sobald
    der Baum reinspielt?
  - Node-Effekte: gleiche `EffectType`-Palette wie der normale Skill-Baum
    (HARVEST_YIELD etc., einfach dauerhaft statt pro Run), oder eigene
    "Meta"-Effekte (z.B. Kostenkurven abflachen, Start-Ressourcen für den
    nächsten Run, Lager-Cap dauerhaft erhöhen)?
  - Technisch: gleiches Baum-Muster wie `SkillNodeEntity`/`SkillEdgeEntity`
    (eigene Tabellen `prestige_nodes`/`prestige_edges`) oder eine
    gemeinsame Tabelle mit Discriminator-Feld? Admin-Editor
    (`SkillTreeAdminDialog.vue`) later mitbenutzen oder eigenes Tool?
  Sobald diese Fragen geklärt sind, wird daraus ein
  `docs/plans/`-Eintrag.

## 13. Grundstücke-Paywall im Rathaus (Idee, 2026-08-13)

- [ ] **Nur ein Gebäude soll am Spielstart baubar sein** — aktuell kann ein
  Spieler theoretisch alle 6 Produktionsgebäude direkt kaufen, sobald er
  sich die Kosten zusammengespart hat (`BuildingService` kennt kein Limit
  für gleichzeitig besessene Gebäude, nur den Preis pro einzelnem Gebäude).
  Geplant: weitere Gebäude-"Grundstücke" hinter einer Paywall im Rathaus —
  solange kein Grundstück freigeschaltet ist, ist der Bau-Button für
  weitere Gebäude im Hof-Grid komplett ausgeblendet (nicht nur unbezahlbar,
  sondern gar nicht klickbar). Macht die "1 Gebäude"-Frühphase, die der
  Fortschritts-Simulator (Punkt 11) simuliert, zur echten Spielregel statt
  nur einer Spieler-Selbstbeschränkung.
  **Entschieden (2026-08-13):** eigene Kostenkurve pro Grundstück
  (`plotCost(n) = plotBaseCost × plotCostGrowth^n`, `n` = Anzahl bereits
  besessener Produktionsgebäude, unabhängig vom Rathaus-Level); freie Wahl,
  welches der verbleibenden Gebäude freigeschaltet wird; kombinierter Preis
  (Freischalten + Bauen in einem Kauf, ersetzt den bisherigen
  `baseCost`-Erstkauf, Level-2+-Ausbau bleibt unverändert); gesperrte
  Gebäude im Hof-Grid sichtbar, ausgegraut, Schloss-Icon, Klick zeigt nur
  einen Hinweis, der eigentliche Kauf passiert im Rathaus-Dialog
  (`CitizenDialog.vue`, neue Sektion neben Bürger-Anwerben). Kein neuer
  Endpoint nötig, `buyOrUpgrade()`/`nextLevelCost` reichen mit angepasster
  Preisformel für den `currentLevel == 0`-Fall.
  **Ändert die `prestigeBaseThreshold`-Kalibrierung aus Punkt 11 mit** —
  bisher war Zuckerteich (500)/Kuhstall (600) bewusst kein Tag-1-Kauf,
  das entfällt jetzt (alle 6 Gebäude kosten bei gleichem `n` gleich viel),
  muss beim Umsetzen neu durch den Fortschritts-Simulator laufen.
  Plan: `docs/plans/2026-08-13-open-grundstuecke-paywall.md`.

---

## 14. Priorisierter Fahrplan Richtung Early Access (2026-08-16)

Konsolidiert die oben verstreuten offenen Punkte zu einer Reihenfolge.
Beschreibt nur die Sequenz/Begründung, keine Wiederholung der Details —
siehe jeweils verlinkten Abschnitt.

**Phase A — Polish & Cleanup (schnell, wenig Risiko, jederzeit einschiebbar):**
- Unity-Reste + toter Frontend-Code (`MarketTable.vue`/`TradePanel.vue`) raus (§2)
- Markt-Hover-Popup: statische 8%-Gebühr-Anzeige fixen (§2)
- Vollständiger Gelb-Kontrast-Sweep (§7.2)
- Linux-Build (#14) einmal isoliert mit `--publish=never` laufen lassen,
  Root Cause klären (§1)

**Phase B — Kernwirtschaft abschließen (mittel, größte Gameplay-Wirkung):**
1. **Grundstücke-Paywall (§13)** zuerst — Design ist bereits entschieden
   (Kostenkurve, Rathaus-Dialog-Sektion, kein neuer Endpoint nötig), macht
   die vom Balance-Simulator vorausgesetzte "1-Gebäude-Start"-Phase (§11)
   erst zur echten Regel statt Spieler-Selbstbeschränkung.
2. Danach **Balancing-Testrunden** (§4) neu durch den Fortschritts-Simulator
   laufen lassen — die Paywall ändert `prestigeBaseThreshold`-Annahmen mit
   (in §13 bereits vermerkt).
3. **Skillbaum-Ausbau-Rest**: `2026-08-10-open-skillbaum-automatisierung.md`
   und `2026-08-10-open-skillbaum-bau-buerger-branch.md` (§7.2) — einzige
   noch offenen der 8 Skillbaum-Pläne.
4. **Prestige Passive Tree (§12)** — offene Designfragen zuerst klären
   (`hardReset()`-Verhalten, Punkt-Skalierung pro Run, Meta- vs.
   Standard-Effekte, `prestige_nodes`-Tabelle vs. Discriminator), danach
   eigener `docs/plans/`-Eintrag. Braucht Phase B.1–B.3 als stabile
   Baseline, da Prestige direkt auf die Threshold-/Skillbaum-Balance
   aufsetzt.

**Phase C — Steam-Readiness (vor Early-Access-Release):**
- Windows-Build testen (`npm run build:win`) + `app_build_2816100.vdf`
  (AppID, Depots Client/Server), erst Branch "beta" (§5)
- **AI-Testing v2** (§8) — Mehrspieler-Pentest gegen Race-Conditions
  (`MarketService.performAction`, `PassiveIncomeService.collectBuilding`)
  VOR echter Mehrspieler-Last im Early Access, nicht danach
- Echtes Steam-Avatar über `ISteamUser/GetPlayerSummaries` (§4)
- Fenstermodus + Auflösungs-Einstellung (§7.2) — Machbarkeits-Recherche
  zum Cursor-Containment zuerst
- Steam-Deck-Rest: R1/L1-Gebäude-Zyklus (§7.2)

**Phase D — Post-Launch / große Features (nicht release-blockierend):**
- Rezepte-Rotation + Entdecken-Minigame (§7.2) — eigene Design-Session
- Spieler-Fusion (§9) — abhängig von Phase C (Steam-Auth muss sauber sein,
  bereits erledigt) plus eigener Design-Session
- Season-Automatisierung (§4)
- AI-Testing v3, dynamische Balance-Validierung (§8)

**Nicht eingeordnet, bei Bedarf einschieben:** Kosmetik-System (§4, braucht
zuerst eine Design-Entscheidung "was ist Kosmetik überhaupt").
