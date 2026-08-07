<template>
  <div class="cocoa-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />

    <template v-if="workers > 0">
      <div class="picker">
        <PixelWorker :anim="idle ? 'bob' : 'reach'" :dur="1.5" hat="#56642e" skin="#ebb85b" torso="#764032"
          :tool="idle ? null : { anim: 'swing', dur: 1.5, color: '#402e2b', top: '2px', height: '16px' }" />
      </div>
      <div class="rowwalker">
        <TravelingWorker travel-anim="rowwalk" :travel-dur="4.4" hat="#56642e" torso="#a15c34" />
      </div>
    </template>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/kakao.png'

defineProps({ workers: { type: Number, default: 0 }, idle: { type: Boolean, default: false } })
</script>

<style scoped>
.cocoa-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }
.picker    { position: absolute; left: 150px; top: 74px; z-index: 3; }
.rowwalker { position: absolute; left: 36px; bottom: 0; z-index: 3; }
</style>
