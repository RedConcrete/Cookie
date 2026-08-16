<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="cd-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('creditsDialog.title') }}</span>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <PixelScrollBox class="cd-scroll">
        <div class="cd-body">
          <div v-for="section in sections" :key="section.titleKey" class="cd-section">
            <div class="cd-section-title">{{ t(section.titleKey) }}</div>
            <div class="cd-section-entry" v-html="t(section.entryKey)"></div>
          </div>
        </div>
      </PixelScrollBox>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const { t } = useI18n()

const sections = [
  { titleKey: 'creditsDialog.developmentTitle', entryKey: 'creditsDialog.developmentEntry' },
  { titleKey: 'creditsDialog.artTitle',          entryKey: 'creditsDialog.artEntry' },
  { titleKey: 'creditsDialog.audioTitle',        entryKey: 'creditsDialog.audioEntry' },
  { titleKey: 'creditsDialog.toolsTitle',        entryKey: 'creditsDialog.toolsEntry' },
  { titleKey: 'creditsDialog.thanksTitle',        entryKey: 'creditsDialog.thanksEntry' },
]
</script>

<style scoped>
.cd-box { width: 480px; max-width: 92vw; max-height: 80vh; display: flex; flex-direction: column; }
.cd-scroll { flex: 1; min-height: 0; }
.cd-body { padding: 20px; display: flex; flex-direction: column; gap: 18px; }

.cd-section-title {
  font-family: 'Silkscreen', monospace; font-size: 11px; letter-spacing: 1px;
  color: var(--px-orange-dk); margin-bottom: 4px;
}
.cd-section-entry { font-size: 14px; color: var(--px-ink-txt); line-height: 1.5; }
.cd-section-entry :deep(a) { color: var(--px-orange-dk); }
.cd-section-entry :deep(a:hover) { color: var(--px-orange); }
</style>
