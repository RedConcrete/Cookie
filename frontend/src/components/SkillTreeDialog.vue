<template>
  <div class="std-root" @wheel.stop @mousedown.stop @mousemove.stop>
    <button class="std-close" @click="emit('close')" :title="t('skillTreeDialog.title')">&times;</button>
    <SkillTreeView class="std-body" />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import SkillTreeView from './SkillTreeView.vue'
import { useAudio } from '../composables/useAudio.js'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
/* Full-screen takeover, not a centered modal card -- the skill tree needs
   its own dedicated canvas space, not a shrunk-down dialog box. No title
   bar row either -- just a floating close button so the whole viewport
   height goes to the tree. */
.std-root {
  position: fixed;
  inset: 0;
  z-index: 500;
  background: var(--px-cream);
}
.std-body { position: absolute; inset: 0; }

.std-close {
  position: absolute; top: 14px; right: 14px; z-index: 30;
  font-family: 'Silkscreen', monospace; font-size: 16px;
  padding: 6px 12px;
  border: 3px solid var(--px-ink); background: var(--px-wood3); color: var(--px-cream);
  cursor: pointer;
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.std-close:hover { background: var(--px-red-dk); }
</style>
