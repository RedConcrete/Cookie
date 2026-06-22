<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="dialog-box">
      <button class="dialog-close" @click="emit('close')">✕</button>
      <RecipeCard />
    </div>
  </div>
</template>

<script setup>
import RecipeCard from './RecipeCard.vue'
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
  width: 420px;
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
  line-height: 1;
}
.dialog-close:hover { color: var(--text); }
</style>
