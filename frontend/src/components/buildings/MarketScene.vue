<template>
  <div >
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div class="patrol">
      <TravelingWorker travel-anim="patrol" :travel-dur="5" hat="#e05a4a" torso="#4a3f7a" />
    </div>
  </div>
</template>

<script setup>
import TravelingWorker from './TravelingWorker.vue'
import bgSrc from '../../assets/buildings/markt.svg'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }
.patrol { position: absolute; left: 14px; bottom: 2px; z-index: 3; }
</style>
