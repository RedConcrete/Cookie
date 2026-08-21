<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="sd-box px-panel" :style="dialogStyle">
      <div class="px-titlebar" @pointerdown="onDragStart">
        <span>{{ t('statsDialog.title') }}</span>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>
      <PixelScrollBox class="sd-scroll">
        <StatsView />
      </PixelScrollBox>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import StatsView from './StatsView.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import { useAudio } from '../composables/useAudio.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { useDraggableDialog } from '../composables/useDraggableDialog.js'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()
const { dialogStyle, onDragStart } = useDraggableDialog()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
.sd-box { width: 760px; max-width: 95vw; max-height: 90vh; display: flex; flex-direction: column; overflow: hidden; }
.sd-scroll { flex: 1 1 auto; min-height: 0; }
</style>
