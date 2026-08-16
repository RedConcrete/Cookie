<template>
  <span class="ctrl-btn-icon" :style="{ color }">{{ label }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { useInputMethod } from '../../composables/useInputMethod.js'

// Renders a gamepad button as a short label matching the connected
// controller family, in place of a keyboard-key badge (see ShortcutSlot.vue).
// `index` uses the same numbering as GAMEPAD_BUTTON_LABELS in
// useActionHotkeys.js (standard Gamepad API button order).
const props = defineProps({
  index: { type: Number, required: true },
})

const input = useInputMethod()

// Face buttons (0-3) get a real per-family label + the palette color that
// matches the physical button cap; everything else is a neutral label that
// only changes text between Xbox- and PlayStation-style naming.
const FACE = {
  0: { xbox: 'A', playstation: '✕', generic: 'A', color: 'var(--px-green)' },
  1: { xbox: 'B', playstation: '○', generic: 'B', color: 'var(--px-red)' },
  2: { xbox: 'X', playstation: '□', generic: 'X', color: 'var(--px-blue)' },
  3: { xbox: 'Y', playstation: '△', generic: 'Y', color: 'var(--px-gold)' },
}
const OTHER = {
  4: { xbox: 'LB', playstation: 'L1', generic: 'LB' },
  5: { xbox: 'RB', playstation: 'R1', generic: 'RB' },
  6: { xbox: 'LT', playstation: 'L2', generic: 'LT' },
  7: { xbox: 'RT', playstation: 'R2', generic: 'RT' },
  8: { xbox: 'BACK', playstation: 'SHARE', generic: 'BACK' },
  9: { xbox: 'START', playstation: 'OPT', generic: 'START' },
  10: { xbox: 'LS', playstation: 'L3', generic: 'LS' },
  11: { xbox: 'RS', playstation: 'R3', generic: 'RS' },
  12: { xbox: '↑', playstation: '↑', generic: '↑' },
  13: { xbox: '↓', playstation: '↓', generic: '↓' },
  14: { xbox: '←', playstation: '←', generic: '←' },
  15: { xbox: '→', playstation: '→', generic: '→' },
  16: { xbox: 'HOME', playstation: 'PS', generic: 'HOME' },
}

const label = computed(() => {
  const entry = FACE[props.index] ?? OTHER[props.index]
  if (!entry) return `BTN ${props.index}`
  return entry[input.controllerFamily.value] ?? entry.generic
})
const color = computed(() => FACE[props.index]?.color ?? 'var(--px-gold-txt)')
</script>

<style scoped>
.ctrl-btn-icon {
  font-family: 'Silkscreen', monospace;
  font-weight: 700;
  line-height: 1;
}
</style>
