import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { initGame, getBuildingLayout, buyCitizens as apiBuyCitizens, getUpgrades, getPrestigeStatus } from '../services/api.js'
import { connectMarketWebSocket } from '../services/websocket.js'
import { useMarketStore } from './market.js'

// Resource field on the store <-> the uppercase name the market API uses.
const RESOURCE_MARKET_NAME = {
  sugar: 'SUGAR', flour: 'FLOUR', eggs: 'EGGS', butter: 'BUTTER', chocolate: 'CHOCOLATE', milk: 'MILK',
}

export const usePlayerStore = defineStore('player', () => {
  const steamId    = ref(null)
  const displayName = ref(null)
  const avatarUrl  = ref(null)
  const cookies    = ref(0)
  const sugar      = ref(0)
  const flour      = ref(0)
  const eggs       = ref(0)
  const butter     = ref(0)
  const chocolate  = ref(0)
  const milk       = ref(0)
  const workersIdle      = ref(false)
  const totalResourceCap = ref(100)
  const ownedCitizens    = ref(0)
  // Starts true: App.vue gates RouterView on this (v-if="playerStore.loading"),
  // and init() only flips it false once steamId/data are actually set. Defaulting
  // to false let the farm view mount for one tick before onMounted's init() call
  // even started -- FarmGridView's bakeStore.start(playerStore.steamId) ran with
  // steamId still null on that premature mount, and since start() only ever runs
  // its setup once (guarded by "already running"), the store's internal steamId
  // stayed permanently null for the whole session -- no bake status ever polled.
  const loading       = ref(true)
  const error         = ref(null)
  const recipes       = ref([])
  const ownedBuildings = ref([])  // PlayerBuildingDto[], all buildings (level 0 = not owned)

  // Shared across the app (UpgradeShopView, PrestigeView, FarmGridView's harvest
  // prediction) so there's one fetch each, refreshed on the actions that actually
  // change them (buy an upgrade, prestige) instead of every consumer polling its
  // own copy on a blind interval.
  const upgrades           = ref([])  // UpgradeWithStatusDto[]
  const prestigeMultiplier = ref(1)

  const marketStore = useMarketStore()

  const ownedOnly      = computed(() => ownedBuildings.value.filter(b => b.level > 0))
  const totalWage      = computed(() => ownedOnly.value.reduce((s, b) => s + (b.wagePerMin ?? 0), 0))
  const assignedCitizens = computed(() => ownedOnly.value.reduce((s, b) => s + (b.workers ?? 0), 0))
  const idleCitizens   = computed(() => Math.max(0, ownedCitizens.value - assignedCitizens.value))
  const maxCitizens    = computed(() => {
    const rat = ownedBuildings.value.find(b => b.id === 'rathaus')
    return rat && rat.level > 0 ? rat.level * 4 : 0
  })

  // Net worth: computed purely from state already held client-side (cookies,
  // resources, live market prices via the WebSocket, cumulative upgrade spend)
  // instead of polling a dedicated backend endpoint every few seconds for the
  // HUD's live ticker. The backend stays authoritative for the leaderboard,
  // profile, and prestige eligibility -- those still ask the server directly.
  const nwCookies   = computed(() => cookies.value)
  const nwResources = computed(() => Object.entries(RESOURCE_MARKET_NAME)
    .reduce((sum, [key, name]) => sum + resourceValue(key) * marketStore.priceOf(name), 0))
  const nwUpgrades  = computed(() => upgrades.value.reduce((s, u) => s + (u.totalSpent ?? 0), 0))
  const netWorth    = computed(() => nwCookies.value + nwResources.value + nwUpgrades.value)

  function resourceValue(key) {
    return { sugar, flour, eggs, butter, chocolate, milk }[key].value
  }

  async function init(id, name = null) {
    steamId.value = id
    loading.value = true
    error.value = null
    try {
      const data = await initGame(id, 20, name)
      updateFromDto(data.user)
      if (data.buildings) ownedBuildings.value = data.buildings
      marketStore.setHistory(data.markets)
      if (data.recipes) recipes.value = data.recipes

      connectMarketWebSocket((snapshots) => {
        marketStore.setHistory(snapshots)
      })

      await Promise.all([loadUpgrades(), loadPrestigeMultiplier()])
    } catch (e) {
      error.value = e.message
      console.error('[Player] Init failed', e)
    } finally {
      loading.value = false
    }
  }

  async function loadUpgrades() {
    if (!steamId.value) return
    try { upgrades.value = await getUpgrades(steamId.value) } catch {}
  }

  async function loadPrestigeMultiplier() {
    if (!steamId.value) return
    try { prestigeMultiplier.value = (await getPrestigeStatus(steamId.value)).multiplier ?? 1 } catch {}
  }

  async function buyCitizenAction(count = 1) {
    if (!steamId.value) return
    try {
      const dto = await apiBuyCitizens(steamId.value, count)
      updateFromDto(dto)
    } catch {}
  }

  async function loadBuildings() {
    if (!steamId.value) return
    try {
      ownedBuildings.value = await getBuildingLayout(steamId.value)
    } catch {}
  }

  function updateFromDto(dto) {
    if (dto.displayName !== undefined) displayName.value = dto.displayName
    if (dto.avatarUrl !== undefined) avatarUrl.value = dto.avatarUrl
    cookies.value   = dto.cookies
    sugar.value     = dto.sugar
    flour.value     = dto.flour
    eggs.value      = dto.eggs
    butter.value    = dto.butter
    chocolate.value = dto.chocolate
    milk.value      = dto.milk
    if (dto.workersIdle !== undefined)      workersIdle.value      = dto.workersIdle
    if (dto.totalResourceCap !== undefined) totalResourceCap.value = dto.totalResourceCap
    if (dto.ownedCitizens !== undefined)    ownedCitizens.value    = dto.ownedCitizens
  }

  return {
    steamId, displayName, avatarUrl, cookies, sugar, flour, eggs, butter, chocolate, milk,
    workersIdle, totalResourceCap, ownedBuildings, ownedOnly, totalWage,
    ownedCitizens, assignedCitizens, idleCitizens, maxCitizens,
    upgrades, prestigeMultiplier, loadUpgrades, loadPrestigeMultiplier,
    netWorth, nwCookies, nwResources, nwUpgrades, loading, error, recipes,
    init, updateFromDto, loadBuildings, buyCitizenAction,
  }
})
