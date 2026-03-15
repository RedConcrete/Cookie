<template>
  <div class="trade-panel">
    <h3>Handeln</h3>

    <div v-if="!selected" class="trade-hint">
      Ressource in der Tabelle auswählen
    </div>

    <template v-else>
      <div class="trade-resource-name">{{ LABELS[selected] }}</div>

      <div class="trade-price-info">
        Aktueller Preis: <strong>{{ fmt(marketStore.priceOf(selected)) }}</strong> Cookies
      </div>

      <div class="trade-amount-row">
        <label>Menge</label>
        <input v-model.number="amount" type="number" min="0.01" step="1" class="amount-input" />
      </div>

      <div class="trade-cost-row">
        Gesamt: <strong>{{ fmt(totalCost) }}</strong> Cookies
      </div>

      <div class="trade-buttons">
        <button class="btn btn-buy" :disabled="busy || !canBuy" @click="doTrade('BUY')">
          Kaufen
        </button>
        <button class="btn btn-sell" :disabled="busy || !canSell" @click="doTrade('SELL')">
          Verkaufen
        </button>
      </div>

      <div v-if="feedback" :class="['trade-feedback', feedbackType]">
        {{ feedback }}
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { trade } from '../services/api.js'

const props = defineProps({ selected: { type: String, default: null } })

const playerStore = usePlayerStore()
const marketStore = useMarketStore()

const amount = ref(1)
const busy = ref(false)
const feedback = ref('')
const feedbackType = ref('ok')

const LABELS = {
  SUGAR: 'Zucker', FLOUR: 'Mehl', EGGS: 'Eier',
  BUTTER: 'Butter', CHOCOLATE: 'Schokolade', MILK: 'Milch'
}

const totalCost = computed(() =>
  marketStore.priceOf(props.selected) * (amount.value || 0)
)

const canBuy = computed(() =>
  amount.value > 0 && playerStore.cookies >= totalCost.value
)

const canSell = computed(() => {
  if (!props.selected || amount.value <= 0) return false
  return playerStore[props.selected.toLowerCase()] >= amount.value
})

async function doTrade(action) {
  if (!props.selected || busy.value) return
  busy.value = true
  feedback.value = ''
  try {
    const updatedUser = await trade(playerStore.steamId, action, props.selected, amount.value)
    playerStore.updateFromDto(updatedUser)
    feedback.value = action === 'BUY' ? 'Gekauft!' : 'Verkauft!'
    feedbackType.value = 'ok'
  } catch (e) {
    feedback.value = e.message
    feedbackType.value = 'err'
  } finally {
    busy.value = false
  }
}

function fmt(v) {
  return Number(v).toFixed(4)
}
</script>
