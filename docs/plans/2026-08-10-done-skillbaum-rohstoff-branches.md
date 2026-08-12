# ✅ Skillbaum: Rohstoff-Branches (Zucker/Mehl/Eier/Butter/Schoko)

> **Status:** ✅ Umgesetzt (2026-08-11/2026-08-12), Marker war zunächst nicht
> nachgezogen worden. Topologie hier (5 Knoten, 1 Keystone, 1 Fork pro
> Branch) inzwischen durch
> [[2026-08-12-done-skillbaum-rohstoff-poe-mesh]] ersetzt (8 Knoten, 2
> Keystones, PoE-Mesh mit Cross-Links pro Branch) — dieser Plan bleibt als
> historische Referenz stehen.

## Context

`docs/ROADMAP.md` (Abschnitt "Mehr passive Skills") vermerkt bereits: ein
Branch pro Rohstoff analog zum bestehenden MILK-Zweig, für Zucker/Mehl/
Eier/Butter/Schokolade (`ResourceName`-Enum:
`backend/.../enums/ResourceName.java` — `SUGAR, FLOUR, EGGS, BUTTER,
CHOCOLATE, MILK`). Jede dieser Ressourcen hat bereits ein eigenes
Produktionsgebäude (`BuildingService.java:37-42`: `pond`→SUGAR,
`hof`→FLOUR, `huhn`→EGGS, `butter`→BUTTER, `kakao`→CHOCOLATE,
`kuh`→MILK, 1:1-Zuordnung, keine Mehrfach-Ressourcen-Gebäude).

Erweiterung über die reine ROADMAP-Idee hinaus (User-Wunsch): Rohstoff-Knoten
sollen nicht nur "mehr Ertrag" geben, sondern abwechselnd auch den **Lohn
der Arbeiter im zugehörigen Gebäude** senken — z. B. ein Zuckerteich-Arbeiter
kostet weniger, wenn im SUGAR-Branch entsprechend investiert wurde. Das ist
der "nicht 5× Melk-Poster hintereinander"-Punkt aus der Anforderung: ein Pfad
mischt Effekt-Typen statt eine Kette gleicher Boni zu wiederholen.
Globale (alle Ressourcen gleichzeitig) Boni bleiben bewusst selten und liegen
nicht in diesen 5 Branches selbst, sondern im
`keystone_alleskoenner`-Knoten aus
[[2026-08-10-open-skillbaum-wheel-keystones]] (Cross-Branch-Wheel) — dieser
Plan hier baut nur reguläre, ressourcen-gebundene Effekte.

**Baut zwingend auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf**
(Mehrfach-Effekte pro Knoten, `NodeTier`-Enum, `nameDe/nameEn/
descriptionDe/descriptionEn`-Schema) — dieser Plan nutzt das dort gebaute
Fundament direkt, keine eigene Schema-Arbeit mehr nötig.

## Design-Entscheidungen

- **Neuer `EffectType`: `RESOURCE_WAGE_REDUCTION`** — analog zu
  `HARVEST_YIELD` mit `targetResource`-Scoping, aber wirkt auf den Lohn statt
  auf den Ernte-Ertrag. Bewusst ein eigener Typ statt Wiederverwendung von
  `WAGE_INTEREST_REDUCTION` (das ist der bestehende DISPO-Zinssatz-Effekt,
  siehe §9 im Design-Doc — inhaltlich komplett anderer Hebel, Verwechslung
  würde die Balance-Doku verwirren). Ebenfalls bewusst getrennt von
  `WAGE_REDUCTION` (global, ressourcen-unabhängig) aus
  [[2026-08-10-open-skillbaum-bau-buerger-branch]] — dieser Plan hier ist
  strikt pro-Ressource.
