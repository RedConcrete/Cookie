# Cookie — Roadmap & Fix-Liste

Stand: 2026-07-31. Ersetzt keine der Detail-Dokumente (`cookie-game-design.md`,
`CLAUDE.md`), sondern bündelt alle bekannten offenen Baustellen an einer
Stelle: Bugs, Aufräumarbeiten, Build/Deployment, Design-Doc-Pflege.

Priorität grob absteigend pro Abschnitt. Abgehakt = erledigt, nicht löschen
(Historie), sondern Häkchen setzen und ggf. Datum/Commit ergänzen.

---

## 0. Sofort (Sicherheit / Datenintegrität)

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

- [ ] **Balancing** — alle Platzhalter-Zahlen (sellFeeRate aktuell `0.05`
  fix im Code, Prestige-Schwelle/Multiplikator, Rezept-Mengen/Output/
  Backzeit, Upgrade-Kostenkurven) sind nie in einer echten Testphase
  durchgespielt worden. Nächster sinnvoller Schritt vor Early-Access:
  ein bis zwei interne Testrunden, dann Werte in `MarketConfig`,
  `RecipeEntity`-Seeds, `UpgradeEntity`-Seeds nachziehen
- [ ] **Kosmetik-System** — Design-Doc Abschnitt 11 lässt bewusst offen,
  was "freigeschaltete Kosmetik" konkret bedeutet (Titel? Rahmen? Icons?).
  `PlayerCosmeticEntity` als Datenmodell vorgesehen, aber ohne konkrete
  Inhalte nicht sinnvoll baubar — Design-Entscheidung nötig, bevor hier
  Code entsteht
- [ ] **Season-Automatisierung** — aktuell rein manuell ausgelöst
  (`AdminController`). Falls das Spiel produktiv läuft, überlegen ob ein
  Scheduler (`SeasonScheduler`, analog `MarketScheduler`) mit konfigu-
  rierbarem Intervall sinnvoller ist als "Dev drückt manuell einen Knopf"
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
