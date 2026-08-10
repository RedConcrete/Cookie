# ⏳ Skillbaum: Automatisierung (Auto-Sammeln, Auto-Verkauf, Auto-Backen, Hover-Tempo)

> **Status:** ⏳ Offen

## Context

User-Idee (2026-08-10, direkt im Anschluss an den Rohstoff-Branches-Pass):
vier neue Automatisierungs-Features, die über neue Skill-Baum-Knoten
freigeschaltet werden sollen:

1. Automatisches Einsammeln der passiven Produktion aus allen Gebäuden ins
   Hauptlager (statt jedes Gebäude einzeln anklicken).
2. Prozentualer Auto-Verkauf von Ressourcen, mit einem einstellbaren
   Maximalwert, der selbst über den Skill-Baum "gelevelt" werden kann,
   konfiguriert im Markt.
3. Automatisches Backen — Häufigkeit und Rezeptauswahl konfigurierbar.
4. Weitere Hover-Boni, die **nicht** an eine bestimmte Ressource gebunden
   sind (im Gegensatz zu den bestehenden `HARVEST_YIELD`-Knoten, die immer
   pro Ressource oder global auf den Ertrag wirken).

**Wichtiger Unterschied zu allen bisherigen Skillbaum-Plänen:** die bisherigen
Effekte (`HARVEST_YIELD`, `BAKE_OUTPUT`, `MARKET_FEE_REDUCTION`,
`WAGE_INTEREST_REDUCTION`, `RESOURCE_WAGE_REDUCTION`, siehe
[[2026-08-10-open-skillbaum-wheel-keystones]]) sind alle **numerische
Multiplikatoren/Abzüge** auf einen bestehenden Wert. Die vier Features hier
sind **Freischaltungen ganzer Spielmechaniken** (Automatisierung), keine
Zahlen-Buffs — das Effekt-Modell (`getEffectTotal` summiert additiv) passt
trotzdem: ein Unlock ist einfach ein Effekt mit `value > 0`, geprüft als
Bool statt addiert. Kein Schema-Umbau nötig, nur eine Lese-Konvention.

**Baut zwingend auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf**
(Mehrfach-Effekte, `NodeTier`, zweisprachige Texte, String-`effectType`
ohne CHECK-Constraint-Falle). Reine Neustart-Sensivität: `settle(...)` in
`BuildingService.java` bekommt hier eine neue `String userId`-Signatur —
**[[2026-08-10-open-skillbaum-crit-system]]** und
**[[2026-08-10-open-skillbaum-lager-branch]]** wollten dieselbe Änderung an
derselben Stelle — Lager-Branch wurde 2026-08-10 zuerst gebaut, `settle(...)`
hat den `String userId`-Parameter also bereits (siehe aktueller
`BuildingService.java`-Quelltext beim Bauen prüfen, dieser Plan hier war
zuerst geschrieben und ist auf diesem Punkt jetzt überholt).

Alle vier Features hängen sich als **zusätzliche Endknoten an bereits
bestehende Branches**, statt einen eigenen neuen Arm zu bauen — passt
thematisch 1:1 (Lager→Storage, Verkauf→Market, Backen→Baking,
Hover-Tempo→Core/generalistisch). Der Baum steht nach dem Lager-Branch-Pass
bei **11** Branches (STORAGE als 11. Arm, Layout dafür bereits ein zweites
Mal auf 360°/11 ≈ 32.7° umverteilt, siehe `docs/cookie-game-design.md` §9
"Radiales 11-Branch-Layout") — **kein hartes Maximum bei 10** wie ursprünglich
hier vermutet, radiales Neuverteilen aller Branch-Bearings auf 360°/n ist die
etablierte Vorgehensweise für jeden weiteren Branch. Die vier Features hier
brauchen trotzdem keinen 12. Arm, da sie bewusst an vorhandene Branches
anknüpfen statt eigene zu werden.
**Neue Koordinaten müssen wie beim Cross-Branch-Wheel gegen alle
bestehenden Knoten/Kanten geprüft werden, bevor sie ins Backend wandern**
(Kollisions-/Kreuzungs-Skript, siehe Verifikationsplan).

