<template>
  <div class="butter-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div v-if="workers > 0" class="churner">
      <PixelWorker anim="bob" :dur="0.7" hat="#fffaf0" torso="#4a3f7a"
        :tool="{ anim: 'churn', dur: 0.7, color: '#a97b4a', top: '4px', height: '18px' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import bgSrc from '../../assets/buildings/butter.svg'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.butter-scene { position: absolute; inset: 0; }
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }
.churner { position: absolute; left: 32px; bottom: 22px; z-index: 3; }
</style>
