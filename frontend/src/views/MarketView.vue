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
            <th>Preis</th>
            <th>Bestand</th>
            <th>Menge</th>
            <th>Handel</th>
            <th class="th-feedback"></th>
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

            <!-- Menge: eigene +/− Buttons außerhalb des Inputs -->
            <td class="td-stepper">
              <div class="stepper">
                <button class="step-btn" @click="dec(res.name)">−</button>
                <input
                  v-model.number="amounts[res.name]"
                  type="number"
                  min="1"
                  class="step-input"
                  @blur="clamp(res.name)"
                />
                <button class="step-btn" @click="inc(res.name)">+</button>
              </div>
            </td>

            <td>
              <div class="trade-col">
                <div class="trade-row-actions">
                  <button
                    class="btn btn-buy"
                    :disabled="busy[res.name] || !canBuy(res)"
                    @click="doTrade(res, 'BUY')"
                  >Kaufen</button>
                  <button
                    class="btn btn-sell"
                    :disabled="busy[res.name] || !canSell(res)"
                    @click="doTrade(res, 'SELL')"
                  >Verkaufen</button>
                </div>
                <div class="trade-preview">
                  <span class="preview-cost">−{{ fmt(buyCost(res)) }} C</span>
                  <span class="preview-sep">/</span>
                  <NestedTooltip :content="[
                    { text: '+' + fmt(netPayout(res)) + ' C' },
                    { text: ' (nach ', tooltip: `Marktgebühr: ${(sellFeeRate * 100).toFixed(0)}% auf jeden Verkauf` },
                    { text: 'Gebühr)' },
                  ]">
                    <span class="preview-earn">+{{ fmt(netPayout(res)) }} C</span>
                  </NestedTooltip>
                </div>
              </div>
            </td>

            <!-- Feste Breite verhindert Layout-Shift -->
            <td class="td-feedback" :class="feedbackType[res.name]">
              {{ feedback[res.name] }}
            </td>

          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { trade, getConfig } from '../services/api.js'
import PriceChart from '../components/PriceChart.vue'
import NestedTooltip from '../components/NestedTooltip.vue'
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()
const marketStore = useMarketStore()

const sellFeeRate = ref(0.05)
onMounted(async () => {
  try { const cfg = await getConfig(); sellFeeRate.value = cfg.sellFeeRate ?? 0.05 } catch {}
})

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

// Stepper helpers
function inc(name)   { amounts[name] = (amounts[name] || 0) + 1 }
function dec(name)   { amounts[name] = Math.max(1, (amounts[name] || 1) - 1) }
function clamp(name) { if (!amounts[name] || amounts[name] < 1) amounts[name] = 1 }

function canBuy(res) {
  const cost = marketStore.priceOf(res.name) * (amounts[res.name] || 0)
  return amounts[res.name] > 0 && playerStore.cookies >= cost
}
function canSell(res) {
  return amounts[res.name] > 0 && playerStore[res.key] >= amounts[res.name]
}
function buyCost(res) {
  return marketStore.priceOf(res.name) * (amounts[res.name] || 0)
}
function netPayout(res) {
  return buyCost(res) * (1 - sellFeeRate.value)
}

async function doTrade(res, action) {
  if (busy[res.name]) return
  busy[res.name] = true
  feedback[res.name] = ''
  try {
    const updated = await trade(playerStore.steamId, action, res.name, amounts[res.name])
    playerStore.updateFromDto(updated)
    feedback[res.name] = action === 'BUY'
      ? '✓ Gekauft'
      : `✓ +${fmt(netPayout(res))} C`
    feedbackType[res.name] = 'ok'
  } catch {
    feedback[res.name] = 'Fehler'
    feedbackType[res.name] = 'err'
  } finally {
    busy[res.name] = false
    setTimeout(() => { feedback[res.name] = '' }, 2000)
  }
}

function fmt(v)  { return Number(v).toFixed(2) }
function fmt2(v) { return Number(v).toFixed(1) }
</script>

<style scoped>
.res-name { display: flex; align-items: center; gap: 8px; font-weight: 700; }
.res-icon { width: 28px; height: 28px; object-fit: contain; }

.trade-col { display: flex; flex-direction: column; gap: 4px; }

.trade-preview {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  padding: 0 2px;
}
.preview-cost { color: var(--error); font-weight: 600; }
.preview-earn { color: var(--success); font-weight: 600; }
.preview-sep  { color: var(--text-muted); }

/* Stepper */
.td-stepper { white-space: nowrap; }

.stepper {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.step-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface2);
  color: var(--text);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.1s;
}
.step-btn:hover { background: var(--accent); color: #fff; border-color: var(--accent); }

.step-input {
  width: 56px;
  padding: 4px 6px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  color: var(--text);
  font-size: 13px;
  text-align: center;
  /* native spinner entfernen */
  -moz-appearance: textfield;
}
.step-input::-webkit-outer-spin-button,
.step-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }

/* Feedback — feste Breite damit die Tabelle nicht springt */
.td-feedback {
  width: 140px;
  min-width: 140px;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.td-feedback.ok  { color: var(--success); }
.td-feedback.err { color: var(--error); }
.th-feedback { width: 140px; min-width: 140px; }
</style>
