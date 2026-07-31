<template>
  <div class="pw-root" :style="rootStyle">
    <svg class="pw-body" viewBox="0 0 10 16" width="16" height="26">
      <rect v-for="(cell, i) in cells" :key="i" :x="cell.x" :y="cell.y" width="1" height="1" :fill="cell.fill" />
    </svg>
    <div v-if="tool" class="pw-tool" :style="toolStyle"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { buildSpriteCells, dots } from './spriteGrid.js'

const props = defineProps({
  // 'work' = per-building work loop (ease-in-out), 'walk' = 2-frame walk cycle (steps)
  variant: { type: String, default: 'work' },
  anim:    { type: String, default: 'bob' },   // bend | bob | reach | milk | knead | walk
  dur:     { type: Number, default: 1 },
  delay:   { type: Number, default: 0 },
  hat:     { type: String, default: '#c9702a' },
  skin:    { type: String, default: '#f0c9a0' },
  torso:   { type: String, default: '#8a5a34' },
  tool:    { type: Object, default: null },    // { anim, dur, delay, color, top, left, width, height }
})

// 10x16 pixel grid, side view: cap, head w/ eye, torso, two legs+feet.
const GRID = [
  dots(2) + 'h'.repeat(6) + dots(2),
  dots(2) + 'h'.repeat(6) + dots(2),
  dots(2) + 's'.repeat(6) + dots(2),
  dots(2) + 's'.repeat(4) + 'e' + 's' + dots(2),
  dots(2) + 's'.repeat(6) + dots(2),
  dots(2) + 's'.repeat(6) + dots(2),
  dots(1) + 't'.repeat(8) + dots(1),
  dots(1) + 't'.repeat(8) + dots(1),
  dots(1) + 't'.repeat(8) + dots(1),
  dots(1) + 't'.repeat(8) + dots(1),
  dots(1) + 't'.repeat(8) + dots(1),
  dots(1) + 't'.repeat(8) + dots(1),
  dots(2) + 't'.repeat(2) + dots(2) + 't'.repeat(2) + dots(2),
  dots(2) + 't'.repeat(2) + dots(2) + 't'.repeat(2) + dots(2),
  dots(2) + 'd'.repeat(2) + dots(2) + 'd'.repeat(2) + dots(2),
  dots(2) + 'd'.repeat(2) + dots(2) + 'd'.repeat(2) + dots(2),
]

const cells = computed(() => buildSpriteCells(GRID, {
  h: props.hat, s: props.skin, t: props.torso, e: '#1a120b', d: '#1a120b',
}))

const rootStyle = computed(() => ({
  width: '16px', height: '26px', position: 'relative',
  animation: props.variant === 'walk'
    ? `px-walk ${props.dur}s steps(2,end) ${props.delay}s infinite`
    : `px-${props.anim} ${props.dur}s ease-in-out ${props.delay}s infinite`,
}))

const toolStyle = computed(() => {
  const t = props.tool
  if (!t) return {}
  return {
    position: 'absolute',
    left:   t.left   ?? '13px',
    top:    t.top    ?? '6px',
    width:  t.width  ?? '4px',
    height: t.height ?? '15px',
    background: t.color ?? '#8d6a3d',
    boxShadow: '0 0 0 2px #1a120b',
    transformOrigin: '50% 90%',
    animation: `px-${t.anim} ${t.dur}s ease-in-out ${t.delay ?? 0}s infinite`,
  }
})
</script>

<style scoped>
.pw-body { position: absolute; left: 0; top: 0; display: block; shape-rendering: crispEdges; }
</style>
