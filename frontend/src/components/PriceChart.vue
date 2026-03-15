<template>
  <div class="price-chart-wrapper">
    <canvas ref="canvasRef"></canvas>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Chart, LineController, LineElement, PointElement, LinearScale, TimeScale, CategoryScale, Tooltip, Legend } from 'chart.js'
import { useMarketStore } from '../stores/market.js'

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip, Legend)

const marketStore = useMarketStore()
const canvasRef = ref(null)
let chart = null

const COLORS = {
  SUGAR:     '#ef4444',
  FLOUR:     '#3b82f6',
  EGGS:      '#22c55e',
  BUTTER:    '#eab308',
  CHOCOLATE: '#a855f7',
  MILK:      '#06b6d4'
}

const LABELS = {
  SUGAR: 'Zucker', FLOUR: 'Mehl', EGGS: 'Eier',
  BUTTER: 'Butter', CHOCOLATE: 'Schokolade', MILK: 'Milch'
}

function buildChartData(history) {
  // history is newest-first, chart needs oldest-first
  const sorted = [...history].reverse()
  const labels = sorted.map(m => {
    const d = new Date(m.date)
    return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}`
  })
  const resources = ['SUGAR', 'FLOUR', 'EGGS', 'BUTTER', 'CHOCOLATE', 'MILK']
  const datasets = resources.map(r => ({
    label: LABELS[r],
    data: sorted.map(m => m[r.toLowerCase() + 'Price']),
    borderColor: COLORS[r],
    backgroundColor: COLORS[r] + '22',
    borderWidth: 2,
    pointRadius: 2,
    tension: 0.3
  }))
  return { labels, datasets }
}

function initChart() {
  chart = new Chart(canvasRef.value, {
    type: 'line',
    data: buildChartData(marketStore.history),
    options: {
      responsive: true,
      animation: false,
      plugins: {
        legend: { position: 'bottom', labels: { color: '#ccc', boxWidth: 12 } },
        tooltip: { mode: 'index', intersect: false }
      },
      scales: {
        x: { ticks: { color: '#888', maxTicksLimit: 8 }, grid: { color: '#333' } },
        y: { ticks: { color: '#888' }, grid: { color: '#333' } }
      }
    }
  })
}

watch(() => marketStore.history, (h) => {
  if (!chart) return
  const { labels, datasets } = buildChartData(h)
  chart.data.labels = labels
  chart.data.datasets.forEach((ds, i) => { ds.data = datasets[i].data })
  chart.update('none')
})

onMounted(initChart)
onUnmounted(() => chart?.destroy())
</script>
