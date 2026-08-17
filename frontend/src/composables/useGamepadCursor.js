import { ref, computed } from 'vue'

// Shared "where does a controller click land" state (singleton, like
// useInputMethod.js). Two modes:
// - 'fixed': crosshair pinned to the screen center (rendered by
//   GamepadCursor.vue via pure CSS, no JS position needed) -- whichever
//   pannable view is active (FarmGridView / SkillTreeView) moves its own
//   world under it via the left stick.
// - 'free': a real cursor the right stick moves around the screen, for
//   clicking UI that isn't reachable by panning something under a fixed
//   center point (dialog buttons, sliders, ...).
// R3 (see FarmGridView.vue) toggles between the two; closing a dialog
// always resets back to 'fixed'.
const CURSOR_DEADZONE = 0.2 // same feel as the camera-pan stick deadzone
const CURSOR_SPEED = 700 // px/sec at full stick deflection

const mode = ref('fixed') // 'fixed' | 'free'
const cursorX = ref(0)
const cursorY = ref(0)

const currentPoint = computed(() =>
  mode.value === 'fixed'
    ? { x: window.innerWidth / 2, y: window.innerHeight / 2 }
    : { x: cursorX.value, y: cursorY.value }
)

function toggle() {
  if (mode.value === 'fixed') {
    cursorX.value = window.innerWidth / 2
    cursorY.value = window.innerHeight / 2
    mode.value = 'free'
  } else {
    mode.value = 'fixed'
  }
}

function reset() {
  mode.value = 'fixed'
}

// Called once per frame by whichever view is already polling the gamepad
// (FarmGridView's camTick) -- moves the free cursor via the right stick.
// No-op while in 'fixed' mode.
function tick(pad, dt) {
  if (mode.value !== 'free' || !pad) return
  const ax = pad.axes[2] ?? 0
  const ay = pad.axes[3] ?? 0
  const mag = Math.hypot(ax, ay)
  if (mag < CURSOR_DEADZONE) return
  const scale = Math.min(1, (mag - CURSOR_DEADZONE) / (1 - CURSOR_DEADZONE)) / mag
  cursorX.value = Math.min(window.innerWidth, Math.max(0, cursorX.value + ax * scale * CURSOR_SPEED * dt))
  cursorY.value = Math.min(window.innerHeight, Math.max(0, cursorY.value + ay * scale * CURSOR_SPEED * dt))
}

export function useGamepadCursor() {
  return { mode, cursorX, cursorY, currentPoint, toggle, reset, tick }
}
