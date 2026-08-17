import { reactive, ref, watch } from 'vue'

const DIRECTIONS = ['up', 'down', 'left', 'right']
const DEFAULT_KEYS = { up: 'w', down: 's', left: 'a', right: 'd' }
const DEFAULT_SPEED = 1200 // px/sec, screen space
// Multiplicative rate per second for gamepad D-pad zoom (FarmGridView.vue +
// SkillTreeView.vue), same feel as the mouse wheel's 1.1/0.9 step.
const DEFAULT_ZOOM_SPEED = 0.8

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

function loadZoomSpeed() {
  const v = parseFloat(localStorage.getItem('cookieCameraZoomSpeed'))
  return isNaN(v) ? DEFAULT_ZOOM_SPEED : v
}

// ── Singleton state (shared between FarmGridView, SkillTreeView and SettingsDialog) ──
const cameraKeys  = reactive(loadKeys())
const cameraSpeed = ref(loadSpeed())
const zoomSpeed    = ref(loadZoomSpeed())

watch(cameraKeys, v => localStorage.setItem('cookieCameraKeys', JSON.stringify(v)), { deep: true })
watch(cameraSpeed, v => localStorage.setItem('cookieCameraSpeed', v))
watch(zoomSpeed, v => localStorage.setItem('cookieCameraZoomSpeed', v))

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
  return { cameraKeys, cameraSpeed, zoomSpeed, keyLabel, rebind, DEFAULT_SPEED }
}
