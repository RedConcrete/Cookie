import { reactive, ref, watch } from 'vue'

// Discrete-trigger keyboard/gamepad-button shortcuts for one-off UI actions
// (opening a dialog etc.) -- as opposed to the continuous WASD camera pan
// in useCameraControls.js, which needs held-key state instead of a single
// press. Mirrors that composable's storage/rebind pattern.
const ACTIONS = ['networth']
const DEFAULT_KEYS = { networth: 'n' }
const DEFAULT_GAMEPAD_BUTTONS = { networth: null } // null = unbound

// Standard Gamepad API button order (Xbox-style layout; most controllers,
// including PlayStation/Steam Deck, map to this via the browser's standard mapping).
const GAMEPAD_BUTTON_LABELS = [
  'A', 'B', 'X', 'Y', 'LB', 'RB', 'LT', 'RT', 'BACK', 'START', 'LS', 'RS',
  '↑', '↓', '←', '→', 'HOME'
]

function loadKeys() {
  try {
    const raw = JSON.parse(localStorage.getItem('cookieActionKeys'))
    if (raw && typeof raw === 'object') return { ...DEFAULT_KEYS, ...raw }
  } catch {}
  return { ...DEFAULT_KEYS }
}

function loadGamepadButtons() {
  try {
    const raw = JSON.parse(localStorage.getItem('cookieActionGamepadButtons'))
    if (raw && typeof raw === 'object') return { ...DEFAULT_GAMEPAD_BUTTONS, ...raw }
  } catch {}
  return { ...DEFAULT_GAMEPAD_BUTTONS }
}

function loadEnabled() {
  const raw = localStorage.getItem('cookieActionHotkeysEnabled')
  return raw === null ? true : raw === 'true'
}

// ── Singleton state (shared between FarmGridView and SettingsDialog) ──
const actionKeys = reactive(loadKeys())
const actionGamepadButtons = reactive(loadGamepadButtons())
const enabled = ref(loadEnabled())

watch(actionKeys, v => localStorage.setItem('cookieActionKeys', JSON.stringify(v)), { deep: true })
watch(actionGamepadButtons, v => localStorage.setItem('cookieActionGamepadButtons', JSON.stringify(v)), { deep: true })
watch(enabled, v => localStorage.setItem('cookieActionHotkeysEnabled', v))

function keyLabel(key) {
  if (!key) return '—'
  if (key === ' ') return 'LEER'
  if (key.length === 1) return key.toUpperCase()
  if (key.startsWith('arrow')) return { arrowup: '↑', arrowdown: '↓', arrowleft: '←', arrowright: '→' }[key] ?? key
  return key.toUpperCase()
}

function gamepadButtonLabel(index) {
  if (index === null || index === undefined) return '—'
  return GAMEPAD_BUTTON_LABELS[index] ?? `BTN ${index}`
}

// Rebinds `action` to `newKey`; if another action already used that key,
// the two actions swap keys instead of ending up with duplicates.
function rebind(action, newKey) {
  const key = newKey.toLowerCase()
  const other = ACTIONS.find(a => a !== action && actionKeys[a] === key)
  const prev = actionKeys[action]
  actionKeys[action] = key
  if (other) actionKeys[other] = prev
}

function rebindGamepad(action, buttonIndex) {
  const other = ACTIONS.find(a => a !== action && actionGamepadButtons[a] === buttonIndex)
  const prev = actionGamepadButtons[action]
  actionGamepadButtons[action] = buttonIndex
  if (other) actionGamepadButtons[other] = prev
}

export function useActionHotkeys() {
  return { actionKeys, actionGamepadButtons, enabled, keyLabel, gamepadButtonLabel, rebind, rebindGamepad }
}
