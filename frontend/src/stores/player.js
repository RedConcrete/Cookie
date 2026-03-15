import { defineStore } from 'pinia'
import { ref } from 'vue'
import { initGame } from '../services/api.js'
import { connectMarketWebSocket } from '../services/websocket.js'
import { useMarketStore } from './market.js'

export const usePlayerStore = defineStore('player', () => {
  const steamId = ref(null)
  const cookies = ref(0)
  const sugar = ref(0)
  const flour = ref(0)
  const eggs = ref(0)
  const butter = ref(0)
  const chocolate = ref(0)
  const milk = ref(0)
  const loading = ref(false)
  const error = ref(null)

  async function init(id) {
    steamId.value = id
    loading.value = true
    error.value = null
    try {
      const data = await initGame(id)
      updateFromDto(data.user)

      const marketStore = useMarketStore()
      marketStore.setHistory(data.markets)

      connectMarketWebSocket((snapshots) => {
        marketStore.setHistory(snapshots)
      })
    } catch (e) {
      error.value = e.message
      console.error('[Player] Init failed', e)
    } finally {
      loading.value = false
    }
  }

  function updateFromDto(dto) {
    cookies.value = dto.cookies
    sugar.value = dto.sugar
    flour.value = dto.flour
    eggs.value = dto.eggs
    butter.value = dto.butter
    chocolate.value = dto.chocolate
    milk.value = dto.milk
  }

  return { steamId, cookies, sugar, flour, eggs, butter, chocolate, milk, loading, error, init, updateFromDto }
})
