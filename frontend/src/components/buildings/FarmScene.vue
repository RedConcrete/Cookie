<template>
  <div class="farm-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <template v-if="workers > 0">
      <div class="mower mower-1">
        <PixelWorker anim="bend" :dur="0.9" hat="#e8c766" torso="#8a5a34"
          :tool="{ anim: 'swing', dur: 0.9, color: '#c9b06a', height: '17px' }" />
      </div>
      <div class="mower mower-2">
        <PixelWorker anim="bend" :dur="0.9" :delay="0.45" hat="#c9702a" skin="#e8b489" torso="#4a3f7a"
          :tool="{ anim: 'swing', dur: 0.9, delay: 0.45, color: '#c9b06a', height: '17px' }" />
      </div>
      <div class="commute">
        <TravelingWorker travel-anim="commute" :travel-dur="8" hat="#4a7c2f" torso="#6b4f2a" />
      </div>
    </template>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/hof.svg'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.farm-scene { position: absolute; inset: 0; }
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }
.mower { position: absolute; z-index: 3; }
.mower-1 { left: 186px; top: 52px; }
.mower-2 { left: 236px; top: 14px; }
.commute { position: absolute; left: 92px; bottom: 4px; z-index: 3; }
</style>
