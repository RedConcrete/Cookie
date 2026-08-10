# ⏳ Skillbaum: Krit-System (Ernte, Passiv-Produktion, Backen)

> **Status:** ⏳ Offen

## Context

User-Wunsch: eine Krit-Chance (Chance auf überproportionalen Bonus-Ertrag),
die **nicht nur beim Backen** greift (wie ursprünglich vage im ROADMAP
angedacht — "Crit-Backen ... noch keine Spezifikation"), sondern an allen
drei Stellen, an denen Ressourcen/Cookies erzeugt werden: manuelles
Hover-Ernten, passive Arbeiter-Produktion, Backen.

Aktueller Stand (verifiziert direkt im Code):
- Kein `Random`/`ThreadLocalRandom` im Backend — einzige bestehende
  Zufallsquelle ist `Math.random()` in
  `MarketService.java:400,403` (`applyRandomPriceFluctuation()`).
- Kein Floating-Number/Toast/Partikel-Feedback im Frontend für Boni jeglicher
  Art — Krit-Feedback ist komplett neue UI.

**Baut zwingend auf [[2026-08-10-open-skillbaum-wheel-keystones]] auf**
(Mehrfach-Effekte pro Knoten, `NodeTier`-Enum, zweisprachige Texte).

## Design-Entscheidungen

- **Neue `EffectType`-Werte**: `CRIT_CHANCE` (0–1, Wahrscheinlichkeit pro
  Ereignis) und `CRIT_MULTIPLIER` (Bonus-Faktor bei Treffer, additiv zu
  1.0 — z. B. 0.5 heißt ×1.5 Ertrag). Getrennte Typen statt einem
  kombinierten Wert, damit ein Pfad zwischen "öfter kritisch" und "krasser
  kritisch" wählen kann.
- **Global (targetResource=null), nicht ressourcen-gebunden** für v1 — Krit
  gilt einheitlich auf Ernte/Produktion/Backen. Ressourcen-spezifisches Krit
  wäre ein möglicher Folge-Ausbau, hier bewusst nicht gebaut.
- **Keystone mit echtem Tradeoff** (nutzt die Mehrfach-Effekt-Fähigkeit aus
  dem Fundament-Plan): großer `CRIT_CHANCE`-Bonus, gleichzeitig ein
  **negativer** `CRIT_MULTIPLIER` — Krits passieren deutlich öfter, treffen
  dafür schwächer. Direkter PoE-Tradeoff ohne neuen Effekt-Typ.
- **Passive Arbeiter-Produktion ist eine geschlossene Zeitintegration, kein
  Tick-Loop** (`BuildingService.settle()`, Zeilen 266-275: `produced =
  passiveRatePerSecPerWorker × workers × elapsedSeconds`, einmalig
  berechnet). Ein "Krit pro Sekunde" ist damit nicht sauber abbildbar — die
  Entscheidung hier: **ein Bernoulli-Wurf pro `settle()`-Aufruf**, der bei
  Treffer die komplette in diesem Aufruf akkumulierte Menge multipliziert.
  Nebenwirkung, die dem Spieler auffallen kann: seltener einsammeln (mehr
  akkumulierte Zeit pro `settle()`) bedeutet weniger, aber "fettere" Krit-
  Würfe; oft einsammeln bedeutet mehr, kleinere Würfe bei gleicher
  Erwartungswert-Ausbeute. Bewusst akzeptiert statt eines komplexeren
  Ticklauf-Umbaus nur für Krit.
- **RNG-Quelle**: `Math.random()`, konsistent mit dem einzigen bestehenden
  Zufalls-Code (`MarketService`) — kein neues Pattern einführen, wo keins
  gebraucht wird.

## Backend-Änderungen

**`enums/EffectType.java`**: `CRIT_CHANCE`, `CRIT_MULTIPLIER` ergänzen.
Dank [[2026-08-10-open-skillbaum-wheel-keystones]] (Abschnitt 2:
`effectType` als reiner String) braucht das keinen `ALTER TABLE ... DROP
CONSTRAINT`-Schritt mehr, sofern der Fundament-Plan schon umgesetzt ist —
sonst gilt noch die alte Falle (ROADMAP Abschnitt 0).

Gemeinsamer Helper in `SkillTreeService` (neu), von allen drei Stellen
genutzt:
```java
public double rollCritMultiplier(String userId) {
    double chance = Math.max(0, Math.min(1, getEffectTotal(userId, EffectType.CRIT_CHANCE, null)));
    if (Math.random() >= chance) return 1.0;
    double bonus = Math.max(0, getEffectTotal(userId, EffectType.CRIT_MULTIPLIER, null));
    return 1.0 + bonus;
}
```
`Math.max(0, ...)` auf `bonus` verhindert, dass der Keystone-Downside
(negativer `CRIT_MULTIPLIER`) den Multiplikator unter 1.0 drückt, wenn kein
anderer Knoten ihn ausgleicht — ein Krit soll im schlimmsten Fall neutral
sein (×1.0), nie ein negativer Ertrag.

