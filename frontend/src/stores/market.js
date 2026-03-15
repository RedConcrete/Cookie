import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useMarketStore = defineStore('market', () => {
  // Array of MarketDto, newest first (matches backend order)
  const history = ref([])

  const current = computed(() => history.value[0] ?? null)

  function setHistory(snapshots) {
    history.value = snapshots
  }

  function priceOf(resourceName) {
    if (!current.value) return 0
    const key = resourceName.toLowerCase() + 'Price'
    return current.value[key] ?? 0
  }

  return { history, current, setHistory, priceOf }
})
