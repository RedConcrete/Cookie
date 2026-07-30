<template>
  <div class="wh-scene">
    <div v-for="(h, i) in houses" :key="i" class="house" :style="{ left: h.left + 'px' }">
      <div class="roof"   :style="{ top: h.roofTop + 'px', width: h.w + 'px', background: h.roof }"></div>
      <div class="wall"   :style="{ top: h.wallTop + 'px', width: (h.w - 8) + 'px', left: '4px', background: h.wall }"></div>
      <div class="door"   :style="{ top: (h.wallTop + 28) + 'px', left: (h.w / 2 - 6) + 'px' }"></div>
      <div class="window" :style="{ top: (h.wallTop + 8) + 'px', left: '5px' }"></div>
    </div>
    <div class="path"></div>
    <div class="crate crate-1"></div>
    <div class="crate crate-2"></div>
    <div class="trek">
      <TravelingWorker travel-anim="trek" :travel-dur="7" hat="#6b4f2a" torso="#3d6b25" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'

const houses = [
  { left: 4,   roofTop: 10, wallTop: 22, w: 62, roof: '#6b4f2a', wall: '#e9d6b4' },
  { left: 74,  roofTop: 18, wallTop: 30, w: 58, roof: '#8d6a3d', wall: '#e2d3b4' },
  { left: 142, roofTop: 10, wallTop: 22, w: 62, roof: '#6b4f2a', wall: '#ecdfc0' },
]
</script>

<style scoped>
.wh-scene {
  position: absolute; inset: 0;
  background: #b98f57;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 8px, transparent 8px 16px);
}
.house { position: absolute; top: 0; }
.roof   { position: absolute; height: 12px; box-shadow: 0 0 0 2px #3a2a1c; }
.wall   { position: absolute; height: 46px; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 6px, transparent 6px 12px); }
.door   { position: absolute; width: 12px; height: 18px; background: #5a4023; }
.window { position: absolute; width: 9px; height: 8px; background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.path { position: absolute; left: 0; right: 0; bottom: 0; height: 26px; background: #a8823f; background-image: repeating-linear-gradient(90deg, rgba(0,0,0,.12) 0 10px, transparent 10px 20px); }
.crate { position: absolute; bottom: 28px; width: 22px; height: 16px; background: #8d6a3d; box-shadow: 0 0 0 2px #3a2a1c; background-image: linear-gradient(45deg, transparent 44%, #5a4023 44% 56%, transparent 56%); }
.crate-1 { left: 24px; }
.crate-2 { left: 160px; }
.trek { position: absolute; left: 16px; bottom: 2px; z-index: 3; }
</style>
