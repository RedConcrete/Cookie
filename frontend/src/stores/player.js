import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { initGame, getNetWorth, getBuildingLayout, buyCitizens as apiBuyCitizens } from '../services/api.js'
import { connectMarketWebSocket } from '../services/websocket.js'
import { useMarketStore } from './market.js'

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
  const netWorth      = ref(0)
  const nwCookies     = ref(0)
  const nwResources   = ref(0)
  const nwUpgrades    = ref(0)
  const loading       = ref(false)
  const error         = ref(null)
  const recipes       = ref([])
  const ownedBuildings = ref([])  // PlayerBuildingDto[], all buildings (level 0 = not owned)

  const ownedOnly      = computed(() => ownedBuildings.value.filter(b => b.level > 0))
  const totalWage      = computed(() => ownedOnly.value.reduce((s, b) => s + (b.wagePerMin ?? 0), 0))
  const assignedCitizens = computed(() => ownedOnly.value.reduce((s, b) => s + (b.workers ?? 0), 0))
  const idleCitizens   = computed(() => Math.max(0, ownedCitizens.value - assignedCitizens.value))
  const maxCitizens    = computed(() => {
    const rat = ownedBuildings.value.find(b => b.id === 'rathaus')
    return rat && rat.level > 0 ? rat.level * 4 : 0
  })

  let netWorthTimer = null

  async function init(id, name = null) {
    steamId.value = id
    loading.value = true
    error.value = null
    try {
      const data = await initGame(id, 20, name)
      updateFromDto(data.user)
      if (data.buildings) ownedBuildings.value = data.buildings

      const marketStore = useMarketStore()
      marketStore.setHistory(data.markets)
      if (data.recipes) recipes.value = data.recipes

      connectMarketWebSocket((snapshots) => {
        marketStore.setHistory(snapshots)
      })

      await refreshNetWorth()
      netWorthTimer = setInterval(refreshNetWorth, 10000)
    } catch (e) {
      error.value = e.message
      console.error('[Player] Init failed', e)
    } finally {
      loading.value = false
    }
  }

  async function refreshNetWorth() {
    if (!steamId.value) return
    try {
      const data = await getNetWorth(steamId.value)
      netWorth.value    = data.netWorth
      nwCookies.value   = data.cookies
      nwResources.value = data.resourceValue
      nwUpgrades.value  = data.upgradeValue
    } catch {}
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
    netWorth, nwCookies, nwResources, nwUpgrades, loading, error, recipes,
    init, updateFromDto, refreshNetWorth, loadBuildings, buyCitizenAction,
  }
})
