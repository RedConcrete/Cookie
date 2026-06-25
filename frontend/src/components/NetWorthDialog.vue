<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="dialog-box">
      <div class="dialog-header">
        <span class="dialog-title">Net Worth — {{ fmtBig(nw?.netWorth) }}</span>
        <button class="dialog-close" @click="emit('close')">✕</button>
      </div>

      <div class="nw-layout">

        <!-- ── Links: Breakdown ───────────────────────── -->
        <div class="nw-left">

          <div class="nw-section-label">Aufschlüsselung</div>

          <div v-if="nw" class="breakdown">
            <div class="bk-row">
              <span class="bk-icon">🍪</span>
              <span class="bk-label">Cookies</span>
              <div class="bk-bar-wrap">
                <div class="bk-bar" :style="{ width: pct(nw.cookies) + '%', background: '#ef9f27' }"></div>
              </div>
              <span class="bk-val">{{ fmtBig(nw.cookies) }}</span>
            </div>

            <div class="bk-row">
              <span class="bk-icon">🌾</span>
              <span class="bk-label">Ressourcen</span>
              <div class="bk-bar-wrap">
                <div class="bk-bar" :style="{ width: pct(nw.resourceValue) + '%', background: '#4a9c40' }"></div>
              </div>
              <span class="bk-val">{{ fmtBig(nw.resourceValue) }}</span>
            </div>

            <div class="bk-row">
              <span class="bk-icon">⬆</span>
              <span class="bk-label">Upgrades</span>
              <div class="bk-bar-wrap">
                <div class="bk-bar" :style="{ width: pct(nw.upgradeValue) + '%', background: '#7F77DD' }"></div>
              </div>
              <span class="bk-val">{{ fmtBig(nw.upgradeValue) }}</span>
            </div>
          </div>

          <div v-else class="nw-loading">Lade…</div>

          <div class="nw-divider"></div>

          <template v-if="nw">
            <div class="stat-row">
              <span class="stat-label">Rang</span>
              <span class="stat-val">#{{ nw.rank }}</span>
            </div>
            <div class="stat-row">
              <span class="stat-label">Gesamt</span>
              <span class="stat-val accent">{{ fmtBig(nw.netWorth) }} C</span>
            </div>
          </template>
        </div>

        <!-- ── Rechts: Chart ──────────────────────────── -->
        <div class="nw-right">
          <div class="nw-section-label">Verlauf</div>

          <div class="chart-toolbar">
            <div class="chart-toggles">
              <button
                v-for="ds in DATASETS"
                :key="ds.key"
                class="toggle-btn"
                :class="{ inactive: !visible[ds.key] }"
                :style="{ '--dot': ds.color }"
                @click="toggleDataset(ds.key)"
              >
                <span class="dot"></span>{{ ds.label }}
              </button>
            </div>
            <button class="pct-btn" @click="resetZoom" title="Zoom zurücksetzen">⊙</button>
          </div>

          <div class="chart-wrap">
            <canvas ref="canvasRef"></canvas>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import {
  Chart, LineController, LineElement, PointElement,
  LinearScale, TimeScale, Tooltip, Legend
} from 'chart.js'
import 'chartjs-adapter-date-fns'
import ZoomPlugin from 'chartjs-plugin-zoom'
import { getNetWorth, getNetWorthHistory } from '../services/api.js'
import { usePlayerStore } from '../stores/player.js'

Chart.register(LineController, LineElement, PointElement, LinearScale, TimeScale, Tooltip, Legend, ZoomPlugin)

const props = defineProps({ steamId: { type: String, required: true } })
const emit  = defineEmits(['close'])

const playerStore = usePlayerStore()

const DATASETS = [
  { key: 'netWorth',     label: 'Net Worth',  color: '#aaff88' },
  { key: 'cookies',      label: 'Cookies',    color: '#ef9f27' },
  { key: 'resourceValue',label: 'Ressourcen', color: '#4a9c40' },
  { key: 'upgradeValue', label: 'Upgrades',   color: '#7F77DD' },
]

const nw        = ref(null)
const canvasRef = ref(null)
const visible   = reactive(Object.fromEntries(DATASETS.map(d => [d.key, true])))
let chart        = null
let fullHistory  = []
let userHasMoved = false
let historyTimer = null

const INITIAL_WINDOW_MS = 10 * 60 * 1000

function fmtBig(v) {
  if (!v) return '0'
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v).toFixed(1)
}

function pct(val) {
  if (!nw.value?.netWorth) return 0
  return Math.min(100, (val / nw.value.netWorth) * 100)
}

function latestMs() {
  return fullHistory.length ? new Date(fullHistory[fullHistory.length - 1].timestamp).getTime() : Date.now()
}

