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

// Fetch server config (devMode, sellFeeRate, …)
export function getConfig() {
  return request('GET', '/api/v1/config')
}

// Fetch all upgrades with current player levels.
export function getUpgrades(steamId) {
  return request('GET', `/api/v1/upgrades?userId=${steamId}`)
}

// Buy next level of an upgrade. Returns updated upgrade list.
export function buyUpgrade(steamId, upgradeId) {
  return request('POST', `/api/v1/upgrades/buy/${steamId}`, { upgradeId })
}

// Fetch global leaderboard sorted by net worth.
export function getLeaderboard() {
  return request('GET', '/api/v1/leaderboard')
}

// Fetch net worth breakdown for one player.
export function getNetWorth(steamId) {
  return request('GET', `/api/v1/players/${steamId}/networth`)
}

// Fetch full player profile (net worth + lifetime stats + upgrades).
export function getProfile(steamId) {
  return request('GET', `/api/v1/players/${steamId}/profile`)
}

// Fetch prestige status (level, multiplier, threshold, canPrestige).
export function getPrestigeStatus(steamId) {
  return request('GET', `/api/v1/game/prestige/status/${steamId}`)
}

// Execute prestige reset.
export function doPrestige(steamId) {
  return request('POST', `/api/v1/game/prestige/${steamId}`)
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

// Produce cookies from ingredients (legacy instant bake, kept for compatibility).
export function produceCookies(steamId, batches = 1) {
  return request('POST', `/api/v1/game/produce/${steamId}`, { amount: batches })
}

// Fetch all available recipes.
export function getRecipes() {
  return request('GET', '/api/v1/recipes')
}

// Start a bake job. Deducts resources immediately.
export function bakeStart(steamId, recipeId, batches) {
  return request('POST', `/api/v1/game/bake/start/${steamId}`, { recipeId, batches })
}

// Get current bake job status for a player.
export function bakeStatus(steamId) {
  return request('GET', `/api/v1/game/bake/status/${steamId}`)
}

// Claim a finished bake job → returns updated UserInformationDto.
export function bakeClaim(steamId) {
  return request('POST', `/api/v1/game/bake/claim/${steamId}`)
}
