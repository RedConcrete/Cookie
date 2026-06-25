import { defineStore } from 'pinia'
import { ref } from 'vue'
import { initGame, getNetWorth } from '../services/api.js'
import { connectMarketWebSocket } from '../services/websocket.js'
import { useMarketStore } from './market.js'

export const usePlayerStore = defineStore('player', () => {
  const steamId    = ref(null)
  const cookies    = ref(0)
  const sugar      = ref(0)
  const flour      = ref(0)
  const eggs       = ref(0)
  const butter     = ref(0)
  const chocolate  = ref(0)
  const milk       = ref(0)
  const netWorth      = ref(0)
  const nwCookies     = ref(0)
  const nwResources   = ref(0)
  const nwUpgrades    = ref(0)
  const loading       = ref(false)
  const error         = ref(null)
  const recipes       = ref([])

  let netWorthTimer = null

  async function init(id) {
    steamId.value = id
    loading.value = true
    error.value = null
    try {
      const data = await initGame(id)
      updateFromDto(data.user)

      const marketStore = useMarketStore()
      marketStore.setHistory(data.markets)
      if (data.recipes) recipes.value = data.recipes

      connectMarketWebSocket((snapshots) => {
        marketStore.setHistory(snapshots)
      })

      // Net Worth alle 10s aktualisieren
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

  function updateFromDto(dto) {
    cookies.value   = dto.cookies
    sugar.value     = dto.sugar
    flour.value     = dto.flour
    eggs.value      = dto.eggs
    butter.value    = dto.butter
    chocolate.value = dto.chocolate
    milk.value      = dto.milk
  }

  return {
    steamId, cookies, sugar, flour, eggs, butter, chocolate, milk,
    netWorth, nwCookies, nwResources, nwUpgrades, loading, error, recipes,
    init, updateFromDto, refreshNetWorth,
  }
})
