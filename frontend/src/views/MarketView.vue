<template>
  <div class="mv-root">
    <div class="mv-chart-row">
      <div class="mv-legend">
        <button
          v-for="r in resources" :key="r.name"
          :ref="el => legendEls[r.name] = el"
          class="mv-legend-chip"
          :class="{ inactive: chartRef && chartRef.visible[r.name] === false }"
          @click="chartRef?.toggle(r.name)"
          @mouseenter="hoverLegendKey = r.name" @mouseleave="hoverLegendKey = null"
        >
          <span class="mv-legend-dot" :style="{ background: COLORS[r.name] }"></span>
          <span class="mv-legend-name">{{ t(r.labelKey) }}</span>
          <span class="mv-legend-val">{{ legendValue(r) }}</span>
        </button>
        <PixelTip
          v-for="r in resources" :key="'lt:'+r.name"
          :anchor="legendEls[r.name]" :text="t(r.labelKey)" :visible="hoverLegendKey === r.name"
        />
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
        <div class="mv-info">
          <div></div><div>{{ t('marketView.colResource') }}</div><div>{{ t('marketView.colPrice') }}</div><div>{{ t('marketView.colTrend') }}</div><div>{{ t('marketView.colStock') }}</div>
        </div>
        <div class="mv-controls"><div>{{ t('marketView.colQty') }}</div><div>{{ t('marketView.colAction') }}</div></div>
      </div>

      <div v-for="res in resources" :key="res.name" class="mv-row" :class="{ success: flashSuccess[res.name] }" :style="rowBorderStyle(res)">
        <div class="mv-info">
          <div
            class="mv-icon" :ref="el => iconEls[res.name] = el"
            @mouseenter="hoverIconKey = res.name" @mouseleave="hoverIconKey = null"
          >
            <PixelIcon :name="res.icon" :size="20" />
          </div>
          <PixelTip :anchor="iconEls[res.name]" :text="t(res.labelKey)" :visible="hoverIconKey === res.name" />
          <div class="mv-name">{{ t(res.labelKey) }}</div>
          <div class="mv-price">{{ fmt(marketStore.priceOf(res.name)) }}</div>
          <div class="mv-trend" :style="{ color: trendOf(res.name) >= 0 ? '#56642e' : '#b74132' }">
            {{ trendOf(res.name) >= 0 ? '+' : '' }}{{ trendOf(res.name).toFixed(1) }} %
          </div>
          <div class="mv-stock">{{ fmt2(playerStore[res.key]) }}</div>
        </div>

        <div class="mv-controls">
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
                class="mv-max-btn mv-max-buy" :ref="el => maxBuyEls[res.name] = el" :disabled="maxBuyQty(res) <= 0"
                @click="setMax(res, 'BUY')"
                @mouseenter="hoverMaxKey = res.name + ':BUY'" @mouseleave="hoverMaxKey = null"
              >&#9650;</button>
              <PixelTip :anchor="maxBuyEls[res.name]" :text="t('marketView.maxBuyTitle')" :visible="hoverMaxKey === res.name + ':BUY'" />
              <button
                class="mv-max-btn mv-max-sell" :ref="el => maxSellEls[res.name] = el" :disabled="maxSellQty(res) <= 0"
                @click="setMax(res, 'SELL')"
                @mouseenter="hoverMaxKey = res.name + ':SELL'" @mouseleave="hoverMaxKey = null"
              >&#9660;</button>
              <PixelTip :anchor="maxSellEls[res.name]" variant="sell" :text="t('marketView.maxSellTitle')" :visible="hoverMaxKey === res.name + ':SELL'" />
            </div>
          </div>

          <div class="mv-actions">
            <div class="mv-actions-buttons">
              <div class="mv-action-wrap">
                <button
                  class="px-btn px-btn-buy mv-action-btn" :ref="el => buyBtnEls[res.name] = el" :disabled="busy[res.name] || !canBuy(res)"
                  @click="doTrade(res, 'BUY')"
                  @mouseenter="hoverActionKey = res.name + ':BUY'" @mouseleave="hoverActionKey = null"
                >{{ t('marketView.buyButton') }}</button>
                <PixelTip :anchor="buyBtnEls[res.name]" :text="buyPreviewText(res)" :visible="hoverActionKey === res.name + ':BUY'" />
              </div>
              <div class="mv-action-wrap">
                <button
                  class="px-btn px-btn-sell mv-action-btn" :ref="el => sellBtnEls[res.name] = el" :disabled="busy[res.name] || !canSell(res)"
                  @click="doTrade(res, 'SELL')"
                  @mouseenter="hoverActionKey = res.name + ':SELL'" @mouseleave="hoverActionKey = null"
                >{{ t('marketView.sellButton') }}</button>
                <PixelTip :anchor="sellBtnEls[res.name]" variant="sell" :text="sellPreviewText(res)" :visible="hoverActionKey === res.name + ':SELL'" />
              </div>
            </div>
            <div class="mv-trade-status" :class="{ visible: isPendingRow(res) }">
              <PixelIcon name="sanduhr" :size="12" class="mv-lock-icon" />
              <span>{{ t('marketView.tradeProcessing') }}</span>
            </div>
          </div>
        </div>
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
import { roundUp } from '../utils/formatNumber.js'
import { useAudio } from '../composables/useAudio.js'
import PriceChart from '../components/PriceChart.vue'
import PixelIcon from '../components/pixel/PixelIcon.vue'
import PixelScrollBox from '../components/pixel/PixelScrollBox.vue'
import PixelTip from '../components/pixel/PixelTip.vue'
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
const hoverActionKey  = ref(null)
const hoverLegendKey  = ref(null)
const hoverIconKey    = ref(null)

