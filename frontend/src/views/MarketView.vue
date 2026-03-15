<template>
  <div class="market-view">
    <div class="price-chart-wrapper">
      <PriceChart />
    </div>

    <div class="market-trade-table">
      <table>
        <thead>
          <tr>
            <th>Ressource</th>
            <th>Preis (Cookies)</th>
            <th>Bestand</th>
            <th>Menge</th>
            <th>Handel</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="res in resources" :key="res.name">
            <td class="res-name">
              <img :src="res.icon" class="res-icon" :alt="res.label" />
              {{ res.label }}
            </td>
            <td class="res-price">{{ fmt(marketStore.priceOf(res.name)) }}</td>
            <td>{{ fmt2(playerStore[res.key]) }}</td>
            <td>
              <input
                v-model.number="amounts[res.name]"
                type="number"
                min="0.01"
                step="1"
                class="trade-amount-input"
              />
            </td>
            <td>
              <div class="trade-row-actions">
                <button class="btn btn-buy"  :disabled="busy[res.name] || !canBuy(res)"  @click="doTrade(res, 'BUY')">Kaufen</button>
                <button class="btn btn-sell" :disabled="busy[res.name] || !canSell(res)" @click="doTrade(res, 'SELL')">Verkaufen</button>
              </div>
            </td>
            <td :class="['trade-feedback-cell', feedbackType[res.name]]">
              {{ feedback[res.name] }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { trade } from '../services/api.js'
import PriceChart from '../components/PriceChart.vue'
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()
const marketStore = useMarketStore()

const resources = [
  { name: 'SUGAR',     label: 'Zucker',     key: 'sugar',     icon: sugarIcon  },
  { name: 'FLOUR',     label: 'Mehl',       key: 'flour',     icon: flourIcon  },
  { name: 'EGGS',      label: 'Eier',       key: 'eggs',      icon: eggsIcon   },
  { name: 'BUTTER',    label: 'Butter',     key: 'butter',    icon: butterIcon },
  { name: 'CHOCOLATE', label: 'Schokolade', key: 'chocolate', icon: chocoIcon  },
  { name: 'MILK',      label: 'Milch',      key: 'milk',      icon: milkIcon   },
]

const amounts      = reactive(Object.fromEntries(resources.map(r => [r.name, 1])))
const busy         = reactive(Object.fromEntries(resources.map(r => [r.name, false])))
const feedback     = reactive(Object.fromEntries(resources.map(r => [r.name, ''])))
const feedbackType = reactive(Object.fromEntries(resources.map(r => [r.name, ''])))

function canBuy(res) {
  const cost = marketStore.priceOf(res.name) * (amounts[res.name] || 0)
  return amounts[res.name] > 0 && playerStore.cookies >= cost
}
function canSell(res) {
  return amounts[res.name] > 0 && playerStore[res.key] >= amounts[res.name]
}

async function doTrade(res, action) {
  if (busy[res.name]) return
  busy[res.name] = true
  feedback[res.name] = ''
  try {
    const updated = await trade(playerStore.steamId, action, res.name, amounts[res.name])
    playerStore.updateFromDto(updated)
    feedback[res.name] = action === 'BUY' ? '✓ Gekauft' : '✓ Verkauft'
    feedbackType[res.name] = 'ok'
  } catch {
    feedback[res.name] = 'Fehler'
    feedbackType[res.name] = 'err'
  } finally {
    busy[res.name] = false
    setTimeout(() => { feedback[res.name] = '' }, 2000)
  }
}

function fmt(v)  { return Number(v).toFixed(4) }
function fmt2(v) { return Number(v).toFixed(1) }
</script>

<style scoped>
.res-name { display: flex; align-items: center; gap: 8px; font-weight: 700; }
.res-icon { width: 28px; height: 28px; object-fit: contain; }
</style>
