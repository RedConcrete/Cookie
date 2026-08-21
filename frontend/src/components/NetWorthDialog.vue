<template>
  <span ref="wrapRef" class="nw-trigger-wrap" @mouseenter="onTriggerEnter" @mouseleave="onTriggerLeave">
    <slot />

    <Teleport to="body">
      <div
        v-if="popupVisible"
        class="nw-box px-panel" :style="popupStyle"
        @mouseenter="onPopupEnter" @mouseleave="onPopupLeave"
        @wheel.stop @mousedown.stop @mousemove.stop
      >
      <div class="nw-layout">

        <!-- ── Links: Chart + Legende NEBENEINANDER (wie MarketView.vue's mv-chart-row:
             Chart-Box links, schmale Toggle-Legende rechts daneben), Rest der Breite
             rechts fuer die Breakdown-Liste (wie mv-table) ── -->
        <div class="nw-chart-row">
          <div class="nw-chart-box">
            <div class="chart-toolbar">
              <div class="nw-section-label">{{ t('netWorthDialog.historyLabel') }}</div>
              <NestedTooltip :content="t('netWorthDialog.resetZoomTitle')" silent instant>
                <button class="pct-btn" @click="resetZoom"><ShortcutSlot /><PixelIcon name="zentrieren" :size="14" /></button>
              </NestedTooltip>
            </div>
            <div class="chart-wrap">
              <canvas ref="canvasRef"></canvas>
            </div>
          </div>

          <div class="nw-legend">
            <button
              v-for="ds in DATASETS"
              :key="ds.key"
              class="toggle-btn"
              :class="{ inactive: !visible[ds.key] }"
              :style="{ '--dot': ds.color }"
              @click="toggleDataset(ds.key)"
            >
              <ShortcutSlot />
              <span class="dot"></span>{{ t(ds.labelKey) }}
            </button>
          </div>
        </div>

        <!-- ── Rechts: Breakdown + Stats (wie mv-table) ── -->
        <PixelScrollBox class="nw-bottom">
          <div class="nw-bottom-inner">

          <div v-if="nw" class="breakdown">
            <div class="bk-row">
              <PixelIcon name="cookie" :size="16" class="bk-icon" />
              <span class="bk-label">{{ t('netWorthDialog.cookiesLabel') }}</span>
              <div class="bk-bar-wrap">
                <div class="bk-bar" :style="{ width: pct(nw.cookies) + '%', background: '#c78539' }"></div>
              </div>
              <span class="bk-val">{{ fmtBig(nw.cookies) }}</span>
            </div>

            <div class="bk-row">
              <PixelIcon name="mehl" :size="16" class="bk-icon" />
              <span class="bk-label">{{ t('netWorthDialog.resourcesLabel') }}</span>
              <div class="bk-bar-wrap">
                <div class="bk-bar" :style="{ width: pct(nw.resourceValue) + '%', background: '#349c58' }"></div>
              </div>
              <span class="bk-val">{{ fmtBig(nw.resourceValue) }}</span>
            </div>

            <div class="bk-row">
              <PixelIcon name="upgrade" :size="16" class="bk-icon" />
              <span class="bk-label">{{ t('netWorthDialog.skillTreeLabel') }}</span>
              <div class="bk-bar-wrap">
                <div class="bk-bar" :style="{ width: pct(nw.skillTreeValue) + '%', background: '#6f6e72' }"></div>
              </div>
              <span class="bk-val">{{ fmtBig(nw.skillTreeValue) }}</span>
            </div>

            <div class="bk-row bk-row-stat">
              <span class="bk-label">{{ t('netWorthDialog.rankLabel') }}</span>
              <span class="stat-val">#{{ nw.rank }}</span>
            </div>
            <div class="bk-row bk-row-stat">
              <span class="bk-label">{{ t('netWorthDialog.totalLabel') }}</span>
              <span class="stat-val accent">{{ fmtBig(nw.netWorth) }} C</span>
            </div>
          </div>

          <div v-else class="nw-loading"><LoadingIndicator /></div>
          </div>
        </PixelScrollBox>

      </div>
      </div>
    </Teleport>
  </span>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Chart, LineController, LineElement, PointElement,
  LinearScale, TimeScale, Tooltip, Legend
} from 'chart.js'
import 'chartjs-adapter-date-fns'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import ZoomPlugin from 'chartjs-plugin-zoom'
import { getNetWorth, getNetWorthHistory } from '../services/api.js'
import { fmtBig } from '../utils/formatNumber.js'
import { usePlayerStore } from '../stores/player.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import NestedTooltip from './NestedTooltip.vue'

