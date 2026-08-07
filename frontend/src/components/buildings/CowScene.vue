<template>
  <div class="cow-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />

    <svg v-for="(c, i) in cows" :key="i" class="cow" viewBox="0 0 16 11" width="32" height="22"
         :style="{ left: c.left + 'px', bottom: c.bottom + 'px', animationDelay: c.delay + 's' }">
      <rect v-for="(cell, j) in cowCells(c.body, c.spot)" :key="j" :x="cell.x" :y="cell.y" width="1" height="1" :fill="cell.fill" />
    </svg>

    <div v-if="workers > 0" class="milker">
      <PixelWorker :anim="idle ? 'bob' : 'milk'" :dur="1.1" hat="#6dba79" torso="#534664"
        :tool="idle ? null : { anim: 'tap', dur: 1.1, color: '#aea47e', top: '12px', height: '9px', width: '7px' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/kuh.png'
import { buildSpriteCells, dots } from '../pixel/spriteGrid.js'

defineProps({ workers: { type: Number, default: 0 }, idle: { type: Boolean, default: false } })

const cows = [
  { left: 68,  bottom: 46, body: '#fff1a9', spot: '#402e2b', delay: 0 },
  { left: 122, bottom: 42, body: '#fff1a9', spot: '#764032', delay: 0.9 },
]

// 16x11 pixel grid, side view: horns, head w/ eye, spotted body, four legs.
const COW_GRID = [
  dots(12) + 'h' + '.' + 'h' + '.',
  dots(10) + 'b'.repeat(6),
  dots(10) + 'b'.repeat(6),
  'b'.repeat(14) + 'e' + 'b',
  'bb' + 'ss' + 'b'.repeat(11) + 'n',
  'bb' + 'ss' + 'b'.repeat(11) + 'n',
  'b'.repeat(16),
  'b'.repeat(12) + dots(4),
  'b'.repeat(12) + dots(4),
  dots(2) + 'll' + dots(4) + 'll' + dots(6),
  dots(2) + 'll' + dots(4) + 'll' + dots(6),
]

function cowCells(body, spot) {
  return buildSpriteCells(COW_GRID, { b: body, s: spot, h: '#fff1a9', e: '#120e23', n: '#fff1a9', l: '#120e23' })
}
</script>

<style scoped>
.cow-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }

.cow { position: absolute; animation: px-chew 2.4s ease-in-out infinite; }

.milker { position: absolute; left: 78px; bottom: 38px; z-index: 4; }
</style>
