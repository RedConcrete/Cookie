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

- [ ] **Keine echte Steam-Auth-Verifizierung (kritisch vor Public/Early-Access).**
  `app.dev-mode=false` schaltet aktuell NUR die Admin-Token-Pflicht scharf
  (`AdminConfigController`, `AdminController`) sowie Bake-Dauer/Dev-Reset —
  alle normalen Gameplay-Endpunkte (`game/init`, `farm/*`, `market/*`) nehmen
  die `steamId` ungeprüft als Parameter entgegen. Jeder Client (curl, o.ä.)
  kann sich als beliebige `steamId` ausgeben, fremde Ressourcen/Cookies
  ändern oder im geteilten Markt handeln. `electron/main.js` holt zwar über
  `steamworks.js` eine echte SteamID vom Client, reicht sie aber nur
  ungeprüft weiter — keine serverseitige Verifizierung.
  **Fix (noch offen):** `GetAuthSessionTicket` client-seitig (via
  `steamworks.js`) + serverseitige Validierung über Steamworks Web API
  (`ISteamUserAuth/AuthenticateUserTicket`) vor jedem Request, der eine
  `steamId` entgegennimmt. Für eine geschlossene Freundes-Beta (kleine,
  vertraute Testgruppe, kein Fremd-Traffic) vorerst zurückgestellt —
  zwingend vor jedem Early-Access-/Public-Release nachholen.

- [ ] **Browser-Zugang (ohne Electron) — Steam OpenID-Login.**
  Aktuell nur über Electron+`steamworks.js` spielbar; der Web-Fallback in
  `App.vue` (kein `window.electronAPI`) nutzt nur `DEV_PLAYER_001` bei
  `dev-mode=true` — kein echter Login im Browser. Separater Mechanismus
  von obigem Ticket-Auth-Punkt: Steam OpenID (`login.steampowered.com/openid`,
  klassisches "Sign in through Steam"-Redirect), läuft komplett im Browser,
  kein natives SDK nötig. Server verifiziert die OpenID-Antwort → echte
  SteamID. Achtung: OpenID bestätigt nur die Identität, nicht den
  Spielbesitz — für Kaufpflicht zusätzlich Ownership-Check über die Steam
  Web API (`CheckAppOwnership` o.ä.) mit der verifizierten SteamID. Eigener
  Implementierungsaufwand, kommt on top zum Ticket-Auth-Punkt oben, nicht
  parallel nebenbei einflicken.

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
- [ ] **Toten Frontend-Code entfernen:** `MarketTable.vue` und
  `TradePanel.vue` — durch `MarketView.vue`/`MarketDialog.vue` ersetzt,
  keine Referenzen mehr im Code (siehe #21 oben)
- [ ] **`frontend/src/assets` durchsehen** (214 MB) auf ungenutzte
  Sprites/Sounds aus der alten Unity-Optik — insbesondere nach dem
  Pixel-Art-Rework (`2142ecc`, `493354a`) dürften alte Sprite-Sets aus dem
  vorherigen Look verwaist sein
- [ ] Große Audio-Assets im Build prüfen (`ElevatorMusic-*.wav` ~16.9 MB,
  mehrere `.mp3` >3–5 MB) — für Steam-Distribution ok, aber ggf. auf OGG/
  niedrigere Bitrate umstellen, wenn Downloadgröße relevant wird
- [ ] **Gebäude-Szenen auf Fruitpunch24-Palette migrieren** (siehe
  `cookie-game-design.md` Abschnitt 8). Alle aktuellen Gebäude-SVGs
  (`frontend/src/assets/buildings/*.svg`) sind Platzhalter und nutzen
  keine der 24 Palette-Farben — bewusst so, werden im Rahmen des
  UI-Rebuilds nach und nach durch palette-konforme Pixel-Art ersetzt.
  Neue Referenz-Assets liegen bereits unter
  `frontend/src/assets/buildings/StorageBuildng/` (Lager-Gebäude,
  Testbilder, noch nicht verdrahtet).
- [ ] **Markt-Hover-Popup zeigt statische 8%-Marktgebühr** (`buildingInfo.js`,
  `BUILDING_INFO.markt.rows`), obwohl die echte Gebühr mit Markt-Level sinkt
  (`BuildingService#getEffectiveSellFeeRate`, −2%/Stufe über Stufe 1). Gleiches
  Bug-Muster wie bei den Produktionsgebäuden (Lohn/Ertrag, 2026-08-09 gefixt) —
  nur das Markt-Gebäude selbst wurde dabei bewusst ausgeklammert (kein
  Arbeiter-/Lohn-Bezug, eigenständiges Thema).

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
  - **Erkennung:** `steamworks.js` bringt `isSteamRunningOnSteamDeck()`
    fertig mit (`client.d.ts`) — automatischer Check beim Start in
    `electron/main.js`, Ergebnis per IPC (analog `steam-auth`) ans Frontend
    durchreichen, z.B. `steam-deck-mode` Event oder Teil des bestehenden
    `steam-auth`-Payloads.
  - **Fadenkreuz-Modus (Standard auf Deck):** Crosshair fix in Bildschirm-
    mitte. Kamera-Pan via Stick ist jetzt da (s.o.) — offen ist noch der
    Crosshair selbst: Position bleibt zentriert, die Welt bewegt sich
    darunter.
  - **Interaktion:** Wenn Crosshair über einem Gebäude steht und Spieler
    A drückt → selbes Verhalten wie Klick (`BuildingFrame.vue` `@open`).
    Braucht Hit-Test von Bildschirmmitte gegen die aktuell sichtbaren
    Gebäude-Bounding-Boxes.
  - **Umschalten Fadenkreuz ↔ Maus:** Klick auf linken Stick (L3) togglet
    Modus. Im Maus-Modus steuert der linke Stick einen echten Mauszeiger
    (Standard-Gamepad-zu-Maus-Verhalten), damit UI-Buttons/Dialoge normal
    bedienbar bleiben, die kein Gamepad-Konzept haben.
  - Braucht generell: Gamepad-Input-Handling im Frontend (`Gamepad API` des
    Browsers reicht i.d.R., kein natives SDK nötig), neuer Composable
    (z.B. `useGamepadCursor.js`) analog zu `useHotkeys.js`.
  Zurückgestellt, User will das später angehen.
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
  - [ ] **Anti-Cheat-Re-Verifikation für Skill-Allokationen.** Der
    Allokations-Endpunkt prüft die Konnektivität nur beim Freischalten
    selbst — es gibt keinen periodischen Job, der bestehende
    `player_skill_nodes`-Zeilen im Nachhinein erneut gegen die Kanten
    validiert (z. B. nach einem manuellen DB-Eingriff oder einem Bug in
    einer früheren Version). Vor Public-Release nachholen, analog zu
    anderen serverseitigen Integritätschecks.
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
  - [ ] **Werte-Editor** — bestehende Nodes im Tool selbst umbenennen/
    Effekte anpassen/Branch+Tier ändern, statt Java-Seed-Code editieren zu
    müssen. Backend kann das schon (`PUT /skilltree/nodes/{id}` nimmt
    bereits Name/Beschreibung/Effekte/Branch/Tier entgegen), fehlt nur die
    UI (Formular im selben Dialog, z. B. im Info-Panel bei ausgewähltem
    Node).
  - [ ] **Neue Nodes erstellen/löschen** — aktuell kann der Editor nur
    bestehende (geseedete) Nodes verschieben/verbinden, keine komplett
    neuen anlegen. Bräuchte einen neuen `POST /admin/skilltree/nodes`-
    Endpoint (frei wählbare ID) + `DELETE` + Klick-auf-leere-Fläche-
    erstellt-Node o.ä. im Frontend.
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
    `docs/cookie-game-design.md` Abschnitt 9).
  - [ ] **Nodes klonen** — bestehenden Node duplizieren (Werte/Effekte
    übernehmen, neue ID, leicht versetzte Position, keine Kanten
    mitkopieren) statt jeden neuen Node einzeln von Hand anzulegen. Baut auf
    dem "Neue Nodes erstellen"-Punkt oben auf (braucht den
    `POST /admin/skilltree/nodes`-Endpoint), spart danach beim Erweitern
    des Baums viel Klickarbeit.
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

