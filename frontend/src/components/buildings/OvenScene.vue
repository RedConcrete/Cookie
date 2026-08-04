<template>
  <div class="oven-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />

    <!-- Chimney with animated smoke -->
    <div class="bh-chimney">
      <div class="bh-smoke bh-smoke-1"></div>
      <div class="bh-smoke bh-smoke-2"></div>
      <div class="bh-smoke bh-smoke-3"></div>
    </div>

    <!-- Baker -->
    <div class="bh-baker">
      <PixelWorker anim="knead" :dur="0.6" hat="#fff1a9" torso="#aea47e"
        :tool="{ anim: 'churn', dur: 0.6, color: '#a15c34' }" />
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import bgSrc from '../../assets/buildings/placeholder/ofen.png'
</script>

<style scoped>
.oven-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }

/* Chimney */
.bh-chimney {
  position: absolute; left: 95px; top: 0; width: 14px; height: 26px;
  overflow: visible;
}
.bh-smoke {
  position: absolute; left: 2px; width: 10px; height: 10px;
  border-radius: 50%; background: rgb(200, 190, 180);
  animation: bh-rise 2s ease-out infinite;
}
.bh-smoke-1 { bottom: 100%; animation-delay: 0s; }
.bh-smoke-2 { bottom: 100%; animation-delay: 0.7s; opacity: 0.7; }
.bh-smoke-3 { bottom: 100%; animation-delay: 1.4s; opacity: 0.4; }
@keyframes bh-rise {
  0%   { transform: translateY(0)   scale(1); opacity: 1; }
  100% { transform: translateY(-40px) translateX(12px) scale(2); opacity: 0; }
}

/* Baker worker */
.bh-baker { position: absolute; left: 78px; bottom: 4px; z-index: 3; }
</style>
