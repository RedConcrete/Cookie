<template>
  <div class="mv-root">
    <div class="mv-chart-row">
      <div class="mv-legend">
        <button
          v-for="r in resources" :key="r.name"
          class="mv-legend-chip"
          :class="{ inactive: chartRef && chartRef.visible[r.name] === false }"
          @click="chartRef?.toggle(r.name)"
        >
          <span class="mv-legend-dot" :style="{ background: COLORS[r.name] }"></span>{{ t(r.labelKey) }}
          <span class="mv-legend-val">{{ legendValue(r) }}</span>
        </button>
      </div>
      <div class="mv-chart-box">
        <PriceChart
          ref="chartRef"
          @hover-resource="hoveredResource = $event"
          @hover-point="hoveredPoint = $event"
          @pct-mode-change="onPctModeChange"
        />
      </div>
    </div>

    <PixelScrollBox class="mv-table">
      <div class="mv-table-inner">
      <div class="mv-row mv-head">
        <div></div><div>{{ t('marketView.colResource') }}</div><div>{{ t('marketView.colPrice') }}</div><div>{{ t('marketView.colTrend') }}</div><div>{{ t('marketView.colStock') }}</div><div>{{ t('marketView.colQty') }}</div><div>{{ t('marketView.colAction') }}</div>
        <div class="mv-col-cost">{{ t('marketView.colCost') }} &darr;</div>
      </div>

      <div v-for="res in resources" :key="res.name" class="mv-row" :class="{ success: flashSuccess[res.name] }" :style="rowBorderStyle(res)">
        <div class="mv-icon"><PixelIcon :name="res.icon" :size="20" /></div>
        <div class="mv-name">{{ t(res.labelKey) }}</div>
        <div class="mv-price">{{ fmt(marketStore.priceOf(res.name)) }}</div>
        <div class="mv-trend" :style="{ color: trendOf(res.name) >= 0 ? '#56642e' : '#b74132' }">
          {{ trendOf(res.name) >= 0 ? '+' : '' }}{{ trendOf(res.name).toFixed(1) }} %
        </div>
        <div class="mv-stock">{{ fmt2(playerStore[res.key]) }}</div>

        <div class="mv-qty">
          <button class="mv-qty-btn" @mousedown="startHold(res.name, -1)" @mouseup="stopHold" @mouseleave="stopHold">&minus;</button>
          <input
            class="mv-qty-val mv-qty-input"
            type="text" inputmode="numeric"
            :value="amounts[res.name]"
            @input="onQtyInput(res, $event)"
            @blur="onQtyBlur(res)"
          />
          <button class="mv-qty-btn" @mousedown="startHold(res.name, 1)" @mouseup="stopHold" @mouseleave="stopHold">+</button>
          <div class="mv-qty-max">
            <button
              class="mv-max-btn mv-max-buy" :disabled="maxBuyQty(res) <= 0"
              @click="setMax(res, 'BUY')"
              @mouseenter="hoverMaxKey = res.name + ':BUY'" @mouseleave="hoverMaxKey = null"
            >&#9650;</button>
            <div v-if="hoverMaxKey === res.name + ':BUY'" class="mv-max-notice mv-max-notice-buy">{{ t('marketView.maxBuyTitle') }}</div>
            <button
              class="mv-max-btn mv-max-sell" :disabled="maxSellQty(res) <= 0"
              @click="setMax(res, 'SELL')"
              @mouseenter="hoverMaxKey = res.name + ':SELL'" @mouseleave="hoverMaxKey = null"
            >&#9660;</button>
            <div v-if="hoverMaxKey === res.name + ':SELL'" class="mv-max-notice mv-max-notice-sell">{{ t('marketView.maxSellTitle') }}</div>
          </div>
        </div>

        <div class="mv-actions">
          <button class="px-btn px-btn-buy mv-action-btn" :disabled="busy[res.name] || !canBuy(res)" @click="doTrade(res, 'BUY')">{{ t('marketView.buyButton') }}</button>
          <button class="px-btn px-btn-sell mv-action-btn" :disabled="busy[res.name] || !canSell(res)" @click="doTrade(res, 'SELL')">{{ t('marketView.sellButton') }}</button>
        </div>

        <PixelInfoPopover :rows="costRows(res)" :title="t(res.labelKey) + ' · ' + t('marketView.costSuffix')" side="left" :width="284" :z="9" bar-placement="edge">
          <div class="mv-cost">
            <div class="mv-cost-buy">&minus;{{ fmt(buyCost(res)) }}<PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" /></div>
            <div class="mv-cost-sell">+{{ fmt(netPayout(res)) }}<PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" /></div>
          </div>
        </PixelInfoPopover>
      </div>
      </div>
    </PixelScrollBox>

    <div v-if="errorMsg" class="err-overlay" @click.self="errorMsg = null">
      <div class="err-dialog">
        <div class="err-title">{{ t('common.error') }}</div>
        <div class="err-body">{{ errorMsg }}</div>
        <button class="px-btn px-btn-accent" style="margin-top:12px" @click="errorMsg = null"><ShortcutSlot />{{ t('marketView.okButton') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { trade, getConfig } from '../services/api.js'
import { useAudio } from '../composables/useAudio.js'
import PriceChart from '../components/PriceChart.vue'
import PixelIcon from '../components/pixel/PixelIcon.vue'
import PixelInfoPopover from '../components/pixel/PixelInfoPopover.vue'
import PixelScrollBox from '../components/pixel/PixelScrollBox.vue'
import ShortcutSlot from '../components/pixel/ShortcutSlot.vue'

const { t } = useI18n()
const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const chartRef     = ref(null)

// Muss mit RESOURCE_COLORS in PriceChart.vue uebereinstimmen -- Legend-Chip-Farbe
// soll der jeweiligen Chart-Linie entsprechen.
const COLORS = {
  SUGAR: '#e67146', FLOUR: '#2a7d75', EGGS: '#349c58',
  BUTTER: '#c9c03d', CHOCOLATE: '#e67a84', MILK: '#6f6e72',
}

const { playCoins } = useAudio()
const sellFeeRate     = ref(0.08)
const hoveredResource = ref(null)
const hoveredPoint    = ref(null)
const chartPctMode    = ref(false)
const chartBases      = ref({})
const errorMsg        = ref(null)
const hoverMaxKey     = ref(null)

const flashSuccess = reactive(Object.fromEntries(
  ['SUGAR','FLOUR','EGGS','BUTTER','CHOCOLATE','MILK'].map(n => [n, false])
))

function onPctModeChange({ active, bases }) {
  chartPctMode.value = active
  chartBases.value   = bases
}

function isResourceSelected(res) {
  return !chartRef.value || chartRef.value.visible[res.name] !== false
}
function rowBorderStyle(res) {
  const selected = isResourceSelected(res)
  const hovered  = hoveredResource.value === res.name
  return (selected || hovered) ? { borderColor: COLORS[res.name] } : {}
}

function legendValue(res) {
  const point = hoveredPoint.value ?? marketStore.current
  if (chartPctMode.value && chartBases.value[res.name]) return fmtPct(point?.[res.priceKey], chartBases.value[res.name])
  return fmt(point?.[res.priceKey])
}

onMounted(async () => {
  try { const cfg = await getConfig(); sellFeeRate.value = cfg.sellFeeRate ?? 0.08 } catch {}
})

const resources = [
  { name: 'SUGAR',     labelKey: 'marketView.resourceSugar',     key: 'sugar',     priceKey: 'sugarPrice',     icon: 'zucker' },
  { name: 'FLOUR',     labelKey: 'marketView.resourceFlour',     key: 'flour',     priceKey: 'flourPrice',     icon: 'mehl'   },
  { name: 'EGGS',      labelKey: 'marketView.resourceEggs',      key: 'eggs',      priceKey: 'eggsPrice',      icon: 'eier'   },
  { name: 'BUTTER',    labelKey: 'marketView.resourceButter',    key: 'butter',    priceKey: 'butterPrice',    icon: 'butter' },
  { name: 'CHOCOLATE', labelKey: 'marketView.resourceChocolate', key: 'chocolate', priceKey: 'chocolatePrice', icon: 'schoko' },
  { name: 'MILK',      labelKey: 'marketView.resourceMilk',      key: 'milk',      priceKey: 'milkPrice',      icon: 'milch'  },
]

const amounts = reactive(Object.fromEntries(resources.map(r => [r.name, 10])))
const busy    = reactive(Object.fromEntries(resources.map(r => [r.name, false])))

function trendOf(name) {
  const key = resources.find(r => r.name === name)?.priceKey
  const hist = marketStore.history
  if (!key || !hist?.length) return 0
  const now = marketStore.priceOf(name)
  const past = hist[hist.length - 1]?.[key]
  if (!past) return 0
  return ((now - past) / past) * 100
}

// ── Hold-to-repeat ──────────────────────────────────────
let holdTimer = null
let holdRepeat = null
function startHold(name, delta) {
  step(name, delta)
  holdTimer = setTimeout(() => { holdRepeat = setInterval(() => step(name, delta), 80) }, 400)
}
function stopHold() { clearTimeout(holdTimer); clearInterval(holdRepeat); holdTimer = null; holdRepeat = null }
function step(name, delta) { amounts[name] = Math.max(1, (amounts[name] || 1) + delta) }
onUnmounted(stopHold)

function onQtyInput(res, e) {
  const digits = e.target.value.replace(/\D/g, '')
  e.target.value = digits
  amounts[res.name] = digits === '' ? '' : Number(digits)
}
function onQtyBlur(res) {
  if (!amounts[res.name] || amounts[res.name] < 1) amounts[res.name] = 1
}

// ── Trade ───────────────────────────────────────────────
const totalResources = computed(() =>
  (playerStore.sugar ?? 0) + (playerStore.flour ?? 0) + (playerStore.eggs ?? 0) +
  (playerStore.butter ?? 0) + (playerStore.chocolate ?? 0) + (playerStore.milk ?? 0)
)
const freeStorage = computed(() => Math.max(0, playerStore.totalResourceCap - totalResources.value))

function canBuy(res) {
  return amounts[res.name] > 0 && playerStore.cookies >= buyCost(res) && amounts[res.name] <= freeStorage.value
}
function canSell(res)   { return amounts[res.name] > 0 && playerStore[res.key] >= amounts[res.name] }
function buyCost(res)   { return marketStore.priceOf(res.name) * (amounts[res.name] || 0) }
function netPayout(res) { return buyCost(res) * (1 - sellFeeRate.value) }

function maxBuyQty(res) {
  const price = marketStore.priceOf(res.name)
  if (!price) return 0
  return Math.max(0, Math.min(Math.floor(playerStore.cookies / price), Math.floor(freeStorage.value)))
}
function maxSellQty(res) { return Math.max(0, Math.floor(playerStore[res.key] ?? 0)) }
function setMax(res, action) { amounts[res.name] = action === 'BUY' ? maxBuyQty(res) : maxSellQty(res) }

function costRows(res) {
  return [
    { k: t('marketView.costRowQtyPrice'), v: `${amounts[res.name]} × ${fmt(marketStore.priceOf(res.name))} C`, color: 'w' },
    { k: t('marketView.costRowBuy'), v: `−${fmt(buyCost(res))} C`, color: 'o' },
    { k: t('marketView.costRowSell', { pct: Math.round(sellFeeRate.value * 100) }), v: `+${fmt(netPayout(res))} C`, color: 'g' },
  ]
}

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
    errorMsg.value = e?.message ?? t('marketView.unknownError')
  } finally {
    busy[res.name] = false
  }
}

