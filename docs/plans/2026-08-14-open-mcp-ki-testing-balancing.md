# ⏳ MCP-Schnittstelle für KI-gesteuertes Testen + Balancing-Report-Tool (Dev-only)

> **Status:** ⏳ Offen

## Context

Zusammengelegt aus zwei ursprünglich getrennten Plänen (2026-08-14, auf
Wunsch des Users), weil sie dasselbe Grundproblem von zwei Seiten angehen —
**Balance-Bugs in der AMM-Markt-Wirtschaft und der Kostenkurven wurden
bisher nur durch manuelles Playtesting gefunden** (siehe
`docs/ROADMAP.md` Abschnitt 7.1: Markt-Crash bei ~200 Einheiten,
Negative-Amount-Exploit, Race-Condition beim gleichzeitigen Einsammeln):

- **`docs/plans/2026-08-11-open-mcp-ki-testing.md`** (Kollegen-Idee): ein
  KI-Agent spielt über MCP-Tools wie ein echter Spieler, um Bugs und
  Nebenläufigkeits-Probleme zu finden.
- **`docs/plans/2026-08-13-open-balance-report-tool.md`**: ein Node-Skript
  rechnet die Formeln aus `GameBalanceConfig`/`MarketConfig`/
  `SkillTreeService` zu Kurven/Zielband-Checks durch — aber **rein
  idealisiert**, explizit ohne Ereignis-Simulation (siehe "Nicht im Scope"
  dort).

Die Lücke zwischen beiden: das Formel-Skript sagt, wie sich der Markt
*laut Formel* verhalten sollte, der MCP-Agent kann echte Aktionen gegen den
Dev-Server fahren und zeigen, wie er sich *tatsächlich* verhält — inklusive
Rundungsfehlern, Cooldowns, Nebenläufigkeit, die eine reine Formel nicht
abbildet. Beide Pläne bleiben inhaltlich wie vorher, nur um Stufe **v3**
unten ergänzt, die sie verbindet. Die beiden Quell-Dateien werden gelöscht,
dieser Plan ist ab jetzt die einzige Quelle für beide Themen.

## Ziel

1. Ein KI-Agent (Claude über MCP-Client) kann Spielaktionen ausführen wie
   ein echter Spieler und damit automatisiert Bugs/Race-Conditions finden.
2. Ein wiederholbar laufendes Skript zeigt jederzeit die aktuellen
   Balance-Kurven (Bürger, Gebäude, Skill-Punkte, Keystones, Markt) und
   flaggt Ausreißer außerhalb definierter Zielbänder.
3. **Neu (v3):** die vom MCP-Agent erzeugten echten Spielverläufe werden
   als Datenquelle für Modul 5 (Markt) des Reports nutzbar — reale
   Slippage/Payback-Werte neben die idealisierten Formel-Werte gestellt.

**Ausdrücklich nur Dev-Umgebung, nie aktive Nutzung in Produktiv** (gilt
für MCP-Tools und für `--live`-Fetches des Report-Skripts gleichermaßen).

## v0 — Statisches Balance-Report-Skript ✅ Umgesetzt (2026-08-13, Commit 39b2c6c)

Kein MCP nötig, kleinstes eigenständiges Stück, bereits vollständig gebaut
und im Einsatz — 1:1 aus dem Ursprungsplan
`2026-08-13-open-balance-report-tool.md` übernommen (Datei war schon vor
dem Merge dieses Plans fertig, nur der Status-Marker dort hinkte
hinterher):

- **Neues Skript** `frontend/scripts/balance-report.mjs`, npm-Skript
  `"balance:report": "node scripts/balance-report.mjs"` (Muster:
  `check-palette.mjs`). Flags `--live` (Default, fetcht von
  `http://localhost:9876`), `--static` (Fallback-Defaults ohne Server),
  `--out <pfad>`.
- Fünf Report-Module mit Zielband-Checks: Bürger/Worker-Payback,
  Gebäude-Payback pro Level, Skill-Punkte-Kostenkurve +
  Effekt-Magnitude pro Tier, Keystone-Bonus:Malus-Verhältnis,
  Markt-AMM-Slippage bei typischen Handelsmengen.
- Vorschlagswert-Berechnung per Bisektion über die eine betroffene
  Konstante (ceteris paribus).
- Ausgabe: Konsolen-Tabellen, selbstständige HTML-Datei mit
  SVG-Kurven, datierte Markdown-Vorschlagsdatei
  `docs/balance-reports/YYYY-MM-DD-HHmm-suggestions.md` als
  Übergabe-Artefakt an Claude (Skript schreibt nie in Quellcode selbst).
