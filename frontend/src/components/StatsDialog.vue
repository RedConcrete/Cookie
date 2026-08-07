<template>
  <div class="sd-root" @wheel.stop @mousedown.stop @mousemove.stop>
    <button class="sd-close" @click="emit('close')" :title="t('statsDialog.title')"><ShortcutSlot />&times;</button>
    <div class="sd-title">{{ t('statsDialog.title') }}</div>
    <PixelScrollBox class="sd-body">
      <StatsView />
    </PixelScrollBox>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import StatsView from './StatsView.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import { useAudio } from '../composables/useAudio.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
/* Full-screen takeover, same pattern as SkillTreeDialog -- a stat dashboard wants the
   whole viewport for its grid of panels, not a shrunk-down centered card. */
.sd-root {
  position: fixed;
  inset: 0;
  z-index: 500;
  background: var(--px-cream);
  display: flex;
  flex-direction: column;
}
.sd-title {
  text-align: center; padding: 18px 0 4px;
  font-family: 'Silkscreen', monospace; font-size: 15px; letter-spacing: 1px;
  color: var(--px-ink-txt); flex-shrink: 0;
}
.sd-body { flex: 1 1 auto; min-height: 0; }

.sd-close {
  position: absolute; top: 14px; right: 14px; z-index: 30;
  font-family: 'Silkscreen', monospace; font-size: 16px;
  padding: 6px 12px;
  border: 3px solid var(--px-ink); background: var(--px-wood3); color: var(--px-cream);
  cursor: pointer;
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.sd-close:hover { background: var(--px-red-dk); }
</style>
