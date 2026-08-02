# Code Review Task — Branch `claude/pixel-layouts-rework`

> Für Claude Code. Branch: `claude/pixel-layouts-rework` (5 commits ahead of `main`, +6127/−2122, 112 files).
> Fokus dieser Runde: **Backend** (`backend/cookie-server-spring-boot`). Frontend-Review steht noch aus (siehe Abschnitt am Ende).
> Arbeite die Punkte in Reihenfolge ab (P1 zuerst). Jeder Punkt hat Datei, Problem, Fix. Nach jedem Fix: `./mvnw test` im backend-Modul.

---

## P1 — Business-Exceptions liefern 500 statt 400

**Datei:** `exception/GlobalExceptionHandler.java`, `service/BuildingService.java`

Handler faengt nur `IllegalArgumentException` (→400) und `NoSuchElementException` (→404).
Alle Geschaeftsfehler im `BuildingService` sind aber `IllegalStateException`:
- "Building already owned"
- "Not enough cookies. Need ..."
- "No available citizens"
- "Max citizens reached (...)"
- "Rathaus not built"
- "Building not owned"

→ fallen in generischen `Exception`-Handler → Client bekommt `{"error":"Unerwarteter Serverfehler."}` statt echter Meldung.
Der Handler-Kommentar behauptet sogar "Nicht genug Cookies" gebe 400 — tut es nicht.

**Fix (bevorzugt):** eigenen Handler ergaenzen, damit Semantik erhalten bleibt:
```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
}
```
Alternativ: alle `IllegalStateException` im `BuildingService` auf `IllegalArgumentException` umstellen (dann 400). Eine Variante waehlen, nicht mischen.

---

## P1 — Overflow-Payout ignoriert Markt-Upgrade-Rabatt

**Datei:** `scheduler/PassiveIncomeScheduler.java`

Overflow-Verkauf nutzt globale Rate:
```java
double payout = overflow * price * (1.0 - marketService.getSellFeeRate());
```
`BuildingService.getEffectiveSellFeeRate(userId, baseRate)` (mit `markt`-Level-Rabatt, 0.02/Level, floor 0.01) existiert — wird hier aber nicht aufgerufen. Spieler mit hohem Markt-Level werden beim Overflow-Verkauf zu hoch besteuert.

**Fix:** effektive Rate pro User verwenden:
```java
double feeRate = buildingService.getEffectiveSellFeeRate(user.getSteamId(), marketService.getSellFeeRate());
double payout  = overflow * price * (1.0 - feeRate);
```
Konsistenz gegen den manuellen Verkaufspfad in `MarketService` (~Zeile 228) pruefen — dort ebenfalls `marketConfig.getSellFeeRate()` direkt. Entscheiden ob der Markt-Rabatt auch dort greifen soll, sonst zwei Verkaufspfade mit unterschiedlicher Steuer.

---

## P1 — Scheduler-Race: WageScheduler vs PassiveIncomeScheduler

**Dateien:** `scheduler/PassiveIncomeScheduler.java` (`fixedRate = 5_000`), `scheduler/WageScheduler.java` (`fixedRate = 60_000`)

Beide `@Transactional`, iterieren `userRepository.findAll()`, schreiben `user.setCookies(...)` + `userRepository.save(user)`. Alle 60s ueberlappen die Ticks. Ohne Locking: last-write-wins → Passive-Payout ODER Wage-Abzug geht verloren.

**Fix-Optionen (eine waehlen):**
- Optimistic Locking: `@Version`-Feld auf `UserEntity`, Retry auf `OptimisticLockException`.
- Wages im selben Tick wie Passive rechnen (ein Scheduler, ein Write pro User pro Tick).
- Pessimistic Lock beim Laden (`SELECT ... FOR UPDATE` via `@Lock(PESSIMISTIC_WRITE)` im Repo) — teuerer, nur wenn noetig.

Empfehlung: `@Version` (billig, deckt auch kuenftige nebenlaeufige Endpunkte ab).

---

## P2 — N+1 Queries im Passive-Tick (alle 5s)

**Datei:** `scheduler/PassiveIncomeScheduler.java` + `service/BuildingService.java`

Pro User pro Tick:
- `computePassiveTicks` → `ownedMap` → `findByUserId`
- `getTotalCap` → `getBuildingLevel` → `findByUserIdAndBuildingId` (weitere Query)
- ggf. `getCurrentPrice` / Fee-Lookup