Chart.register(LineController, LineElement, PointElement, LinearScale, TimeScale, Tooltip, Legend, ZoomPlugin)

// Selbststaendiges Hover-Popup statt extern (dialog-State in FarmGridView.vue) gesteuerter
// Dialog -- Trigger kommt per Slot rein, wie PixelInfoPopover.vue (das die alte, einfache
// Hover-Vorschau war und durch dieses Popup hier komplett ersetzt wird). Kein Klick, kein
// Titlebar/Drag/Close-Button mehr noetig.
const props = defineProps({ steamId: { type: String, required: true } })
const audio = useAudio()
const { t } = useI18n()

const playerStore = usePlayerStore()

// Feste Panel-Groesse (siehe .nw-box CSS) statt Nach-dem-Rendern-Messen -- Position steht
// so schon vorm ersten Frame fest, kein Flackern (gleiche Lektion wie bei NestedTooltip.vue's
// positionPopup()). Ankert unten rechts am eigenen Trigger-Wrapper (wrapRef), wie
// PixelInfoPopover's side="below-right".
const PANEL_WIDTH = 820
const PANEL_HEIGHT = 420
const EDGE_MARGIN = 8
const GAP_BELOW = 14

const wrapRef = ref(null)
const anchorPos = ref({ left: EDGE_MARGIN, top: EDGE_MARGIN })

function computeAnchorPos() {
  if (!wrapRef.value) return
  const rect = wrapRef.value.getBoundingClientRect()
  let left = rect.right - PANEL_WIDTH
  let top  = rect.bottom + GAP_BELOW
  left = Math.min(Math.max(left, EDGE_MARGIN), window.innerWidth - PANEL_WIDTH - EDGE_MARGIN)
  top  = Math.min(Math.max(top, EDGE_MARGIN), window.innerHeight - PANEL_HEIGHT - EDGE_MARGIN)
  anchorPos.value = { left, top }
}

const popupStyle = computed(() => ({
  position: 'fixed',
  left: anchorPos.value.left + 'px',
  top: anchorPos.value.top + 'px',
}))

// Hover-Reveal mit kurzer Zu-Verzoegerung (Maus darf vom Trigger in den Popup wandern, ohne
// dass er zwischendurch zuklappt) -- gleiches Grundmuster wie NestedTooltip.vue's
// Drain-Delay/PixelInfoPopover's useHoverReveal, hier eigenstaendig gehalten, weil dieses
// Popup zusaetzlich lazy beim ERSTEN Oeffnen die Chart-Daten laedt (siehe loadData unten).
const popupVisible = ref(false)
const CLOSE_DELAY = 250
let closeTimer = null
let dataInitialized = false

async function onTriggerEnter() {
  clearTimeout(closeTimer)
  if (popupVisible.value) return
  computeAnchorPos()
  popupVisible.value = true
  if (!dataInitialized) {
    dataInitialized = true
    audio.playBookOpen()
    loadData()
  } else {
    // War schon mal offen: fullHistory ist noch da, aber der Canvas wurde beim letzten
    // Schliessen (v-if) aus dem DOM entfernt und mit ihm die alte Chart.js-Instanz zerstoert
    // (siehe onTriggerLeave) -- nach dem naechsten Tick (neuer Canvas im DOM) neu aufbauen.
    await nextTick()
    initChart()
  }
}
function onTriggerLeave() {
  closeTimer = setTimeout(() => {
    popupVisible.value = false
    chart?.destroy()
    chart = null
  }, CLOSE_DELAY)
}
function onPopupEnter() {
  clearTimeout(closeTimer)
}
function onPopupLeave() {
  onTriggerLeave()
}