Verifizierter Ist-Zustand (vollständig recherchiert, keine Annahmen):

- **Kein globaler Scheduler für Produktion/Ernte** — `BuildingService.settle()`
  wird lazy bei jedem Read/Write berechnet, nicht per Tick-Job. Es gibt
  bereits zwei `@Scheduled`-Komponenten: `WageScheduler`
  (`fixedRate = 60_000`, zieht Lohn pro Spieler ab) und `MarketScheduler`
  (Markt-Fluktuation) — beide unter
  `backend/.../scheduler/`. Neue Automatisierung reiht sich hier ein statt
  eine neue Architektur zu erfinden.
- **Hauptlager ist bereits ein einziger gemeinsamer Topf** über alle 6
  Ressourcen (`UserEntity.getTotalResources()`,
  `BuildingService.getTotalCap()`, Zeilen 195-203) — kein Cap pro Ressource.
  Pro-Gebäude-Puffer (`BuildingDef.storageCapacity()`) ist der eigentliche
  Engpass bei Abwesenheit, wird in [[2026-08-10-open-skillbaum-lager-branch]]
  adressiert (nicht hier — die beiden Pläne überschneiden sich nicht: Lager
  macht Overflow seltener, dieser Plan hier sammelt automatisch ein).
- **Einsammeln ist aktuell strikt pro Gebäude**: `PassiveIncomeService
  .collectBuilding(userId, buildingId)` (Zeilen 41-97), Endpunkt
  `POST /api/v1/farm/buildings/collect/{userId}/{buildingId}`. Kein
  "collect all".
- **Verkauf ist aktuell strikt manuell**, ein Request = ein Trade
  (`MarketService.performAction`, Zeilen 230-314). Kein Auto-Verkauf, kein
  Schwellenwert-Konzept, keine persistierten Spieler-Einstellungen für den
  Markt existieren bisher irgendwo im Code.
- **Backen ist strikt Ein-Ofen-Einzelauftrag** (`BakeService.startBake`,
  Zeilen 83-131, wirft bei bereits vorhandenem `claimed=false`-Job) — kein
  Auto-Restart, `claim()` (Zeilen 155-193) ist manuell.
- **Hover-Ernte-Takt ist client- UND serverseitig eine feste Konstante**
  (`FarmGridView.vue`: `HARVEST_MS = 900`; `UserService.java`:
  `HARVEST_TICK_MS = 900`, Zeile 32) — kein Skill-Knoten wirkt aktuell auf
  Takt/Mechanik, nur auf den Ertrag pro Tick (`HARVEST_YIELD`). Achtung:
  `useHoverReveal.js` ist ein **anderer, unabhängiger** Mechanismus (reine
  Tooltip-Anzeigelogik) — nicht verwechseln mit dem Hover-Ernte-Takt in
  `FarmGridView.vue`.

## Design-Entscheidungen

### 1. Auto-Sammeln → Ende des STORAGE-Branches

- Neuer Knoten `storage_6` "Automatisierte Sammler" / "Automated
  Collectors", **KEYSTONE** (zweiter Keystone im STORAGE-Branch aus
  [[2026-08-10-open-skillbaum-lager-branch]] — `storage_4` ist dort schon
  einer, "höchstens 1-2 Keystones pro Branch" laut Fundament-Konvention
  erlaubt das). Hängt linear hinter `storage_4`.
- Neuer `EffectType.AUTO_COLLECT_UNLOCK` — reiner Bool-Effekt, `value = 1.0`
  als Sentinel, kein `targetResource`. Keine numerische Nachteilsseite: die
  60s-Scheduler-Kadenz **ist** der Tradeoff gegenüber perfekt getimtem
  manuellem Sammeln (kein künstlicher zweiter Downside nötig).
- Neuer `PassiveIncomeService.collectAll(String userId)`: iteriert alle
  Produktionsgebäude des Spielers (`passiveResource() != null`), ruft
  dieselbe Settle+Credit-Logik wie `collectBuilding` aber gegen einen
  gemeinsam mitgeführten `available`-Rest (Hauptlager ist ein Topf — jedes
  Gebäude in der Schleife reduziert den Rest für die nächsten). Neuer
  Endpunkt `POST /api/v1/farm/buildings/collect-all/{userId}` (manueller
  "Alles einsammeln"-Button, unabhängig vom Skill nützlich) **plus**
  Aufruf aus dem neuen Scheduler unten für Spieler mit dem Unlock.
