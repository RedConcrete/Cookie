<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="ud-box px-panel px-scroll">
      <div class="px-titlebar">
        <span>{{ t('upgradeDialog.title') }}</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
      <UpgradeShopView />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import UpgradeShopView from './UpgradeShopView.vue'
import { useAudio } from '../composables/useAudio.js'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
.ud-box {
  width: 700px;
  max-width: 95vw;
  max-height: 85vh;
  overflow: auto;
  display: flex;
  flex-direction: column;
}
</style>
