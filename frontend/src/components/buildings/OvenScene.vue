<template>
  <div class="oven-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />

    <!-- Chimney with animated smoke -->
    <div class="bh-chimney">
      <div class="bh-smoke bh-smoke-1"></div>
      <div class="bh-smoke bh-smoke-2"></div>
      <div class="bh-smoke bh-smoke-3"></div>
    </div>

    <!-- Baker -->
    <div class="bh-baker">
      <PixelWorker anim="knead" :dur="0.6" hat="#fff1a9" torso="#aea47e"
        :tool="{ anim: 'churn', dur: 0.6, color: '#a15c34' }" />
    </div>

    <!-- Bake progress: visible on the map itself, not just inside the Bake dialog -->
    <div v-if="job" class="bh-bake-status">
      <template v-if="!job.done">
        <div class="bh-bake-track"><div class="bh-bake-bar" :style="{ width: progressPct + '%' }"></div></div>
        <div class="bh-bake-time">{{ formatDuration(job.remainingSeconds) }}</div>
      </template>
      <div v-else class="bh-bake-done">FERTIG!</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PixelWorker from '../pixel/PixelWorker.vue'
import { useBakeStore } from '../../stores/bake.js'
import bgSrc from '../../assets/buildings/placeholder/ofen.png'

const bakeStore = useBakeStore()
const job = computed(() => bakeStore.status)

const progressPct = computed(() => {
  const j = job.value
  if (!j?.recipe) return 0
  const total = j.recipe.bakeDurationSeconds * j.batches
  if (total <= 0) return 100
  return Math.min(100, ((total - j.remainingSeconds) / total) * 100)
})

function formatDuration(s) {
  if (s <= 0) return '0s'
  const m = Math.floor(s / 60); const sec = s % 60
  return m > 0 ? `${m}m ${sec}s` : `${sec}s`
}
</script>

<style scoped>
.oven-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }

/* Chimney */
.bh-chimney {
  position: absolute; left: 95px; top: 0; width: 14px; height: 26px;
  overflow: visible;
}
.bh-smoke {
  position: absolute; left: 2px; width: 10px; height: 10px;
  border-radius: 50%; background: rgb(200, 190, 180);
  animation: bh-rise 2s ease-out infinite;
}
.bh-smoke-1 { bottom: 100%; animation-delay: 0s; }
.bh-smoke-2 { bottom: 100%; animation-delay: 0.7s; opacity: 0.7; }
.bh-smoke-3 { bottom: 100%; animation-delay: 1.4s; opacity: 0.4; }
@keyframes bh-rise {
  0%   { transform: translateY(0)   scale(1); opacity: 1; }
  100% { transform: translateY(-40px) translateX(12px) scale(2); opacity: 0; }
}

/* Baker worker */
.bh-baker { position: absolute; left: 78px; bottom: 4px; z-index: 3; }

/* Bake progress overlay -- pinned to the top of the scene, above the baker/smoke.
   Bar matches BuildingFrame.vue's .bf-hold-bar (the move/drag progress bar):
   a single growing gold rectangle with a dark ink halo, no separate track. */
.bh-bake-status {
  position: absolute; left: 6px; right: 6px; top: 4px; z-index: 6;
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  pointer-events: none;
}
.bh-bake-track { width: 100%; height: 10px; }
.bh-bake-bar {
  height: 100%; background: var(--px-gold); box-shadow: 0 0 0 3px var(--px-ink);
  transition: width .3s linear;
}
.bh-bake-time {
  font-family: 'Silkscreen', monospace; font-size: 9px; color: #fff1a9;
  text-shadow: 1px 1px 0 var(--px-ink), -1px -1px 0 var(--px-ink), 1px -1px 0 var(--px-ink), -1px 1px 0 var(--px-ink);
  white-space: nowrap;
}
.bh-bake-done {
  padding: 3px 8px;
  font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-ink-txt);
  background: var(--px-gold); box-shadow: 0 0 0 3px var(--px-ink);
  animation: bh-pulse 1s ease-in-out infinite;
}
@keyframes bh-pulse {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.08); }
}
</style>
