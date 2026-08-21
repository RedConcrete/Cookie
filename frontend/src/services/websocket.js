import { ref } from 'vue'

const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:9876/ws-market'
const RECONNECT_DELAY_MS = 3000

let socket = null
let messageHandler = null
let shouldReconnect = true

// Reaktiver Status fuers DevStatsPanel (siehe components/DevStatsPanel.vue) -- sonst
// keine Sichtbarkeit von aussen, ob der Markt-Socket gerade steht oder haengt.
export const wsConnected = ref(false)
export const wsLastMessageAt = ref(null)

export function connectMarketWebSocket(onMessage) {
  messageHandler = onMessage
  shouldReconnect = true
  connect()
}

export function disconnectMarketWebSocket() {
  shouldReconnect = false
  if (socket) socket.close()
  socket = null
  wsConnected.value = false
}

function connect() {
  socket = new WebSocket(WS_URL)

  socket.onopen = () => {
    console.log('[WS] Connected to market')
    wsConnected.value = true
  }

  socket.onmessage = (event) => {
    wsLastMessageAt.value = Date.now()
    try {
      const data = JSON.parse(event.data)
      if (messageHandler) messageHandler(data)
    } catch (e) {
      console.error('[WS] Failed to parse message', e)
    }
  }

  socket.onclose = () => {
    console.warn('[WS] Disconnected from market')
    wsConnected.value = false
    if (shouldReconnect) {
      setTimeout(connect, RECONNECT_DELAY_MS)
    }
  }

  socket.onerror = (err) => {
    console.error('[WS] Error', err)
  }
}
