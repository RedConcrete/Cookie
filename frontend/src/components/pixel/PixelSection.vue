<template>
  <div class="ps-section">
    <button class="ps-head" @click="open = !open">
      <ShortcutSlot />
      <span class="ps-label">{{ title }}</span>
      <span class="ps-arrow">{{ open ? '▾' : '▸' }}</span>
    </button>
    <div v-if="open" class="ps-body">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ShortcutSlot from './ShortcutSlot.vue'

const props = defineProps({
  title:       { type: String, required: true },
  defaultOpen: { type: Boolean, default: false },
})
const open = ref(props.defaultOpen)
</script>

<style scoped>
.ps-section { border: 3px solid var(--px-brown2); background: var(--px-cream2); }

/* The whole header bar is the click target (edge-to-edge, full padding
   included), not just the text glyphs -- was a <button> with zero padding
   before, so only the thin text baseline actually registered clicks. */
.ps-head {
  position: relative;
  width: 100%; display: flex; align-items: center; justify-content: space-between;
  background: none; border: none; padding: 10px; margin: 0; font: inherit; cursor: pointer;
}
.ps-head:hover { background: rgba(0,0,0,.06); }
/* Dark ink text, not the low-contrast tan/orange combo this used to have --
   both were medium-brightness against the same tan panel and unreadable. */
.ps-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-ink-txt); letter-spacing: 1px; }
.ps-head:hover .ps-label { color: var(--px-orange-dk); }
.ps-arrow { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }

.ps-body { padding: 10px; border-top: 2px solid #fff1a9; display: flex; flex-direction: column; gap: 10px; }
</style>
