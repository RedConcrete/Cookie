<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="pd-box px-panel">
      <div class="px-titlebar">
        <span>PRESTIGE</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
      <PrestigeView />
    </div>
  </div>
</template>

<script setup>
import PrestigeView from './PrestigeView.vue'
const emit = defineEmits(['close'])
</script>

<style scoped>
.pd-box { width: 520px; max-width: 95vw; max-height: 90vh; overflow: auto; }
</style>