// Anker-Elemente fuer PixelTip (teleportierte Hover-Popovers) -- per
// Funktions-Ref aus dem v-for befuellt, ein Eintrag pro Ressourcenname.
const legendEls  = reactive({})
const iconEls    = reactive({})
const maxBuyEls  = reactive({})
const maxSellEls = reactive({})
const buyBtnEls  = reactive({})
const sellBtnEls = reactive({})

// ── Trade-Sperre (Anti-Spam) ────────────────────────────
// Serverseitiger Cooldown gespiegelt (siehe ConfigController/MarketConfig#getTradeCooldownMs),
// Fallback 2 Ticks * 2000ms falls /api/v1/config noch nicht geladen ist -- lieber einen Tick zu
// vorsichtig sperren als den echten Wert zu unterschreiten. Gilt global fuer Buy/Sell ueber alle
// Ressourcen (nicht pro Ressource), zeigt eine Pixel-Sanduhr solange die Sperre laeuft. Der
// eigentliche Schutz bleibt der serverseitige Check in MarketService#performAction.
const tradeCooldownMs = ref(4000)
const tradeLockedUntil = ref(0)
const lockTick = ref(Date.now())
let lockTicker = null
function startLockTicker() {
  if (lockTicker) return
  lockTicker = setInterval(() => {
    lockTick.value = Date.now()
    if (lockTick.value >= tradeLockedUntil.value) {
      clearInterval(lockTicker)
      lockTicker = null
    }
  }, 100)
}
onUnmounted(() => { if (lockTicker) clearInterval(lockTicker) })
const isTradeLocked = computed(() => lockTick.value < tradeLockedUntil.value)

// Welcher Trade (Ressource + Aktion) die aktuelle Sperre ausgeloest hat -- der Status-Hinweis
// erscheint nur inline neben den Buttons dieser einen Zeile, kein globales Popover.
const lastTradeKey = ref(null)
function isPendingRow(res) {
  return isTradeLocked.value && lastTradeKey.value?.startsWith(res.name + ':')
}

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
  try {
    const cfg = await getConfig()
    sellFeeRate.value = cfg.sellFeeRate ?? 0.08
    if (cfg.marketTradeCooldownMs) tradeCooldownMs.value = cfg.marketTradeCooldownMs
  } catch {}
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
// Hauptlager ist ein gemeinsamer Topf ueber alle 6 Rohstoffe (analog zu
// UserService#harvest) -- der freie Platz fuer einen Kauf haengt vom Gesamtbestand ab,
// nicht nur vom aktuellen Stand dieses einen Rohstoffs.
function freeStorageFor(res) { return Math.max(0, playerStore.totalResourceCap - playerStore.totalResources) }

