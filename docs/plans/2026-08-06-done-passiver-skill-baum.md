# ✅ Passive Skill Tree (replaces Upgrade system)

> **Status:** ✅ Umgesetzt (06.08.2026, commit aab1643)

## Context

The current cookie economy has almost no sink — the flat 3-upgrade shop
(`boost_harvest`, `boost_harvest_speed` [dead/unused], `boost_bake`) barely
drains cookies and gives the player no real choice. The user wants a
Path-of-Exile-style passive skill tree instead: a node graph the player
expands outward from a center by spending Cookies (→ Skill Points → nodes),
with real branching choice (milk production, baking speed, market fees, ...)
and a connectivity rule (can only allocate a node adjacent to one you already
own) for strategic depth. This is a full replacement, not an addition — the
old Upgrade system is deleted outright.

Confirmed scope for this pass (via direct Q&A with the user):
- Full generic infrastructure + a real, hand-authored ~18-node v1 tree across
  4 branches. Adding node #19+ later is a seed/admin-table change, no new
  allocation/validation/rendering code.
- PoE-style connectivity gating (must path from root).
- Harvest bonuses are **per-resource** (see Key Design Decision below).
- Prestige integration (3 bonus points on full reset) is explicitly **out of
  scope** — separate future roadmap item, just leave room for it.
- Anti-cheat re-verification job is explicitly **out of scope** — leave a
  `ROADMAP.md` note, don't build it.
- Admin dialog gets full per-node live editing (name/effect/value/position),
  same shape as today's Upgrades admin table.
- Net-worth field renamed `upgradeValue` → `skillTreeValue` throughout.
- Prestige (when rebuilt later) resets `totalSkillPointsBought` too — the
  skill-point cost curve goes back to cheap on a full reset, unlike
  `totalPrestiges` which stays permanent.

## Key Design Decision: per-resource effects via existing `resource` param

`UserService.harvest(String userId, ResourceName resource, double storageCap,
double marketPrice, double sellFeeRate)` **already receives which resource is
being harvested** — the old code just never used that to vary the boost. This
means "more milk production" doesn't need a harvest-formula rebuild, only a
resource-aware effect resolver:

```java
// SkillTreeService
public double getEffectTotal(String userId, EffectType type, String targetResource) {
    Set<String> allocated = allocatedNodeIds(userId); // + root
    return nodeCache.values().stream()
        .filter(n -> allocated.contains(n.getId()) && n.getEffectType() == type)
        .filter(n -> n.getTargetResource() == null || n.getTargetResource().equalsIgnoreCase(targetResource))
        .mapToDouble(SkillNodeEntity::getEffectValue)
        .sum();
}
```
A node with `targetResource == null` applies to every resource (a generalist
pick); a node with `targetResource == "MILK"` only counts when
`resource == MILK`. Call with `targetResource = null` for `BAKE_OUTPUT` /
`MARKET_FEE_REDUCTION` (not resource-scoped). This is the one piece of the
plan that differs from the initial Explore-agent research — verified directly
against `UserService.java:178-219` before finalizing.

## Backend data model

**`SkillNodeEntity`** (table `skill_nodes`) — static tree definition, single
source of truth, admin-editable:
```
id, name, description, branch (String, e.g. "MILK"/"BAKING"/"MARKET"/"CORE"),
effectType (enum EffectType: HARVEST_YIELD | BAKE_OUTPUT | MARKET_FEE_REDUCTION),
targetResource (String, nullable — resource name for HARVEST_YIELD nodes, null = global),
effectValue (double), isRoot (boolean), x (int), y (int)
```
Binary allocation (owned or not) — no per-node levels/maxLevel, that concept
belongs to the old system.

**`SkillEdgeEntity`** (table `skill_edges`) — join table, not a delimited
column (matches every other relationship in this codebase —
`PlayerBuildingEntity` etc. — and needs bidirectional adjacency lookups for
the connectivity check, which a CSV field would force ad-hoc string-parsing
for): `id, fromNode (FK), toNode (FK)`. One directional row per pair; the
connectivity check treats edges as undirected.

