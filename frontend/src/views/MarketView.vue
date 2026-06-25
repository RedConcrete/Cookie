<template>
  <div class="market-layout">

    <!-- ── Chart-Seite ──────────────────────────────────── -->
    <div class="chart-side">
      <PriceChart
        @hover-resource="hoveredResource = $event"
        @hover-point="hoveredPoint = $event"
        @pct-mode-change="onPctModeChange"
      />
      <div class="hover-bar">
        <span class="hover-time">{{ hoveredPoint ? fmtTime(hoveredPoint.date) : 'Aktuell' }}</span>
        <span v-for="res in resources" :key="res.name" class="hover-chip" :style="{ borderColor: COLORS[res.name] }">
          {{ res.label }}:
          <template v-if="chartPctMode && chartBases[res.name]">
            {{ fmtPct((hoveredPoint ?? marketStore.current)?.[res.priceKey], chartBases[res.name]) }}
          </template>
          <template v-else>
            {{ fmt((hoveredPoint ?? marketStore.current)?.[res.priceKey]) }}
          </template>
        </span>
      </div>
    </div>

    <!-- ── Trade-Panel (einklappbar) ────────────────────── -->
    <div class="trade-side" :class="{ collapsed: panelCollapsed }">

      <!-- Eingeklappt: nur Toggle-Button -->
      <button v-if="panelCollapsed" class="panel-toggle" @click="panelCollapsed = false" title="Handel öffnen">◀</button>

      <!-- Ausgeklappt: Toggle + Inhalt -->
      <template v-else>
        <button class="panel-toggle" @click="panelCollapsed = true" title="Handel schließen">▶</button>
        <div class="trade-content">
          <div
            v-for="res in resources" :key="res.name"
            class="trade-row"
            :class="{ 'row-highlight': hoveredResource === res.name, 'row-success': flashSuccess[res.name] }"
          >
            <!-- Header: Icon + Name + Preis | Bestand -->
            <div class="tr-header">
              <img :src="res.icon" class="res-icon" :alt="res.label" />
              <span class="res-label">{{ res.label }}</span>
              <span class="res-price">{{ fmt(marketStore.priceOf(res.name)) }}</span>
              <span class="res-stock">{{ fmt2(playerStore[res.key]) }}</span>
            </div>

            <!-- Stepper + Buttons in einer Zeile -->
            <div class="tr-controls">
              <div class="stepper">
                <button
                  class="step-btn"
                  @mousedown="startHold(res.name, -1)"
                  @mouseup="stopHold"
                  @mouseleave="stopHold"
                >−</button>
                <input v-model.number="amounts[res.name]" type="number" min="1" class="step-input" @blur="clamp(res.name)" />
                <button
                  class="step-btn"
                  @mousedown="startHold(res.name, 1)"
                  @mouseup="stopHold"
                  @mouseleave="stopHold"
                >+</button>
              </div>
              <button class="btn btn-buy"  :disabled="busy[res.name] || !canBuy(res)"  @click="doTrade(res, 'BUY')">
                Kaufen<span class="btn-cost">−{{ fmt(buyCost(res)) }} C</span>
              </button>
              <button class="btn btn-sell" :disabled="busy[res.name] || !canSell(res)" @click="doTrade(res, 'SELL')">
                Verkaufen<span class="btn-earn">+{{ fmt(netPayout(res)) }} C</span>
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- ── Fehler-Dialog ─────────────────────────────────── -->
    <div v-if="errorMsg" class="err-overlay" @click.self="errorMsg = null">
      <div class="err-dialog">
        <div class="err-title">Fehler</div>
        <div class="err-body">{{ errorMsg }}</div>
        <button class="btn btn-buy" style="margin-top:12px" @click="errorMsg = null">OK</button>
      </div>
    </div>

  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { trade, getConfig } from '../services/api.js'
import { useAudio } from '../composables/useAudio.js'
import PriceChart from '../components/PriceChart.vue'
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()
const marketStore = useMarketStore()

const COLORS = {
  SUGAR: '#ef4444', FLOUR: '#3b82f6', EGGS: '#22c55e',
  BUTTER: '#eab308', CHOCOLATE: '#a855f7', MILK: '#06b6d4',
}

const { playCoins } = useAudio()
const sellFeeRate     = ref(0.05)
const hoveredResource = ref(null)
const hoveredPoint    = ref(null)
const panelCollapsed  = ref(false)
const chartPctMode    = ref(false)
const chartBases      = ref({})
const errorMsg        = ref(null)

const flashSuccess = reactive(Object.fromEntries(
  ['SUGAR','FLOUR','EGGS','BUTTER','CHOCOLATE','MILK'].map(n => [n, false])
))

function onPctModeChange({ active, bases }) {
  chartPctMode.value = active
  chartBases.value   = bases
}

onMounted(async () => {
  try { const cfg = await getConfig(); sellFeeRate.value = cfg.sellFeeRate ?? 0.05 } catch {}
})

const resources = [
  { name: 'SUGAR',     label: 'Zucker',     key: 'sugar',     priceKey: 'sugarPrice',     icon: sugarIcon  },
  { name: 'FLOUR',     label: 'Mehl',       key: 'flour',     priceKey: 'flourPrice',     icon: flourIcon  },
  { name: 'EGGS',      label: 'Eier',       key: 'eggs',      priceKey: 'eggsPrice',      icon: eggsIcon   },
  { name: 'BUTTER',    label: 'Butter',     key: 'butter',    priceKey: 'butterPrice',    icon: butterIcon },
  { name: 'CHOCOLATE', label: 'Schokolade', key: 'chocolate', priceKey: 'chocolatePrice', icon: chocoIcon  },
  { name: 'MILK',      label: 'Milch',      key: 'milk',      priceKey: 'milkPrice',      icon: milkIcon   },
]

