<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="book">
      <RecipeCard @close="close" />
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import RecipeCard from './RecipeCard.vue'
import { useAudio } from '../composables/useAudio.js'

const emit  = defineEmits(['close'])
const audio = useAudio()

onMounted(() => audio.playBookOpen())

function close() {
  audio.playBookClose()
  emit('close')
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.book {
  width: min(820px, 96vw);
  height: min(560px, 90vh);
  display: flex;
  border-radius: 4px 12px 12px 4px;
  box-shadow:
    -6px 0 16px rgba(0,0,0,0.5),
    0 8px 32px rgba(0,0,0,0.4),
    inset 0 0 0 1px rgba(255,255,255,0.05);
  overflow: hidden;
}
</style>
