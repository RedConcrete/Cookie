<template>
  <div class="chart-root">
    <div class="chart-toolbar">
      <div class="chart-toggles">
        <button
          v-for="r in RESOURCES"
          :key="r"
          class="toggle-btn"
          :class="{ inactive: !visible[r] }"
          :style="{ '--dot': COLORS[r] }"
          @click="toggle(r)"
        >
          <span class="dot"></span>{{ LABELS[r] }}
        </button>
      </div>
      <button class="pct-btn" :class="{ active: pctMode }" @click="pctMode = !pctMode" title="% Änderung">%</button>
      <button class="pct-btn" @click="() => { chart?.resetZoom(); userHasMoved = false }" title="Zoom zurücksetzen">⊙</button>
    </div>
    <div class="chart-wrap">
      <canvas ref="canvasRef"></canvas>
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
import { useMarketStore } from '../stores/market.js'
import { getFullMarketHistory } from '../services/api.js'

Chart.register(LineController, LineElement, PointElement, LinearScale, TimeScale, Tooltip, Legend, ZoomPlugin)

const emit = defineEmits(['hover-resource', 'hover-point', 'pct-mode-change'])

const marketStore = useMarketStore()
const canvasRef   = ref(null)
const pctMode     = ref(false)
let chart         = null
let fullHistory   = []   // always sorted oldest→newest
let userHasMoved  = false

const RESOURCES = ['SUGAR', 'FLOUR', 'EGGS', 'BUTTER', 'CHOCOLATE', 'MILK']
const PRICE_KEY = {
  SUGAR: 'sugarPrice', FLOUR: 'flourPrice', EGGS: 'eggsPrice',
  BUTTER: 'butterPrice', CHOCOLATE: 'chocolatePrice', MILK: 'milkPrice',
}
const COLORS = {
  SUGAR: '#ef4444', FLOUR: '#3b82f6', EGGS: '#22c55e',
  BUTTER: '#eab308', CHOCOLATE: '#a855f7', MILK: '#06b6d4',
}
const LABELS = {
  SUGAR: 'Zucker', FLOUR: 'Mehl', EGGS: 'Eier',
  BUTTER: 'Butter', CHOCOLATE: 'Schokolade', MILK: 'Milch',
}

const visible = reactive(Object.fromEntries(RESOURCES.map(r => [r, true])))

function toggle(r) {
  visible[r] = !visible[r]
  rebuildChart()  // ruft applyYRange intern auf
}

function buildDatasets(history) {
  return RESOURCES.map(r => {
    const key = PRICE_KEY[r]
    const rawValues = history.map(m => m[key] ?? 0)
    const base = pctMode.value ? (rawValues.find(v => v > 0) ?? 1) : 1
    return {
      resourceKey: r,
      label: LABELS[r],
      data: history.map((m, i) => ({
        x: new Date(m.date),
        y: pctMode.value ? ((rawValues[i] - base) / base) * 100 : rawValues[i],
      })),
      borderColor: COLORS[r],
      backgroundColor: COLORS[r] + '22',
      borderWidth: 2,
      pointRadius: 0,
      tension: 0.3,
      hidden: !visible[r],
    }
  })
}

const INITIAL_WINDOW_MS = 10 * 60 * 1000  // 10 Minuten Standard-Ansicht

function latestMs() {
  return fullHistory.length ? new Date(fullHistory[fullHistory.length - 1].date).getTime() : Date.now()
}

function setXRange(minMs, maxMs) {
  chart.options.scales.x.min = minMs
  chart.options.scales.x.max = maxMs
  chart.options.plugins.zoom.limits.x.max = maxMs + 1000
}


function computeYRange() {
  const visibleResources = RESOURCES.filter(r => visible[r])
  if (!visibleResources.length || !fullHistory.length) return null

  let min = Infinity, max = -Infinity

  if (pctMode.value) {
    for (const r of visibleResources) {
      const key = PRICE_KEY[r]
      const raw = fullHistory.map(m => m[key] ?? 0)
      const base = raw.find(v => v > 0) ?? 1
      for (const v of raw) {
        const pct = ((v - base) / base) * 100
        if (pct < min) min = pct
        if (pct > max) max = pct
      }
    }
  } else {
    for (const entry of fullHistory) {
      for (const r of visibleResources) {
        const v = entry[PRICE_KEY[r]] ?? 0
        if (v > 0) { if (v < min) min = v; if (v > max) max = v }
      }
    }
  }

  if (!isFinite(min)) return null
  const pad = (max - min) * 0.08 || Math.abs(max) * 0.05 || 0.1
  return { min: pctMode.value ? min - pad : Math.max(0, min - pad), max: max + pad }
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
  chart.data.datasets = buildDatasets(fullHistory)
  applyYRange()
  chart.update('none')
}