**Hover-Ernte** — `UserService.harvest()`, Zeile 214:
```java
double amount = (1.0 + harvestBonus) * prestigeMultiplier * ticks
                * skillTreeService.rollCritMultiplier(userId);
```
vor dem Storage-Cap-Clamp (Zeile 222). Response-DTO sollte ein `crit:
boolean`-Flag mitgeben (Multiplikator > 1.0 → true), damit das Frontend ohne
eigene Nachrechnung weiß, ob Feedback gezeigt werden soll.

**Passive Produktion** — `BuildingService.settle()` (Zeilen 266-275),
aktuelle Signatur `settle(PlayerBuildingEntity ent, BuildingDef def, boolean
idle, LocalDateTime now)` — **hat kein `userId`**. Muss um `String userId`
erweitert werden; beide bestehenden Aufrufer haben `userId` bereits im Scope
(`settleAllForIdleTransition(String userId, ...)`, Zeile 284-292, sowie
`PassiveIncomeService.collectBuilding()`, Zeile 60). Zeile 271:
```java
double produced = def.passiveRatePerSecPerWorker() * ent.getWorkers() * elapsedSeconds
                   * skillTreeService.rollCritMultiplier(userId);
```
`PlayerBuildingDto`/Collect-Response bekommt ein `crit`-Flag für die
Sammel-Bestätigung im Frontend.

**Backen** — `BakeService.claim()`, Zeilen 168-170:
```java
double outputMultiplier = (1.0 + bakeBonus) * skillTreeService.rollCritMultiplier(userId);
```
Claim-Response bekommt `crit`-Flag.

## Neuer Branch CRIT — Knotenliste

| ID | DE Name | EN Name | Tier | Effekt(e) |
|---|---|---|---|---|
| crit_1 | Aufmerksamer Blick | Sharp Eye | PASSIVE | `CRIT_CHANCE` +0.03 |
| crit_2 | Glückssträhne | Lucky Streak | PASSIVE | `CRIT_MULTIPLIER` +0.25 |
| crit_3 | Geschickter Zugriff | Skilled Grasp | NOTABLE | `CRIT_CHANCE` +0.04 |
| crit_4 | Verzweifelter Griff | Desperate Grip | KEYSTONE | `CRIT_CHANCE` +0.08 **und** `CRIT_MULTIPLIER` −0.15 |
| crit_5 | Zweite Chance | Second Chance | PASSIVE | Fork ab crit_2: `CRIT_MULTIPLIER` +0.35 |

Kanten: `root→crit_1→crit_2→crit_3→crit_4`, `crit_2→crit_5`.
`crit_4`-Beschreibung (DE) z. B.: "Deutlich höhere Krit-Chance, aber jeder
Krit fällt spürbar schwächer aus."

## Frontend-Änderungen

Komplett neue Feedback-Komponente (kein bestehendes Floating-Number-System
zum Erweitern gefunden) — Vorschlag: kleine, kurzlebige Pixel-Text-Anzeige
("KRITISCH!" o. ä., Fruitpunch24-Palette) an der Stelle, wo geerntet/
gesammelt/gebacken wird, ausgelöst vom `crit`-Flag der jeweiligen Response.

`BRANCH_ICON`-Map: `CRIT: 'krit'` (neues Icon, 8×8, Fruitpunch24-Palette).
`crit_4` bekommt zusätzlich ein eigenes Keystone-Icon
(`keystone_crit4.svg`), gleiches Muster wie die anderen Keystones.

## Verifikationsplan

1. `rollCritMultiplier` über curl-Serie prüfen: mit `CRIT_CHANCE=1.0`
   (temporär per Admin-API gesetzt) → jeder Aufruf kritisch, Multiplikator
   korrekt, nie < 1.0 auch mit aktivem `crit_4`-Downside.
2. Hover-Ernte, Sammel-Aktion, Bake-Claim je einmal mit `CRIT_CHANCE=0` (kein
   Krit, Baseline unverändert) und `CRIT_CHANCE=1` (immer Krit, Menge ×
   erwarteter Multiplikator) testen.
3. Frontend: Krit-Feedback erscheint an allen drei Stellen, verschwindet
   nach kurzer Zeit, keine Layout-Verschiebung.
4. `npm run check:palette` grün.

## Kritische Dateien

- `backend/.../enums/EffectType.java`
- `backend/.../service/SkillTreeService.java` — neuer `rollCritMultiplier`
- `backend/.../service/UserService.java:196-236` — `harvest()`
- `backend/.../service/BuildingService.java:266-275` — `settle()`
- `backend/.../service/PassiveIncomeService.java:42-97` — `collectBuilding()`
- `backend/.../service/BakeService.java:156-193` — `claim()`
- `backend/.../service/MarketService.java:400,403` — bestehendes
  `Math.random()`-Vorbild
- `frontend/src/components/SkillTreeView.vue`
