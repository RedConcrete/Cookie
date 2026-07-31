<template>
  <div class="cocoa-scene">
    <div class="soil-line"></div>
    <div class="ground"></div>

    <div v-for="(t, i) in row1" :key="'r1-' + i" class="tree" :style="{ left: t + 'px', top: '6px' }">
      <div class="canopy" :style="{ background: i % 2 === 0 ? '#2f5a1c' : '#3d6b25' }"></div>
      <div class="trunk"></div>
      <div class="fruit"></div>
    </div>
    <div v-for="(t, i) in row2" :key="'r2-' + i" class="tree" :style="{ left: t + 'px', top: '62px' }">
      <div class="canopy" :style="{ background: i % 2 === 0 ? '#3d6b25' : '#2f5a1c' }"></div>
      <div class="trunk"></div>
      <div class="fruit"></div>
    </div>

    <div class="rack"></div>
    <div class="rack-legs"></div>

    <template v-if="workers > 0">
      <div class="picker">
        <PixelWorker anim="reach" :dur="1.5" hat="#4a7c2f" skin="#e8b489" torso="#6b4f2a"
          :tool="{ anim: 'swing', dur: 1.5, color: '#40230f', top: '2px', height: '16px' }" />
      </div>
      <div class="rowwalker">
        <TravelingWorker travel-anim="rowwalk" :travel-dur="4.4" hat="#4a7c2f" torso="#8a5a34" />
      </div>
    </template>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import TravelingWorker from './TravelingWorker.vue'

defineProps({ workers: { type: Number, default: 0 } })

const row1 = [10, 58, 106, 154]
const row2 = [24, 72, 120]
</script>

<style scoped>
.cocoa-scene {
  position: absolute; inset: 0;
  background: #7a5734;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 7px, transparent 7px 14px);
}
.soil-line { position: absolute; left: 0; right: 0; top: 52px; height: 8px; background: #8d6a3d; }
.ground    { position: absolute; left: 0; right: 0; bottom: 0; height: 14px; background: #5f4126; }
.tree      { position: absolute; }
.canopy { position: absolute; width: 34px; height: 20px; box-shadow: 0 0 0 2px #1a3410; background-image: repeating-linear-gradient(45deg, rgba(255,255,255,.1) 0 4px, transparent 4px 8px); }
.trunk  { position: absolute; left: 14px; top: 20px; width: 6px; height: 20px; background: #6b4f2a; box-shadow: 0 0 0 2px #3a2a1c; }
.fruit  { position: absolute; left: 4px;  top: 22px; width: 6px; height: 9px; background: #c9702a; box-shadow: 0 0 0 2px #3a2a1c; }
.rack      { position: absolute; right: 6px;  bottom: 16px; width: 34px; height: 18px; background: #a97b4a; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(90deg, rgba(0,0,0,.2) 0 3px, transparent 3px 6px); }
.rack-legs { position: absolute; right: 10px; bottom: 34px; width: 26px; height: 6px;  background: #40230f; }
.picker    { position: absolute; left: 150px; top: 74px; z-index: 3; }
.rowwalker { position: absolute; left: 20px; bottom: 0; z-index: 3; }
</style>
