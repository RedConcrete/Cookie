<template>
  <div class="px-dialog-overlay" @click.self="close" @wheel.stop>
    <div class="book px-panel">
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
.book {
  width: min(960px, 96vw);
  height: min(720px, 90vh);
  display: flex;
  overflow: hidden;
}
</style>
