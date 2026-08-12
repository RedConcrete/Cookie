# ⏳ Skillbaum: STORAGE-Branch (Speicherkapazität)

> **Status:** ⏳ Offen

## Context

`docs/ROADMAP.md` (Eintrag zum Start-Balance-Fix, Abschnitt "Folgearbeit
bewusst zurückgestellt") vermerkt explizit einen offenen Punkt: ein echter
Ausgleich für volles Lager (z. B. Ressourcen-Umwandlung, Lager-Overflow-
Puffer) "als größere Mechanik im Skill-/Passiv-Baum ... noch nicht
spezifiziert, Spieler will das gezielt als Skill-Baum-Feature designen".
Dieser Plan ist genau das.

**Branch-Key bewusst `STORAGE`, nicht `LAGER`** — alle bestehenden
Branch-Keys sind englische Bezeichner (`MILK`, `BAKING`, `MARKET`, `CORE`,
`DISPO`), auch wenn die Spieltexte selbst Deutsch sind. `STORAGE` folgt
dieser Konvention konsistent.

**Baut zwingend auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf**
(Mehrfach-Effekte pro Knoten, `NodeTier`-Enum, zweisprachige Texte).

Verifizierter Ist-Zustand:
- **Globaler Speicher** (ein gemeinsamer Topf für alle 6 Ressourcen, kein
  Deckel pro Ressource — `UserEntity.getTotalResources()`,
  `UserService.java:219-221`, bestätigt in `docs/ROADMAP.md`):
  `getTotalCap(...)` = `balance.getBaseStorageCap() + lagerLevel ×
  balance.getStoragePerLevel()` (`BuildingService.java:195-203`).
  Überschuss beim Ernten wird schlicht **nicht gutgeschrieben** (kein
  Auto-Verkauf mehr seit 2026-08-07, `UserService.java:222-223`).
- **Pro-Gebäude-Puffer**: jedes Produktionsgebäude hat zusätzlich einen
  eigenen kleinen Zwischenspeicher (`BuildingDef.storageCapacity()`, z. B.
  Kuhstall 2880), der die passive Produktion stoppt, sobald er voll ist
  (`BuildingService.settle()`, Zeile 272:
  `Math.min(def.storageCapacity(), ...)`) — das ist der eigentliche Verlust
  bei langer Abwesenheit (Hauptlager-Cap ist meist großzügiger).

## Design-Entscheidungen

- **Zwei neue `EffectType`-Werte**, in einem Pfad gemischt (kein reiner
  "immer +X% Lager"-Ketten-Branch):
  - `STORAGE_CAP_BONUS` — Prozent-Bonus auf den globalen Hauptlager-Cap
    (`getTotalCap`).
  - `BUILDING_BUFFER_BONUS` — Prozent-Bonus auf den Pro-Gebäude-Puffer
    (`storageCapacity` je Gebäude) — das ist der eigentliche "Ausgleich für
    volles Lager bei Abwesenheit", den das ROADMAP-Item meint, da hier der
    tatsächliche Produktions-Stopp passiert.
- Beide Effekte sind global (targetResource=null) — ein Puffer-Bonus gilt
  einheitlich für alle Gebäude, keine Pro-Ressource-Variante.
- **Keystone mit echtem Tradeoff**: großer `BUILDING_BUFFER_BONUS`,
  gleichzeitig ein **negativer** `STORAGE_CAP_BONUS` — die Gebäude sammeln
  deutlich länger ungestört weiter, aber das Hauptlager selbst wird kleiner
  (der Spieler muss öfter zum Markt/Backhaus, statt alles zu horten).
- Kein Auto-Verkauf-Ersatz und keine Ressourcen-Umwandlung in diesem Pass —
  die Design-Doc-Entscheidung, keinen pauschalen Auto-Verkauf zu bauen,
  bleibt bestehen. Dieser Branch macht Overflow **seltener**, ersetzt ihn
  nicht durch eine neue Verkaufsmechanik.

## Backend-Änderungen

**`enums/EffectType.java`**: `STORAGE_CAP_BONUS`, `BUILDING_BUFFER_BONUS`
ergänzen. Dank [[2026-08-10-open-skillbaum-wheel-keystones]] (Abschnitt 2:
`effectType` als reiner String) braucht das keinen `ALTER TABLE ... DROP
CONSTRAINT`-Schritt mehr, sofern der Fundament-Plan schon umgesetzt ist —
sonst gilt noch die alte Falle (ROADMAP Abschnitt 0).

**`BuildingService.getTotalCap(...)`** (Zeilen 195-203): Die
`String userId`-Variante (Zeile 195-197) hat `userId` bereits, die
`Map`-Variante (Zeilen 200-203, von mehreren Stellen direkt mit einer schon
geladenen Map aufgerufen) nicht — gleiches Muster wie historisch bei
`getEffectiveSellFeeRate` (Map-Overload ohne `userId`, entweder Parameter
ergänzen oder über die String-userId-Variante routen). Empfehlung: `userId`
als zusätzlichen Parameter an die `Map`-Variante anhängen (alle Aufrufer
durchsuchen und anpassen).
```java
public double getTotalCap(Map<String, PlayerBuildingEntity> owned, String userId) {
    int lagerLevel = owned.containsKey("lager") ? owned.get("lager").getLevel() : 0;
    double base = balance.getBaseStorageCap() + lagerLevel * balance.getStoragePerLevel();
    double bonus = skillTreeService.getEffectTotal(userId, EffectType.STORAGE_CAP_BONUS, null);
    return base * (1 + Math.max(-0.5, bonus));
}
```
Floor bei `-0.5` verhindert, dass der Keystone-Downside plus weitere
negative Effekte das Lager theoretisch auf 0 oder negativ drücken.

**`BuildingService.settle(...)`** (Zeilen 266-275, Signatur bekommt in
[[2026-08-10-open-skillbaum-crit-system]] ohnehin schon ein neues
`String userId`-Parameter — falls dieser Plan unabhängig zuerst gebaut wird,
hier genauso ergänzen, nicht doppelt divergierend einführen): Zeile 272
```java
double bufferBonus = skillTreeService.getEffectTotal(userId, EffectType.BUILDING_BUFFER_BONUS, null);
double effectiveCap = def.storageCapacity() * (1 + Math.max(0, bufferBonus));
ent.setPendingAmount(Math.min(effectiveCap, ent.getPendingAmount() + produced));
```
**Hinweis falls Crit- und Storage-Plan zeitlich getrennt umgesetzt werden:**
wer zuerst kommt, fügt `userId` zur `settle()`-Signatur hinzu; der zweite
Plan findet den Parameter dann bereits vor.

## Neuer Branch STORAGE — Knotenliste

| ID | DE Name | EN Name | Tier | Effekt(e) |
|---|---|---|---|---|
| storage_1 | Ordentliche Regale | Tidy Shelves | PASSIVE | `STORAGE_CAP_BONUS` +0.05 |
| storage_2 | Isolierte Fässer | Insulated Barrels | PASSIVE | `BUILDING_BUFFER_BONUS` +0.10 |
| storage_3 | Erweiterter Anbau | Extended Wing | NOTABLE | `STORAGE_CAP_BONUS` +0.08 |
| storage_4 | Übervolle Scheune | Overflowing Barn | KEYSTONE | `BUILDING_BUFFER_BONUS` +0.25 **und** `STORAGE_CAP_BONUS` −0.10 |
| storage_5 | Doppelter Boden | Double Floor | PASSIVE | Fork ab storage_2: `STORAGE_CAP_BONUS` +0.10 |

Kanten: `root→storage_1→storage_2→storage_3→storage_4`, `storage_2→storage_5`.
`storage_4`-Beschreibung (DE) z. B.: "Gebäude sammeln deutlich länger
ungestört weiter, aber das Hauptlager selbst schrumpft."

## Frontend-Änderungen

`BRANCH_ICON`-Map: `STORAGE: 'kiste'` (neues Icon, 8×8, Fruitpunch24-
Palette, Registrierung in `PixelIcon.vue`). `storage_4` bekommt zusätzlich
ein eigenes Keystone-Icon (`keystone_storage4.svg`).

## Verifikationsplan

1. `GET /api/v1/skilltree?userId=...` — STORAGE-Branch korrekt angebunden,
   `storage_4` liefert 2 gegensätzliche Effekte.
2. Hauptlager-Cap vor/nach `STORAGE_CAP_BONUS`-Knoten (z. B. via
   Stats-/Rathaus-Anzeige oder direkt harvest bis zum alten Cap, dann
   Knoten allokieren, weiter ernten können).
3. Gebäude lange nicht einsammeln (oder `lastSettledAt` künstlich
   zurückdatieren, dev-seitig) vor/nach `BUILDING_BUFFER_BONUS` → höherer
   `pendingAmount`-Deckel messbar.
4. `storage_4` allozieren → Puffer größer, aber Hauptlager-Cap kleiner,
   beide Effekte gleichzeitig messbar.
5. `npm run check:palette` grün nach neuem Icon.

## Kritische Dateien

- `backend/.../enums/EffectType.java`
- `backend/.../service/BuildingService.java:195-203,266-275`
- `backend/.../service/SkillTreeService.java`
- `frontend/src/components/SkillTreeView.vue:120-121`
