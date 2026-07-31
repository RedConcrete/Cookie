<template>
  <div class="cocoa-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />

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
import bgSrc from '../../assets/buildings/kakao.svg'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.cocoa-scene { position: absolute; inset: 0; }
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }
.picker    { position: absolute; left: 150px; top: 74px; z-index: 3; }
.rowwalker { position: absolute; left: 20px; bottom: 0; z-index: 3; }
</style>
