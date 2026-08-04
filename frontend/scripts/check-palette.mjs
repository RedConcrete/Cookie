// Fails if any .vue/.css/.js file under src/ uses a hex color outside the
// Fruitpunch24 palette (src/assets/colorpalate/fruitpunch24.hex).
// Run: node scripts/check-palette.js
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..')
const SRC = join(ROOT, 'src')
const PALETTE_FILE = join(SRC, 'assets/colorpalate/fruitpunch24.hex')

const PALETTE = new Set(
  readFileSync(PALETTE_FILE, 'utf8')
    .split(/\r?\n/)
    .map((l) => l.trim().toLowerCase())
    .filter(Boolean)
)

const EXTS = new Set(['.vue', '.css', '.js'])
const SKIP_DIRS = new Set(['_unused', 'node_modules'])
const HEX_RE = /#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})\b/g

function walk(dir, files = []) {
  for (const entry of readdirSync(dir)) {
    if (SKIP_DIRS.has(entry)) continue
    const full = join(dir, entry)
    const st = statSync(full)
    if (st.isDirectory()) walk(full, files)
    else if (EXTS.has(extname(entry))) files.push(full)
  }
  return files
}

let violations = 0
for (const file of walk(SRC)) {
  const text = readFileSync(file, 'utf8')
  const lines = text.split(/\r?\n/)
  lines.forEach((line, i) => {
    for (const m of line.matchAll(HEX_RE)) {
      let hex = m[1].toLowerCase()
      if (hex.length === 3) hex = [...hex].map((c) => c + c).join('')
      if (!PALETTE.has(hex)) {
        violations++
        console.log(`${file.replace(ROOT + '/', '')}:${i + 1}  #${hex}  not in fruitpunch24 palette`)
      }
    }
  })
}

if (violations > 0) {
  console.log(`\n${violations} color(s) outside the Fruitpunch24 palette.`)
  process.exit(1)
} else {
  console.log('All colors are within the Fruitpunch24 palette.')
}
