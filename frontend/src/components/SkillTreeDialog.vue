<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="std-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('skillTreeDialog.title') }}</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
      <SkillTreeView class="std-body" />
    </div>
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
.std-box {
  width: 900px;
  max-width: 96vw;
  height: 640px;
  max-height: 88vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.std-body { flex: 1 1 auto; min-height: 0; }
</style>