function buildDatasets() {
  return DATASETS.map(d => ({
    label: d.label,
    dataKey: d.key,
    data: fullHistory.map(h => ({ x: new Date(h.timestamp), y: h[d.key] })),
    borderColor: d.color,
    backgroundColor: d.color + '22',
    borderWidth: 2,
    pointRadius: 0,
    tension: 0.3,
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
      ctx.font = 'bold 10px monospace'
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
          ticks: { color: '#999', maxTicksLimit: 6, maxRotation: 0 },
          grid:  { color: 'rgba(255,255,255,0.06)' },
        },
        y: {
          ticks: { color: '#999', maxTicksLimit: 6 },
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
  () => [playerStore.netWorth, playerStore.nwCookies, playerStore.nwResources, playerStore.nwUpgrades],
  ([netWorth, cookies, resourceValue, upgradeValue]) => {
    if (!nw.value) return
    nw.value = { ...nw.value, netWorth, cookies, resourceValue, upgradeValue }
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

onMounted(async () => {
  const [nwData, history] = await Promise.all([
    getNetWorth(props.steamId).catch(() => null),
    getNetWorthHistory(props.steamId).catch(() => []),
  ])
  nw.value = nwData
  fullHistory = [...history].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp))
  initChart()
  historyTimer = setInterval(refreshHistory, 30_000)
})

onUnmounted(() => {
  chart?.destroy()
  clearInterval(historyTimer)
})
</script>

<style scoped>
.dialog-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.55);
  display: flex; align-items: center; justify-content: center; z-index: 300;
}
.dialog-box {
  background: var(--surface); border: 2px solid var(--border);
  border-radius: 16px; width: 820px; max-width: 96vw; height: 480px;
  display: flex; flex-direction: column; overflow: hidden;
}
.dialog-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px 10px; border-bottom: 1px solid var(--border); flex-shrink: 0;
}
.dialog-title { font-size: 14px; font-weight: 700; color: var(--text); }
.dialog-close {
  background: none; border: none; font-size: 18px; cursor: pointer;
  color: var(--text-muted); padding: 2px 6px; border-radius: 4px;
}
.dialog-close:hover { color: var(--text); background: var(--surface2); }

.nw-layout {
  display: flex; flex: 1; min-height: 0; overflow: hidden;
}

/* ── Links ─────────────────────────────────────── */
.nw-left {
  width: 280px; flex-shrink: 0;
  padding: 16px; border-right: 1px solid var(--border);
  display: flex; flex-direction: column; gap: 8px;
  overflow-y: auto;
}

.nw-section-label {
  font-size: 10px; font-weight: 700; text-transform: uppercase;
  letter-spacing: 1px; color: var(--text-muted); margin-bottom: 4px;
}

.breakdown { display: flex; flex-direction: column; gap: 10px; }
.bk-row { display: flex; align-items: center; gap: 8px; }
.bk-icon  { font-size: 16px; flex-shrink: 0; }
.bk-label { width: 80px; flex-shrink: 0; font-size: 12px; color: var(--text); }
.bk-bar-wrap {
  flex: 1; height: 8px; background: rgba(255,255,255,0.08);
  border-radius: 4px; overflow: hidden;
}
.bk-bar { height: 100%; border-radius: 4px; transition: width 0.4s; }
.bk-val { width: 52px; text-align: right; font-size: 12px; font-weight: 700; color: var(--text); }

.nw-divider { border-top: 1px solid var(--border); margin: 4px 0; }
.stat-row { display: flex; justify-content: space-between; font-size: 12px; }
.stat-label { color: var(--text-muted); }
.stat-val   { font-weight: 700; color: var(--text); }
.stat-val.accent { color: #aaff88; }

.nw-loading { color: var(--text-muted); font-size: 12px; }

/* ── Rechts ─────────────────────────────────────── */
.nw-right {
  flex: 1; min-width: 0; padding: 16px;
  display: flex; flex-direction: column; gap: 8px;
}

.chart-toolbar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap; flex-shrink: 0;
}

.chart-toggles {
  display: flex; flex-wrap: wrap; gap: 6px; flex: 1;
}

.toggle-btn {
  display: flex; align-items: center; gap: 5px;
  padding: 3px 8px; border: 1px solid var(--border);
  border-radius: 6px; background: var(--surface2);
  color: var(--text); font-size: 12px; cursor: pointer;
  transition: opacity 0.15s;
}
.toggle-btn.inactive { opacity: 0.35; }
.toggle-btn:hover    { opacity: 1; }

.dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--dot); flex-shrink: 0;
}

.pct-btn {
  padding: 3px 10px; border: 1px solid var(--border);
  border-radius: 6px; background: var(--surface2);
  color: var(--text-muted); font-size: 12px; font-weight: 700;
  cursor: pointer; transition: background 0.15s, color 0.15s; white-space: nowrap;
}
.pct-btn:hover { color: var(--text); }

.chart-wrap { flex: 1; min-height: 0; position: relative; }
</style>
