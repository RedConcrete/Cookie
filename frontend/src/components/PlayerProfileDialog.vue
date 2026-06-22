<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="dialog-box">
      <button class="dialog-close" @click="emit('close')">✕</button>
      <PlayerProfileView :steamId="steamId" />
    </div>
  </div>
</template>

<script setup>
import PlayerProfileView from './PlayerProfileView.vue'

defineProps({ steamId: { type: String, required: true } })
const emit = defineEmits(['close'])
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}
.dialog-box {
  background: var(--surface);
  border: 2px solid var(--border);
  border-radius: 16px;
  width: 520px;
  max-width: 95vw;
  max-height: 90vh;
  overflow: auto;
  position: relative;
  padding: 20px;
}
.dialog-close {
  position: absolute;
  top: 12px; right: 14px;
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-muted);
}
.dialog-close:hover { color: var(--text); }
</style>
