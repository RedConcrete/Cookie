<template>
  <div class="chicken-scene">
    <div class="roof"></div>
    <div class="wall"></div>
    <div class="door"></div>
    <div class="window"></div>
    <div class="trough"></div>
    <div class="fence-posts"></div>
    <div class="fence-rail fence-rail-top"></div>
    <div class="fence-rail fence-rail-mid"></div>

    <div v-for="(h, i) in hens" :key="i" class="hen-wrap" :style="{ left: h.left + 'px', bottom: h.bottom + 'px' }">
      <div class="hen" :style="{ animationDelay: h.delay + 's' }">
        <div class="hen-body" :style="{ background: h.color }"></div>
        <div class="hen-head" :style="{ background: h.color }"></div>
        <div class="hen-comb"></div>
        <div class="hen-beak"></div>
        <div class="hen-leg hen-leg-l"></div>
        <div class="hen-leg hen-leg-r"></div>
      </div>
    </div>

    <div class="patroller">
      <TravelingWorker travel-anim="patrol" :travel-dur="5.5" :leg-dur="0.55" hat="#4a3f7a" torso="#8a5a34" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'

const hens = [
  { left: 82,  bottom: 40, color: '#fffaf0', delay: 0 },
  { left: 116, bottom: 56, color: '#f3e6cc', delay: 0.5 },
  { left: 146, bottom: 38, color: '#d9c39a', delay: 1 },
]
</script>

<style scoped>
.chicken-scene {
  position: absolute; inset: 0;
  background: #cdae7c;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.06) 0 6px, transparent 6px 12px);
}
.roof { position: absolute; left: 6px; top: 8px; width: 66px; height: 12px; background: #8d3f28; box-shadow: 0 0 0 2px #3a2a1c; }
.wall { position: absolute; left: 10px; top: 20px; width: 58px; height: 44px; background: #b8623a; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.1) 0 6px, transparent 6px 12px); }
.door   { position: absolute; left: 33px; top: 46px; width: 12px; height: 18px; background: #3a2a1c; }
.window { position: absolute; left: 15px; top: 28px; width: 9px; height: 8px; background: #e8b93c; box-shadow: 0 0 0 2px #3a2a1c; }
.trough { position: absolute; left: 80px; bottom: 34px; width: 34px; height: 7px; background: #8d6a3d; box-shadow: 0 0 0 2px #3a2a1c; background-image: repeating-linear-gradient(90deg, #e8c766 0 2px, transparent 2px 5px); }
.fence-posts { position: absolute; left: 0; right: 0; bottom: 6px; height: 26px; background-image: repeating-linear-gradient(90deg, #a97b4a 0 5px, transparent 5px 26px); }
.fence-rail { position: absolute; left: 0; right: 0; height: 4px; background: #8d6a3d; }
.fence-rail-top { bottom: 26px; }
.fence-rail-mid { bottom: 14px; }

.hen-wrap { position: absolute; }
.hen { position: relative; width: 11px; height: 13px; animation: px-peck 1.6s ease-in-out infinite; }
.hen-body { position: absolute; left: 0; top: 5px; width: 11px; height: 7px; box-shadow: 0 0 0 1px #3a2a1c; }
.hen-head { position: absolute; left: 6px; top: 0;  width: 5px; height: 5px; box-shadow: 0 0 0 1px #3a2a1c; }
.hen-comb { position: absolute; left: 7px; top: -2px; width: 3px; height: 2px; background: #b83232; }
.hen-beak { position: absolute; left: 11px; top: 2px; width: 3px; height: 2px; background: #e8b93c; }
.hen-leg  { position: absolute; top: 12px; width: 2px; height: 2px; background: #e8b93c; }
.hen-leg-l { left: 2px; }
.hen-leg-r { left: 7px; }

.patroller { position: absolute; left: 96px; bottom: 34px; z-index: 4; }
</style>