- Neue `scheduler/AutomationScheduler.java` (`@Scheduled(fixedRate =
  60_000)`, gleiches Muster wie `WageScheduler`): pro Spieler prüfen, ob
  `skillTreeService.getEffectTotal(userId, EffectType.AUTO_COLLECT_UNLOCK,
  null) > 0`, wenn ja `passiveIncomeService.collectAll(userId)`. Gleicher
  Retry-Umgang bei `OptimisticLockingFailureException` wie in
  `WageScheduler.deductWithRetry` (Kollisionsrisiko mit anderen
  Schreibzugriffen auf denselben User).

### 2. Auto-Verkauf → Ende des MARKET-Branches + neue Spieler-Einstellung

- Neuer Knoten `market_5` "Handelsvollmacht" / "Trading Authority",
  **KEYSTONE** (zweiter Keystone im MARKET-Branch), schaltet die Funktion
  frei. Neuer `EffectType.AUTO_SELL_UNLOCK` (Bool-Effekt wie oben).
- Neuer Knoten `market_6` "Großhändler-Kontakte" / "Wholesale Contacts",
  **PASSIVE**, linear hinter `market_5`: `EffectType
  .AUTO_SELL_MAX_PERCENT_BONUS` (+0.10 je Knoten) — hebt die Obergrenze an,
  bis zu der der Spieler den Auto-Verkaufs-Prozentsatz selbst einstellen
  darf (Basis-Obergrenze z. B. 30 % ohne diesen Knoten, siehe
  Balancing-Platzhalter-Policy).
- **Schwellen-Logik bewusst an den bereits bestehenden gemeinsamen
  Hauptlager-Topf gekoppelt statt an 6 einzelne Ressourcen-Schwellen** (passt
  zum verifizierten Ist-Zustand): ein Trigger-Prozentsatz des Hauptlager-Caps
  (Systemkonstante, z. B. 80 %, kein Skill-Wert) löst aus; verkauft wird dann
  `autoSellPercent` % **jeder aktuell gehaltenen Ressource** einzeln über
  die bestehende AMM-Kurve — kein Sonderpreis, keine Ausnahme von
  `getEffectiveSellFeeRate`/`sellPayout`, sonst entsteht eine
  Schatten-Ökonomie neben dem manuellen Verkauf.
- **Neue Entity** `PlayerAutomationSettingsEntity` (userId PK):
  `autoSellEnabled` (boolean), `autoSellPercent` (double, serverseitig
  gegen `30 + 10×AUTO_SELL_MAX_PERCENT_BONUS_KNOTEN` gedeckelt — Client-Wert
  nie vertrauen, siehe `CLAUDE.md`), `autoBakeEnabled`,
  `autoBakeRecipeId`, `autoBakeBatches` (siehe Punkt 3). Ein Entity für
  beide Automatisierungs-Settings, kein Grund für zwei Tabellen.
- Neue Endpunkte `GET/PUT /api/v1/automation/settings/{userId}` — PUT
  validiert `autoSellPercent` gegen das Skill-Maximum und `AUTO_SELL_UNLOCK`
  (400 falls nicht freigeschaltet oder Wert außerhalb des erlaubten
  Bereichs), analog zum bestehenden Validierungsmuster in
  `AdminConfigController`.
- `AutomationScheduler` (derselbe Tick wie oben): pro Spieler mit
  `AUTO_SELL_UNLOCK` und `settings.autoSellEnabled`: `totalResources /
  getTotalCap(...)` gegen den Trigger-Prozentsatz prüfen, bei Überschreitung
  je Ressource `autoSellPercent` % über `MarketService`s bestehende
  Verkaufslogik verkaufen (neue kleine `MarketService`-Methode, die
  denselben Pfad wie `performAction(SELL)` nutzt, nur ohne
  Trade-Cooldown-Check — Automatisierung soll nicht am Spieler-Cooldown
  scheitern, der ist für Spam-Schutz beim manuellen Klicken gedacht).

