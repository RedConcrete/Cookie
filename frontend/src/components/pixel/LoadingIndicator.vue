<template>
  <span class="pli">
    <PixelIcon name="cookie" :size="size" class="pli-icon" />
    <span class="pli-text">{{ t('common.loading') }}{{ dots }}</span>
  </span>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import PixelIcon from './PixelIcon.vue'

defineProps({ size: { type: Number, default: 18 } })
const { t } = useI18n()

// One dot changes per tick, bouncing between 1 and 3 instead of jumping straight
// back to 3 -- keeps the "only one dot moves at a time" rhythm going both ways.
const DOT_STEPS = ['...', '..', '.', '..']
const dots = ref(DOT_STEPS[0])
let step = 0
let timer = null

onMounted(() => {
  timer = setInterval(() => {
    step = (step + 1) % DOT_STEPS.length
    dots.value = DOT_STEPS[step]
  }, 400)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.pli { display: inline-flex; align-items: center; gap: 8px; }
.pli-icon { animation: pli-spin 1s steps(8, end) infinite; }
.pli-text { font-family: 'Silkscreen', monospace; }

@keyframes pli-spin {
  to { transform: rotate(360deg); }
}
</style>
