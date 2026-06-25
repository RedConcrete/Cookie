<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="dialog-box">
      <div class="dialog-header">
        <span class="dialog-title">Markt</span>
        <button class="dialog-close" @click="emit('close')">✕</button>
      </div>
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
  max-width: 98vw;
  width: 1260px;
  height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.dialog-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}
.dialog-close {
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-muted);
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
}
.dialog-close:hover { color: var(--text); background: var(--surface2); }
</style>
