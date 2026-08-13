# ⏳ Grundstücke-Paywall im Rathaus

> **Status:** ⏳ Offen

## Context

Aktuell kann ein Spieler theoretisch alle 6 Produktionsgebäude direkt
kaufen, sobald er sich die Kosten zusammengespart hat — `BuildingService`
kennt kein Limit für gleichzeitig besessene Gebäude, nur den Preis pro
einzelnem Gebäude (`BuildingDef.baseCost`, siehe `BUILDINGS`-Liste). Ziel
(Diskussion 2026-08-13): nur ein Gebäude soll am Spielstart baubar sein,
weitere Gebäude-"Grundstücke" werden über eine eigene Kostenkurve im
Rathaus freigeschaltet — das macht die "1 Gebäude"-Frühphase, die der
Fortschritts-Simulator (`docs/plans/2026-08-13-open-balance-report-tool.md`,
ROADMAP Punkt 11) simuliert, zur echten Spielregel statt nur einer
Spieler-Selbstbeschränkung.

**Entschieden:**
- Eigene Kostenkurve pro Grundstück (nicht an Rathaus-*Level* gekoppelt wie
  `citizensPerRatLevel`), analog zur Bürger-Kostenkurve:
  `plotCost(n) = plotBaseCost × plotCostGrowth^n`, `n` = Anzahl bereits
  besessener Produktionsgebäude.
- Freie Wahl: der Spieler entscheidet bei jedem Freischalten selbst,
  welches der noch nicht besessenen Produktionsgebäude er will — der Preis
  hängt nur von `n` ab, nicht davon, WELCHES Gebäude gewählt wird (Zuckerteich
  kostet als 3. Grundstück genauso viel wie Kuhstall als 3. Grundstück).
- Kombinierter Preis: Freischalten UND Bauen (Level 0→1) in einem Kauf,
  ersetzt den bisherigen Einzelkauf zum `baseCost` des jeweiligen Gebäudes.
  Level-2+-Ausbau bleibt unverändert (nutzt weiterhin
  `def.baseCost() × buildingCostGrowth^level`, siehe unten).
- UI: gesperrte Gebäude sind im Hof-Grid sichtbar, ausgegraut, mit
  Schloss-Icon — Klick zeigt einen Hinweis ("im Rathaus freischalten"),
  löst aber selbst keinen Kauf aus. Der eigentliche Kauf passiert im
  Rathaus (`CitizenDialog.vue`, aktuell nur Bürger-Anwerben).

**Bewusst nicht in Stein gemeißelt:** die konkreten Werte für
`plotBaseCost`/`plotCostGrowth` — siehe Verification unten, müssen über den
Fortschritts-Simulator kalibriert werden, da sie die "2-3 Tage bis 1.
Prestige mit 1 Gebäude"-Kalibrierung (ROADMAP Punkt 11,
`prestigeBaseThreshold = 4500`) direkt beeinflussen (der erste
Gebäude-Kauf läuft jetzt über `plotCost(0)` statt über das per Gebäude
unterschiedliche `baseCost` — die bisherige Design-Absicht "Zuckerteich/
Kuhstall sind kein Tag-1-Ziel" entfällt dadurch, da alle 6 Gebäude bei
gleichem `n` gleich viel kosten müssen neu bedacht werden).

## Umsetzung

**Backend:**
- `GameBalanceConfig.java`: neue Felder `plotBaseCost`, `plotCostGrowth`
  (+ Getter/Setter, analog `citizenBaseCost`/`citizenCostGrowth`).
  `application.properties`: `balance.plot-base-cost`, `balance.plot-cost-growth`.
