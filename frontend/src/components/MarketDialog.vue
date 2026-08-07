<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="md-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('marketDialog.title') }}</span>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>
      <MarketView />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import MarketView from '../views/MarketView.vue'
import { useAudio } from '../composables/useAudio.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
.md-box {
  max-width: 98vw;
  width: 1260px;
  height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
