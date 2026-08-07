<template>
  <div class="farm-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <template v-if="workers > 0">
      <div class="mower mower-1">
        <PixelWorker :anim="idle ? 'bob' : 'bend'" :dur="0.9" hat="#ebb85b" torso="#a15c34"
          :tool="idle ? null : { anim: 'swing', dur: 0.9, color: '#aea47e', height: '17px' }" />
      </div>
      <div class="mower mower-2">
        <PixelWorker :anim="idle ? 'bob' : 'bend'" :dur="0.9" :delay="0.45" hat="#c78539" skin="#ebb85b" torso="#534664"
          :tool="idle ? null : { anim: 'swing', dur: 0.9, delay: 0.45, color: '#aea47e', height: '17px' }" />
      </div>
      <div class="commute">
        <TravelingWorker travel-anim="commute" :travel-dur="8" hat="#56642e" torso="#764032" />
      </div>
    </template>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/hof.png'

defineProps({ workers: { type: Number, default: 0 }, idle: { type: Boolean, default: false } })
</script>

<style scoped>
.farm-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }
.mower { position: absolute; z-index: 3; }
.mower-1 { left: 186px; top: 52px; }
.mower-2 { left: 236px; top: 14px; }
.commute { position: absolute; left: 92px; bottom: 4px; z-index: 3; }
</style>