// Fuer den Networth-Hotkey (Tastatur/Controller, siehe FarmGridView.vue) -- ohne Hover gibt es
// kein "Maus verlaesst den Trigger"-Ereignis, das automatisch schliessen wuerde, also simple
// Auf/Zu-Umschaltung per erneutem Tastendruck statt eines Delay-basierten Auto-Close.
function toggle() {
  if (popupVisible.value) {
    clearTimeout(closeTimer)
    popupVisible.value = false
    chart?.destroy()
    chart = null
  } else {
    onTriggerEnter()
  }
}
defineExpose({ toggle })

const DATASETS = [
  { key: 'netWorth',      labelKey: 'netWorthDialog.netWorthLabel', color: '#aea47e' },
  { key: 'cookies',       labelKey: 'netWorthDialog.cookiesLabel',  color: '#c78539' },
  { key: 'resourceValue', labelKey: 'netWorthDialog.resourcesLabel',color: '#349c58' },
  { key: 'skillTreeValue', labelKey: 'netWorthDialog.skillTreeLabel', color: '#6f6e72' },
]

const nw        = ref(null)
const canvasRef = ref(null)
const visible   = reactive(Object.fromEntries(DATASETS.map(d => [d.key, true])))
let chart        = null
let fullHistory  = []
let userHasMoved = false
let historyTimer = null

const INITIAL_WINDOW_MS = 10 * 60 * 1000

function pct(val) {
  if (!nw.value?.netWorth) return 0
  return Math.min(100, (val / nw.value.netWorth) * 100)
}

function latestMs() {
  return fullHistory.length ? new Date(fullHistory[fullHistory.length - 1].timestamp).getTime() : Date.now()
}

function buildDatasets() {
  return DATASETS.map(d => ({
    label: t(d.labelKey),
    dataKey: d.key,
    data: fullHistory.map(h => ({ x: new Date(h.timestamp), y: h[d.key] })),
    borderColor: d.color,
    backgroundColor: d.color + '22',
    borderWidth: 2,
    pointRadius: 0,
    tension: 0,
    stepped: true,
    hidden: !visible[d.key],
  }))
}

function computeYRange() {
  const active = DATASETS.filter(d => visible[d.key])
  if (!active.length || !fullHistory.length) return null
  let min = Infinity, max = -Infinity
  for (const entry of fullHistory) {
    for (const d of active) {
      const v = entry[d.key] ?? 0
      if (v > 0) { if (v < min) min = v; if (v > max) max = v }
    }
  }
  if (!isFinite(min)) return null
  const pad = (max - min) * 0.08 || Math.abs(max) * 0.05 || 0.1
  return { min: Math.max(0, min - pad), max: max + pad }
}

function applyYRange() {
  if (!chart) return
  const range = computeYRange()
  if (range) {
    chart.options.scales.y.min = range.min
    chart.options.scales.y.max = range.max
  } else {
    delete chart.options.scales.y.min
    delete chart.options.scales.y.max
  }
}

function rebuildChart() {
  if (!chart) return
  chart.data.datasets = buildDatasets()
  applyYRange()
  chart.update('none')
}

function toggleDataset(key) {
  visible[key] = !visible[key]
  rebuildChart()
}

function resetZoom() {
  chart?.resetZoom()
  userHasMoved = false
}