---

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
  `2026-08-10-open-skillbaum-respec.md` (Punkte gegen Cookies zurückgeben,
  fixer Preis, konnektivitäts-sicher),
  `2026-08-10-open-skillbaum-suche-buildplanung.md` (Such-/Filter-UI +
  erweiterte Tooltips),
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

## 8. AI-Driven Testing (Idee, 2026-08-11)

- [ ] **Alles im Spiel soll später per KI-Tools testbar sein.** Ziel: MCP-
  Schnittstellen bauen, die Spielaktionen (klicken, Gebäude kaufen, Markt
  handeln, Skill-Punkte setzen, etc.) für KI-Agents zugänglich machen, damit
  ein Agent das Spiel automatisiert durchspielen und dabei Bugs/Balance-
  Probleme finden kann (v.a. relevant für die AMM-Markt-Wirtschaft, siehe
  Abschnitt 6 `cookie-game-design.md`). **Ausdrücklich nur Dev-Umgebung,
  nie aktive Nutzung in Produktiv.** Architektur-Vorschlag (v1: MCP-Server
  wrappt bestehende REST-API, spielt als Dev-Player, Scope zuerst Markt/Farm;
  v2: mehrere simulierte Spieler gleichzeitig für Race-Condition-/Pentest-
  Findung) jetzt in `docs/plans/2026-08-11-open-mcp-ki-testing.md` — noch
  offene Fragen dort (ein Server vs. mehrere, Playwright-Anbindung ja/nein).

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
