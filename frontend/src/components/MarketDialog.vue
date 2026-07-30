<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="md-box px-panel">
      <div class="px-titlebar">
        <span>MARKT</span>
        <button class="px-close" @click="emit('close')">&times;</button>
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
.md-box {
  max-width: 98vw;
  width: 1260px;
  height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