- **5 neue Branches**, je 5 Knoten (mirror des MILK-Musters: `milk_1..4`
  linear + `milk_5` Fork), mit gemischten Effekt-Typen statt reiner
  `HARVEST_YIELD`-Kette und einer expliziten Notable-Stufe:
  1. `<res>_1` — PASSIVE, `HARVEST_YIELD` (targetResource=`<RES>`), klein
  2. `<res>_2` — PASSIVE, `RESOURCE_WAGE_REDUCTION` (targetResource=`<RES>`), klein
  3. `<res>_3` — NOTABLE, `HARVEST_YIELD` (targetResource=`<RES>`), mittel-groß
  4. `<res>_4` — **KEYSTONE**, 2 Effekte: großer `HARVEST_YIELD`-Bonus
     (targetResource=`<RES>`) **plus** ein kleinerer **negativer**
     `RESOURCE_WAGE_REDUCTION`-Wert auf derselbe Ressource (Arbeiter in
     diesem Gebäude werden spürbar teurer — echter PoE-Tradeoff, kein neuer
     `EffectType` nötig, siehe Fundament-Plan "Downsides sind ein zweiter
     Effekt mit negativem Wert")
  5. `<res>_5` — PASSIVE, Fork ab `<res>_2`: `RESOURCE_WAGE_REDUCTION`
     (targetResource=`<RES>`), mittel
- Reihenfolge/Mix ist bewusst nicht für alle 5 Branches identisch (z. B.
  SUGAR startet mit Ertrag, BUTTER könnte mit Lohnsenkung starten) — spiegelt
  den PoE-Punkt "nicht immer der gleiche Weg zum Ziel" auch zwischen den
  Branches, nicht nur innerhalb eines Pfads. Exakte Zuordnung beim Bauen
  final festlegen, hier nur das Grundmuster.
- Kalibrierung (genaue `effectValue`-Zahlen, inkl. Downside-Größe) ist
  Platzhalter, analog zur bestehenden Policy (`docs/ROADMAP.md` §4,
  Balancing separater Pass) — grob an bestehenden MILK-Werten orientieren
  (+0.05/+0.05/+0.07/+0.10 Muster), `RESOURCE_WAGE_REDUCTION` in
  Prozentpunkten analog zu `MARKET_FEE_REDUCTION` (−0.5%/−0.75%/...), der
  Downside am Keystone deutlich kleiner als der Upside (z. B. Upside +0.15
  Ertrag, Downside −0.03 Lohnsenkung → netto immer noch klar positiv, aber
  spürbar).

## Backend-Änderungen

**`enums/EffectType.java`**: `RESOURCE_WAGE_REDUCTION` ergänzen. Dank
[[2026-08-10-open-skillbaum-wheel-keystones]] (Abschnitt 2: `effectType`
als reiner String statt typed Enum-Spalte) braucht das **keinen**
`ALTER TABLE ... DROP CONSTRAINT`-Schritt mehr — vorausgesetzt der
Fundament-Plan ist bereits umgesetzt. Falls dieser Plan ausnahmsweise
**vor** dem Fundament-Plan gebaut wird, gilt noch die alte Falle (ROADMAP
Abschnitt 0): nach dem ersten Boot mit befüllter `skill_nodes`-Tabelle
`ALTER TABLE skill_nodes DROP CONSTRAINT IF EXISTS
skill_nodes_effect_type_check;` lokal **und** live ausführen.

**`BuildingService.effectiveWage(...)`** (aktuell Zeilen 227-233, Signatur
`effectiveWage(BuildingDef def, PlayerBuildingEntity ent)`): Signatur um
`String userId` erweitern (Aufrufer `getWageBreakdown(String userId)`,
Zeilen 211-220, hat `userId` bereits im Scope). Für Produktionsgebäude
(`def.passiveResource() != null`) zusätzlich:
```java
double wageReduction = skillTreeService.getEffectTotal(
    userId, EffectType.RESOURCE_WAGE_REDUCTION, def.passiveResource().name());
return ent.getWorkers() * balance.getWagePerMinPerWorker() * (1 - Math.min(0.9, Math.max(-0.5, wageReduction)));
```
Floor **und** Cap (`Math.min`/`Math.max`) nötig, weil `wageReduction`
jetzt durch den Keystone-Downside auch **negativ** werden kann (Lohn steigt
statt sinkt) — ohne Cap könnte ein Spieler mit vielen negativen Effekten
theoretisch einen absurd hohen Lohn erzeugen; `-0.5` als Platzhalter-Deckel
("Lohn maximal um 50% erhöhbar durch Skill-Nachteile"), beim Balancing-Pass
nachschärfen.

**`SkillTreeService.buildNodes()`/`buildEdges()`**: 5× 5 neue Knoten +
Kanten, je an `root` hängend (eigener Arm, analog MILK/BAKING/MARKET/CORE/
DISPO). Positionierung (x/y) so wählen, dass zwischen den bestehenden 5 Armen
noch Platz ist (aktuelle Anordnung in `buildNodes()` beim Implementieren
direkt nachsehen — mit 10 Branches ist das Layout ohnehin neu zu
verteilen). Jeder `<res>_4`-Aufruf nutzt die neue Mehrfach-Effekt-Liste aus
dem Fundament-Plan (`List.of(new Effect(HARVEST_YIELD, RES, +0.15), new
Effect(RESOURCE_WAGE_REDUCTION, RES, -0.03))`), `nodeTier=KEYSTONE`.

## DE/EN-Knotentexte (Vorschlag, final beim Bauen abstimmen)

Ein Branch exemplarisch (SUGAR) als Vorlage — die anderen 4 spiegeln
dasselbe Muster mit passenden Ressourcen-Begriffen:

| ID | DE Name | EN Name | Tier |
|---|---|---|---|
| sugar_1 | Feinkörniger Zucker | Fine Grain Sugar | PASSIVE |
| sugar_2 | Faire Bezahlung | Fair Pay | PASSIVE |
| sugar_3 | Zuckerrohr-Expertise | Sugarcane Expertise | NOTABLE |
| sugar_4 | Zucker-Baron | Sugar Baron | KEYSTONE |
| sugar_5 | Nebenverdienst | Side Income | PASSIVE |

`sugar_4`-Beschreibung (DE) z. B.: "Deutlich mehr Zucker pro Ernte-Tick,
aber Arbeiter im Zuckerteich kosten spürbar mehr Lohn." — macht den
Tradeoff im Tooltip explizit lesbar statt nur über die Zahlen.

## Frontend-Änderungen

`BRANCH_ICON`-Map (`SkillTreeView.vue:120`) um 5 neue Einträge erweitern
(`SUGAR: 'zucker'`, `FLOUR: 'mehl'`, `EGGS: 'ei'`, `BUTTER: 'butter'`,
`CHOCOLATE: 'kakao'` — Icon-Namen final beim Bauen abstimmen). Neue SVGs
unter `frontend/src/assets/icons/` (8×8-Raster, Fruitpunch24-Palette, wie
bestehende Icons), in `PixelIcon.vue`s `ICONS`-Map registrieren. Keine
sonstigen Frontend-Änderungen nötig — der Baum rendert neue Branches
automatisch aus den Backend-Daten. `<res>_4`-Keystones bekommen zusätzlich
ein eigenes Icon (`keystone_sugar4.svg` usw.), gleiches Muster wie die 4
bestehenden Keystones aus dem Fundament-Plan.

## Verifikationsplan

1. `GET /api/v1/skilltree?userId=...` — 25 neue Knoten (5×5) + Kanten
   sichtbar, korrekt an `root` angebunden, `<res>_4` liefert 2 Einträge in
   `effects[]` mit gegensätzlichem Vorzeichen.
2. Ernte SUGAR/FLOUR/EGGS/BUTTER/CHOCOLATE vor/nach Allokation des
   jeweiligen `HARVEST_YIELD`-Knotens → Bonus nur bei passender Ressource.
3. `GET /api/v1/farm/wage-status/{userId}` (oder Rathaus-Lohnübersicht)
   vor/nach `RESOURCE_WAGE_REDUCTION`-Knoten → Lohn nur für das zugehörige
   Gebäude sinkt, andere Gebäude unverändert.
4. Keystone `<res>_4` allozieren → Ernte-Bonus UND Lohn-Erhöhung für das
   zugehörige Gebäude gleichzeitig messbar (Netto-Effekt klar positiv, aber
   beide Seiten wirksam).
5. `npm run check:palette` grün nach neuen Icons.
6. Visuell: 5 neue Arme im Baum, Pfad mischt sichtbar Ertrag/Lohn-Knoten
   statt gleichförmiger Kette, Keystone optisch größer/eigenes Icon.

## Kritische Dateien

- `backend/.../enums/EffectType.java`
- `backend/.../enums/ResourceName.java`
- `backend/.../service/BuildingService.java:37-47,211-233` — `BUILDINGS`-Liste,
  `getWageBreakdown`, `effectiveWage`
- `backend/.../service/SkillTreeService.java`
- `frontend/src/components/SkillTreeView.vue:120-121`
- `frontend/src/components/pixel/PixelIcon.vue`