function initChart() {
  const maxMs     = latestMs()
  const minMs     = maxMs - INITIAL_WINDOW_MS
  const oldestMs  = maxMs - 30 * 24 * 60 * 60 * 1000  // 30 Tage zurück als hard limit

  const tipLabelsPlugin = {
    id: 'tipLabels',
    afterDraw(c) {
      const ctx = c.ctx
      const yScale = c.scales.y
      // sort datasets by last-point y so labels stack without overlap
      const items = c.data.datasets
        .map((ds, i) => ({ ds, i, meta: c.getDatasetMeta(i) }))
        .filter(({ ds, meta }) => !ds.hidden && meta.data.length)
        .map(({ ds, i, meta }) => {
          const pt = meta.data[meta.data.length - 1]
          const val = ds.data[ds.data.length - 1]?.y
          return { color: ds.borderColor, label: formatTip(val), x: pt.x, y: pt.y, val }
        })
        .sort((a, b) => a.y - b.y)

      // spread labels vertically if they overlap
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

  function formatTip(val) {
    if (val === undefined || val === null) return ''
    return pctMode.value
      ? `${val >= 0 ? '+' : ''}${val.toFixed(1)}%`
      : val.toFixed(2)
  }

  chart = new Chart(canvasRef.value, {
    type: 'line',
    data: { datasets: buildDatasets(fullHistory) },
    plugins: [tipLabelsPlugin],
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      onHover: (_e, elements) => {
        if (!elements.length) {
          emit('hover-resource', null)
          emit('hover-point', null)
          return
        }
        const ds  = chart.data.datasets[elements[0].datasetIndex]
        const idx = elements[0].index
        emit('hover-resource', ds?.resourceKey ?? null)
        emit('hover-point', fullHistory[idx] ?? null)
      },
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
}

watch(() => marketStore.history, (incoming) => {
  if (!incoming?.length) return
  const knownDates = new Set(fullHistory.map(h => String(h.date)))
  const newEntries = [...incoming].filter(e => !knownDates.has(String(e.date)))
  if (!newEntries.length) return

  fullHistory = [...fullHistory, ...newEntries].sort((a, b) => new Date(a.date) - new Date(b.date))
  rebuildChart()

  // Nur mitlaufen wenn User nicht selbst verschoben hat
  if (!userHasMoved && chart) {
    const newMax = latestMs()
    const viewWidth = chart.scales.x.max - chart.scales.x.min
    setXRange(newMax - viewWidth, newMax)
    chart.update('none')
  }
})

function computeBases() {
  const bases = {}
  for (const r of RESOURCES) {
    const raw = fullHistory.map(m => m[PRICE_KEY[r]] ?? 0)
    bases[r] = raw.find(v => v > 0) ?? 1
  }
  return bases
}

watch(pctMode, () => {
  rebuildChart()
  emit('pct-mode-change', { active: pctMode.value, bases: computeBases() })
})

onMounted(async () => {
  try {
    const raw = await getFullMarketHistory()
    fullHistory = [...raw].sort((a, b) => new Date(a.date) - new Date(b.date))
  } catch {
    fullHistory = marketStore.history ? [...marketStore.history].reverse() : []
  }
  initChart()
})

onUnmounted(() => chart?.destroy())
</script>

<style scoped>
.chart-root {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
}

.chart-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.chart-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}

.toggle-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface2);
  color: var(--text);
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.toggle-btn.inactive { opacity: 0.35; }
.toggle-btn:hover    { opacity: 1; }

.dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--dot);
  flex-shrink: 0;
}

.pct-btn {
  padding: 3px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface2);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
}
.pct-btn.active { background: var(--accent); color: #fff; border-color: var(--accent); }

.chart-wrap {
  flex: 1;
  min-height: 0;
  position: relative;
}
</style>
