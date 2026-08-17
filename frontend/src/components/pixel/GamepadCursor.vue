<template>
  <div v-if="visible" class="gp-cursor" :class="{ 'gp-cursor-free': mode === 'free' }" :style="style">
    <div class="gp-cursor-ring" :class="{ pulse }"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useInputMethod } from '../../composables/useInputMethod.js'
import { useGamepadCursor } from '../../composables/useGamepadCursor.js'

// Pulse briefly true on an A-press (parent clears it via setTimeout, same
// shake-toggle pattern as BuildingFrame.vue's collect-shake).
defineProps({
  pulse: { type: Boolean, default: false },
})

const input = useInputMethod()
const gamepadCursor = useGamepadCursor()
const mode = gamepadCursor.mode

const visible = computed(() => input.activeMethod.value === 'gamepad')
// 'fixed' mode is pure CSS (top/left:50%, see .gp-cursor below) so it stays
// centered on any window size without a resize listener -- only 'free' mode
// needs an explicit JS position.
const style = computed(() =>
  mode.value === 'free'
    ? { left: gamepadCursor.cursorX.value + 'px', top: gamepadCursor.cursorY.value + 'px' }
    : {}
)
</script>

<style scoped>
.gp-cursor {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 28px;
  height: 28px;
  pointer-events: none;
  z-index: 600;
}
.gp-cursor-free {
  transform: translate(-50%, -50%);
  transition: left 40ms linear, top 40ms linear;
}
.gp-cursor-ring {
  width: 100%;
  height: 100%;
  border: 3px solid var(--px-gold);
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0,0,0,.5);
}
.gp-cursor-ring.pulse { animation: gp-cursor-pulse .3s ease-out; }
@keyframes gp-cursor-pulse {
  0%   { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.7); opacity: 0; }
}
</style>
