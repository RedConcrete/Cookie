<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="ud-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('upgradeDialog.title') }}</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
      <PixelScrollBox class="ud-scroll">
        <UpgradeShopView />
      </PixelScrollBox>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import UpgradeShopView from './UpgradeShopView.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
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
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.ud-scroll { flex: 1 1 auto; min-height: 0; }
</style>
