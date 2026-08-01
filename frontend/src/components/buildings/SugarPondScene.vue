<template>
  <!-- The scene itself — harvest fires via BuildingFrame's @mouseenter/@mouseleave -->
  <div class="pond-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <!-- Worker -->
    <div v-if="workers > 0" class="cutter">
      <PixelWorker anim="bend" :dur="0.75" hat="#9fb3c2" torso="#8a5a34"
        :tool="{ anim: 'swing', dur: 0.75, color: '#8d6a3d' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import bgSrc from '../../assets/buildings/pond.svg'

defineProps({ workers: { type: Number, default: 0 } })
</script>

<style scoped>
.pond-scene {
  position: absolute; inset: 0;
  cursor: pointer;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }

.cutter { position: absolute; left: 110px; bottom: 1px; z-index: 3; }
</style>