**`PlayerSkillNodeEntity`** (table `player_skill_nodes`) — mirrors
`PlayerUpgradeEntity`'s shape: `id = userId+"#"+nodeId, userId, nodeId`. No
`level`/`totalSpent` — binary allocation, spend is tracked centrally (below).
Root is never stored as a row — implicitly allocated for everyone.

**New `UserEntity` fields** (added directly, same place as `prestigeLevel`
etc.):
```
int skillPoints                    // currently unspent
int totalSkillPointsBought         // lifetime count, drives the cost curve exponent, reset on Prestige
double totalSkillPointCookiesSpent // cumulative cookie spend, feeds Net Worth (replaces totalSpent summation)
```

**Repositories**: `SkillNodeRepository`, `SkillEdgeRepository` (+
`findByFromNodeOrToNode`), `PlayerSkillNodeRepository` (+ `findByUserId`,
`existsByUserIdAndNodeId`, `deleteByUserId`).

## Effect system

`enums/EffectType.java`: `HARVEST_YIELD, BAKE_OUTPUT, MARKET_FEE_REDUCTION`.

`SkillTreeService` caches all nodes in a `Map<String, SkillNodeEntity>`
(refreshed on admin edits, avoids `findAll()` per harvest tick) and exposes
`getEffectTotal(userId, type, targetResource)` as shown above — the single
centralized resolver replacing the old scattered hardcoded-ID lookups.

**Exact call sites rewired:**

1. `UserService.harvest()` (line 178-219) — remove the
   `playerUpgradeRepository.findByUserIdAndUpgradeId(userId, "boost_harvest")`
   lookup (line 183-186) and the hardcoded `boostLevel * 0.5` (line 199).
   Replace with:
   ```java
   double harvestBonus = skillTreeService.getEffectTotal(userId, EffectType.HARVEST_YIELD, resource.name());
   double amount = (1.0 + harvestBonus) * prestigeMultiplier * ticks;
   ```
   Remove the `PlayerUpgradeRepository` constructor dependency, add
   `SkillTreeService`.

2. `BakeService.claim()` (~line 165-171) — same pattern, remove `boost_bake`
   lookup + hardcoded `bakeBoost * 0.10`, replace with
   `skillTreeService.getEffectTotal(userId, EffectType.BAKE_OUTPUT, null)`.

3. `BuildingService.getEffectiveSellFeeRate(String userId, double baseRate)`
   **already exists** (line 202-204, delegates to the `Map`-based overload at
   206-211) — extend the `Map`-based overload to also subtract
   `skillTreeService.getEffectTotal(userId, EffectType.MARKET_FEE_REDUCTION, null)`
   before the `Math.max(0.01, ...)` floor. Both `MarketService` and
   `PassiveIncomeService` already call through this method, so both get the
   new discount automatically — no separate rewiring needed. (Note: the
   `Map`-overload doesn't currently take `userId` — either add it as a
   parameter or route through the `String userId` overload instead; small
   signature adjustment, not a redesign.)

`FarmGridView.vue`'s client-side harvest prediction (`boostHarvestLevel()` /
`localHarvestTick()`, ~line 564-575) mirrors call site #1 for instant visual
feedback and must become resource-aware the same way (compute the bonus for
whichever resource is currently being hover-harvested, from the player's
allocated node list client-side).

## Skill point purchase + allocation

**`SkillTreeService.buySkillPoint(userId)`** — `@Transactional`, same shape
as the old `UpgradeService.buyUpgrade()`: `cost = skillPointBaseCost ×
skillPointCostGrowth ^ totalSkillPointsBought`, rounded to 2 decimals
(`Math.round(... * 100.0) / 100.0`, same as `UpgradeService.nextCost()`).
Checks cookies, deducts, `skillPoints++`, `totalSkillPointsBought++`,
`totalSkillPointCookiesSpent += cost`. New `GameBalanceConfig` fields
`skillPointBaseCost` (50) / `skillPointCostGrowth` (1.15, matching the
existing citizen/upgrade growth rate convention) — live-tunable via
`AdminConfigController`, same pattern as `citizenCostGrowth`/
`buildingCostGrowth` (verified exact getter/setter shape in
`GameBalanceConfig.java:28,34,60-67`).