function initChart() {
  if (!canvasRef.value || !fullHistory.length) return

  const maxMs    = latestMs()
  const minMs    = maxMs - INITIAL_WINDOW_MS
  const oldestMs = fullHistory.length ? new Date(fullHistory[0].timestamp).getTime() : maxMs - 30 * 24 * 60 * 60 * 1000

  const tipLabelsPlugin = {
    id: 'tipLabels',
    afterDraw(c) {
      const ctx    = c.ctx
      const yScale = c.scales.y
      const items  = c.data.datasets
        .map((ds, i) => ({ ds, meta: c.getDatasetMeta(i) }))
        .filter(({ ds, meta }) => !ds.hidden && meta.data.length)
        .map(({ ds, meta }) => {
          const pt  = meta.data[meta.data.length - 1]
          const val = ds.data[ds.data.length - 1]?.y
          return { color: ds.borderColor, label: fmtBig(val), x: pt.x, y: pt.y }
        })
        .sort((a, b) => a.y - b.y)

      const LINE_H = 14
      for (let k = 1; k < items.length; k++) {
        if (items[k - 1].y + LINE_H > items[k].y)
          items[k].y = items[k - 1].y + LINE_H
      }

      ctx.save()
      ctx.font = 'bold 10px Silkscreen'
      ctx.textAlign = 'left'
      for (const { color, label, x, y } of items) {
        ctx.fillStyle = color
        ctx.fillText(label, x + 6, Math.min(y + 4, yScale.bottom - 2))
      }
      ctx.restore()
    },
  }

  chart = new Chart(canvasRef.value, {
    type: 'line',
    data: { datasets: buildDatasets() },
    plugins: [tipLabelsPlugin],
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend:  { display: false },
        tooltip: { enabled: false },
        zoom: {
          limits: {
            x: { min: oldestMs, max: maxMs + 1000, minRange: 30_000 },
          },
          pan: {
            enabled: true,
            mode: 'x',
            onPanComplete: () => { userHasMoved = true },
          },
          zoom: {
            wheel: { enabled: true },
            pinch: { enabled: true },
            mode: 'x',
            onZoomComplete: () => { userHasMoved = true },
          },
        },
      },
      scales: {
        x: {
          type: 'time',
          min: minMs,
          max: maxMs,
          time: {
            displayFormats: {
              second: 'HH:mm:ss', minute: 'HH:mm',
              hour: 'HH:mm', day: 'dd.MM.', week: 'dd.MM.', month: 'MM.yy',
            },
          },
          ticks: { color: '#aea47e', maxTicksLimit: 6, maxRotation: 0, font: { family: 'Silkscreen' } },
          grid:  { color: 'rgba(255,255,255,0.06)' },
        },
        y: {
          ticks: { color: '#aea47e', maxTicksLimit: 6, font: { family: 'Silkscreen' } },
          grid:  { color: 'rgba(255,255,255,0.06)' },
        },
      },
    },
  })

  applyYRange()
  chart.update('none')
}

// Breakdown live aus playerStore
watch(
  () => [playerStore.netWorth, playerStore.nwCookies, playerStore.nwResources, playerStore.nwSkillTreeValue],
  ([netWorth, cookies, resourceValue, skillTreeValue]) => {
    if (!nw.value) return
    nw.value = { ...nw.value, netWorth, cookies, resourceValue, skillTreeValue }
  }
)

async function refreshHistory() {
  const history = await getNetWorthHistory(props.steamId).catch(() => [])
  if (!history.length) return
  const sorted = [...history].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
  const knownTs = new Set(fullHistory.map(h => String(h.timestamp)))
  const newEntries = sorted.filter(h => !knownTs.has(String(h.timestamp)))
  if (!newEntries.length) return
  fullHistory = [...fullHistory, ...newEntries]
  if (!chart) { initChart(); return }
  rebuildChart()
  if (!userHasMoved && chart) {
    const newMax = latestMs()
    const viewWidth = chart.scales.x.max - chart.scales.x.min
    chart.options.scales.x.min = newMax - viewWidth
    chart.options.scales.x.max = newMax
    chart.options.plugins.zoom.limits.x.max = newMax + 1000
    chart.update('none')
  }
}

// Lazy: erst beim ERSTEN Hover-Oeffnen laden (Popup ist jetzt dauerhaft im DOM, siehe
// wrapRef oben, nicht mehr per v-if von aussen gemountet -- ein Datenfetch bei jedem
// FarmGridView-Mount waere Verschwendung, wenn der Spieler nie hovert).
async function loadData() {
  const [nwData, history] = await Promise.all([
    getNetWorth(props.steamId).catch(() => null),
    getNetWorthHistory(props.steamId).catch(() => []),
  ])
  nw.value = nwData
  fullHistory = [...history].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
  await nextTick()
  initChart()
  historyTimer = setInterval(refreshHistory, 30_000)
}

onUnmounted(() => {
  chart?.destroy()
  clearInterval(historyTimer)
})
</script>

<style scoped>
/* Traeger fuer den Slot-Trigger (HUD-Chip) -- Layout/Groesse kommt vom Aufrufer per Klasse
   (siehe .hud-networth-wrap in FarmGridView.vue), hier nur die Hover-Erkennung noetig,
   analog PixelInfoPopover.vue's .pip-wrap. */
