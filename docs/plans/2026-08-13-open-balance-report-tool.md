# ⏳ Wiederholbares Balancing-Report-Tool (Markt, Gebäude, Bürger/Lohn, Skill-Baum)

> **Status:** ⏳ Offen

## Context

Die Balance-Zahlen liegen aktuell verstreut in vier Stellen, jede mit
eigener Formel, keine davon gegeneinander geprüft:

- **Bürger/Worker-Preis**: `citizenBaseCost × citizenCostGrowth^ownedCount`
  (`BuildingService.java:184`, Default 50 × 1.15ⁿ).
- **Gebäude-Kosten**: `baseCost × buildingCostGrowth^level` pro Gebäude aus
  `BuildingService.BUILDINGS` (`BuildingService.java:36-47`, 309-310),
  Wachstum pauschal 2.0 für alle Gebäude gleich.
- **Lohn**: `workers × wagePerMinPerWorker` (`BuildingService.java:240`,
  Default 2.0/min/Worker).
- **Skill-Punkte-Kosten**: `skillPointBaseCost × skillPointCostGrowth^n`
  (`SkillTreeService.java:538-539`, Default 150 × 1.4ⁿ) — unabhängig davon,
  welcher Node gekauft wird.
- **Node-/Keystone-Effektwerte**: pro Node einzeln von Hand eingetragen in
  `SkillTreeService#buildNodes()` (~450 Zeilen Node-Definitionen). Keystones
  haben oft zwei Effekte (Bonus + Malus, z.B. `sugar_y3`: +20% Ernte, aber
  -5% Lohn-Reduktion = Lohn steigt) — ob das Verhältnis zwischen Bonus und
  Malus über alle ~15 Keystones konsistent ist, wurde nie systematisch
  geprüft.
- **Markt**: AMM-Konstante-Produkt-Formel `Preis = K / stock` mit
  `K = initialStock² × initialPrice` (`MarketConfig.java:9-12`) — wie stark
  ein normaler Kauf/Verkauf den Preis bewegt, hängt von `initialStock` pro
  Ressource ab, nie gegen eine typische Handelsmenge durchgerechnet.

Bisher wird an diesen Werten "nach Gefühl" gedreht (`GameBalanceConfig`,
`MarketConfig`, Skill-Node-Seeds sind laut `docs/cookie-game-design.md`
Abschnitt zu Konfiguration nie in Stein gemeißelt). Ziel: ein Skript, das
jederzeit erneut laufen kann — nach jeder Balance-Änderung, jedem neuen
Gebäude, jeder neuen Skill-Branch, vor jedem Season-Reset — und die
aktuellen Kurven/Verhältnisse als Report ausgibt, statt jedes Mal von Hand
nachzurechnen.

## Recherche: kostenlose Tools

Kurz recherchiert, was es an fertigen Tools gibt:

