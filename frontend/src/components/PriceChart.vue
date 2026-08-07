<template>
  <div class="chart-root">
    <div class="chart-toolbar">
      <button class="pct-btn" :class="{ active: pctMode }" @click="pctMode = !pctMode" :title="t('priceChart.pctChangeTitle')"><ShortcutSlot />%</button>
      <button class="pct-btn" @click="() => { chart?.resetZoom(); userHasMoved = false; applyYRange(); chart?.update('none') }" :title="t('priceChart.resetZoomTitle')"><ShortcutSlot />RESET</button>
    </div>
    <div class="chart-wrap" @mouseleave="onChartLeave">
      <canvas ref="canvasRef"></canvas>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Chart, LineController, LineElement, PointElement,
  LinearScale, TimeScale, Tooltip, Legend
} from 'chart.js'
import 'chartjs-adapter-date-fns'
import ZoomPlugin from 'chartjs-plugin-zoom'
import { useMarketStore } from '../stores/market.js'
import { getFullMarketHistory } from '../services/api.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

Chart.register(LineController, LineElement, PointElement, LinearScale, TimeScale, Tooltip, Legend, ZoomPlugin)

const emit = defineEmits(['hover-resource', 'hover-point', 'pct-mode-change'])

const marketStore = useMarketStore()
const { t }       = useI18n()
const canvasRef   = ref(null)
const pctMode     = ref(false)
let chart         = null
let fullHistory   = []   // always sorted oldest→newest
let userHasMoved  = false

function onChartLeave() {
  emit('hover-resource', null)
  emit('hover-point', null)
}

const RESOURCES = ['SUGAR', 'FLOUR', 'EGGS', 'BUTTER', 'CHOCOLATE', 'MILK']
const PRICE_KEY = {
  SUGAR: 'sugarPrice', FLOUR: 'flourPrice', EGGS: 'eggsPrice',
  BUTTER: 'butterPrice', CHOCOLATE: 'chocolatePrice', MILK: 'milkPrice',
}
const COLORS = {
  SUGAR: '#e67146', FLOUR: '#2a7d75', EGGS: '#349c58',
  BUTTER: '#c9c03d', CHOCOLATE: '#e67a84', MILK: '#6f6e72',
}
const LABEL_KEYS = {
  SUGAR: 'priceChart.sugar', FLOUR: 'priceChart.flour', EGGS: 'priceChart.eggs',
  BUTTER: 'priceChart.butter', CHOCOLATE: 'priceChart.chocolate', MILK: 'priceChart.milk',
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
      label: t(LABEL_KEYS[r]),
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


// Nur die Punkte im aktuell sichtbaren (gezoomten/verschobenen) X-Bereich —
// sonst bleibt die Y-Achse am Allzeit-Hoch haengen und man sieht beim Reinzoomen nur Striche.
function visibleHistory() {
  const xMin = chart?.options?.scales?.x?.min
  const xMax = chart?.options?.scales?.x?.max
  if (xMin == null || xMax == null) return fullHistory
  const windowed = fullHistory.filter(m => {
    const t = new Date(m.date).getTime()
    return t >= xMin && t <= xMax
  })
  return windowed.length ? windowed : fullHistory
}

function computeYRange() {
  const visibleResources = RESOURCES.filter(r => visible[r])
  if (!visibleResources.length || !fullHistory.length) return null
  const source = visibleHistory()

  let min = Infinity, max = -Infinity

  if (pctMode.value) {
    for (const r of visibleResources) {
      const key = PRICE_KEY[r]
      // Basis muss dieselbe sein wie in buildDatasets() (erster Wert der GESAMTEN
      // Historie, nicht des gezoomten Fensters) -- sonst weicht die Achse von den
      // tatsaechlich geplotteten Punkten ab.
      const base = fullHistory.map(m => m[key] ?? 0).find(v => v > 0) ?? 1
      const raw = source.map(m => m[key] ?? 0)
      for (const v of raw) {
        const pct = ((v - base) / base) * 100
        if (pct < min) min = pct
        if (pct > max) max = pct
      }
    }
  } else {
    for (const entry of source) {
      for (const r of visibleResources) {
        const v = entry[PRICE_KEY[r]] ?? 0
        if (v > 0) { if (v < min) min = v; if (v > max) max = v }
      }
    }
  }

  if (!isFinite(min)) return null
  // Oben immer +10% Puffer über dem sichtbaren Maximum, damit Linien nicht am Rand kleben.
  const topPad = Math.abs(max) * 0.1 || 0.1
  const botPad = (max - min) * 0.08 || Math.abs(max) * 0.05 || 0.1
  return { min: pctMode.value ? min - botPad : Math.max(0, min - botPad), max: max + topPad }
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
      ctx.textAlign = 'right'
      for (const { color, label, x, y } of items) {
        ctx.fillStyle = color
        ctx.fillText(label, x - 6, Math.min(y + 4, yScale.bottom - 2))
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
            onPanComplete: () => { userHasMoved = true; applyYRange(); chart.update('none') },
          },
          zoom: {
            wheel: { enabled: true },
            pinch: { enabled: true },
            mode: 'x',
            onZoomComplete: () => { userHasMoved = true; applyYRange(); chart.update('none') },
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
          ticks: { color: '#aea47e', maxTicksLimit: 6, maxRotation: 0 },
          grid:  { color: 'rgba(255,255,255,0.06)' },
        },
        y: {
          position: 'right',
          ticks: { color: '#aea47e', maxTicksLimit: 6 },
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
    applyYRange()
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

defineExpose({ toggle, visible })
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
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.pct-btn {
  position: relative;
  font-family: 'Silkscreen', monospace;
  padding: 5px 10px;
  border: 3px solid var(--px-ink);
  color: var(--px-cream);
  background: var(--px-wood3);
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
}
.pct-btn:hover { filter: brightness(1.08); }
.pct-btn.active {
  background: var(--px-orange);
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt);
}

.chart-wrap {
  flex: 1;
  min-height: 0;
  position: relative;
}
</style>
