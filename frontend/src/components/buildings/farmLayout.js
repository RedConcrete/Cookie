export const CANVAS = { w: 1280, h: 800 }

// Full pannable world — comfortably fits all buildings (x:30-1260, y:120-642)
// with room to grow. Camera panning is clamped to this rect.
export const WORLD = { w: 2000, h: 1400 }

// One ground tile as displayed (matches the 5x-scaled 20px native tile in
// assets/tiles/grass.png) — building placement snaps to this grid.
export const TILE_SIZE = 100

// Snaps an offset so the building's *absolute* position (base + offset) lands
// on a global tile boundary, regardless of where base.x/base.y themselves sit.
export function snapOffset(base, offset, tileSize = TILE_SIZE) {
  return {
    x: Math.round((base.x + offset.x) / tileSize) * tileSize - base.x,
    y: Math.round((base.y + offset.y) / tileSize) * tileSize - base.y,
  }
}

export const BASE = {
  pond:    { x: 40,  y: 126, w: 230 },
  ofen:    { x: 300, y: 150, w: 170 },
  rathaus: { x: 700, y: 120, w: 240 },
  markt:   { x: 960, y: 126, w: 250 },
  lager:   { x: 980, y: 262, w: 230 },
  hof:     { x: 30,  y: 484, w: 290 },
  huhn:    { x: 336, y: 500, w: 210 },
  butter:  { x: 40,  y: 262, w: 210 },
  kakao:   { x: 764, y: 480, w: 230 },
  kuh:     { x: 1010,y: 484, w: 250 },
}

export const SCENE_H = {
  pond: 120, ofen: 84, rathaus: 118, markt: 116, lager: 120,
  hof: 150, huhn: 120, butter: 116, kakao: 130, kuh: 130,
}

// BuildingFrame's .bf-overlay label bar (icon + title + worker count) adds this much on
// top of the scene itself. Used to be a hardcoded +8 per building here, drifted from the
// real rendered height (measured via getBoundingClientRect: consistently +20, not +8) --
// caused false drop-collision rejections (dropOk below) against empty-looking tiles next
// to a building, since its true footprint was taller than this table claimed. Derived from
// SCENE_H now instead of a second hand-maintained table so the two can't drift apart again.
const OVERLAY_BAR_HEIGHT = 20
export const HGT = Object.fromEntries(
  Object.entries(SCENE_H).map(([id, h]) => [id, h + OVERLAY_BAR_HEIGHT])
)

function rectsOverlap(a, b) {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
}

// Returns false only when dragged building overlaps another building.
// No canvas-boundary or road restrictions — buildings can be placed anywhere on the grass.
export function dropOk(id, offset, otherOffsets = {}) {
  const b = BASE[id]
  const h = HGT[id] || 120
  const a = { x: b.x + offset.x, y: b.y + offset.y, w: b.w, h }

  for (const [otherId, otherOff] of Object.entries(otherOffsets)) {
    if (otherId === id) continue
    const ob = BASE[otherId]
    if (!ob) continue
    const oh = HGT[otherId] || 120
    const o = { x: ob.x + otherOff.x, y: ob.y + otherOff.y, w: ob.w, h: oh }
    if (rectsOverlap(a, o)) return false
  }
  return true
}
