<template>
  <div class="tw-outer" :style="outerStyle">
    <PixelWorker variant="walk" :dur="legDur" :hat="hat" :skin="skin" :torso="torso" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PixelWorker from '../pixel/PixelWorker.vue'

const props = defineProps({
  travelAnim:  { type: String, default: 'patrol' }, // patrol | trek | wander | wander2 | wander3 | rowwalk | commute
  travelDur:   { type: Number, default: 5 },
  travelDelay: { type: Number, default: 0 },
  legDur:      { type: Number, default: 0.5 },
  hat:         { type: String, default: '#c78539' },
  skin:        { type: String, default: '#fff1a9' },
  torso:       { type: String, default: '#a15c34' },
})

const outerStyle = computed(() => ({
  position: 'relative', width: '16px', height: '26px',
  animation: `px-${props.travelAnim} ${props.travelDur}s linear ${props.travelDelay}s infinite`,
}))
</script>
