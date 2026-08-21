<template>
  <div class="px-dialog-overlay" @click.self="emit('close', dontAskAgain)" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="hrd-box px-panel" :style="dialogStyle">
      <div class="px-titlebar" @pointerdown="onDragStart">
        <span>{{ t('hardResetDialog.title') }}</span>
        <button class="px-close" @click="emit('close', dontAskAgain)"><ShortcutSlot />&times;</button>
      </div>

      <div class="hrd-body">
        <p>{{ t(mode === 'bankruptcy' ? 'hardResetDialog.bankruptcyBody' : 'hardResetDialog.manualBody') }}</p>
        <p class="hrd-warning">{{ t('hardResetDialog.warning') }}</p>
        <label v-if="mode === 'bankruptcy'" class="hrd-snooze">
          <input type="checkbox" v-model="dontAskAgain" />
          {{ t('hardResetDialog.dontAskAgain') }}
        </label>
      </div>

      <div class="hrd-actions">
        <button class="px-btn px-btn-flat" @click="emit('close', dontAskAgain)">{{ t('common.cancel') }}</button>
        <button class="px-btn px-btn-sell" @click="emit('confirmed')">
          <ShortcutSlot />{{ t('hardResetDialog.confirm') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { useDraggableDialog } from '../composables/useDraggableDialog.js'

defineProps({
  mode: { type: String, default: 'manual' }, // 'bankruptcy' | 'manual'
})
const emit = defineEmits(['close', 'confirmed'])
const { t } = useI18n()
const { dialogStyle, onDragStart } = useDraggableDialog()

// Nur fuers Bankrott-Warnung relevant (siehe FarmGridView#onBankruptcyWarningClose) --
// Spieler soll nicht bei jedem 15s-Wage-Poll erneut genervt werden, solange er noch
// versucht rauszukommen.
const dontAskAgain = ref(false)
</script>

<style scoped>
.hrd-box { width: 420px; max-width: 92vw; }
.hrd-body { padding: 20px; display: flex; flex-direction: column; gap: 12px; }
.hrd-body p { font-size: 14px; color: var(--px-ink-txt); line-height: 1.5; margin: 0; }
.hrd-warning { color: var(--px-red-dk); font-weight: 700; }
.hrd-error { color: var(--px-red-dk); }
.hrd-snooze {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: var(--px-ink-txt);
  cursor: pointer;
}

.hrd-actions {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 0 20px 20px;
}
</style>
