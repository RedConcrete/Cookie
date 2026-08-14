// Duenner fetch-Wrapper um die bestehende REST-API, Stil analog zu
// frontend/scripts/balance-report.mjs. Jeder Call geht zuerst durch
// guardrail.assertDevMode() -- siehe dort fuer die Begruendung.
import { assertDevMode } from './guardrail.mjs'

export const BASE_URL = process.env.MCP_TARGET_BASE_URL || 'http://localhost:9876'
export const DEV_PLAYER_ID = process.env.MCP_DEV_PLAYER_ID || 'DEV_PLAYER_001'

async function guardedFetch(path, options = {}) {
  await assertDevMode(BASE_URL)
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  const text = await res.text()
  let body = null
  if (text) {
    try { body = JSON.parse(text) } catch { body = text }
  }
  if (!res.ok) {
    // GlobalExceptionHandler.java liefert {"error": "..."} fuer erwartete
    // Validierungsfehler (z.B. "Amount must be positive", "Markt gesperrt").
    const message = body && typeof body === 'object' && body.error ? body.error : `HTTP ${res.status}`
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  return body
}

// GameController.java:32-48 -- legt den Dev-Player beim ersten Aufruf an, falls er
// noch nicht existiert (keine explizite "create user"-Aktion in v1 noetig).
export async function ensurePlayerExists(userId = DEV_PLAYER_ID) {
  return guardedFetch(`/api/v1/game/init/${encodeURIComponent(userId)}?displayName=${encodeURIComponent(userId)}`)
}

// UserController.java:41-46 + BuildingController.java:36-39 + MarketController.java:22-25
// -- gebuendelter Snapshot, damit der Agent nicht raten muss.
export async function getGameState(userId = DEV_PLAYER_ID) {
  const [user, buildings, latestMarket] = await Promise.all([
    guardedFetch(`/api/v1/users/${encodeURIComponent(userId)}`),
    guardedFetch(`/api/v1/farm/buildings/${encodeURIComponent(userId)}`),
    guardedFetch(`/api/v1/market/get/1`),
  ])
  return { user, buildings, prices: latestMarket?.[0] ?? null }
}

// MarketController.java:37-41 -- action BUY|SELL, resource {name, amount}.
// Negative-Amount-Exploit-Fix (MarketService.java:242-244) laeuft serverseitig,
// hier keine eigene Vorab-Validierung -- der Fehlertext des Servers ist das
// gewuenschte Testergebnis fuer den Agent.
export async function marketTrade(action, resourceName, amount, userId = DEV_PLAYER_ID) {
  return guardedFetch('/api/v1/market', {
    method: 'POST',
    body: JSON.stringify({ userId, action, resource: { name: resourceName, amount } }),
  })
}

// GameController.java:59-67 -- Menge wird rein serverseitig aus vergangener Zeit
// berechnet (UserService.harvest, MAX_HARVEST_BATCH_MS=6s pro Call), amount im
// Request gibt es nicht.
export async function harvest(resourceName, userId = DEV_PLAYER_ID) {
  return guardedFetch(`/api/v1/game/harvest/${encodeURIComponent(userId)}`, {
    method: 'POST',
    body: JSON.stringify({ resource: resourceName }),
  })
}

// BuildingController.java:107-118 -- buildingId kommt aus getGameState().buildings[].id.
export async function collectBuilding(buildingId, userId = DEV_PLAYER_ID) {
  return guardedFetch(`/api/v1/farm/buildings/collect/${encodeURIComponent(userId)}/${encodeURIComponent(buildingId)}`, {
    method: 'POST',
  })
}