function fmt(v)  { return Number(v ?? 0).toFixed(2) }
function fmt2(v) { return Number(v ?? 0).toFixed(1) }
function fmtPct(v, base) { const pct = ((Number(v) - base) / base) * 100; return `${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%` }
</script>

<style scoped>
.mv-root { display: flex; flex-direction: column; height: 100%; min-height: 0; background: var(--px-cream); }

.mv-chart-row { flex: 0 0 auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; border-bottom: 4px solid var(--px-ink); }
.mv-legend { display: flex; gap: 6px; flex-wrap: wrap; }
.mv-legend-chip { display: flex; align-items: center; gap: 6px; padding: 6px 10px; background: var(--px-cream2); border: 3px solid var(--px-brown2); font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-ink-txt); cursor: pointer; transition: opacity 0.15s; }
.mv-legend-chip.inactive { opacity: 0.4; }
.mv-legend-chip:hover { opacity: 1; }
.mv-legend-dot { display: inline-block; width: 10px; height: 10px; box-shadow: 0 0 0 2px #402e2b; }
.mv-legend-val { font-size: 12px; letter-spacing: 0.5px; color: var(--px-orange); margin-left: 2px; }
.mv-chart-box { height: 196px; flex: 0 0 auto; position: relative; background: var(--px-ink); border: 4px solid var(--px-ink); padding: 14px; }

.mv-icon { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; background: var(--px-wood3); border: 2px solid var(--px-ink); }

.mv-table { flex: 1 1 auto; min-height: 0; background: var(--px-cream2); }
.mv-table-inner { padding: 10px 16px; display: flex; flex-direction: column; gap: 3px; }
.mv-row {
  display: grid; grid-template-columns: 34px 1fr 90px 84px 100px 156px 190px 200px;
  gap: 10px; align-items: center; padding: 7px 10px; min-width: 930px;
}
.mv-head { background: var(--px-wood); border: 3px solid var(--px-ink); font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-gold); }
.mv-col-cost { text-align: right; }
.mv-row:not(.mv-head) { background: var(--px-cream); border: 2px solid #fff1a9; position: relative; }
.mv-row.success { background: #fff1a9; }

.mv-name  { font-size: 16px; font-weight: 600; color: var(--px-ink-txt); }
.mv-price { font-family: 'Silkscreen', monospace; font-size: 13px; letter-spacing: 0.5px; color: var(--px-orange); }
.mv-trend { font-family: 'Silkscreen', monospace; font-size: 13px; letter-spacing: 0.5px; }
.mv-stock { font-family: 'Silkscreen', monospace; font-size: 13px; letter-spacing: 0.5px; color: var(--px-tan-ink); }

.mv-qty { display: flex; align-items: center; border: 3px solid var(--px-ink); background: var(--px-cream2); width: max-content; }
.mv-qty-btn { font-family: 'Silkscreen', monospace; font-size: 11px; padding: 5px 9px; background: none; border: none; cursor: pointer; color: var(--px-ink-txt); }
.mv-qty-btn:hover { background: #fff1a9; }
.mv-qty-val { padding: 5px 10px; font-family: 'Silkscreen', monospace; font-size: 13px; letter-spacing: 0.5px; border-left: 3px solid var(--px-ink); border-right: 3px solid var(--px-ink); color: var(--px-ink-txt); }
.mv-qty-input { width: 38px; text-align: center; background: none; outline: none; box-sizing: content-box; }

.mv-qty-max { position: relative; display: flex; flex-direction: column; border-left: 3px solid var(--px-ink); }
.mv-max-btn { font-family: 'Silkscreen', monospace; font-size: 8px; line-height: 1; padding: 2px 6px; background: none; border: none; cursor: pointer; }
.mv-max-buy { color: var(--px-green); border-bottom: 2px solid var(--px-ink); }
.mv-max-sell { color: var(--px-red); }
.mv-max-btn:hover:not(:disabled) { background: #fff1a9; }
.mv-max-btn:disabled { opacity: 0.3; cursor: default; }

.mv-max-notice {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  width: max-content;
  max-width: 150px;
  background: var(--px-wood);
  border: 3px solid var(--px-green);
  box-shadow: inset 2px 2px 0 #402e2b, 0 4px 0 rgba(0,0,0,.4);
  padding: 5px 8px;
  font-family: 'Silkscreen', monospace;
  font-size: 9px;
  line-height: 1.4;
  color: #fff1a9;
  text-align: center;
  pointer-events: none;
  z-index: 20;
}
.mv-max-notice-sell { border-color: var(--px-red); }

.mv-actions { display: flex; gap: 6px; }
.mv-action-btn { flex: 1; padding: 6px 0; text-align: center; font-size: 10px; }

.mv-cost { text-align: right; }
.mv-cost-buy  { font-family: 'Silkscreen', monospace; font-size: 15px; letter-spacing: 0.5px; color: var(--px-ink-txt); border-bottom: 2px dashed var(--px-orange); display: inline-block; padding-bottom: 2px; cursor: help; }
.mv-cost-sell { font-family: 'Silkscreen', monospace; font-size: 13px; letter-spacing: 0.5px; color: #56642e; margin-top: 4px; }

.err-overlay { position: absolute; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; z-index: 50; }
.err-dialog { background: var(--px-cream); border: 4px solid var(--px-red); padding: 20px 24px; max-width: 320px; text-align: center; }
.err-title { font-family: 'Silkscreen', monospace; font-weight: 700; font-size: 15px; color: var(--px-red); margin-bottom: 8px; }
.err-body  { font-size: 13px; color: var(--px-ink-txt); line-height: 1.5; }
</style>
