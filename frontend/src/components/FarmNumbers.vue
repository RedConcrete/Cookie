<template>
  <div
    v-for="n in numbers"
    :key="n.id"
    class="farm-num"
    :style="{
      left:      n.currentX + 'px',
      top:       n.currentY + 'px',
      fontSize:  n.size + 'px',
      color:     n.color,
      opacity:   n.opacity,
      transform: `translateX(-50%) rotate(${n.rotate}deg)`,
    }"
  >{{ n.label }}<PixelIcon v-if="n.icon" :name="n.icon" :size="Math.round(n.size * 0.8)" class="farm-num-icon" /></div>
</template>

<script setup>
import { useFarmNumbers } from '../composables/useFarmNumbers.js'
import PixelIcon from './pixel/PixelIcon.vue'
const { numbers } = useFarmNumbers()
</script>

<style>
.farm-num {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 3px;
  pointer-events: none;
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(0,0,0,0.6);
  white-space: nowrap;
  z-index: 100;
  transform-origin: center bottom;
  transition: none;
}
.farm-num-icon { flex-shrink: 0; filter: drop-shadow(0 1px 2px rgba(0,0,0,0.6)); }
</style>
