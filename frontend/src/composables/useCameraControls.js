import { reactive, ref, watch } from 'vue'

const DIRECTIONS = ['up', 'down', 'left', 'right']
const DEFAULT_KEYS = { up: 'w', down: 's', left: 'a', right: 'd' }
const DEFAULT_SPEED = 1200 // px/sec, screen space

function loadKeys() {
  try {
    const raw = JSON.parse(localStorage.getItem('cookieCameraKeys'))
    if (raw && typeof raw === 'object') return { ...DEFAULT_KEYS, ...raw }
  } catch {}
  return { ...DEFAULT_KEYS }
}

function loadSpeed() {
  const v = parseFloat(localStorage.getItem('cookieCameraSpeed'))
  return isNaN(v) ? DEFAULT_SPEED : v
}

// ── Singleton state (shared between FarmGridView and SettingsDialog) ──
const cameraKeys  = reactive(loadKeys())
const cameraSpeed = ref(loadSpeed())

watch(cameraKeys, v => localStorage.setItem('cookieCameraKeys', JSON.stringify(v)), { deep: true })
watch(cameraSpeed, v => localStorage.setItem('cookieCameraSpeed', v))

function keyLabel(key) {
  if (!key) return '—'
  if (key === ' ') return 'LEER'
  if (key.length === 1) return key.toUpperCase()
  if (key.startsWith('arrow')) return { arrowup: '↑', arrowdown: '↓', arrowleft: '←', arrowright: '→' }[key] ?? key
  return key.toUpperCase()
}

// Rebinds `direction` to `newKey`; if another direction already used that
// key, the two directions swap keys instead of ending up with duplicates.
function rebind(direction, newKey) {
  const key = newKey.toLowerCase()
  const other = DIRECTIONS.find(d => d !== direction && cameraKeys[d] === key)
  const prev = cameraKeys[direction]
  cameraKeys[direction] = key
  if (other) cameraKeys[other] = prev
}

export function useCameraControls() {
  return { cameraKeys, cameraSpeed, keyLabel, rebind, DEFAULT_SPEED }
}
