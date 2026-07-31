import { reactive, watch } from 'vue'

const DEFAULTS = [
  { id: 'center',    action: 'Hof zentrieren',      key: 'LEER' },
  { id: 'harvest',   action: 'Ernten (halten)',     key: 'E' },
  { id: 'bake',      action: 'Backofen öffnen',     key: 'B' },
  { id: 'market',    action: 'Markt öffnen',        key: 'M' },
  { id: 'upgrades',  action: 'Upgrade-Shop',        key: 'U' },
  { id: 'leaderboard', action: 'Rangliste',         key: 'L' },
  { id: 'recipes',   action: 'Rezeptbuch',          key: 'R' },
  { id: 'sellall',   action: 'Alles verkaufen',     key: 'SHIFT+V' },
  { id: 'move',      action: 'Gebäude verschieben', key: 'HALTEN' },
  { id: 'zoomreset', action: 'Zoom zurücksetzen',   key: '0' },
  { id: 'settings',  action: 'Einstellungen',       key: 'ESC' },
  { id: 'badges',    action: 'Orden-Vitrine',       key: 'O' },
]

function load() {
  try {
    const raw = localStorage.getItem('cookieHotkeys')
    if (!raw) return DEFAULTS.map(d => ({ ...d }))
    const saved = JSON.parse(raw)
    return DEFAULTS.map(d => ({ ...d, key: saved[d.id] ?? d.key }))
  } catch {
    return DEFAULTS.map(d => ({ ...d }))
  }
}

const state = reactive({ bindings: load(), editingId: null })

watch(() => state.bindings, (bindings) => {
  const flat = Object.fromEntries(bindings.map(b => [b.id, b.key]))
  localStorage.setItem('cookieHotkeys', JSON.stringify(flat))
}, { deep: true })

export function keyLabelFromEvent(e) {
  const parts = []
  if (e.shiftKey) parts.push('SHIFT')
  if (e.ctrlKey)  parts.push('CTRL')
  if (e.altKey)   parts.push('ALT')
  let k = e.key === ' ' ? 'LEER' : e.key.toUpperCase()
  if (!['SHIFT', 'CONTROL', 'ALT'].includes(k)) parts.push(k)
  return parts.join('+')
}

export function useHotkeys() {
  function startEditing(id) { state.editingId = id }

  function assign(id, e) {
    e.preventDefault()
    const label = keyLabelFromEvent(e)
    const b = state.bindings.find(x => x.id === id)
    if (b) b.key = label
    state.editingId = null
  }

  function reset() { state.bindings = DEFAULTS.map(d => ({ ...d })) }

  function isDuplicate(id) {
    const b = state.bindings.find(x => x.id === id)
    if (!b) return false
    return state.bindings.some(x => x.id !== id && x.key === b.key)
  }

  return {
    state,
    startEditing, assign, reset, isDuplicate,
  }
}