- `BuildingService.java`:
  - Neue Helper-Methode `countOwnedProductionBuildings(userId)` — zählt
    `PlayerBuildingEntity`-Zeilen für Gebäude mit `passiveResource != null`
    (Rathaus/Lager/Markt/Backhaus zählen nicht mit, die sind pre-built und
    unabhängig von dieser Mechanik).
  - `buyOrUpgrade()`: im `currentLevel == 0`-Zweig für Produktionsgebäude
    (`def.passiveResource() != null`, nicht `preBuilt`) den Preis über
    `plotBaseCost × plotCostGrowth^countOwnedProductionBuildings(userId)`
    statt über `computeCost(def, 0)` (= `def.baseCost()`) berechnen.
    Level-1+-Ausbau (`currentLevel >= 1`) bleibt unverändert bei
    `computeCost(def, currentLevel)` mit dem gebäude-eigenen `baseCost`.
  - `PlayerBuildingDto.nextLevelCost` zeigt dadurch automatisch den
    richtigen Preis an (kein neues DTO-Feld nötig, `toDto()` ruft für
    `level == 0` bereits `computeCost(def, level)` auf — dort dieselbe
    Fallunterscheidung wie in `buyOrUpgrade()` einbauen, oder beide auf
    eine gemeinsame private `costFor(def, level, userId)`-Methode
    zusammenziehen, um die Logik nicht zu duplizieren).
  - Kein neuer Endpoint nötig — `POST /api/v1/farm/buildings/buy/{userId}`
    (bestehend) reicht, der Preis kommt jetzt korrekt aus der neuen Formel.

**Frontend:**
- `FarmGridView.vue`/`BuildingFrame.vue`: Gebäude ohne `PlayerBuildingEntity`
  (nicht besessen) zeigen einen gesperrten Zustand (ausgegraut, Schloss-Icon
  über `PixelIcon`) statt des normalen "Bauen"-Buttons. Klick öffnet ein
  kurzes Popover/Hinweis ("Im Rathaus freischalten") statt direkt
  `buyOrUpgrade` aufzurufen.
- `CitizenDialog.vue`: neue Sektion "Grundstücke" neben dem bestehenden
  Bürger-Anwerben-Bereich — listet alle noch nicht besessenen
  Produktionsgebäude mit dem aktuellen `plotCost` (kommt über
  `getBuildingLayout`/`nextLevelCost, s.o.), Klick auf ein Gebäude ruft
  den bestehenden `buyBuilding`-Store-Call (`buyOrUpgrade`) mit dessen
  `buildingId` auf.
- i18n: neue Keys in `citizenDialog.json` (Sektion-Titel, Freischalten-
  Button, Preis-Label) + `farmGridView.json`/`buildingFrame`-Namespace für
  den Lock-Hinweis (de/en).

## Verification

- Backend: 2. Produktionsgebäude kaufen kostet `plotCost(1)`, unabhängig
  davon welches der 5 verbleibenden gewählt wird (mit zwei verschiedenen
  Gebäuden gegentesten, gleicher Preis). Level-2-Ausbau eines bereits
  besessenen Gebäudes bleibt beim alten `baseCost`-basierten Preis
  (Regressionscheck: Kosten vor/nach dieser Änderung identisch für
  Level-Ausbau).
- `npm run balance:report -- --live`: `BUILDING_DEFS`/Simulator-Logik im
  Skript muss nachgezogen werden (erster Kauf = `plotCost(0)` statt
  `def.baseCost`, alle Gebäude jetzt gleich teuer für Slot 0) — danach
  `plotBaseCost`/`plotCostGrowth` so kalibrieren, dass die "2-3 Tage bis 1.
  Prestige"-Zielband aus ROADMAP Punkt 11 mit dem neuen Erstkauf-Preis
  weiterhin ungefähr stimmt (nicht plötzlich komplett anders, nur weil der
  Startpreis jetzt einheitlich ist statt 280-600 gestaffelt).
- Frontend end-to-end (Playwright wie bei früheren UI-Plänen): gesperrtes
  Gebäude im Grid zeigt Schloss + Hinweis, kein Bauen-Klick möglich;
  Rathaus-Dialog zeigt Grundstücke-Liste mit korrektem Preis; Freischalten
  baut das Gebäude sofort (Level 1, sichtbar im Grid); zweites Grundstück
  zeigt danach den gestiegenen `plotCost(1)`-Preis für die restlichen 4.

## Nicht im Scope

- Rückwirkende Migration bestehender Spieler-Accounts (die schon mehrere
  Gebäude besitzen) — betrifft nur neue/leere Accounts sauber, bestehende
  Spieler behalten ihre bereits gebauten Gebäude unangetastet.
- Verkaufen/Zurückgeben eines freigeschalteten Grundstücks.
- Anpassung der Season-Reset-Logik (falls Grundstücke season-übergreifend
  behandelt werden sollen) — separates Thema, hier nicht mitgeplant.
