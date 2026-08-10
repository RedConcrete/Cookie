# ✅ Passive income: per-building storage + manual collect (like rent)

> **Status:** ✅ Umgesetzt (09.–10.08.2026, commits 68f1f72, 144eedc)

## Context

Passive building income is currently credited by `PassiveIncomeScheduler`
(`backend/.../scheduler/PassiveIncomeScheduler.java`), a global `@Scheduled(fixedRate=5000)`
loop that iterates **every** registered user every 5 seconds and writes straight into
`UserEntity`'s resource fields. The frontend never learns about these background writes
until some unrelated API call happens to return a fresh snapshot of all 6 resources —
in practice that's almost always the hover-harvest sync (`FarmGridView.vue` `syncHarvest`,
every 3s while hovering a building), whose `playerStore.updateFromDto(updated)` overwrites
all 6 resource fields at once. That's the "jump" the user is seeing: resources silently
piled up server-side, and the first thing to re-poll authoritative state (hovering) reveals
it all at once.

This also means the server does real work every 5 seconds for every account that has ever
played, active or not — the user explicitly wants that load gone.

Redesign, mirroring how baking already works (`BakeService` — accumulate, then explicit
`claim()`): each production building accrues its own resource locally, capped by a
per-building storage capacity. Once full, the building stops producing until the player
collects it — like rent. Collection is computed lazily from elapsed real time, only
persisted when something meaningful happens (collect, worker change, building bought/
upgraded, wage-idle toggle). No more constant per-5s loop over all users. Collection is
possible directly from the farm grid via a click on a badge over the building — no need to
open the building dialog (confirmed with user: badge appears as soon as *anything* is
pending, not gated to "only once full"; a "collect all" overview is explicitly **out of
scope for now** — user said that can live in the Rathaus dialog "vielleicht mal später").

## Backend changes

### `PlayerBuildingEntity` (entity/PlayerBuildingEntity.java)
Add two columns:
- `pendingAmount` (double, default 0) — resource currently stored in the building, not yet collected.
- `lastSettledAt` (LocalDateTime, default `now()` on creation) — accrual checkpoint.

DB is disposable (per project memory), Hibernate `ddl-auto` regenerates schema — no manual migration needed.

### `BuildingService.java` — storage cap + settle logic
- Add `storageCapacity` to the `BuildingDef` record, populated only for the 6 production
  buildings (pond/hof/huhn/butter/kakao/kuh) in the existing `BUILDINGS` list — size each to
  roughly 10 minutes of output at that building's level-1 base worker count (same style as
  the existing hardcoded baseCost/wagePerMin numbers, tunable later).
- New method `settle(PlayerBuildingEntity ent, BuildingDef def, boolean idle, LocalDateTime now)`:
  - `elapsedSeconds = seconds since ent.lastSettledAt` (treat null as `now` on first touch).
  - if `!idle && ent.workers() > 0 && def.passiveResource() != null`:
    `pendingAmount = min(storageCapacity, pendingAmount + rate*workers*elapsedSeconds)`.
  - `lastSettledAt = now` always.
  - Pure in-memory mutation — caller decides whether to persist.
- Call sites:
  - `getBuildings(userId)`: needs `UserEntity.isWorkersIdle()` now too (small extra lookup,
    same pattern as other callers) — compute a **settled preview** per building for display
    only, do **not** persist, so plain reads (dialog open, polling) stay cheap and don't
    write to the DB.
  - `changeWorkers()`: settle+persist first (old worker count, old idle) before applying the delta.
  - `buyOrUpgrade()`: settle+persist before applying the level change; new entities start
    with `pendingAmount=0`, `lastSettledAt=now`.
- Drop `computePassiveTicks()` (no longer used once the scheduler is gone) and the
  `PassiveTick` record.

### `WageService.deductWageForUser()` — idle-boundary correctness
When `workersIdle` actually flips (either direction), settle+persist all of that user's
owned production buildings using the **old** idle value first, before writing the new flag.
Piggybacks on the already-existing 60s wage loop (`WageScheduler`) instead of adding a new
scheduler — keeps idle-transition accrual accurate without extra continuous ticking.