### 3. Auto-Backen → Ende des BAKING-Branches

- Neuer Knoten `bake_6` "Selbstlaufende Backstube" / "Self-Running
  Bakery", **KEYSTONE** (zweiter Keystone im BAKING-Branch), linear hinter
  `bake_4`. Neuer `EffectType.AUTO_BAKE_UNLOCK` (Bool-Effekt).
- Nutzt `autoBakeEnabled`/`autoBakeRecipeId`/`autoBakeBatches` aus
  `PlayerAutomationSettingsEntity` (siehe oben) — Rezeptauswahl ist einfach
  eine gespeicherte `recipeId`, validiert gegen `RecipeRepository`
  (existiert, ändert sich nicht).
- **"Häufigkeit" ist beim Ein-Ofen-Modell kein Intervall-Wert, sondern
  Auto-Restart**: `BakeService` erlaubt strukturell nur einen aktiven Job
  gleichzeitig (`countByUserIdAndClaimedFalse >= 1` wirft), "häufiger
  backen" heißt hier "sofort neu starten sobald der letzte Job geclaimt
  ist", nicht mehrere parallele Öfen. Das entspricht dem im Code
  dokumentierten Balance-Ansatz ("später über Backgeschwindigkeit/
  Ressourcenverbrauch balanciert", `BakeService.java` Zeile 87-88) — kein
  Widerspruch, nur Klarstellung im Plan, damit "Häufigkeit" beim Bauen nicht
  als neues Zeit-Feld missverstanden wird.
- `AutomationScheduler`: pro Spieler mit `AUTO_BAKE_UNLOCK` und
  `settings.autoBakeEnabled`: `BakeService.findActiveJob(userId)` prüfen —
  falls fertig und unclaimed: `claim(userId)`, danach (gleicher Tick oder
  nächster, wichtig ist die Reihenfolge: erst `claimed=true` committen,
  dann erst `startBake`, siehe der bereits im Code dokumentierte
  Zwei-Zeilen-Bug-Kommentar zu `findAllByUserIdAndClaimedFalse` in
  `BakeService.findActiveJob`) neuen Job mit `autoBakeRecipeId`/
  `autoBakeBatches` starten, falls Zutaten reichen (sonst überspringen,
  kein Fehler-Log-Spam bei jedem Tick — z. B. stilles Skip plus
  Re-Check nächster Tick).

### 4. Hover-Boni "separat von Ressourcen" → Ende des CORE-Branches

- Neuer Knoten `core_5` "Flinke Finger" / "Nimble Fingers", **PASSIVE**,
  linear hinter `core_4`. Neuer `EffectType.HARVEST_TICK_SPEED` — Prozent-
  Reduktion auf `HARVEST_TICK_MS` (schnellerer Ernte-Takt bei Hover, wirkt
  **für jede Ressource gleich**, nicht ressourcengebunden — das ist genau
  der "separat von Ressourcen"-Punkt aus der Anforderung, im Unterschied zu
  `HARVEST_YIELD`, das immer pro Ressource/Global auf die **Menge** wirkt,
  nie auf die **Geschwindigkeit**).
- **Muss client- und serverseitig synchron bleiben**, sonst drifted die
  optimistische Vorhersage stärker vor jeder `syncHarvest`-Korrektur:
  - Backend: `UserService.HARVEST_TICK_MS` (Zeile 32, aktuell `private
    static final long`) wird zu einem pro Request berechneten Wert:
    `HARVEST_TICK_MS_BASE * (1 - min(0.5, getEffectTotal(userId,
    HARVEST_TICK_SPEED, null)))`. Bleibt weiterhin die **serverseitige
    Wahrheit** — der Client sendet nie eine Tick-Zahl, nur die Elapsed-Zeit
    wird serverseitig gegen den (jetzt personalisierten) Takt geprüft.
  - Frontend: `FarmGridView.vue`s `HARVEST_MS`-Konstante wird zu einem
    `computed`, das denselben Skill-Wert aus dem geladenen Skilltree-State
    liest (`playerStore.skillTree` liefert bereits alle Effekte, kein
    Extra-Fetch nötig) — sonst tickt die lokale Vorhersage weiterhin am
    alten Takt und wird bei jedem 3s-Sync sichtbar korrigiert (ruckeln).
- Kein zweiter Effekt-Typ für "Sofort-Bonus pro Hover-Start" in diesem Pass
  — eine zusätzliche Idee (z. B. Krit-Chance beim Hover) überschneidet sich
  mit [[2026-08-10-open-skillbaum-crit-system]] und gehört dort hin, nicht
  hierher (keine Doppelarbeit).

## Backend-Änderungen

- `enums/EffectType.java`: `AUTO_COLLECT_UNLOCK`, `AUTO_SELL_UNLOCK`,
  `AUTO_SELL_MAX_PERCENT_BONUS`, `AUTO_BAKE_UNLOCK`, `HARVEST_TICK_SPEED`
  ergänzen (dank String-`effectType` seit dem Fundament-Plan kein
  CHECK-Constraint-Risiko).
- `BuildingService.settle(...)`: `String userId`-Parameter ergänzen, falls
  nicht schon durch [[2026-08-10-open-skillbaum-crit-system]] oder
  [[2026-08-10-open-skillbaum-lager-branch]] geschehen (alle drei Pläne
  greifen an derselben Stelle an, siehe Context).
- `PassiveIncomeService`: neue `collectAll(String userId)`.
- `BuildingController`: neuer Endpunkt `POST
  /api/v1/farm/buildings/collect-all/{userId}`.
- `MarketService`: kleine neue Methode für Auto-Verkauf (wiederverwendet
  `sellPayout`/`getEffectiveSellFeeRate`, kein Cooldown-Check).
- Neue Entity `PlayerAutomationSettingsEntity` + Repository.
- Neuer Controller (oder Erweiterung eines bestehenden) für
  `GET/PUT /api/v1/automation/settings/{userId}` mit serverseitiger
  Validierung gegen Skill-Unlocks/-Obergrenzen.
- `BakeService`: keine Signatur-Änderung nötig, `AutomationScheduler` nutzt
  die bestehenden `findActiveJob`/`claim`/`startBake`-Methoden von außen.
- `UserService.harvest(...)`: `HARVEST_TICK_MS`-Konstante durch
  personalisierte Berechnung ersetzen (siehe Design-Entscheidung 4).
- Neue `scheduler/AutomationScheduler.java` (`@Scheduled(fixedRate =
  60_000)`), bündelt Auto-Sammeln/Auto-Verkauf/Auto-Backen-Checks pro
  Spieler in einem Tick statt drei separater Scheduler-Komponenten.

## Frontend-Änderungen

- `SkillTreeView.vue`: `EFFECT_LABEL_KEY` um die 5 neuen Typen erweitern
  (i18n-Texte DE/EN); Bool-Unlock-Effekte im Tooltip anders formatieren als
  Prozentwerte (z. B. "Freigeschaltet" statt "+100 %" — `effectRows()`
  braucht einen Sonderfall für `value === 1` bei den vier Unlock-Typen).
  Neue Keystone-Icons: `keystone_storage6.svg`, `keystone_market5.svg`,
  `keystone_bake6.svg` (core_5 ist PASSIVE, kein eigenes Icon nötig).
- Neuer Bereich in `MarketView.vue` (oder eigenes `AutoSellSettingsDialog.vue`
  nach Vorbild von `SettingsDialog.vue`s Slider-Muster, Zeilen 12-26/219-259)
  für `autoSellEnabled`-Toggle + `autoSellPercent`-Slider, nur sichtbar/
  aktivierbar wenn `AUTO_SELL_UNLOCK` alloziert (sonst ausgegraut mit
  Hinweis "Skill X benötigt", analog zu gesperrten Skill-Knoten).
- Neuer Bereich in `RecipeCard.vue` (Backen-Ansicht) für
  `autoBakeEnabled`-Toggle + Rezept-Auswahl (bestehende Rezeptliste
  wiederverwenden) + Batch-Anzahl, gleiche Sperr-Optik wie oben.
- `FarmGridView.vue`: `HARVEST_MS` von Konstante zu `computed`, gespeist aus
  `playerStore.skillTree`s `HARVEST_TICK_SPEED`-Effekt-Summe (gleiche
  Berechnung wie serverseitig, siehe Design-Entscheidung 4).
- Neuer manueller "Alles einsammeln"-Button in `LagerDialog.vue`
  (unabhängig vom Skill nützlich, ruft einfach den neuen
  `collect-all`-Endpunkt).

## Verifikationsplan

1. **Vor dem Implementieren**: Kollisions-/Kreuzungs-Skript (wie beim
   Cross-Branch-Wheel, Python gegen `x`/`y`/`nodeTier` aller Knoten +
   Kanten-Segmente) gegen die geplanten `storage_6`/`market_5`/`market_6`/
   `bake_6`/`core_5`-Koordinaten laufen lassen, bevor sie in
   `SkillTreeService#buildNodes()` landen — exakte Werte hier bewusst nicht
   vorgegeben, sondern beim Bauen anhand des dann aktuellen Layouts
   bestimmt (kann sich durch andere zwischenzeitlich gebaute Pläne
   verschoben haben).
2. `GET /api/v1/skilltree?userId=...` — alle 5 neuen Knoten korrekt
   angebunden, Bool-Unlock-Effekte mit `effectValue = 1`.
3. Auto-Sammeln: `storage_6` allozieren, Gebäude passiv produzieren lassen,
   60s warten, Hauptlager-Bestand steigt ohne manuellen Collect-Klick.
4. Auto-Verkauf: `market_5`+`market_6` allozieren, `autoSellPercent` per
   PUT setzen (auch: Wert über dem Skill-Maximum senden → 400), Hauptlager
   über den Trigger-Prozentsatz füllen, 60s warten, Ressourcen sinken,
   Cookies steigen, Marktgebühr/AMM-Kurve identisch zu einem manuellen
   Verkauf gleicher Menge.
5. Auto-Backen: `bake_6` allozieren, `autoBakeEnabled`+Rezept setzen, Job
   durchlaufen lassen, prüfen dass nach Claim automatisch ein neuer Job
   startet, nie zwei unclaimed Jobs gleichzeitig entstehen (Regressions-
   check gegen den bereits bekannten Bug aus `findActiveJob`s Kommentar).
6. Hover-Tempo: `core_5` allozieren, Ernte-Tick-Intervall vor/nach messen
   (client **und** server, beide müssen übereinstimmen), keine sichtbaren
   Ruckler/Korrekturen mehr beim 3s-Sync.
7. `npm run check:palette` grün nach den 3 neuen Icons.
8. Regressionscheck: bestehende manuelle Collect-/Sell-/Bake-Flows
   funktionieren unverändert für Spieler ohne die neuen Knoten.

## Kritische Dateien

- `backend/.../enums/EffectType.java`
- `backend/.../service/PassiveIncomeService.java`
- `backend/.../service/BuildingService.java:195-203,275-292` (`getTotalCap`,
  `settle`)
- `backend/.../service/MarketService.java:230-314` (`performAction`,
  Referenz für die neue Auto-Verkauf-Methode)
- `backend/.../service/BakeService.java:83-193` (`startBake`, `claim`,
  `findActiveJob`)
- `backend/.../service/UserService.java:32,196-236` (`HARVEST_TICK_MS`,
  `harvest`)
- `backend/.../scheduler/WageScheduler.java` (Vorbild für
  `AutomationScheduler`)
- `backend/.../entity/` — neue `PlayerAutomationSettingsEntity`
- `backend/.../service/SkillTreeService.java` (`buildNodes`/`buildEdges`)
- `frontend/src/views/FarmGridView.vue:32-33,878-966` (`HARVEST_MS`,
  Hover-Ernte-Loop)
- `frontend/src/components/MarketView.vue`, `RecipeCard.vue`,
  `LagerDialog.vue`
- `frontend/src/components/SkillTreeView.vue`
- `frontend/src/components/pixel/PixelIcon.vue`