.nw-trigger-wrap { position: relative; }

.nw-box {
  width: 820px; max-width: 96vw; height: 420px;
  display: flex; flex-direction: column; overflow: hidden;
  z-index: 600; /* wie PixelInfoPopover -- Popup statt Vollbild-Dialog, kein Overlay-Parent mit eigenem z-index mehr */
}

/* Chart+Legende links (nebeneinander, wie MarketView.vue's mv-chart-row), Breakdown-
   Liste rechts (wie mv-table) -- MarketView.vue's mv-root ist selbst flex-direction:row,
   Chart-Bereich und Tabelle sitzen NEBENEINANDER, nicht uebereinander. */
.nw-layout {
  display: flex; flex-direction: row; flex: 1; min-height: 0; overflow: hidden;
}

.nw-section-label {
  font-family: 'Silkscreen', monospace; font-size: 10px;
  letter-spacing: 1px; color: var(--px-wood2); margin-bottom: 4px;
}

/* ── Links: Chart-Box + schmale Toggle-Legende nebeneinander ──── */
.nw-chart-row {
  flex: 0 0 62%; display: flex; flex-direction: row; gap: 10px;
  padding: 16px; min-width: 0;
  border-right: 3px solid var(--px-tan);
}
.nw-chart-box { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.nw-legend {
  flex: 0 0 100px; width: 100px;
  display: flex; flex-direction: column; gap: 6px; overflow-y: auto;
}

/* ── Rechts: Breakdown + Stats ──────────────────── */
.nw-bottom { flex: 1 1 260px; min-width: 220px; min-height: 0; }
.nw-bottom-inner { padding: 16px; }

.breakdown { display: flex; flex-direction: column; gap: 10px; }
.bk-row { display: flex; align-items: center; gap: 8px; }
.bk-icon  { flex-shrink: 0; }
.bk-label { width: 92px; flex-shrink: 0; font-size: 13px; color: var(--px-ink-txt); white-space: nowrap; }
.bk-bar-wrap {
  flex: 1; height: 10px; background: var(--px-ink);
  border: 2px solid var(--px-ink); overflow: hidden;
}
.bk-bar { height: 100%; transition: width 0.4s; }
.bk-val { width: 52px; text-align: right; font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }

.bk-row-stat { padding-top: 6px; border-top: 2px solid #fff1a9; justify-content: space-between; }
.bk-row-stat:first-of-type { margin-top: 2px; }
.stat-val   { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-ink-txt); }
.stat-val.accent { color: #56642e; }

.nw-loading { color: var(--px-tan-ink); font-size: 13px; }

.chart-toolbar {
  display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0;
}

.toggle-btn {
  position: relative; width: 100%; box-sizing: border-box;
  display: flex; align-items: center; gap: 5px;
  padding: 4px 9px; border: 2px solid var(--px-brown2);
  background: var(--px-cream2);
  color: var(--px-ink-txt); font-size: 11px; cursor: pointer;
  transition: opacity 0.15s;
}
.toggle-btn.inactive { opacity: 0.35; }
.toggle-btn:hover    { opacity: 1; }

.dot {
  width: 8px; height: 8px;
  background: var(--dot); flex-shrink: 0; box-shadow: 0 0 0 1px #402e2b;
}

.pct-btn {
  position: relative;
  padding: 4px 10px; border: 2px solid var(--px-brown2);
  background: var(--px-cream2);
  display: flex; align-items: center; justify-content: center;
  color: var(--px-ink-txt); font-size: 12px; font-weight: 700;
  cursor: pointer; transition: background 0.15s, color 0.15s; white-space: nowrap;
}
.pct-btn:hover { color: var(--px-ink-txt); background: #fff1a9; }

/* Padding, damit die Y-Achsen-Ticks (z.B. "53") nicht direkt am Rand kleben und
   abgeschnitten wirken -- .mv-chart-box im Markt-Dialog hat aus demselben Grund
   padding:14px, hier gab es bisher gar keins. */
.chart-wrap { flex: 1; min-height: 0; position: relative; padding: 4px 8px 4px 2px; box-sizing: border-box; }
</style>
