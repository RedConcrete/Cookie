<template>
  <div>
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div v-if="idleCount > 0" class="th-idle-label" :class="{ 'th-idle-label-warn': idleWarn }">
      {{ idleCount }} IDLE
    </div>
  </div>
</template>

<script setup>
import bgSrc from '../../assets/buildings/rathaus.svg'

defineProps({
  idleCount: { type: Number, default: 0 },
  idleWarn:  { type: Boolean, default: false },
})
</script>

<style scoped>
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }

.th-idle-label {
  position: absolute; left: 0px; bottom: 0px; z-index: 12;
  font-family: 'Silkscreen', monospace; font-size: 9px; padding: 2px 5px;
  background: var(--px-wood2); color: var(--px-muted); border: 2px solid var(--px-ink);
}
.th-idle-label-warn {
  background: #5a1a1a; color: #ff8888; border-color: #8b3333;
  animation: th-idle-blink 1.2s step-end infinite;
}
@keyframes th-idle-blink { 0%,100% { opacity:1 } 50% { opacity:0.4 } }
</style>