function canBuy(res) {
  return !isTradeLocked.value && amounts[res.name] > 0 && playerStore.cookies >= buyCost(res) && amounts[res.name] <= freeStorageFor(res)
}
function canSell(res)   { return !isTradeLocked.value && amounts[res.name] > 0 && playerStore[res.key] >= amounts[res.name] }
function buyCost(res)   { return marketStore.priceOf(res.name) * (amounts[res.name] || 0) }
function netPayout(res) { return buyCost(res) * (1 - sellFeeRate.value) }

function maxBuyQty(res) {
  const price = marketStore.priceOf(res.name)
  if (!price) return 0
  return Math.max(0, Math.min(Math.floor(playerStore.cookies / price), Math.floor(freeStorageFor(res))))
}
function maxSellQty(res) { return Math.max(0, Math.floor(playerStore[res.key] ?? 0)) }
function setMax(res, action) { amounts[res.name] = action === 'BUY' ? maxBuyQty(res) : maxSellQty(res) }

function buyPreviewText(res)  { return `${amounts[res.name]} × ${fmt(marketStore.priceOf(res.name))} C = −${fmt(buyCost(res))} C` }
function sellPreviewText(res) { return `${amounts[res.name]} × ${fmt(marketStore.priceOf(res.name))} C = +${fmt(netPayout(res))} C` }

