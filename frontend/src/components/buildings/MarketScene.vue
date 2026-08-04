<template>
  <div class="market-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div class="patrol">
      <TravelingWorker travel-anim="patrol" :travel-dur="5" hat="#e67146" torso="#534664" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/markt.png'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.market-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }
.patrol { position: absolute; left: 14px; bottom: 2px; z-index: 3; }
</style>
