<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="pd-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('prestigeDialog.title') }}</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
      <PixelScrollBox class="pd-scroll">
        <PrestigeView />
      </PixelScrollBox>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PrestigeView from './PrestigeView.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import { useAudio } from '../composables/useAudio.js'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
.pd-box { width: 520px; max-width: 95vw; max-height: 90vh; display: flex; flex-direction: column; overflow: hidden; }
.pd-scroll { flex: 1 1 auto; min-height: 0; }
</style>
