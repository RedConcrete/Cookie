<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="hrd-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('hardResetDialog.title') }}</span>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="hrd-body">
        <p>{{ t(mode === 'bankruptcy' ? 'hardResetDialog.bankruptcyBody' : 'hardResetDialog.manualBody') }}</p>
        <p class="hrd-warning">{{ t('hardResetDialog.warning') }}</p>
        <p v-if="errorMsg" class="hrd-error">{{ errorMsg }}</p>
      </div>

      <div class="hrd-actions">
        <button class="px-btn px-btn-flat" :disabled="resetting" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button class="px-btn px-btn-sell" :disabled="resetting" @click="confirmReset">
          <ShortcutSlot />{{ resetting ? t('common.loading') : t('hardResetDialog.confirm') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { hardResetPlayer } from '../services/api.js'
import { usePlayerStore } from '../stores/player.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

defineProps({
  mode: { type: String, default: 'manual' }, // 'bankruptcy' | 'manual'
})
const emit = defineEmits(['close'])
const { t } = useI18n()
const playerStore = usePlayerStore()

const resetting = ref(false)
const errorMsg = ref(null)

async function confirmReset() {
  resetting.value = true
  errorMsg.value = null
  try {
    await hardResetPlayer(playerStore.steamId)
    await playerStore.init(playerStore.steamId)
    emit('close')
  } catch (e) {
    errorMsg.value = t('hardResetDialog.error')
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.hrd-box { width: 420px; max-width: 92vw; }
.hrd-body { padding: 20px; display: flex; flex-direction: column; gap: 12px; }
.hrd-body p { font-size: 14px; color: var(--px-ink-txt); line-height: 1.5; margin: 0; }
.hrd-warning { color: var(--px-red-dk); font-weight: 700; }
.hrd-error { color: var(--px-red-dk); }

.hrd-actions {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 0 20px 20px;
}
</style>
