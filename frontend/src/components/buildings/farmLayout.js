export const CANVAS = { w: 1280, h: 800 }

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

export const HGT = {
  pond: 128, ofen: 92, rathaus: 126, markt: 124, lager: 128,
  hof: 158, huhn: 128, butter: 124, kakao: 138, kuh: 138,
}

export const SCENE_H = {
  pond: 120, ofen: 84, rathaus: 118, markt: 116, lager: 120,
  hof: 150, huhn: 120, butter: 116, kakao: 130, kuh: 130,
}

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
