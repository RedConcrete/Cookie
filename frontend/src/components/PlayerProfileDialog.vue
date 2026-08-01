<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="ppd-box px-panel px-scroll">
      <div class="px-titlebar">
        <span>PROFIL</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
      <PlayerProfileView :steamId="steamId" />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import PlayerProfileView from './PlayerProfileView.vue'
import { useAudio } from '../composables/useAudio.js'

defineProps({ steamId: { type: String, required: true } })
const emit = defineEmits(['close'])
const audio = useAudio()

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
.ppd-box { width: 560px; max-width: 95vw; max-height: 90vh; overflow: auto; }
</style>
