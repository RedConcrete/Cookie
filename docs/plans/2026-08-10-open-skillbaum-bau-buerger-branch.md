# ⏳ Skillbaum: CONSTRUCTION-Branch (Gebäudekosten, globaler Lohn)

> **Status:** ⏳ Offen

## Context

User-Wunsch: eine Spielweise, die auf Expansion/Wachstum statt reiner
Ressourcen-Ausbeute setzt — günstigere Gebäude und/oder niedrigere Löhne.
Abzugrenzen von [[2026-08-10-open-skillbaum-rohstoff-branches]]s
`RESOURCE_WAGE_REDUCTION` (dort: Lohnsenkung **pro Ressourcen-Gebäude**,
gezielt) — dieser Branch hier senkt **global** über alle Gebäude/Arbeiter
hinweg, unabhängig von der produzierten Ressource. Beide Effekte können
später gleichzeitig aktiv sein und addieren sich additiv (wie alle
Skill-Effekte, `SkillTreeService#getEffectTotal`).

**Branch-Key bewusst `CONSTRUCTION`, nicht `BAU`** — folgt der bestehenden
Konvention englischer Branch-Keys (`MILK`, `BAKING`, `MARKET`, `CORE`,
`DISPO`).

**Baut zwingend auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf**
(Mehrfach-Effekte pro Knoten, `NodeTier`-Enum, zweisprachige Texte).

Verifizierter Ist-Zustand:
- Gebäudekosten: `BuildingService.computeCost(BuildingDef def, int
  currentLevel)`, Zeilen 294-297: `baseCost × buildingCostGrowth^level`
  (`GameBalanceConfig.buildingCostGrowth`, Default 2.0).
- Lohn: `effectiveWage(...)`, Zeilen 227-233: `workers ×
  balance.getWagePerMinPerWorker()` — flacher globaler Satz, keine
  Variation nach Gebäude/Ressource (das ändert erst der
  Rohstoff-Branches-Plan für den Ressourcen-Teil).

## Design-Entscheidungen

- **Zwei neue `EffectType`-Werte**, gemischt in einem Pfad:
  - `BUILDING_COST_REDUCTION` — Prozent-Rabatt auf `computeCost(...)`.
  - `WAGE_REDUCTION` — Prozent-Rabatt auf den globalen
    `wagePerMinPerWorker`-Satz, **nicht** zu verwechseln mit dem
    bestehenden `WAGE_INTEREST_REDUCTION` (DISPO-Branch, senkt den
    Dispo-**Zinssatz** bei negativem Cookie-Bestand) oder mit
    `RESOURCE_WAGE_REDUCTION` (ressourcen-gebunden, Rohstoff-Branches-Plan).
- **Keystone mit echtem Tradeoff**: großer `BUILDING_COST_REDUCTION`,
  gleichzeitig ein **negativer** `WAGE_REDUCTION` — Expansion wird günstig,
  der laufende Unterhalt (Lohn) dafür teurer. Direkt grounded in
  bestehenden Effekt-Typen, kein neuer Mechanismus nötig.
- Beide Reduktionen brauchen einen Floor **und** Cap (`Math.max`/
  `Math.min`), analog zum bestehenden Muster bei `MARKET_FEE_REDUCTION`
  (`getEffectiveSellFeeRate`, Floor 0.01) — jetzt zusätzlich relevant, weil
  Downside-Effekte die Werte auch negativ werden lassen können (Kosten/Lohn
  steigen statt sinken).
- Kein neuer Effekt auf `buildingCostGrowth` selbst (die Wachstumsrate) —
  nur auf den Basis-Multiplikator, bewusst außerhalb des Scopes.

## Backend-Änderungen

**`enums/EffectType.java`**: `BUILDING_COST_REDUCTION`, `WAGE_REDUCTION`
ergänzen. Dank [[2026-08-10-open-skillbaum-wheel-keystones]] (Abschnitt 2:
`effectType` als reiner String) braucht das keinen `ALTER TABLE ... DROP
CONSTRAINT`-Schritt mehr, sofern der Fundament-Plan schon umgesetzt ist —
sonst gilt noch die alte Falle (ROADMAP Abschnitt 0).

