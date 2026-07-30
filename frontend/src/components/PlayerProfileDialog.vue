<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="ppd-box px-panel">
      <div class="px-titlebar">
        <span>PROFIL</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
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
.ppd-box { width: 560px; max-width: 95vw; max-height: 90vh; overflow: auto; }
</style>