**`SkillTreeService.allocateNode(userId, nodeId)`** — `@Transactional`:
- 404 if node unknown; reject if `isRoot` or already allocated.
- Reject (400) if `skillPoints < 1`.
- **Connectivity check**: build the player's allocated set (+ root), then
  confirm at least one edge connects `nodeId` to a member of that set via
  `edgeRepository.findByFromNodeOrToNode(nodeId, nodeId)`.
- On success: insert `PlayerSkillNodeEntity`, `skillPoints--`, save.
- Returns the full updated tree-status DTO (same "return the whole thing"
  pattern as `UpgradeService.buyUpgrade()`).

Server computes `allocated`/`allocatable` per node in the response DTO — the
frontend does zero connectivity math itself, only renders server state
(never trust client-derived game logic, same philosophy already documented
in `UserService.harvest()`'s comment about not trusting client-sent ticks).

## REST API — new `SkillTreeController` at `/api/v1/skilltree`

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/skilltree?userId=` | Full tree + player status |
| POST | `/api/v1/skilltree/buy-point/{userId}` | Buy 1 skill point |
| POST | `/api/v1/skilltree/allocate/{userId}` | `{nodeId}` → allocate |

`SkillTreeDto`: `nodes[] {id,name,description,branch,effectType,
targetResource,effectValue,x,y,isRoot,allocated,allocatable},
edges[]{from,to}, skillPoints, totalSkillPointsBought, nextPointCost`.

No respec/un-allocate endpoint in v1 (not requested, easy follow-up).

**Admin**: new `GET/PUT /api/v1/admin/skilltree/nodes[/{id}]` (full per-node
live editing — id/name/effect/value/position — same CRUD shape as today's
`/api/v1/admin/upgrades[/{id}]`), plus the 2 new `GameBalanceConfig` fields
added to the existing balance-config PUT.

## Frontend architecture

- **Rendering**: absolutely-positioned divs (not SVG) for nodes, reusing
  `pixel.css` primitives directly — plus a single absolutely-positioned
  `<svg>` overlay *inside* the same pan/zoom container containing only
  `<line>` elements for edges (colored by allocated/locked state). Nodes stay
  HTML divs on top so they get full pixel-art styling + `PixelInfoPopover`
  tooltips without SVG `foreignObject` hacks.
- **Pan/zoom**: port `FarmGridView.vue`'s camera pattern (`panX/panY/zoom/
  clampPan()/onWheel()/panStart/panMove/panEnd`, ~line 393-500) into the new
  component's own local refs — independent camera, no need for
  `useCameraControls()`'s WASD/gamepad support since this is a modal
  overlay. No new dependency (d3/vis-network) — ~18 fixed-position nodes is
  exactly what hand-rolled pan/zoom handles well.
- **Node states**: locked (dim/desaturated, matches `.building-idle`'s
  `filter: saturate(0.5) brightness(0.85)`), allocatable (bright/gold
  border), allocated (filled/green, matches `.hud-networth`'s active-state
  language) — driven entirely by the backend's per-node flags.
- **Tooltips**: `PixelInfoPopover.vue`, same usage as today's
  `UpgradeShopView.vue` (`:rows`, `:title`).
- **Purchase-point UI**: fixed HUD element (doesn't pan with the tree, same
  treatment as `FarmGridView.vue`'s `.hud`), shows current Skill Points, next
  point cost, buy button (`.px-btn-accent`).
- **Loading/scroll**: reuse `PixelScrollBox.vue` for any scrollable admin
  table, `LoadingIndicator.vue` for the initial tree-fetch loading state —
  do not reinvent either, both already exist and match the pixel-art system.

**New files**:
```
frontend/src/components/SkillTreeDialog.vue   — replaces UpgradeDialog.vue (modal shell)
frontend/src/components/SkillTreeView.vue     — replaces UpgradeShopView.vue (canvas + nodes + edges + purchase HUD)
```
(Keep nodes inline in `SkillTreeView.vue` rather than splitting a
`SkillNode.vue` — only ~18 nodes, no reuse elsewhere, extra file adds
indirection without payoff.)

**`FarmGridView.vue` wiring** (mirrors current Upgrade wiring exactly):
HUD button `dialog='upgrades'` → `dialog='skilltree'` (label key
`farmGridView.skillTreeLabel`), `<UpgradeDialog>` → `<SkillTreeDialog>`,
import swap, mobile-nav `navShop` repoint, `rowUpgrades` net-worth row →
`rowSkillTree` sourced from `playerStore.nwSkillTreeValue`, and
`boostHarvestLevel()`/`localHarvestTick()` become resource-aware per the Key
Design Decision above.

**`stores/player.js`**: replace `upgrades`/`nwUpgrades`/`loadUpgrades()` with
`skillTree = ref({nodes,edges,skillPoints,totalSkillPointsBought,
nextPointCost})`, `nwSkillTreeValue` computed from
`totalSkillPointCookiesSpent` (server-authoritative value, mirrored
client-side same as before), `loadSkillTree()` called from `init()`.

**`services/api.js`**: remove `getUpgrades/buyUpgrade/getAdminUpgrades/
updateAdminUpgrade`, add `getSkillTree/buySkillPoint/allocateSkillNode/
getAdminSkillNodes/updateAdminSkillNode`.

**`NetWorthDialog.vue`**: `upgradesLabel`/`nw.upgradeValue` (lines ~37-42,
122) → `skillTreeLabel`/`nw.skillTreeValue`.

**`PlayerProfileView.vue`** (verified directly, corrects an earlier
mis-report — this file DOES reference upgrades extensively): line 42-45
(`upgradesTitle` label + `activeUpgrades` chip list + `noUpgrades` empty
state), line 83 (`activeUpgrades` computed off `data.value?.upgrades`), line
88 (`statUpgradeValue` off `data.value.upgradeValue`). Repoint all three to
the new allocated-skill-node list + `skillTreeValue` — chips become
"allocated node name" instead of "upgrade name · level N" (binary allocation
has no level to show).

**`AdminDialog.vue`**: replace the Upgrades admin section (table over
`getAdminUpgrades()`/`updateAdminUpgrade()`) with an analogous Skill Tree
section (table over nodes: id/name/effectType/targetResource/effectValue/x/y,
save-per-row) + the 2 new balance-config fields in the existing config form.

**i18n**: remove `upgradeShopView.json`/`upgradeDialog.json` (de+en) plus
upgrade-related keys inside `farmGridView.json`, `netWorthDialog.json`,
`playerProfileView.json`, `adminDialog.json`; add
`skillTreeDialog.json`/`skillTreeView.json` (de+en) plus new keys in those 4
existing files. Follow the exact convention in `CLAUDE.md` "Lokalisierung
(i18n)" — one JSON pair per component, `useI18n()`/`t()`.

## V1 node list — ~18 nodes, 4 branches

Calibration anchors: old harvest boost was `+0.5` flat/level, bake boost
`+0.10`/level, cost growth `1.15^n`. These are placeholder-but-shippable
numbers, not final balance (matches the project's existing "balancing is a
separate later pass" stance, see `docs/ROADMAP.md` §4).

**ROOT** (`root`, isRoot, no effect, x=0,y=0)

**Branch MILK** (resource-specific, `targetResource="MILK"`):
1. `milk_1` "Bessere Melkkannen" — HARVEST_YIELD +0.15 — → root
2. `milk_2` "Sanftere Hand" — HARVEST_YIELD +0.15 — → milk_1
3. `milk_3` "Weidewissen" — HARVEST_YIELD +0.20 — → milk_2
4. `milk_4` "Meister-Melker" — HARVEST_YIELD +0.30 — → milk_3 (keystone)
5. `milk_5` "Zweite Kanne" — HARVEST_YIELD +0.20 — → milk_2 (fork)

**Branch BAKING** (`targetResource=null`, not resource-scoped):
6. `bake_1` "Warmer Ofen" — BAKE_OUTPUT +0.05 — → root
7. `bake_2` "Gleichmäßige Hitze" — BAKE_OUTPUT +0.05 — → bake_1
8. `bake_3` "Süßes Händchen" — BAKE_OUTPUT +0.08 — → bake_2
9. `bake_4` "Meisterbäcker" — BAKE_OUTPUT +0.12 — → bake_3 (keystone)
10. `bake_5` "Geheimrezept" — BAKE_OUTPUT +0.10 — → bake_2 (fork)

**Branch MARKET** (`targetResource=null`):
11. `market_1` "Verhandlungsgeschick" — MARKET_FEE_REDUCTION +0.01 — → root
12. `market_2` "Guter Ruf" — MARKET_FEE_REDUCTION +0.01 — → market_1
13. `market_3` "Marktkenner" — MARKET_FEE_REDUCTION +0.015 — → market_2
14. `market_4` "Händlerlizenz" — MARKET_FEE_REDUCTION +0.02 — → market_3 (keystone)

**Branch CORE** (generalist, cheap early picks, `targetResource=null` where
harvest-related so it applies to any resource — deliberately different from
the MILK branch to show both modes working):
15. `core_1` "Fleißige Hände" — HARVEST_YIELD +0.10 (global) — → root
16. `core_2` "Ausdauer" — BAKE_OUTPUT +0.04 — → core_1
17. `core_3` "Sparsamkeit" — MARKET_FEE_REDUCTION +0.01 — → core_1
18. `core_4` "Alleskönner" — HARVEST_YIELD +0.15 (global) — → core_2 **and**
    → core_3 (converging fork, 2 inbound edges — exercises multi-parent
    connectivity in the algorithm)

Layout: root at (0,0), four branches radiate ~90° apart (N/E/S/W), 2-3 tiers
each, keystones at branch tips. Exact pixel coordinates chosen during
implementation to fit the default zoom level comfortably.

## Removal checklist

**Backend — delete**: `entity/UpgradeEntity.java`,
`entity/PlayerUpgradeEntity.java`, `enums/UpgradeType.java`,
`repository/UpgradeRepository.java`, `repository/PlayerUpgradeRepository.java`,
`dto/BuyUpgradeRequestDto.java`, `dto/UpgradeWithStatusDto.java`,
`service/UpgradeService.java`, `controller/UpgradeController.java`.

**Backend — edit**: `UserService.java` (rewire, remove
`PlayerUpgradeRepository` dep), `BakeService.java` (rewire, remove dep),
`BuildingService.java` (extend fee method), `NetWorthService.java` (remove
`upgradeValue` computation lines 61-62 + `@Lazy UpgradeService` embed in
`getProfile()`, add `skillTreeValue` from `totalSkillPointCookiesSpent`),
`PrestigeService.java` (line 85: swap `playerUpgradeRepository.deleteAll()`
for `playerSkillNodeRepository.deleteAll()` **and** reset
`user.setTotalSkillPointsBought(0)` per the confirmed decision),
`SeasonService.java` (same swap, global reset), `AdminController.java`
(reset-player swap), `AdminConfigController.java` (remove `UpgradeRepository`
dep + old endpoints, add skill-tree admin endpoints + 2 new balance fields),
`GameBalanceConfig.java` (add fields), DTOs carrying `upgradeValue`
(`LeaderboardEntryDto`, `PlayerProfileDto`, `NetWorthHistoryDto`,
`NetWorthHistoryEntity`) renamed to `skillTreeValue`.

**Backend — DB**: `upgrades`/`player_upgrades` tables simply become
orphaned once the entities are deleted (Hibernate stops managing them) — no
migration needed, DB is disposable (confirmed policy), optionally
`DROP TABLE upgrades, player_upgrades;` once for cleanliness.

**Frontend — delete**: `components/UpgradeShopView.vue`,
`components/UpgradeDialog.vue`, `i18n/locales/{de,en}/upgradeShopView.json`,
`i18n/locales/{de,en}/upgradeDialog.json`.

**Frontend — edit**: `services/api.js`, `stores/player.js`,
`views/FarmGridView.vue`, `components/NetWorthDialog.vue`,
`components/PlayerProfileView.vue`, `components/AdminDialog.vue`, plus i18n
keys in `farmGridView.json`/`netWorthDialog.json`/`playerProfileView.json`/
`adminDialog.json` (de+en each) — all per the sections above.

**Docs**: `docs/cookie-game-design.md` §9 (rewrite as "Skill Tree
(Cookie-Sink)"), §10 (net-worth formula), §11 (prestige/season reset lists —
note it now also resets `totalSkillPointsBought`), §12, §13 (datamodel), §14
(API table). `docs/ROADMAP.md` — remove stale upgrade references, add the
deferred-anti-cheat note (§ "Skill point allocation" per above) and a
follow-up item for Prestige's 3-bonus-point integration.

## Verification plan

**Backend** (curl-level, matches how this repo verifies other services —
no test framework in place for this area):
1. `GET /api/v1/skilltree?userId=DEV_PLAYER_001` — 18 nodes + root, root
   `allocated:true`, root-adjacent nodes `allocatable:true`, rest `false`.
2. `POST .../buy-point/DEV_PLAYER_001` repeatedly — cost follows
   `50×1.15^n`, cookies deducted, 400 when insufficient.
3. Allocate `milk_1` (root-adjacent) → succeeds. Allocate `milk_3` (not yet
   adjacent) → rejected. Allocate `milk_2` then `milk_3` → succeeds. Proves
   the connectivity algorithm end-to-end, including the `core_4` 2-parent
   fork case.
4. Harvest MILK before/after allocating a MILK-targeted node vs. a
   different-resource node → confirm the bonus only applies to milk.
5. Bake claim before/after a `BAKE_OUTPUT` node.
6. Market sell before/after a `MARKET_FEE_REDUCTION` node.
7. `PrestigeService`/`SeasonService`/`AdminController.resetPlayer()` →
   confirm `player_skill_nodes` cleared and `totalSkillPointsBought` reset
   to 0 (per confirmed decision).
8. New admin endpoints — edit a node live, confirm it round-trips and the
   in-memory node cache picks up the change.

**Frontend**: use the `run` skill to launch the dev server, visually confirm
tree renders/pans/zooms, node states (locked/allocatable/allocated) render
correctly, tooltip shows effect+cost, buying+allocating updates HUD live.
`cd frontend && npm run check:palette` (mandatory gate) after all new `.vue`
files are styled. Toggle DE/EN in Settings to catch missed locale keys.

## Critical files

- `backend/.../service/UpgradeService.java` — pattern source (seeding,
  cost-curve, `@PostConstruct` cleanup convention) for new `SkillTreeService`
- `backend/.../service/UserService.java:178-219` — harvest call site,
  already resource-aware via the `resource` parameter
- `backend/.../service/BakeService.java:~165-171` — bake call site
- `backend/.../service/BuildingService.java:201-211` — existing
  `getEffectiveSellFeeRate` overloads, extend for market-fee integration
- `backend/.../config/GameBalanceConfig.java:28,34,60-67` — live-tunable
  config field pattern to follow
- `backend/.../controller/AdminConfigController.java` — pattern source for
  new admin skill-tree endpoints
- `frontend/src/views/FarmGridView.vue:393-500` — camera pan/zoom to port;
  also the dialog-wiring and harvest-prediction call sites to rewire
- `frontend/src/stores/player.js` — state to replace
- `frontend/src/components/UpgradeDialog.vue` /
  `frontend/src/components/UpgradeShopView.vue` — structural template for
  the new dialog/view, including `PixelInfoPopover` usage
- `frontend/src/components/PlayerProfileView.vue:42-45,83,88` — three
  upgrade references to repoint (verified directly, not just from research)
- `frontend/src/components/pixel/PixelScrollBox.vue` /
  `LoadingIndicator.vue` — reuse, don't reinvent