async function doTrade(res, action) {
  if (busy[res.name] || isTradeLocked.value) return
  busy[res.name] = true
  // Sofort setzen, nicht erst nach der Antwort -- sonst kann ein zweiter Klick (auf
  // dieselbe oder eine andere Ressource) noch vor dem ersten Response durchrutschen.
  tradeLockedUntil.value = Date.now() + tradeCooldownMs.value
  lockTick.value = Date.now()
  lastTradeKey.value = res.name + ':' + action
  startLockTicker()
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

function fmt(v)  { return roundUp(v, 2).toFixed(2) }
function fmt2(v) { return roundUp(v, 1).toFixed(1) }
function fmtPct(v, base) { const pct = ((Number(v) - base) / base) * 100; return `${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%` }
</script>

<style scoped>
/* Layout ist immer Chart links / Ressourcen-Panel rechts (kein separates
   "breiter Bildschirm"-Design mehr) -- .md-box (MarketDialog.vue) ist nie
   schmaler als 1260px, ein Umschalten auf ein spaltenweises Layout war
   dadurch ohnehin nie erreichbar. */
.mv-root { display: flex; flex-direction: row; height: 100%; min-height: 0; background: var(--px-cream); }

.mv-chart-row {
  flex: 0 0 66%; flex-direction: row; padding: 16px; gap: 12px;
  display: flex; border-right: 4px solid var(--px-ink); overflow: hidden;
}
.mv-chart-box { order: 1; flex: 1 1 auto; height: auto; min-width: 0; position: relative; background: var(--px-cream); border: 4px solid var(--px-ink); padding: 14px; overflow: hidden; }

.mv-legend {
  order: 2; flex: 0 0 76px; width: 76px;
  display: flex; flex-direction: column; flex-wrap: nowrap; gap: 6px;
  overflow-y: auto; overflow-x: hidden; position: relative; z-index: 1;
}
.mv-legend-chip {
  position: relative; flex: 0 0 auto; width: 100%; box-sizing: border-box;
  display: flex; flex-direction: row; align-items: center; gap: 4px;
  padding: 4px 6px; background: var(--px-cream2); border: 3px solid var(--px-brown2);
  font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-ink-txt);
  cursor: pointer; transition: opacity 0.15s;
}
.mv-legend-chip.inactive { opacity: 0.4; }
.mv-legend-chip:hover { opacity: 1; }
.mv-legend-name { display: none; }
.mv-legend-dot { display: inline-block; width: 8px; height: 8px; box-shadow: 0 0 0 2px #402e2b; }
.mv-legend-val { font-size: 9px; letter-spacing: 0.5px; color: var(--px-ink-txt); margin-left: 0; }

.mv-icon { position: relative; width: 28px; height: 28px; flex: 0 0 auto; display: flex; align-items: center; justify-content: center; background: var(--px-wood3); border: 2px solid var(--px-ink); }

.mv-table { flex: 1 1 360px; min-width: 300px; min-height: 0; background: var(--px-cream2); }
.mv-table-inner { padding: 10px 12px; display: flex; flex-direction: column; gap: 6px; }
.mv-row.mv-head { display: none; }
.mv-row {
  display: flex; flex-direction: column; align-items: stretch; gap: 6px;
  padding: 8px 10px; min-width: 0;
}
.mv-info { display: flex; flex-wrap: nowrap; align-items: center; gap: 8px; min-width: 0; }
.mv-controls { display: flex; flex-wrap: nowrap; align-items: center; justify-content: space-between; gap: 8px; }
.mv-controls > :last-child { flex: 0 0 auto; }
.mv-name { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.mv-actions { flex: 0 0 auto; }
.mv-action-wrap { flex: 0 0 auto; }
.mv-action-btn { width: 74px; padding: 5px 0; font-size: 9px; }
.mv-head { background: var(--px-wood); border: 3px solid var(--px-ink); font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-gold); }
.mv-row:not(.mv-head) { background: var(--px-cream); border: 2px solid #fff1a9; position: relative; }
.mv-row.success { background: #fff1a9; }

.mv-name  { font-weight: 600; color: var(--px-ink-txt); }
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

.mv-actions { display: flex; flex-direction: column; gap: 4px; }
.mv-actions-buttons { display: flex; gap: 6px; }
.mv-action-wrap { position: relative; }

/* Immer im DOM (kein v-if), nur visibility umgeschaltet -- reserviert dauerhaft die Zeilenhoehe,
   damit Kaufen/Verkaufen beim Ein-/Ausblenden nicht springen (siehe .mv-actions-buttons). */
.mv-trade-status { display: flex; align-items: center; gap: 4px; height: 12px; font-family: 'Silkscreen', monospace; font-size: 8px; letter-spacing: 0.3px; color: var(--px-tan-ink); visibility: hidden; }
.mv-trade-status.visible { visibility: visible; }
.mv-trade-status .mv-lock-icon { animation: mv-lock-flip 1s steps(1) infinite; }
@keyframes mv-lock-flip {
  0%, 49% { transform: rotate(0deg); }
  50%, 100% { transform: rotate(180deg); }
}

.err-overlay { position: absolute; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; z-index: 50; }
.err-dialog { background: var(--px-cream); border: 4px solid var(--px-red); padding: 20px 24px; max-width: 320px; text-align: center; }
.err-title { font-family: 'Silkscreen', monospace; font-weight: 700; font-size: 15px; color: var(--px-red); margin-bottom: 8px; }
.err-body  { font-size: 13px; color: var(--px-ink-txt); line-height: 1.5; }

/* Container Query auf .md-box (MarketDialog.vue), nicht @media -- soll auf
   tatsaechliche Dialogbreite reagieren. Ab genug Breite mehr Luft: breiteres
   Ressourcen-Panel, Legend-Chips mit Label statt nur Farbpunkt. */
@container (min-width: 1500px) {
  .mv-table { flex-basis: 460px; }
  .mv-legend { flex-basis: 130px; width: 130px; }
  .mv-legend-chip { padding: 5px 8px; font-size: 10px; }
  .mv-legend-name { display: inline; }
  .mv-legend-dot { width: 10px; height: 10px; }
  .mv-legend-val { font-size: 10px; }

  .mv-info { gap: 10px; }
  .mv-name { font-size: 15px; }
  .mv-controls { flex-wrap: wrap; }
  .mv-actions-buttons { gap: 8px; }
  .mv-action-btn { width: 84px; padding: 6px 0; font-size: 10px; }
}
</style>
