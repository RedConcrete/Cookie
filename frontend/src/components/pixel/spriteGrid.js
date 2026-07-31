// Turns an ASCII pixel-grid (rows of chars, '.' = empty) into a flat list of
// 1x1 SVG cell rects: a dark outline ring around the silhouette (drawn first),
// then the actual palette-colored fill cells on top. Mirrors the technique used
// for the icon assets in iconData.js, scaled up for animated creature sprites.
export const SPRITE_DARK = '#1a120b'

// Repeats '.' n times — used to build grid rows without manually counting dots.
export const dots = (n) => '.'.repeat(n)

export function buildSpriteCells(grid, palette) {
  const rows = grid.length
  const cols = grid[0].length
  const filled = (r, c) => r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] !== '.'

  const outline = []
  const seen = new Set()
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (!filled(r, c)) continue
      for (const [nr, nc] of [[r - 1, c], [r + 1, c], [r, c - 1], [r, c + 1]]) {
        if (filled(nr, nc)) continue
        const key = `${nr},${nc}`
        if (seen.has(key)) continue
        seen.add(key)
        outline.push({ x: nc, y: nr, fill: SPRITE_DARK })
      }
    }
  }

  const fills = []
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const ch = grid[r][c]
      if (ch === '.') continue
      fills.push({ x: c, y: r, fill: palette[ch] || SPRITE_DARK })
    }
  }

  return [...outline, ...fills]
}
