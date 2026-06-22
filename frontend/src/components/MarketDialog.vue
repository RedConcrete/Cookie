<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="dialog-box dialog-wide">
      <button class="dialog-close" @click="emit('close')">✕</button>
      <MarketView />
    </div>
  </div>
</template>

<script setup>
import MarketView from '../views/MarketView.vue'
const emit = defineEmits(['close'])
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.dialog-box {
  background: var(--surface);
  border: 2px solid var(--border);
  border-radius: 16px;
  max-width: 95vw;
  max-height: 90vh;
  overflow: auto;
  position: relative;
  padding: 20px;
}
.dialog-wide { width: 860px; }
.dialog-close {
  position: absolute;
  top: 12px; right: 14px;
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-muted);
  line-height: 1;
}
.dialog-close:hover { color: var(--text); }
</style>
