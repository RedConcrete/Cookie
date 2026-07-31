# Cookie — Roadmap & Fix-Liste

Stand: 2026-07-31. Ersetzt keine der Detail-Dokumente (`cookie-game-design.md`,
`CLAUDE.md`), sondern bündelt alle bekannten offenen Baustellen an einer
Stelle: Bugs, Aufräumarbeiten, Build/Deployment, Design-Doc-Pflege.

Priorität grob absteigend pro Abschnitt. Abgehakt = erledigt, nicht löschen
(Historie), sondern Häkchen setzen und ggf. Datum/Commit ergänzen.

---

## 0. Sofort (Sicherheit / Datenintegrität)

- [ ] **Negative-Amount-Exploit im Markt (kritisch).**
  `MarketService.performAction()` prüft `amount` nie auf `> 0`. Ein `BUY`
  mit negativem `amount` macht `totalCost` negativ →
  `cookies < totalCost` ist immer `false` → `cookies -= totalCost`
  **erhöht** das Cookie-Guthaben, während `addResourceToUser` Ressourcen
  ohne Floor-Clamp abzieht. Ergebnis: Cookie-Duplizierung +
  negative Ressourcenbestände.
  **Fix:** Validierung `amount > 0` serverseitig in `performAction()`
  (und idealerweise `@Positive` auf `MarketRequestDto.amount`), dazu
  Clamp auf `resourceAmount >= 0` als zweite Verteidigungslinie.
  Betrifft: `backend/.../service/MarketService.java`,
  `backend/.../dto/MarketRequestDto.java`.

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

---

## 3. Design-Dokument aktualisieren

`cookie-game-design.md` Abschnitt 2 ("Ist-Zustand") ist veraltet — markiert
Upgrade-System, Net Worth, Prestige, Season-Reset und Leaderboard/Profil
komplett als ❌, obwohl alle fünf serverseitig implementiert und ans
Frontend angebunden sind:

- **Upgrade-System** ✅ — `UpgradeService`/`UpgradeController`,
  `UpgradeShopView.vue`
- **Net Worth** ✅ — `NetWorthService` (Berechnung + 30s-Snapshot-Scheduler),
  `LeaderboardController`
- **Prestige** ✅ — `PrestigeService` + `PrestigeView.vue`/`PrestigeDialog.vue`,
  Formeln (`threshold`, `multiplier`) wie im Doc spezifiziert umgesetzt,
  voller serverseitiger Reset inkl. Validierung der Mindest-Net-Worth
- **Season-Reset** ✅ (aber nur manuell) — `SeasonService.closeSeason()` +
  `AdminController` (`X-Admin-Token`-geschützt), archiviert Rangliste in
  `SeasonResultEntity`, resettet alle Spieler. Kein Cron/Automatik — laut
  Design so gewollt ("Trigger: manuell")
- **Leaderboard/Profil** ✅ — volles Endpoint-Set vorhanden

**Abweichung vom Doc:** Abschnitt 6 "Typ D" (Gebäude als Upgrade-Kauf) wurde
nicht wie spezifiziert im generischen Upgrade-System (`UpgradeType`) gebaut,
sondern als eigenständiges, umfangreicheres System (`BuildingService`,
`BuildShopDialog.vue`) mit Bürger/Worker-Zuweisung, Löhnen, Lagerkapazität
und Markt-Gebühr-Rabatten. Funktional weiter als geplant, aber
architektonisch anders. Doc-Abschnitt 6 sollte auf die tatsächliche
`BuildingService`-Architektur umgeschrieben werden statt als "offen" zu
gelten.

**Aktion:** Abschnitt 2 (Tabelle) und Abschnitt 6 in
`cookie-game-design.md` überarbeiten, damit das Dokument wieder als
verlässliche Quelle für den Implementierungsstand dient.

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
- [ ] **Pixel-Art-Rework (Abschnitt 16)** — DOM-basiertes Hof-Grid ist mit
  `2142ecc`/`493354a` bereits Richtung Pixel-Art-Optik gegangen. Der
  Design-Doc-Absatz beschreibt eine echte Render-Engine (PixiJS/Phaser)
  mit freier Kamera als *langfristige* Vision — klären, ob das aktuelle
  DOM+CSS-Ergebnis als "gut genug" gilt oder der Engine-Wechsel weiterhin
  geplant ist. Falls DOM+CSS bleibt: Abschnitt 16 im Design-Doc auf
  "erledigt/überholt" setzen, sonst als eigene große Iteration einplanen

---

## 5. Build & Deployment (aus `CLAUDE.md` übernommen, weiter aktuell)

- [ ] **Steam-Upload vorbereiten** — Windows-Build testen
  (`npm run build:win`), `app_build_2816100.vdf` mit AppID 2816100 und
  Depots für Windows-Client + Server-Binary anlegen, Upload zuerst auf
  Steam-Branch "beta"
- [ ] **Server-Deployment** — Backend-JAR bauen (`./mvnw package
  -DskipTests`), PostgreSQL auf Zielserver einrichten (Schema via
  Hibernate `auto`), Systemd-Service oder Docker Compose aufsetzen,
  Firewall Port 9876 öffnen (oder Reverse Proxy), `app.dev-mode=false`
  setzen (nur Steam-Auth erlaubt)
- [ ] **HTTPS zwischen Client und Server** (Issue #20) — betrifft direkt
  den produktiven Server-Rollout oben, sollte vor "live" geklärt sein,
  nicht danach nachgerüstet werden

---

## 6. Kleinkram (aus dieser Session, bereits behoben)

Nur als Gedächtnisstütze, kein offener Task mehr:

- ~~`start.sh` hardcodete einen nicht existenten Linuxbrew-JAVA_HOME-Pfad~~
  → jetzt Auto-Detect via `which java`
- ~~`mvnw` hatte kein Ausführungsrecht auf dieser Maschine~~ → `chmod +x`
  gesetzt (in Git bereits korrekt mit `100755` hinterlegt, war ein rein
  lokales Checkout-Problem)
