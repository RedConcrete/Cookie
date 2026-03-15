const WS_URL = 'ws://localhost:9876/ws-market'
const RECONNECT_DELAY_MS = 3000

let socket = null
let messageHandler = null
let shouldReconnect = true

export function connectMarketWebSocket(onMessage) {
  messageHandler = onMessage
  shouldReconnect = true
  connect()
}

export function disconnectMarketWebSocket() {
  shouldReconnect = false
  if (socket) socket.close()
  socket = null
}

function connect() {
  socket = new WebSocket(WS_URL)

  socket.onopen = () => {
    console.log('[WS] Connected to market')
  }

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (messageHandler) messageHandler(data)
    } catch (e) {
      console.error('[WS] Failed to parse message', e)
    }
  }

  socket.onclose = () => {
    console.warn('[WS] Disconnected from market')
    if (shouldReconnect) {
      setTimeout(connect, RECONNECT_DELAY_MS)
    }
  }

  socket.onerror = (err) => {
    console.error('[WS] Error', err)
  }
}
