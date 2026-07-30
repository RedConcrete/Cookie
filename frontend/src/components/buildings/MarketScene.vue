<template>
  <div class="market-scene">
    <div v-for="(s, i) in stands" :key="i" class="stand" :style="{ left: s.left + 'px' }">
      <div class="awning" :style="{ backgroundImage: `repeating-linear-gradient(90deg, ${s.color} 0 8px, #fffaf0 8px 16px)` }"></div>
      <div class="post post-l" :style="{ left: s.left + 2 + 'px' }"></div>
      <div class="post post-r" :style="{ left: s.left + 60 + 'px' }"></div>
      <div class="counter" :style="{ left: s.left + 'px' }"></div>
      <div class="crate" :style="{ left: s.left + 8 + 'px' }"></div>
      <div class="jar" :style="{ left: s.left + 34 + 'px' }"></div>
    </div>
    <div class="front-counter"></div>
    <div class="patrol">
      <TravelingWorker travel-anim="patrol" :travel-dur="5" hat="#e05a4a" torso="#4a3f7a" />
    </div>
    <div class="coin"></div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'

const stands = [
  { left: 8,   color: '#e05a4a' },
  { left: 88,  color: '#5aa0e0' },
  { left: 168, color: '#8fae5c' },
]
</script>

<style scoped>
.market-scene {
  position: absolute; inset: 0;
  background: #cfa972;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.08) 0 8px, transparent 8px 16px);
}
.stand { position: absolute; top: 0; }
.awning  { position: absolute; top: 14px; width: 66px; height: 12px; border-bottom: 2px solid #3a2a1c; }
.post    { position: absolute; top: 26px; width: 4px; height: 44px; background: #8d6a3d; }
.counter { position: absolute; top: 60px; width: 66px; height: 10px; background: #a97b4a; box-shadow: 0 0 0 2px #3a2a1c; }
.crate   { position: absolute; top: 44px; width: 20px; height: 16px; background: #a97b4a; box-shadow: 0 0 0 2px #3a2a1c; background-image: linear-gradient(45deg, transparent 44%, #6b4f2a 44% 56%, transparent 56%); }
.jar     { position: absolute; top: 46px; width: 16px; height: 14px; background: #cfe6f5; box-shadow: 0 0 0 2px #3a2a1c; }
.front-counter { position: absolute; left: 0; right: 0; bottom: 0; height: 28px; background: #b98f57; background-image: repeating-linear-gradient(90deg, rgba(0,0,0,.12) 0 10px, transparent 10px 20px); }
.patrol { position: absolute; left: 14px; bottom: 2px; z-index: 3; }
.coin { position: absolute; right: 10px; bottom: 6px; width: 14px; height: 14px; background: #e8b93c; box-shadow: 0 0 0 2px #b48b1e; }
</style>
