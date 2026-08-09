<template>
  <div
    v-for="n in numbers"
    :key="n.id"
    class="wage-num"
    :style="{
      left:    n.currentX + 'px',
      top:     n.currentY + 'px',
      opacity: n.opacity,
    }"
  >{{ n.label }}</div>
</template>

<script setup>
import { useWageNumbers } from '../composables/useWageNumbers.js'
const { numbers } = useWageNumbers()
</script>

<style>
/* Bewusst NICHT in .hof-canvas eingehaengt (das ist pannable/zoombar, Weltkoordinaten) --
   der Cookie-Chip ist Teil der fixen .hud-Overlay-Schicht, also position: fixed mit
   Bildschirmkoordinaten statt absolute wie bei .farm-num. */
.wage-num {
  position: fixed;
  transform: translateX(-50%);
  pointer-events: none;
  font-family: 'Silkscreen', monospace;
  font-weight: 700;
  font-size: 16px;
  color: var(--px-red);
  text-shadow: 0 1px 3px rgba(0,0,0,0.6);
  white-space: nowrap;
  z-index: 200;
  transition: none;
}
</style>
