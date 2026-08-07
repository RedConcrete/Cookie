<template>
  <!-- The scene itself — harvest fires via BuildingFrame's @mouseenter/@mouseleave -->
  <div class="pond-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <!-- Worker -->
    <div v-if="workers > 0" class="cutter">
      <PixelWorker :anim="idle ? 'bob' : 'bend'" :dur="0.75" hat="#aea47e" torso="#a15c34"
        :tool="idle ? null : { anim: 'swing', dur: 0.75, color: '#a15c34' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/pond.png'

defineProps({ workers: { type: Number, default: 0 }, idle: { type: Boolean, default: false } })
</script>

<style scoped>
.pond-scene {
  position: absolute; inset: 0;
  cursor: pointer;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }

.cutter { position: absolute; left: 110px; bottom: 1px; z-index: 3; }
</style>