- Recherche zu fertigen Tools (Machinations.io, Sheets, Idle-Game-
  Rechner) ergab: keins hat Bezug zu den echten Live-Werten aus dem
  Code — Entscheidung für eigenes Skript steht, siehe Ursprungsplan für
  Details.
- Zusätzlich zum ursprünglich geplanten Payback-Modell entstand beim
  Bauen ein **Fortschritts-Simulator** (`simulateProgression()`): statt
  Payback-in-Minuten pro einzelnem Kauf simuliert er komplette
  Spielverläufe (Check-in alle 12h, Kaufschleife, Hover-Ernte) bis zum
  ersten Prestige-Reset — damit wurde `prestigeBaseThreshold` bereits am
  2026-08-13 von 100.000 auf 4500 korrigiert (siehe Kommentar in
  `GameBalanceConfig.java`).
- Bereits verdrahtet: `npm run balance:report` (`frontend/package.json`),
  `scripts/start.sh --balance` / `start.bat --balance`, Output-Ordner
  `docs/balance-reports/` (`.gitignore`t, mehrere Suggestions-Läufe vom
  13.08. liegen dort schon).

**Für v3 unten relevant:** die Kaufschleife in `simulateProgression()`
nutzt aktuell einen **statischen** Marktpreis (eigener Preis-Impact des
simulierten Spielers wird ignoriert, siehe Kommentar dort "für die grobe
Tage-Kurve ausreichend") — genau die Stelle, die v3 mit echten
MCP-Agent-Handelsdaten verfeinern könnte, statt weiter zu vereinfachen.

## v1 — MCP-Server: Single-Dev-Player-Tools ✅ Umgesetzt (2026-08-14)

**MCP-Server wrappt die bestehende REST-API**, kein eigener Spiel-Zugriff
nötig — Backend-Validierung gilt für den Agent genauso wie für den echten
Client (siehe `CLAUDE.md`: "Client-Werte nie vertrauen"). Eigener
Node-Prozess (`@modelcontextprotocol/sdk`), z. B.
`tools/mcp-testing-server/`, separates Package, kein Teil von
Electron-Build/Spring-Boot-Deployment.

**Spielt als Dev-Player** über dieselbe Mechanik wie der bestehende
Web-Fallback (`DEV_PLAYER_001` bei `app.dev-mode=true`).

**Harte Leitplanke:** Server verweigert Start/jede Aktion, wenn das
Ziel-Backend nicht erkennbar im Dev-Modus läuft (Health-Check gegen
`dev-mode`-Flag oder `localhost`-Whitelist). Kein Admin-Token im
MCP-Server — nur normale Gameplay-Endpunkte.

**Scope v1** (fragilste Systemgruppe zuerst):
- `market_buy` / `market_sell` (wrapt `MarketController`) — Stelle mit den
  bisher kritischsten Bugs (Negative-Amount-Exploit, Markt-Crash bei
  ~200 Einheiten).
- `farm_harvest`, `farm_collect_building` — Kern-Loop.
- `game_get_state` (liest `UserInformationDto`) — Agent sieht echten
  Serverstand statt zu raten.

Bewusst NICHT in v1: Skill-Baum-Allokation, Backen/Rezepte, Season-Reset.

**Umgesetzt:** `tools/mcp-testing-server/` (`package.json`, `src/guardrail.mjs`,
`src/api-client.mjs`, `src/index.mjs`). Fuenf Tools:
`game_get_state` (buendelt `GET /api/v1/users/{id}` + `/api/v1/farm/buildings/{id}`
+ `/api/v1/market/get/1`), `market_buy`/`market_sell`, `farm_harvest`,
`farm_collect_building`. Leitplanke (`guardrail.mjs`) prueft vor JEDEM
Tool-Call erneut Localhost-Whitelist + `devMode:true` gegen
`GET /api/v1/config` (3s Timeout) -- nicht nur beim Start, falls das
Backend waehrend einer laufenden Session neu startet. Verifiziert per
Live-Testlauf gegen den Dev-Server (Corretto 21, `mvnw spring-boot:run`):
`game_get_state` liefert korrekten Snapshot, `farm_harvest` erhoeht Sugar
serverseitig, `market_buy` mit `amount:-5` wird korrekt vom Backend
abgelehnt ("Amount must be positive: -5.0" -- bestaetigt den
Negative-Amount-Exploit-Fix als Regressionstest), `market_sell` und
`farm_collect_building` (Butterei-`pendingAmount` 360 korrekt eingesammelt)
funktionieren wie erwartet. Kein `@modelcontextprotocol/sdk`-Client-Config
(`.mcp.json` o.ae.) im Repo eingetragen -- noch offen, siehe unten.

## v2 — Mehrspieler-Simulation & Pentest

- **Mehrere Dev-Player-Identitäten** (`DEV_PLAYER_001..00N` oder
  Erzeuge-Endpoint für Wegwerf-Testspieler), nur unter
  `app.dev-mode=true`, gleiche Leitplanke wie v1.
- **Parallele Aktionen erzwingen** — MCP-Tool, das N simulierte Spieler
  gleichzeitig dieselbe Aktion ausführen lässt, gezielt gegen
  `MarketService.performAction`/`PassiveIncomeService.collectBuilding`.
  Danach Konsistenz-Check: Summen stimmen, kein negativer Stock, kein
  doppelt gezähltes Einsammeln.
- **Pentest im engeren Sinn**: gezielt Grenzfälle/ungültige Eingaben
  (negativer `amount` etc.) als Regressionstest für bereits gefixte
  Sicherheitslücken. Explizit nur gegen Dev-Server.

## v3 — Zusammenführung: Dynamische Balance-Validierung (neuer Teil, aus dem Merge)

Sobald v1 (und optional v2) stehen, bekommt Modul 5 (Markt) des
Report-Skripts aus v0 einen dritten Modus neben `--live`/`--static`:

- **`--dynamic <log-datei>`**: liest einen JSON-Log echter Aktionen, den
  der MCP-Agent während eines v1/v2-Testlaufs mitschreibt (jeder
  `market_buy`/`market_sell`-Call mit Zeitstempel, Menge, Preis vorher/
  nachher aus der echten Server-Antwort). Skript legt diese realen
  Preisbewegungen neben die idealisierte AMM-Formel-Kurve aus `--live`/
  `--static` und flaggt Abweichungen (z. B. weil mehrere Dev-Player aus
  v2 gleichzeitig gehandelt haben und die reale Slippage dadurch höher
  ausfällt als die Einzelspieler-Formel vorhersagt).
- Gleiches Prinzip für Bürger/Gebäude-Payback: reale Cooldown-/
  Timing-Effekte aus echten `farm_collect_building`-Calls (v1) vs.
  idealisierte Minuten-Rechnung aus v0.
- **Nutzen für v2 konkret:** die Konsistenz-Checks aus v2
  ("kein negativer Stock, keine doppelt gezählten Einsammlungen") laufen
  ohnehin während der Race-Condition-Tests — deren Ergebnis fließt als
  Pass/Fail direkt in dieselbe Markdown-Vorschlagsdatei wie die
  Balance-Flags aus v0, statt in einem separaten Test-Report. Ein
  Balancing-Lauf und ein Race-Condition-Lauf erzeugen damit ein
  gemeinsames Artefakt statt zwei.
- Bewusst **kein** automatisches Eintragen von Vorschlagswerten — wie in
  v0, bleibt Handarbeit von Claude + Entwickler.

## Offene Fragen (vor Umsetzung zu klären)

- Ein MCP-Server für alles, oder mehrere kleine (Markt/Farm/Skillbaum
  getrennt)? Tendenz: mit einem Server starten, splitten falls
  unübersichtlich.
- Soll der Agent auch UI-Zustand prüfen können (Playwright), oder reicht
  reiner REST-API-Zugriff für v1? Reiner REST-Zugriff deckt Wirtschafts-/
  Balance-Bugs ab, aber keine reinen Frontend-Bugs.
- Log-Format für v3 (`--dynamic`-Input): eigenes JSON-Schema im MCP-Server
  definieren, sobald v1 steht — noch nicht festgelegt.
- Wo laufen MCP-Server-Konfiguration/Zugangsdaten (falls später doch ein
  Dev-Token nötig wird)? Nie ins Repo committen.

## Reihenfolge

v0 ist bereits fertig (s.o.). v1 ist der nächste Schritt. v2 erst nach
v1 bewährt. v3 erst, wenn v1 läuft (braucht echte Log-Daten aus v1),
v0 steht dafür schon bereit.

## Nicht im Scope

- Jede Nutzung (MCP-Tools wie `--live`-Report-Fetches) gegen den
  Live-Beta-Server (`https://cookie.r3dconcrete.de`).
- Automatisiertes CI-Testing über die MCP-Tools (denkbare Folgeidee,
  aber erst nach v1 als manuelles Dev-Tool bewährt).
- Automatisches Eintragen von Vorschlagswerten durch irgendein Skript
  (kein Autopilot, siehe v0/v3).
- Monte-Carlo-Simulation *hypothetischer* Spielverläufe (verschiedene
  Spielstile, AFK-Verhalten) — v3 nutzt nur *tatsächlich* vom MCP-Agent
  gefahrene, echte Verläufe, keine synthetische Simulation.
- Anbindung an ein externes Tool (Machinations o.ä.) — bewusst verworfen,
  siehe v0-Recherche.
