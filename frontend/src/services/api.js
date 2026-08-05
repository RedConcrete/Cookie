export const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9876'

async function request(method, path, body) {
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' }
  }
  if (body !== undefined) options.body = JSON.stringify(body)

  const res = await fetch(`${BASE_URL}${path}`, options)
  if (!res.ok) {
    const text = await res.text()
    let message = text
    try { message = JSON.parse(text).error ?? text } catch { /* not JSON, use raw text */ }
    throw new Error(message)
  }
  return res.json()
}

// `avatarUrl` on user/leaderboard/profile DTOs is a path on our own server
// (cached avatar bytes, see UserController#getAvatar) -- resolve it against
// BASE_URL so it works as an <img src> in Electron and web builds alike.
export function avatarSrc(avatarUrl) {
  return avatarUrl ? `${BASE_URL}${avatarUrl}` : null
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

// Fetch net worth history for chart.
export function getNetWorthHistory(steamId) {
  return request('GET', `/api/v1/players/${steamId}/networth/history`)
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
// displayName (optional): Steam display name from steamworks.js, resynced on every login.
// Returns: { user: UserInformationDto, markets: MarketDto[] }
export function initGame(steamId, marketHistoryAmount = 20, displayName = null) {
  const q = new URLSearchParams({ marketHistoryAmount })
  if (displayName) q.set('displayName', displayName)
  return request('GET', `/api/v1/game/init/${steamId}?${q}`)
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

// Fetch last N market snapshots (recent, live).
export function getMarketHistory(amount = 20) {
  return request('GET', `/api/v1/market/get/${amount}`)
}

// Fetch full aggregated market history since server start.
export function getFullMarketHistory() {
  return request('GET', '/api/v1/market/history')
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

// Fetch all buildings with ownership/level info for this player.
export function getBuildingLayout(steamId) {
  return request('GET', `/api/v1/farm/buildings/${steamId}`)
}

// Buy or upgrade a building. Returns full updated building list.
export function buyBuilding(steamId, buildingId) {
  return request('POST', `/api/v1/farm/buildings/buy/${steamId}`, { buildingId })
}

// Change worker count for a building (+1 or -1). Returns full updated building list.
export function changeWorkers(steamId, buildingId, delta) {
  return request('POST', `/api/v1/farm/buildings/workers/${steamId}`, { buildingId, delta })
}

// Buy N citizens (requires Rathaus). Returns updated UserInformationDto.
export function buyCitizens(steamId, count = 1) {
  return request('POST', `/api/v1/farm/citizens/buy/${steamId}`, { count })
}

// Reset player data (dev mode only, no token needed for DEV_PLAYER_001).
export function adminResetPlayer(steamId) {
  return request('POST', `/api/v1/admin/reset/${steamId}`)
}

// Reset market stock/prices to initial values (dev mode only).
export function adminResetMarket() {
  return request('POST', '/api/v1/admin/market/reset')
}

// Live-tunable balance config: { market: MarketConfig, balance: GameBalanceConfig }.
export function getAdminConfig() {
  return request('GET', '/api/v1/admin/config')
}
export function updateAdminMarketConfig(config) {
  return request('PUT', '/api/v1/admin/config/market', config)
}
export function updateAdminBalanceConfig(config) {
  return request('PUT', '/api/v1/admin/config/balance', config)
}

// Upgrade definitions (baseCost/effectPerLevel/maxLevel), editable live.
export function getAdminUpgrades() {
  return request('GET', '/api/v1/admin/upgrades')
}
export function updateAdminUpgrade(id, upgrade) {
  return request('PUT', `/api/v1/admin/upgrades/${id}`, upgrade)
}

// Recipe definitions (ingredients/output/bake time), editable live.
export function getAdminRecipes() {
  return request('GET', '/api/v1/admin/recipes')
}
export function updateAdminRecipe(id, recipe) {
  return request('PUT', `/api/v1/admin/recipes/${id}`, recipe)
}