**`BuildingService.computeCost(...)`** (Zeilen 294-297) — Methode ist
`private`, Aufrufer intern (Buy/Upgrade-Flow) haben `userId` typischerweise
schon im Scope; Signatur um `String userId` erweitern:
```java
private double computeCost(BuildingDef def, int currentLevel, String userId) {
    if (def.baseCost() == 0) return 0;
    double raw = def.baseCost() * Math.pow(balance.getBuildingCostGrowth(), currentLevel);
    double reduction = skillTreeService.getEffectTotal(userId, EffectType.BUILDING_COST_REDUCTION, null);
    return raw * (1 - Math.min(0.5, Math.max(-0.3, reduction)));
}
```
Alle Aufrufer von `computeCost(...)` im Buy/Upgrade-Pfad beim Implementieren
suchen und um `userId` ergänzen (vor dem Bauen mit Grep auf `computeCost(`
verifizieren, welche Methoden genau das sind).

**`BuildingService.effectiveWage(...)`** (Zeilen 227-233) — falls
[[2026-08-10-open-skillbaum-rohstoff-branches]] bereits umgesetzt ist, hat
die Methode dort schon ein `String userId`-Parameter bekommen (für
`RESOURCE_WAGE_REDUCTION`) — hier nur den zweiten Faktor ergänzen:
```java
double globalReduction = skillTreeService.getEffectTotal(userId, EffectType.WAGE_REDUCTION, null);
double resourceReduction = skillTreeService.getEffectTotal(
    userId, EffectType.RESOURCE_WAGE_REDUCTION, def.passiveResource() != null ? def.passiveResource().name() : null);
double totalReduction = Math.min(0.9, Math.max(-0.5, globalReduction + resourceReduction));
return ent.getWorkers() * balance.getWagePerMinPerWorker() * (1 - totalReduction);
```
Falls dieser Plan **vor** den Rohstoff-Branches gebaut wird: nur
`globalReduction` einbauen, `resourceReduction`-Zeile erst ergänzen, wenn
der andere Plan an der Reihe ist (kurz nachsehen, ob `effectiveWage` schon
ein `userId`-Parameter hat, bevor man ihn ein zweites Mal einführt).

## Neuer Branch CONSTRUCTION — Knotenliste

| ID | DE Name | EN Name | Tier | Effekt(e) |
|---|---|---|---|---|
| construction_1 | Günstige Baustoffe | Cheap Materials | PASSIVE | `BUILDING_COST_REDUCTION` +0.03 |
| construction_2 | Effiziente Lohnbuchhaltung | Efficient Payroll | PASSIVE | `WAGE_REDUCTION` +0.02 |
| construction_3 | Verhandelte Lieferverträge | Negotiated Supply Deals | NOTABLE | `BUILDING_COST_REDUCTION` +0.05 |
| construction_4 | Expansionsfieber | Expansion Fever | KEYSTONE | `BUILDING_COST_REDUCTION` +0.12 **und** `WAGE_REDUCTION` −0.04 |
| construction_5 | Sammelbestellung | Bulk Order | PASSIVE | Fork ab construction_2: `WAGE_REDUCTION` +0.04 |

Kanten: `root→construction_1→construction_2→construction_3→construction_4`,
`construction_2→construction_5`.
`construction_4`-Beschreibung (DE) z. B.: "Gebäude werden deutlich
günstiger, aber jeder Arbeiter kostet spürbar mehr Lohn."

## Frontend-Änderungen

`BRANCH_ICON`-Map: `CONSTRUCTION: 'hammer'` (neues Icon, 8×8,
Fruitpunch24-Palette, Registrierung in `PixelIcon.vue`). `construction_4`
bekommt zusätzlich ein eigenes Keystone-Icon
(`keystone_construction4.svg`).

## Verifikationsplan

1. `GET /api/v1/skilltree?userId=...` — CONSTRUCTION-Branch korrekt
   angebunden, `construction_4` liefert 2 gegensätzliche Effekte.
2. Gebäude-Kaufpreis/Upgrade-Preis vor/nach `BUILDING_COST_REDUCTION`
   vergleichen (curl auf den Buy/Upgrade-Preview-Endpunkt oder direkt
   Kauf-Transaktion).
3. `GET /api/v1/farm/wage-status/{userId}` vor/nach `WAGE_REDUCTION` —
   Gesamtlohn sinkt über **alle** Gebäude, nicht nur eines (Abgrenzung zum
   Rohstoff-Branch-Test).
4. `construction_4` allozieren → Gebäudekosten sinken, Lohn steigt
   gleichzeitig messbar.
5. Floor/Cap-Werte greifen: Effekt auf Maximalwert hochdrehen (Admin-API),
   Kosten/Lohn dürfen nie außerhalb der definierten Grenzen liegen.
6. `npm run check:palette` grün nach neuem Icon.

## Kritische Dateien

- `backend/.../enums/EffectType.java`
- `backend/.../service/BuildingService.java:227-233,294-297`
- `backend/.../service/SkillTreeService.java`
- `frontend/src/components/SkillTreeView.vue:120-121`
