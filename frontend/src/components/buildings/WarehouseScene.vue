<template>
  <div>
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div class="trek">
      <TravelingWorker travel-anim="trek" :travel-dur="7" hat="#6b4f2a" torso="#3d6b25" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/lager.svg'
</script>

<style scoped>
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }
.trek { position: absolute; left: 16px; bottom: 2px; z-index: 3; }
</style>
