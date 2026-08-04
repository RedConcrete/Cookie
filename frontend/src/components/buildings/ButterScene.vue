<template>
  <div class="butter-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div v-if="workers > 0" class="churner">
      <PixelWorker anim="bob" :dur="0.7" hat="#fff1a9" torso="#534664"
        :tool="{ anim: 'churn', dur: 0.7, color: '#c78539', top: '4px', height: '18px' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/butter.png'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.butter-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }
.churner { position: absolute; left: 62px; bottom: 18px; z-index: 3; }
</style>