const amounts = reactive(Object.fromEntries(resources.map(r => [r.name, 1])))
const busy    = reactive(Object.fromEntries(resources.map(r => [r.name, false])))

function clamp(name) { if (!amounts[name] || amounts[name] < 1) amounts[name] = 1 }

// ── Hold-to-repeat ──────────────────────────────────────
let holdTimer = null
let holdRepeat = null

function startHold(name, delta) {
  step(name, delta)
  holdTimer = setTimeout(() => {
    holdRepeat = setInterval(() => step(name, delta), 80)
  }, 400)
}
function stopHold() {
  clearTimeout(holdTimer)
  clearInterval(holdRepeat)
  holdTimer = null
  holdRepeat = null
}
function step(name, delta) {
  amounts[name] = Math.max(1, (amounts[name] || 1) + delta)
}

onUnmounted(stopHold)

// ── Trade ───────────────────────────────────────────────
function canBuy(res)    { return amounts[res.name] > 0 && playerStore.cookies >= buyCost(res) }
function canSell(res)   { return amounts[res.name] > 0 && playerStore[res.key] >= amounts[res.name] }
function buyCost(res)   { return marketStore.priceOf(res.name) * (amounts[res.name] || 0) }
function netPayout(res) { return buyCost(res) * (1 - sellFeeRate.value) }

async function doTrade(res, action) {
  if (busy[res.name]) return
  busy[res.name] = true
  try {
    const updated = await trade(playerStore.steamId, action, res.name, amounts[res.name])
    playerStore.updateFromDto(updated)
    playCoins()
    flashSuccess[res.name] = true
    setTimeout(() => { flashSuccess[res.name] = false }, 600)
  } catch (e) {
    errorMsg.value = e?.message ?? 'Unbekannter Fehler'
  } finally {
    busy[res.name] = false
  }
}

// ── Format ──────────────────────────────────────────────
function fmt(v)          { return Number(v ?? 0).toFixed(2) }
function fmt2(v)         { return Number(v ?? 0).toFixed(1) }
function fmtPct(v, base) {
  const pct = ((Number(v) - base) / base) * 100
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%`
}
function fmtTime(date) {
  const d = new Date(date)
  return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}`
}
</script>

<style scoped>
/* ── Layout ─────────────────────────────────────────────── */
.market-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0 0 0 16px;
}

/* ── Chart ──────────────────────────────────────────────── */
.chart-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 12px 12px 0;
  border-right: 1px solid var(--border);
}

.hover-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  padding: 3px 0;
  min-height: 26px;
}
.hover-time { color: var(--text-muted); font-size: 11px; margin-right: 2px; }
.hover-chip {
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid;
  color: var(--text);
  font-size: 11px;
  font-weight: 600;
}

/* ── Trade-Panel ────────────────────────────────────────── */
.trade-side {
  display: flex;
  flex-shrink: 0;
  width: 280px;
  transition: width 0.2s;
  border-bottom-right-radius: 14px;
  overflow: hidden;
}
.trade-side.collapsed { width: 28px; }

.panel-toggle {
  width: 28px;
  flex-shrink: 0;
  background: var(--surface2);
  border: none;
  border-left: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}
.panel-toggle:hover { background: var(--surface); color: var(--text); }

.trade-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 10px 8px 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* ── Trade-Karte ────────────────────────────────────────── */
.trade-row {
  flex: 1;
  min-height: 0;
  padding: 5px 8px;
  border-radius: 8px;
  border: 2px solid var(--border);
  background: var(--surface2);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  transition: border-color 0.15s, background 0.15s;
}
.trade-row.row-highlight { border-color: var(--accent); }
.trade-row.row-success   { border-color: var(--success); background: rgba(34,197,94,0.1); }

.tr-header {
  display: flex;
  align-items: center;
  gap: 6px;
}
.res-icon  { width: 20px; height: 20px; object-fit: contain; flex-shrink: 0; }
.res-label { flex: 1; font-weight: 700; font-size: 12px; }
.res-price { color: var(--accent); font-weight: 700; font-size: 12px; }
.res-stock { color: var(--text-muted); font-size: 11px; margin-left: 4px; }

/* ── Controls: stepper + buttons in einer Reihe ─────────── */
.tr-controls {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.stepper { display: flex; align-items: center; gap: 3px; }
.step-btn {
  width: 22px; height: 22px;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--surface);
  color: var(--text);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
  transition: background 0.1s;
}
.step-btn:hover  { background: var(--accent); color: #fff; border-color: var(--accent); }
.step-btn:active { opacity: 0.7; }

.step-input {
  width: 42px;
  padding: 2px 4px;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--bg);
  color: var(--text);
  font-size: 12px;
  text-align: center;
  -moz-appearance: textfield;
}
.step-input::-webkit-outer-spin-button,
.step-input::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }

.tr-controls .btn { flex: 1; min-width: 0; font-size: 11px; padding: 4px 6px; }

.btn-cost, .btn-earn {
  display: block;
  font-size: 9px;
  font-weight: 700;
  opacity: 0.85;
  line-height: 1;
  margin-top: 1px;
}
.btn-cost { color: #ffbbbb; }
.btn-earn { color: #bbffbb; }

/* ── Fehler-Dialog ──────────────────────────────────────── */
.err-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  border-radius: 16px;
}
.err-dialog {
  background: var(--surface);
  border: 2px solid var(--error);
  border-radius: 12px;
  padding: 20px 24px;
  max-width: 320px;
  text-align: center;
}
.err-title { font-weight: 700; font-size: 15px; color: var(--error); margin-bottom: 8px; }
.err-body  { font-size: 13px; color: var(--text); line-height: 1.5; }
</style>
