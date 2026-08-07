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

// Fetch full skill tree (nodes + edges + player status).
export function getSkillTree(steamId) {
  return request('GET', `/api/v1/skilltree?userId=${steamId}`)
}

// Buy 1 skill point (spends cookies). Returns updated skill tree.
export function buySkillPoint(steamId) {
  return request('POST', `/api/v1/skilltree/buy-point/${steamId}`)
}

// Allocate a skill node (spends 1 skill point). Returns updated skill tree.
export function allocateSkillNode(steamId, nodeId) {
  return request('POST', `/api/v1/skilltree/allocate/${steamId}`, { nodeId })
}

// Fetch global leaderboard sorted by net worth.
export function getLeaderboard() {
  return request('GET', '/api/v1/leaderboard')
}

// Fetch net worth breakdown for one player.
export function getNetWorth(steamId) {
  return request('GET', `/api/v1/players/${steamId}/networth`)
}

// Fetch full player profile (net worth + lifetime stats + allocated skill nodes).
export function getProfile(steamId) {
  return request('GET', `/api/v1/players/${steamId}/profile`)
}

// Fetch net worth history for chart.
export function getNetWorthHistory(steamId) {
  return request('GET', `/api/v1/players/${steamId}/networth/history`)
}

// Fetch personal stats (lifetime counters + currently active bonuses). Own account only.
export function getPlayerStats(steamId) {
  return request('GET', `/api/v1/players/${steamId}/stats`)
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

// Collect a building's accumulated passive resource (like collecting rent). Returns updated UserInformationDto.
export function collectBuilding(steamId, buildingId) {
  return request('POST', `/api/v1/farm/buildings/collect/${steamId}/${buildingId}`)
}

// Buy N citizens (requires Rathaus). Returns updated UserInformationDto.
export function buyCitizens(steamId, count = 1) {
  return request('POST', `/api/v1/farm/citizens/buy/${steamId}`, { count })
}

// Reset player data (dev mode only, no token needed for DEV_PLAYER_001).
export function adminResetPlayer(steamId) {
  return request('POST', `/api/v1/admin/reset/${steamId}`)
}

