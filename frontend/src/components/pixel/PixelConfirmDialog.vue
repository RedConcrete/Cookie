<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="pcd-box px-panel">
      <div class="px-titlebar">
        <span>{{ title }}</span>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="pcd-body">
        <p>{{ body }}</p>
      </div>

      <div class="pcd-actions">
        <button class="px-btn px-btn-flat" @click="emit('close')">{{ t('common.cancel') }}</button>
        <button class="px-btn" :class="danger ? 'px-btn-sell' : 'px-btn-accent'" @click="emit('confirm')">
          <ShortcutSlot />{{ confirmLabel || t('common.confirm') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import ShortcutSlot from './ShortcutSlot.vue'

defineProps({
  title: { type: String, required: true },
  body: { type: String, required: true },
  confirmLabel: { type: String, default: '' },
  danger: { type: Boolean, default: false },
})
const emit = defineEmits(['confirm', 'close'])
const { t } = useI18n()
</script>

<style scoped>
.pcd-box { width: 380px; max-width: 92vw; }
.pcd-body { padding: 20px; }
.pcd-body p { font-size: 14px; color: var(--px-ink-txt); line-height: 1.5; margin: 0; }

.pcd-actions {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 0 20px 20px;
}
</style>
