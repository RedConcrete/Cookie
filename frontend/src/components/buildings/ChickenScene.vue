<template>
  <div class="chicken-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />

    <svg v-for="(h, i) in hens" :key="i" class="hen" viewBox="0 0 11 11" width="20" height="20"
         :style="{ left: h.left + 'px', bottom: h.bottom + 'px', animationDelay: h.delay + 's' }">
      <rect v-for="(cell, j) in henCells(h.color)" :key="j" :x="cell.x" :y="cell.y" width="1" height="1" :fill="cell.fill" />
    </svg>

    <div v-if="workers > 0" class="patroller">
      <TravelingWorker travel-anim="patrol" :travel-dur="5.5" :leg-dur="0.55" hat="#534664" torso="#a15c34" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/huhn.png'
import { buildSpriteCells, dots } from '../pixel/spriteGrid.js'

defineProps({ workers: { type: Number, default: 0 } })

const hens = [
  { left: 82,  bottom: 40, color: '#fff1a9', delay: 0 },
  { left: 116, bottom: 56, color: '#fff1a9', delay: 0.5 },
  { left: 146, bottom: 38, color: '#aea47e', delay: 1 },
]

// 11x11 pixel grid, side view: comb, head w/ eye + beak, body, two legs.
const HEN_GRID = [
  dots(7) + 'r' + dots(3),
  dots(6) + 'c'.repeat(4) + dots(1),
  dots(6) + 'c'.repeat(2) + 'e' + 'c' + dots(1),
  dots(6) + 'c'.repeat(4) + 'y',
  'c'.repeat(10) + dots(1),
  'c'.repeat(10) + dots(1),
  'c'.repeat(10) + dots(1),
  'c'.repeat(7) + dots(4),
  'c'.repeat(7) + dots(4),
  dots(1) + 'yy' + dots(1) + 'yy' + dots(5),
  dots(1) + 'yy' + dots(1) + 'yy' + dots(5),
]

function henCells(color) {
  return buildSpriteCells(HEN_GRID, { c: color, r: '#b74132', e: '#120e23', y: '#ebb85b' })
}
</script>

<style scoped>
.chicken-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }

.hen { position: absolute; animation: px-peck 1.6s ease-in-out infinite; }

.patroller { position: absolute; left: 96px; bottom: 34px; z-index: 4; }
</style>
