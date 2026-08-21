<template>
  <div class="th-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <div v-if="idleCount > 0" class="th-idle-label" :class="{ 'th-idle-label-warn': idleWarn }">
      {{ t('townHallScene.idleLabel', { count: idleCount }) }}
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import bgSrc from '../../assets/buildings/placeholder/rathaus.png'

const { t } = useI18n()

defineProps({
  idleCount: { type: Number, default: 0 },
  idleWarn:  { type: Boolean, default: false },
})
</script>

<style scoped>
.th-scene {
  position: absolute; inset: 0;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: contain; image-rendering: pixelated; }

.th-idle-label {
  position: absolute; left: 66px; bottom: 0px; z-index: 12;
  font-family: 'Silkscreen', monospace; font-size: 9px; padding: 2px 5px;
  background: var(--px-wood2); color: var(--px-cream); border: 2px solid var(--px-ink);
}
.th-idle-label-warn {
  background: #402e2b; color: #e67a84; border-color: #764032;
  animation: th-idle-blink 1.2s step-end infinite;
}
@keyframes th-idle-blink { 0%,100% { opacity:1 } 50% { opacity:0.4 } }
</style>