### Collect endpoint — repurpose `PassiveIncomeService`, delete the scheduler
- Delete `scheduler/PassiveIncomeScheduler.java` entirely.
- Repurpose `PassiveIncomeService` (keep the class/file, replace `creditUser`) with
  `collectBuilding(userId, buildingId)`, mirroring `BakeService.claim()`:
  1. Load building + user, settle the one building (current idle/workers).
  2. `credited = min(pendingAmount, availableRoom)` against the existing global warehouse
     cap (`BuildingService.getTotalCap`) — same "no overflow, no auto-sell" rule already
     established for hover-harvest and the old passive credit (comment in the current
     `creditUser`, `PassiveIncomeService.java:50-56`) — keep that policy, just scoped to one
     building's amount instead of proportionally across many.
  3. Add `credited` to the matching `UserEntity` resource field, `addLifetimeHarvested`.
  4. Reset building: `pendingAmount -= credited` (any amount that didn't fit is simply lost,
     consistent with existing policy — not carried over), `lastSettledAt=now`, save both.
  5. Return updated `UserInformationDto` (existing shape) — frontend applies it without a full reload.
- New endpoint on `BuildingController` (`/api/v1/farm`): `POST /buildings/collect/{userId}/{buildingId}`.

### `PlayerBuildingDto` / `GameBalanceConfig`
- Add `pendingAmount`, `storageCapacity`, `passiveRatePerSec` (replaces `passiveRatePerTick`
  — there's no fixed tick width anymore), `lastSettledAtEpochMs` (lets the frontend locally
  extrapolate the fill bar between polls, same trick the bake dialog already uses via
  `completesAt`, so no extra polling is needed for a smooth animation).
- Remove `GameBalanceConfig.passiveTickSeconds` — nothing multiplies by a fixed tick anymore.

## Frontend changes

### `services/api.js` / `stores/player.js`
- Add `collectBuilding(steamId, buildingId)` calling the new endpoint.
- `ownedBuildings` entries now carry `pendingAmount`, `storageCapacity`, `passiveRatePerSec`, `lastSettledAtEpochMs`.

### Farm grid — collect badge without opening the dialog
- New small badge (styled like the existing bake-done / floating-number visuals) rendered
  above each production `BuildingFrame`, visible whenever that building's locally
  extrapolated `pendingAmount > 0` (extrapolation: `min(cap, dto.pendingAmount +
  rate*(now - lastSettledAtEpochMs)/1000)`, recomputed client-side on a light local timer —
  no extra server polling, per the "take load off the server" ask).
- Click on the badge (`@click.stop` so it doesn't bubble into the frame's `@open`) calls
  `collectBuilding`, applies the returned resource totals to `playerStore` (not a full-store
  clobber — only the touched fields), spawns a floating "+X" via the existing
  `spawnFarmNumber` composable. Dialog never opens.
- Remove `spawnPassiveNumbers` / `passiveTimer` in `FarmGridView.vue` (`FarmGridView.vue:806-826`)
  — it's a cosmetic ticker reading the now-removed `passiveRatePerTick` field and doesn't
  correspond to anything actually credited; the badge replaces it with something that does.

### `BuildingDetailDialog.vue`
- The current `bd-storage` block (`BuildingDetailDialog.vue:87-91`, `:137-156`) shows the
  *global* warehouse stock for that resource type, which is misleading here — replace it
  with this building's own `pendingAmount`/`storageCapacity` bar, plus an "Einsammeln"
  button (disabled at `pendingAmount === 0`) calling the same `collectBuilding` action as
  the map badge.

### Hover-harvest jump — resolved as a side effect, no separate fix needed
Once passive income stops silently mutating `UserEntity` in the background, `syncHarvest`'s
full-DTO snapshot (`FarmGridView.vue:780-785`) has nothing invisible left to reveal — the
reported "jump on hover" goes away because there's no more hidden server-side accrual for it
to expose.

## Docs
Update `docs/cookie-game-design.md` (§4/§5 passive production description — currently says
"alle 5 Sekunden gutgeschrieben") and `docs/ROADMAP.md` to describe the new accrue-and-collect
model, per repo convention that ROADMAP is the single source of truth for open/closed items.

## Out of scope (explicitly deferred per user)
"Collect all" convenience action — user said that can live in the Rathaus dialog later, not needed now.

## Verification
- Backend: `cd backend/cookie-server-spring-boot && ./mvnw test` (or `mvnw.cmd` on Windows).
- Manual via the `run` skill: place a production building with a worker, confirm the badge
  appears and its bar fills locally without extra network calls, confirm the HUD resource
  counter does **not** change until the badge is clicked, click it and confirm resources
  update immediately with no dialog opening, confirm the building visually shows
  idle/stopped once its storage caps out until collected, confirm `BuildingDetailDialog`'s
  collect button does the same thing.
