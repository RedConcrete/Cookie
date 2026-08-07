<template>
  <div class="wh-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div class="trek">
      <TravelingWorker travel-anim="trek" :travel-dur="7" hat="#764032" torso="#56642e" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/lager.png'
</script>

<style scoped>
.wh-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }
.trek { position: absolute; left: 16px; bottom: 2px; z-index: 3; }
</style>
