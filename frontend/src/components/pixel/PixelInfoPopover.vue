<template>
  <div class="pip-wrap" @mouseenter="handleEnter" @mouseleave="handleLeave">
    <slot />

    <div
      v-if="barPlacement !== 'none'"
      class="pip-bar"
      :class="barPlacement === 'edge' ? 'pip-bar-edge' : 'pip-bar-chip'"
      :style="{ opacity: running ? 1 : 0 }"
    >
      <div class="pip-bar-fill" :style="{ width: (state.progress * 100) + '%', background: state.phase === 'drain' ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
    </div>

    <div v-if="state.visible" class="pip-panel" :class="sideClass" :style="{ width: width + 'px', zIndex: z }">
      <div v-if="title" class="pip-header">
        <PixelIcon v-if="icon" :name="icon" :size="16" />
        <span class="pip-title">{{ title }}</span>
      </div>
      <div class="pip-rows">
        <div v-for="(r, i) in rows" :key="i" class="pip-row">
          <span>{{ r.k }}</span>
          <span :style="{ color: colorFor(r.color) }">{{ r.v }}</span>
        </div>
      </div>
      <div v-if="note" class="pip-note">{{ note }}</div>
      <div v-if="pinned" class="pip-pin">GEPINNT &middot; MAUS RAUS = SCHLIESST</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PixelIcon from './PixelIcon.vue'
import { useHoverReveal } from '../../composables/useHoverReveal.js'

const props = defineProps({
  rows:  { type: Array, default: () => [] },   // [{ k, v, color }]  color: g|y|w|o|m|b
  title: { type: String, default: '' },
  icon:  { type: String, default: '' },
  note:  { type: String, default: '' },
  side:  { type: String, default: 'right' },   // right | left | below-left | below-right
  width: { type: Number, default: 268 },
  z:     { type: Number, default: 95 },
  barPlacement: { type: String, default: 'chip' }, // chip | edge | none
})

const emit = defineEmits(['enter', 'leave'])
const { state, onEnter, onLeave, running, pinned } = useHoverReveal()

function handleEnter() { onEnter(); emit('enter') }
function handleLeave() { onLeave(); emit('leave') }

const COLORS = { g: '#a9ff88', y: '#ffd76b', w: '#fff6e0', o: '#e0873a', m: '#8a7a5c', b: '#8fd3ff' }
function colorFor(c) { return COLORS[c] || COLORS.w }

const sideClass = computed(() => `pip-side-${props.side}`)
</script>

<style scoped>
.pip-wrap { position: relative; width: 100%; }

.pip-bar-chip { position: absolute; left: 0; right: 0; bottom: -9px; height: 7px; background: var(--px-ink); box-shadow: 0 0 0 2px var(--px-ink); z-index: 92; }
.pip-bar-edge { position: absolute; left: 0; right: 0; bottom: 0; height: 7px; background: rgba(16,11,7,.75); }
.pip-bar-fill { height: 100%; transition: none; }

.pip-panel {
  position: absolute;
  background: var(--px-wood);
  border: 4px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #55402a, 0 6px 0 rgba(0,0,0,.45);
  padding: 12px 14px;
  font-family: 'Pixelify Sans', system-ui, sans-serif;
}
.pip-side-right      { top: 0; left: calc(100% + 12px); }
.pip-side-left        { top: 0; right: calc(100% + 12px); }
.pip-side-below-left  { top: calc(100% + 14px); left: 0; }
.pip-side-below-right { top: calc(100% + 14px); right: 0; }

.pip-header { display: flex; align-items: center; gap: 7px; margin-bottom: 9px; }
.pip-title  { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); }

.pip-rows { display: flex; flex-direction: column; gap: 5px; }
.pip-row  { display: flex; justify-content: space-between; gap: 10px; font-size: 14px; color: #d6c6a6; white-space: nowrap; }
.pip-row span:last-child { font-family: 'Silkscreen', monospace; font-size: 11px; }

.pip-note { margin-top: 9px; padding-top: 8px; border-top: 3px solid #4d3a26; font-size: 13px; line-height: 1.5; color: #c8b795; }
.pip-pin  { margin-top: 8px; font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-green-txt); }
</style>
