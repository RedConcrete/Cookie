const BASE_URL = 'http://localhost:9876'

async function request(method, path, body) {
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' }
  }
  if (body !== undefined) options.body = JSON.stringify(body)

  const res = await fetch(`${BASE_URL}${path}`, options)
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`${method} ${path} → ${res.status}: ${text}`)
  }
  return res.json()
}

// Load player + last N market snapshots in one call.
// Returns: { user: UserInformationDto, markets: MarketDto[] }
export function initGame(steamId, marketHistoryAmount = 20) {
  return request('GET', `/api/v1/game/init/${steamId}?marketHistoryAmount=${marketHistoryAmount}`)
}

// Create player account (called on first login if needed).
export function createPlayer(steamId) {
  return request('POST', `/api/v1/users/${steamId}`, { token: '' })
}

// Fetch current player data.
export function getPlayer(steamId) {
  return request('GET', `/api/v1/users/${steamId}`)
}

// Execute a market trade.
// action: 'BUY' | 'SELL'
// resourceName: 'SUGAR' | 'FLOUR' | 'EGGS' | 'BUTTER' | 'CHOCOLATE' | 'MILK'
// Returns: UserInformationDto (updated player state)
export function trade(steamId, action, resourceName, amount) {
  return request('POST', '/api/v1/market', {
    userId: steamId,
    action,
    resource: {
      name: resourceName,
      amount
    }
  })
}

// Fetch last N market snapshots.
export function getMarketHistory(amount = 20) {
  return request('GET', `/api/v1/market/get/${amount}`)
}

// Harvest 1 unit of a resource by hovering.
export function harvestResource(steamId, resourceName) {
  return request('POST', `/api/v1/game/harvest/${steamId}`, { resource: resourceName })
}

// Produce cookies from ingredients.
// batches: how many times to run the recipe (1 batch = 10x each ingredient → 100 cookies)
// Returns: UserInformationDto (updated player state)
export function produceCookies(steamId, batches = 1) {
  return request('POST', `/api/v1/game/produce/${steamId}`, { amount: batches })
}