- **[Machinations.io](https://machinations.io/)** — browserbasiertes
  Node-Diagramm-Tool für Wirtschaftssimulation, hat einen kostenlosen
  Free-Tier (Monte-Carlo-Simulation, keine Kreditkarte nötig). Gut für
  frühe Konzept-/Lehr-Phasen, aber: das Wirtschaftsmodell müsste komplett
  manuell in deren eigener Notation nachgebaut und bei jeder Formel-Änderung
  im Java-Code von Hand synchron gehalten werden — Cookie hat die echten
  Formeln längst im Code, ein externes Zweitmodell würde sofort auseinanderlaufen.
- **Google Sheets/Excel** — kostenlos, gut zum Kurven-Anschauen, aber
  gleiches Problem: Formeln müssten manuell gepflegt werden, kein Bezug zu
  den Live-Werten aus `GameBalanceConfig`/`skill_nodes`.
- **Anthony Pecorella Idle-Game-Worksheets** (Internet Archive) — nützliche
  Methodik-Referenz für Payback-Perioden/Kostenkurven-Denkweise, kein
  einsetzbares Tool.
- **Diverse Itch.io-"Idle Game Economy Calculator"-Tools** — generische
  Zahlengeneratoren für neue Projekte, keine Anbindung an bestehenden
  Live-Server/Code.

**Entscheidung**: kein externes Tool, sondern ein kleines Node-Skript im
Repo (Muster: `frontend/scripts/check-palette.mjs`, bereits etablierte
Konvention für Dev-Skripte ohne neue Dependency). Vorteil gegenüber allen
oben genannten Tools: liest die **echten Live-Werte** direkt vom
laufenden Dev-Server (`GET /api/v1/admin/config`,
`GET /api/v1/admin/skilltree/nodes`) und bleibt damit automatisch
synchron, statt ein zweites, driftendes Modell zu pflegen. Kostenlos, kein
Account, keine Daten verlassen die Maschine.

## Umsetzung

**Neues Skript** `frontend/scripts/balance-report.mjs`, npm-Skript
`"balance:report": "node scripts/balance-report.mjs"` in
`frontend/package.json` (analog `check:palette`). Flags:
- `--live` (Default): holt Werte per `fetch` von `http://localhost:9876`
  (Dev-Server muss laufen, `app.dev-mode=true` — kein Admin-Token nötig,
  siehe bestehendes Muster in `api.js`).
- `--static`: nutzt fest hinterlegte Fallback-Defaults (1:1 aus
  `GameBalanceConfig.java`/`MarketConfig.java` abgeschrieben) — Skript
  bleibt lauffähig, auch wenn kein Server läuft.
- `--out <pfad>`: HTML-Report-Ziel, Default `docs/balance-reports/latest.html`
  (Ordner neu, per `.gitignore`-Eintrag ausgeschlossen — ist ein Report-
  Artefakt, kein Quelltext).

**Vorschlagswert-Berechnung**: für jeden Flag (Payback außerhalb Zielband,
Keystone-Ausreißer, Markt-Slippage außerhalb Zielband) rechnet das Skript
zusätzlich einen konkreten Vorschlagswert aus — per Bisektion über genau
die eine betroffene Konstante (z.B. `citizenCostGrowth`), die den Metrik-
Wert in die Mitte des Zielbands bringt, alle anderen Werte bleiben fix
(ceteris paribus, keine gemeinsame Neu-Optimierung mehrerer Konstanten
gleichzeitig — das wäre deutlich komplexer und für den ersten Wurf nicht
nötig).

Jede Formel im Skript trägt einen Kommentar mit exakter Fundstelle im
Backend (Datei + Zeile, siehe Context oben), damit eine künftige
Formel-Änderung dort auffällt, dass das Skript nachgezogen werden muss
(kein automatischer Sync-Test — für ein Dev-only-Tool ausreichend, siehe
bestehende Praxis bei anderen Skripten in `scripts/`).

**Report-Module** (jedes rechnet eine Kurve + markiert Ausreißer gegen fest
im Skript hinterlegte Zielbänder — Konstanten am Skript-Anfang, direkt
editierbar, kein separates Config-Format nötig):

1. **Bürger/Worker**: Kostenkurve für n = 0..30, Payback-Periode je
   zusätzlichem Bürger = `kosten / (wagePerMinPerWorker)` in Minuten.
   Flag, wenn Payback außerhalb `TARGET_CITIZEN_PAYBACK_MIN/MAX_MIN` liegt
   oder zwischen zwei benachbarten Bürgern stark springt.
2. **Gebäude**: pro Gebäude aus `BUILDINGS`-Liste Kostenkurve für Level
   1..10, Payback-Periode je Level = `levelKosten / (workersPerLevel ×
   passiveRatePerSecPerWorker × 60 × aktueller Marktpreis der Ressource)`.
   Nutzt für den Marktpreis den aktuellen AMM-Preis aus Modul 5 (siehe
   unten) statt eines festen Werts — Kosten/Nutzen hängen vom Marktpreis
   der jeweiligen Ressource ab.
3. **Skill-Punkte**: Kostenkurve für n = 0..40 (`skillPointBaseCost ×
   skillPointCostGrowth^n`). Zusätzlich: durchschnittliche Effekt-Magnitude
   pro Tier (PASSIVE/NOTABLE/KEYSTONE), aus allen Node-Effekten der
   geladenen `skill_nodes` aggregiert — Sanity-Check, dass KEYSTONE spürbar
   stärker ist als PASSIVE, im Verhältnis zu den kumulierten Skill-Punkt-
   Kosten bis dahin.
4. **Keystones**: pro KEYSTONE-Node mit ≥2 Effekten Bonus- vs.
   Malus-Magnitude nebeneinander (roh, da Effekttypen oft nicht direkt
   vergleichbare Einheiten sind — z.B. Ernte-% vs. Lohn-%), plus ein grob
   normiertes Bonus:Malus-Verhältnis. Flag, wenn ein Node stark vom Median
   aller Keystones abweicht (deutet auf über-/untertunte Einzel-Node hin).
5. **Markt**: pro Ressource AMM-Preis-Impact für Kauf/Verkauf einer
   "typischen" Menge (Konstanten `TYPICAL_TRADE_QTY = [50, 200, 1000]`),
   berechnet aus `K = initialStock² × initialPrice`, `neuerPreis =
   K / (stock ± menge)`. Flag, wenn die Preisbewegung bei der mittleren
   Menge deutlich unter/über `sellFeeRate` liegt (Slippage sollte spürbar,
   aber nicht dominant gegenüber der Gebühr sein).

**Ausgabe**:
- Konsolen-Tabellen (schlichtes gepaddetes Text-Format wie
  `check-palette.mjs`'s Ausgabe, kein neues Dependency) mit `⚠`-Spalte bei
  Zielband-Verstoß, Exit-Code bleibt 0 (reines Diagnose-Tool, kein CI-Gate
  wie `check:palette`).
- Selbstständige HTML-Datei mit eingebetteten SVG-Liniendiagrammen (kein
  externes CDN, gleiche Machart wie die Netto-Wert-Graphen im Frontend) pro
  Modul, für schnellen visuellen Blick auf die Kurven — wird bei jedem Lauf
  überschrieben.
- **Markdown-Vorschlagsdatei** `docs/balance-reports/YYYY-MM-DD-HHmm-suggestions.md`
  (Datum/Uhrzeit im Namen statt `latest` — Verlauf über mehrere Balancing-
  Sessions bleibt nachvollziehbar, gleiches Muster wie die datierten Dateien
  in `docs/plans/`). Nur die tatsächlich geflaggten Punkte, pro Punkt:
  - Betroffenes Feld mit vollem Pfad (z.B.
    `GameBalanceConfig.citizenCostGrowth`, `SkillTreeService#buildNodes()
    Node "sugar_y3"`) — Claude kann daraus direkt die Zeile im Quellcode
    finden, ohne selbst erst zu suchen.
    Aktueller Wert, berechnete Metrik (Payback-Minuten/Verhältnis/Slippage-%),
    verletztes Zielband, vorgeschlagener neuer Wert samt neu berechneter
    Metrik damit.
  - Kurzer Klartext-Satz pro Punkt (z.B. "Bürger Nr. 12 hat Payback 47min,
    Zielband 5-20min — Vorschlag: citizenCostGrowth 1.15 → 1.11 senkt es auf
    ~18min").
  Dieses File ist bewusst das Übergabe-Artefakt für Claude: nach einem Lauf
  liest Claude die Datei, bespricht die Vorschläge mit dem Entwickler
  (welche übernehmen, welche verwerfen/anpassen), trägt die gewählten Werte
  dann selbst in `GameBalanceConfig.java`/`MarketConfig.java`/
  `SkillTreeService.java` ein. Das Skript selbst schreibt nie in
  Quellcode — nur die `.md`-Datei als Vorschlag, die eigentliche Änderung
  bleibt ein von Claude+Entwickler gemeinsam entschiedener Schritt.

## Verification

- `npm run balance:report -- --live` gegen laufenden Dev-Server (siehe
  `scripts/start.sh`): Werte für 2.-3. Bürger von Hand nachgerechnet
  (50 × 1.15¹ = 57.5) stimmen mit Skript-Ausgabe überein.
- `npm run balance:report -- --static` bei gestopptem Server: läuft
  trotzdem durch, nutzt Fallback-Defaults, keine Exceptions.
- HTML-Report öffnet im Browser, SVG-Kurven rendern, keine Konsolenfehler.
- Mindestens ein bewusst falsch verstellter Wert (z.B.
  `citizenCostGrowth` testweise auf 3.0) lässt das entsprechende Modul im
  Report sichtbar als `⚠` aufflammen — bestätigt, dass die Zielband-Checks
  tatsächlich greifen.
- Bei diesem bewusst falsch verstellten Wert erscheint auch ein Eintrag in
  der `.md`-Vorschlagsdatei mit korrektem Feld-Pfad und einem
  Vorschlagswert, der den falschen Wert wieder ins Zielband bringt.

## Wiederkehrender Einsatz

Shortcut über die bestehenden Start-Skripte: `scripts/start.sh --balance`
(bzw. `scripts\start.bat --balance` unter Windows) startet keinen Server,
sondern ruft direkt `npm run balance:report` im `frontend`-Verzeichnis auf
-- weitere Flags (`--static`, `--out=...`) werden 1:1 durchgereicht. Für den
Live-Modus muss der Dev-Server separat laufen (z.B. `scripts/start.sh` in
einem zweiten Terminal), sonst greift automatisch der `--static`-Fallback.

Ablauf, wiederholbar bei jeder Balancing-Session:

1. `scripts/start.sh --balance` (Server läuft bereits) oder
   `npm run balance:report -- --live` direkt im `frontend`-Verzeichnis.
2. Entwickler bittet Claude, die neue `docs/balance-reports/*-suggestions.md`
   durchzugehen ("mach Balancing" o.ä.).
3. Claude liest die Datei, fasst die geflaggten Punkte zusammen, bespricht
   mit dem Entwickler welche Vorschläge übernommen werden.
4. Claude trägt die gemeinsam entschiedenen Werte in
   `GameBalanceConfig.java`/`MarketConfig.java`/`SkillTreeService.java` ein.
5. Skript erneut laufen lassen, um zu bestätigen, dass die Flags weg/kleiner
   sind.

Lauf triggern bei: Änderung von `GameBalanceConfig`/`MarketConfig`-
Defaults, neuem Gebäude, neuer Skill-Branch/neuem Keystone, vor jedem
Season-Reset (siehe `docs/cookie-game-design.md` Abschnitt 9).

## Nicht im Scope

- Automatisches Eintragen der Vorschlagswerte durch das Skript selbst
  (kein Autopilot) — das Skript schreibt nur die `.md`-Vorschlagsdatei,
  das eigentliche Ändern von Quellcode/Live-Config bleibt ein Schritt, den
  Claude zusammen mit dem Entwickler macht (siehe "Wiederkehrender
  Einsatz").
- Monte-Carlo-Simulation ganzer Spielverläufe über Zeit (verschiedene
  Spielstile, AFK-Verhalten, Dispo-Zinsen-Spiralen) — nur idealisierte
  Kurven auf Basis der reinen Formeln, keine Ereignis-Simulation.
- Anbindung an ein externes Tool (Machinations o.ä.) — siehe Recherche
  oben, bewusst verworfen.
