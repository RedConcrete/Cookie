import { ref } from 'vue'

// Tracks which input device the player last actually used, so hotkey badges
// (ShortcutSlot.vue) can show the matching visual on the fly -- keyboard/mouse
// text vs. controller-button icon (ControllerButtonIcon.vue). Singleton
// pattern (start/stop from App.vue) analog useIdleTimeout.js, since this needs
// to run app-wide regardless of which dialog/view is currently mounted.
const GAMEPAD_DEADZONE = 0.5 // higher than the camera-pan deadzone (0.2) -- this only
// needs to catch a deliberate push, not react to idle stick drift/noise.

const activeMethod = ref('keyboard') // 'keyboard' | 'gamepad'
const controllerFamily = ref('generic') // 'xbox' | 'playstation' | 'generic'
// Increments on every real connect event -- GamepadToast.vue watches this
// (not controllerFamily directly) so reconnecting the same controller
// re-triggers the toast instead of silently no-op'ing on an unchanged value.
const connectNonce = ref(0)

function detectFamily(padId) {
  if (!padId) return 'generic'
  const s = padId.toLowerCase()
  if (s.includes('054c') || s.includes('sony') || s.includes('playstation') || s.includes('dualshock') || s.includes('dualsense')) return 'playstation'
  // Steam Deck reports itself as an XInput pad through Steam Input, so 'xinput'
  // covers it too without any Deck-specific detection code.
  if (s.includes('045e') || s.includes('xbox') || s.includes('xinput')) return 'xbox'
  return 'generic'
}

function markKeyboard() {
  activeMethod.value = 'keyboard'
}

function padHasInput(pad) {
  if (pad.buttons.some(b => b.pressed)) return true
  return pad.axes.some(a => Math.abs(a) > GAMEPAD_DEADZONE)
}

let pollFrame = null
function pollGamepads() {
  const pads = navigator.getGamepads?.() ?? []
  for (const pad of pads) {
    if (!pad) continue
    if (padHasInput(pad)) {
      activeMethod.value = 'gamepad'
      controllerFamily.value = detectFamily(pad.id)
      break
    }
  }
  pollFrame = requestAnimationFrame(pollGamepads)
}

function onGamepadConnected(e) {
  controllerFamily.value = detectFamily(e.gamepad.id)
  connectNonce.value++
  if (pollFrame == null) pollFrame = requestAnimationFrame(pollGamepads)
}
function onGamepadDisconnected() {
  // Only stop polling once no pad is left connected at all.
  const anyLeft = (navigator.getGamepads?.() ?? []).some(p => p)
  if (!anyLeft && pollFrame != null) {
    cancelAnimationFrame(pollFrame)
    pollFrame = null
  }
}

let started = false
function start() {
  if (started) return
  started = true
  window.addEventListener('keydown', markKeyboard)
  window.addEventListener('mousedown', markKeyboard)
  window.addEventListener('mousemove', markKeyboard)
  window.addEventListener('wheel', markKeyboard)
  window.addEventListener('gamepadconnected', onGamepadConnected)
  window.addEventListener('gamepaddisconnected', onGamepadDisconnected)
  // A gamepad already connected before start() (e.g. plugged in on a previous
  // page) doesn't refire 'gamepadconnected', so pick it up here too.
  if ((navigator.getGamepads?.() ?? []).some(p => p) && pollFrame == null) {
    pollFrame = requestAnimationFrame(pollGamepads)
  }
}
function stop() {
  started = false
  window.removeEventListener('keydown', markKeyboard)
  window.removeEventListener('mousedown', markKeyboard)
  window.removeEventListener('mousemove', markKeyboard)
  window.removeEventListener('wheel', markKeyboard)
  window.removeEventListener('gamepadconnected', onGamepadConnected)
  window.removeEventListener('gamepaddisconnected', onGamepadDisconnected)
  if (pollFrame != null) { cancelAnimationFrame(pollFrame); pollFrame = null }
  activeMethod.value = 'keyboard'
}

export function useInputMethod() {
  return { activeMethod, controllerFamily, connectNonce, start, stop }
}
