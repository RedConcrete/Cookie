<template>
  <div class="dsp-root px-panel" :class="{ 'dsp-collapsed': collapsed }">
    <div class="dsp-header" @click="collapsed = !collapsed">
      <span class="dsp-fps" :class="fpsClass">{{ fps }} FPS</span>
      <span class="dsp-toggle">{{ collapsed ? '▸' : '▾' }}</span>
    </div>
    <div v-if="!collapsed" class="dsp-body">
      <div class="dsp-row"><span>Frame</span><span>{{ frameMs.toFixed(1) }} ms</span></div>
      <div class="dsp-row"><span>Ping</span><span>{{ ping !== null ? ping + ' ms' : '…' }}</span></div>
      <div class="dsp-row">
        <span>WebSocket</span>
        <span :class="wsConnected ? 'dsp-ok' : 'dsp-bad'">{{ wsConnected ? 'verbunden' : 'getrennt' }}</span>
      </div>
      <div class="dsp-row"><span>Letzte WS-Nachricht</span><span>{{ wsAgeLabel }}</span></div>
      <div v-if="heapMb !== null" class="dsp-row"><span>Heap</span><span>{{ heapMb }} MB</span></div>
      <div class="dsp-row"><span>Player-ID</span><span class="dsp-id">{{ playerStore.steamId }}</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { getConfig } from '../services/api.js'
import { wsConnected, wsLastMessageAt } from '../services/websocket.js'

const playerStore = usePlayerStore()
const collapsed = ref(false)

const fps = ref(0)
const frameMs = ref(0)
const heapMb = ref(null)
const now = ref(Date.now())

let rafId = null
let frames = 0
let lastFrameTime = performance.now()
let lastFpsSampleTime = lastFrameTime

function tick(t) {
  frames++
  frameMs.value = t - lastFrameTime
  lastFrameTime = t
  if (t - lastFpsSampleTime >= 1000) {
    fps.value = Math.round((frames * 1000) / (t - lastFpsSampleTime))
    frames = 0
    lastFpsSampleTime = t
    now.value = Date.now()
    heapMb.value = performance.memory ? +(performance.memory.usedJSHeapSize / 1048576).toFixed(1) : null
  }
  rafId = requestAnimationFrame(tick)
}

const fpsClass = computed(() => (fps.value >= 50 ? 'dsp-ok' : fps.value >= 25 ? 'dsp-warn' : 'dsp-bad'))

const wsAgeLabel = computed(() => {
  if (!wsLastMessageAt.value) return '–'
  return `vor ${Math.max(0, Math.round((now.value - wsLastMessageAt.value) / 1000))}s`
})

const ping = ref(null)
let pingTimer = null
async function measurePing() {
  const t0 = performance.now()
  try {
    await getConfig()
    ping.value = Math.round(performance.now() - t0)
  } catch {
    ping.value = null
  }
}

onMounted(() => {
  rafId = requestAnimationFrame(tick)
  measurePing()
  pingTimer = setInterval(measurePing, 3000)
})
onUnmounted(() => {
  if (rafId) cancelAnimationFrame(rafId)
  if (pingTimer) clearInterval(pingTimer)
})
</script>

<style scoped>
.dsp-root {
  position: fixed; bottom: 8px; left: 8px; z-index: 200;
  font-family: 'Silkscreen', monospace; font-size: 11px;
  min-width: 150px; user-select: none;
}
.dsp-header {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; padding: 6px 10px; cursor: pointer;
  background: var(--px-wood); color: var(--px-cream);
}
.dsp-root:not(.dsp-collapsed) .dsp-header { border-bottom: 4px solid var(--px-ink); }
.dsp-fps.dsp-ok { color: var(--px-green-lt); }
.dsp-fps.dsp-warn { color: var(--px-gold); }
.dsp-fps.dsp-bad { color: var(--px-red-lt); }
.dsp-toggle { color: var(--px-cream2); }
.dsp-body { padding: 6px 10px 8px; display: flex; flex-direction: column; gap: 3px; }
.dsp-row { display: flex; justify-content: space-between; gap: 12px; color: var(--px-ink-txt); }
.dsp-id { color: var(--px-ink-txt); }
.dsp-ok { color: var(--px-green-panel); }
.dsp-warn { color: var(--px-orange-dk); }
.dsp-bad { color: var(--px-red-dk); }
</style>
