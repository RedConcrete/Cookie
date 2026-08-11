import { ref } from 'vue'
import { getConfig, heartbeatPlayer } from '../services/api.js'

// AFK-Erkennung waehrend des Spielens: solange echte Maus-/Tastatur-Aktivitaet
// (Hover-Ernten loest bereits 'mousemove' aus, zaehlt also automatisch mit) innerhalb der
// Schwelle liegt, wird alle HEARTBEAT_INTERVAL_MS ein Heartbeat an den Server geschickt (siehe
// UserService#recordHeartbeat) -- der haelt WageScheduler davon ab, diesen Spieler als AFK zu
// behandeln und Lohn/Zinsen zu ueberspringen. Bleibt die Aktivitaet aus, wird isAfk gesetzt und
// App.vue schickt den Spieler zurueck ins Hauptmenue.
const HEARTBEAT_INTERVAL_MS = 20_000
const DEFAULT_TIMEOUT_MS = 10 * 60_000

// ── Singleton state ──────────────────────────────────────
const isAfk = ref(false)
let steamId = null
let timeoutMs = DEFAULT_TIMEOUT_MS
let lastActivityAt = Date.now()
let heartbeatTimer = null

function markActive() {
  lastActivityAt = Date.now()
}

function tick() {
  if (Date.now() - lastActivityAt < timeoutMs) {
    heartbeatPlayer(steamId).catch(() => {})
  } else {
    isAfk.value = true
    stop()
  }
}

function start(id) {
  if (heartbeatTimer) return // laeuft schon
  steamId = id
  isAfk.value = false
  lastActivityAt = Date.now()

  getConfig().then(cfg => {
    if (cfg.afkTimeoutMinutes) timeoutMs = cfg.afkTimeoutMinutes * 60_000
  }).catch(() => {})

  window.addEventListener('mousemove', markActive)
  window.addEventListener('mousedown', markActive)
  window.addEventListener('keydown', markActive)
  window.addEventListener('wheel', markActive)

  heartbeatPlayer(steamId).catch(() => {}) // sofort, nicht erst nach dem ersten Intervall-Tick
  heartbeatTimer = setInterval(tick, HEARTBEAT_INTERVAL_MS)
}

function stop() {
  clearInterval(heartbeatTimer)
  heartbeatTimer = null
  window.removeEventListener('mousemove', markActive)
  window.removeEventListener('mousedown', markActive)
  window.removeEventListener('keydown', markActive)
  window.removeEventListener('wheel', markActive)
}

export function useIdleTimeout() {
  return { isAfk, start, stop }
}