Bei N Usern → O(N) × mehrere Queries, im 5s-Takt. Skaliert schlecht.

**Fix:**
- `findByUserId` einmal pro User laden, an `getTotalCap`/Wage/Ticks als bereits geladene Map durchreichen (Overload `computePassiveTicks(Map<String,PlayerBuildingEntity>, ...)` statt userId).
- Optional: alle Buildings aller User in einer Query (`findAll()` auf `PlayerBuildingRepository`, gruppieren per `userId`) statt pro-User-Query.
- User + Buildings vor der Schleife batchen.

---

## P2 — `PASSIVE_TICK_SECONDS` dreifach dupliziert

**Dateien:** `BuildingService.PASSIVE_TICK_SECONDS = 5.0`, `PassiveIncomeScheduler.TICK_SECONDS = 5.0`, `@Scheduled(fixedRate = 5_000)`

Drei Stellen muessen synchron bleiben. `toDto()` berechnet Anzeige-Rate mit `PASSIVE_TICK_SECONDS`; aendert man `fixedRate`, driftet die angezeigte Rate von der echten Gutschrift ab.

**Fix:** eine Quelle. z.B. in `application.properties`:
```properties
game.passive-tick-ms=5000
```
`@Scheduled(fixedRateString = "${game.passive-tick-ms}")`, Sekunden daraus ableiten, `BuildingService` denselben Wert injizieren (Konstruktor/`@Value`).

---

## P2 — Geteilter Lager-Cap ist reihenfolgeabhaengig

**Datei:** `scheduler/PassiveIncomeScheduler.java`, `BuildingService.getTotalCap`

`total` = Summe aller 6 Ressourcen gegen einen gemeinsamen `cap`. Overflow-Verteilung folgt der `BUILDINGS`-Listenreihenfolge: erste Ressource fuellt den Cap, Rest kippt in Overflow-Verkauf. Nicht deterministisch fair zwischen Ressourcen.

**Klaeren:** Ist geteilter Cap gewollt (Design-Doku `docs/cookie-game-design.md` pruefen)?
- Wenn ja: Overflow-Reihenfolge dokumentieren oder Cap proportional aufteilen.
- Wenn nein: per-Ressource-Cap einfuehren.

---

## P3 — Kleinkram

- **`citizenCost` Kommentar falsch:** sagt "wie die Upgrade-Kostenkurve", nutzt aber `1.15^n`, waehrend `computeCost` = `baseCost × 2^level`. Kommentar oder Kurve angleichen. (`BuildingService`)
- **Nutzloser Cast:** `getTotalCap` → `(long) lagerLevel * STORAGE_PER_LEVEL`, Rueckgabe ist `double`. Cast entfernen. (`BuildingService`)
- **Scheduler-Logging:** `log.error("... {}", id, e.getMessage())` schluckt Stacktrace; bei NPE ist `getMessage()` null/leer. → `log.error("Passive tick failed for {}", id, e)`. (beide Scheduler)
- **`ensurePreBuiltBuildings` idempotenz:** pro Building ein `findByUserIdAndBuildingId` — einmal `findByUserId` laden, gegen Set pruefen. (BuildingService)

---

## Noch offen — Frontend (naechste Runde)

Nicht in diesem File reviewt, aber Kandidaten:
- `views/FarmGridView.vue` (+744) — groesste Aenderung, splitten?
- `views/MarketView.vue` (fast neu geschrieben)
- Composables: `useHoldDrag.js`, `useHoverReveal.js`, `useHotkeys.js`, `useBadges.js` — Cleanup/Listener-Leaks pruefen (removeEventListener in onUnmounted?)
- Scene-Architektur `components/buildings/*Scene.vue` + `buildingInfo.js`/`farmLayout.js` — Konsistenz, Magic Numbers
- Letzter Commit mischt Backend-Refactor + Frontend-Feature (`BuildingDetailDialog`) — kuenftig trennen.

## Merge-Hinweis
`FarmGridView.vue` und `MarketView.vue` sind fast komplett umgeschrieben — Merge gegen `main` wird dort konfliktreich, falls parallel geaendert. Vor Merge rebasen und gezielt aufloesen.
