<template>
  <div
    class="building-tile"
    :class="[`tile-${variant}`, { 'harvest-flash': flashing, 'tile-done': variant === 'oven' && bakeStatus?.done }]"
    @mouseenter="variant === 'harvest' && onEnter()"
    @mouseleave="variant === 'harvest' && onLeave()"
    @click="variant !== 'harvest' && emit('open')"
  >
    <!-- Oven: Fortschrittsbalken über dem Tile -->
    <div v-if="variant === 'oven' && bakeStatus?.active" class="oven-progress">
      <div class="oven-progress-bar">
        <div class="oven-progress-fill" :style="{ width: bakeProgressPct + '%' }"></div>
      </div>
      <div class="oven-timer">
        <template v-if="bakeStatus.done">✓ Fertig!</template>
        <template v-else>⏱ {{ formatSec(bakeStatus.remainingSeconds) }}</template>
      </div>
    </div>

    <div class="tile-img">
      <img v-if="icon" :src="icon" :alt="label" />
      <span v-else class="tile-emoji">{{ emoji }}</span>
    </div>

    <div class="tile-label">{{ label }}</div>

    <div v-if="upgradeBadges && upgradeBadges.length" class="tile-badges">
      <span v-for="b in upgradeBadges" :key="b.label" class="tile-badge">
        {{ b.icon }} Stufe {{ b.level }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  variant:       { type: String, required: true }, // 'market' | 'oven' | 'harvest'
  label:         { type: String, required: true },
  icon:          { type: String, default: null },
  bakeStatus:    { type: Object, default: null },
  upgradeBadges: { type: Array,  default: null }, // [{ icon, level }]
})

const emit = defineEmits(['open', 'harvest-start', 'harvest-stop'])

const flashing = ref(false)

const emoji = computed(() => '')

const bakeProgressPct = computed(() => {
  const s = props.bakeStatus
  if (!s?.active || !s.recipe) return 0
  const total = s.recipe.bakeDurationSeconds * s.batches
  if (total === 0) return 100
  return Math.min(100, ((total - s.remainingSeconds) / total) * 100)
})

function formatSec(sec) {
  if (sec <= 0) return '0s'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}

function onEnter() {
  flashing.value = true
  setTimeout(() => { flashing.value = false }, 300)
  emit('harvest-start')
}

function onLeave() {
  emit('harvest-stop')
}
</script>

<style scoped>
.building-tile {
  position: absolute;
  width: 150px;
  background: var(--surface);
  border: 2px solid var(--border);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  padding-bottom: 10px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
  user-select: none;
  overflow: visible;
}

.tile-market { height: 165px; }
.tile-oven   { height: 165px; }
.tile-harvest { height: 195px; }

.building-tile:hover {
  transform: translateY(-6px) scale(1.04);
  box-shadow: 0 12px 28px rgba(100,60,0,0.22);
  border-color: var(--accent);
}

.tile-done { border-color: #4A7C2F; box-shadow: 0 0 12px rgba(74,124,47,0.4); }

.tile-img {
  width: 100%;
  flex: 1;
  background: var(--surface2);
  border-radius: 12px 12px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.tile-img img { width: 100%; height: 100%; object-fit: contain; padding: 12px; }
.tile-emoji   { font-size: 52px; }

.tile-label { font-weight: 700; font-size: 13px; color: var(--text); margin-top: 8px; }
.tile-hint  { font-size: 10px; color: var(--text-muted); margin-top: 2px; }

.tile-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 3px;
  margin-top: 5px;
  padding: 0 4px;
}
.tile-badge {
  font-size: 9px;
  font-weight: 700;
  background: var(--surface2);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1px 6px;
  color: var(--accent);
  white-space: nowrap;
}

/* Harvest flash */
.harvest-flash { animation: hflash 0.3s ease; }
@keyframes hflash {
  0%   { background: var(--surface); }
  50%  { background: #FFF3C4; }
  100% { background: var(--surface); }
}

/* Oven progress (rendered above tile, outside overflow) */
.oven-progress {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0; right: 0;
}
.oven-progress-bar {
  height: 6px;
  background: #2a2a3a;
  border-radius: 3px;
  overflow: hidden;
}
.oven-progress-fill {
  height: 100%;
  background: #7F77DD;
  border-radius: 3px;
  transition: width 0.5s linear;
}
.oven-timer {
  font-size: 11px;
  color: #aaa;
  text-align: center;
  margin-top: 3px;
}
</style>
