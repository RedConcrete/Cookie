// Hof canvas is 1280×800. HUD bar occupies the top 76px (+4px border = 84px).
// Wegkreuz (road cross) stays clear: vertical road x 608-680, horizontal road y 392-464.
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

// Collision height per building (a little taller than the visible scene box).
export const HGT = {
  pond: 128, ofen: 92, rathaus: 126, markt: 124, lager: 128,
  hof: 158, huhn: 128, butter: 124, kakao: 138, kuh: 138,
}

// Visible scene box height (what's actually drawn).
export const SCENE_H = {
  pond: 120, ofen: 84, rathaus: 118, markt: 116, lager: 120,
  hof: 150, huhn: 120, butter: 116, kakao: 130, kuh: 130,
}

export const ROADS = [
  { x: 608, y: 120, w: 72, h: 680 },
  { x: 0,   y: 392, w: 1280, h: 72 },
]

export function dropOk(id, offset) {
  const b = BASE[id]
  const h = HGT[id] || 120
  const a = { x: b.x + offset.x, y: b.y + offset.y, w: b.w, h }
  if (a.x < 4 || a.y < 84 || a.x + a.w > CANVAS.w - 4 || a.y + a.h > CANVAS.h - 4) return false
  return !ROADS.some(r => a.x < r.x + r.w && r.x < a.x + a.w && a.y < r.y + r.h && r.y < a.y + a.h)
}
