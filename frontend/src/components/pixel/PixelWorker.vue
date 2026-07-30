<template>
  <div class="pw-root" :style="rootStyle">
    <div class="pw-hat"   :style="{ background: hat }"></div>
    <div class="pw-face"  :style="{ background: skin }"></div>
    <div class="pw-torso" :style="{ background: torso }"></div>
    <div class="pw-feet"></div>
    <div v-if="tool" class="pw-tool" :style="toolStyle"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

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
    boxShadow: '0 0 0 2px #3a2a1c',
    transformOrigin: '50% 90%',
    animation: `px-${t.anim} ${t.dur}s ease-in-out ${t.delay ?? 0}s infinite`,
  }
})
</script>

<style scoped>
.pw-hat   { position: absolute; left: 3px; top: 0;  width: 10px; height: 5px; }
.pw-face  { position: absolute; left: 4px; top: 5px; width: 8px; height: 7px; box-shadow: 0 0 0 2px #3a2a1c; }
.pw-torso { position: absolute; left: 2px; top: 12px; width: 12px; height: 10px; box-shadow: 0 0 0 2px #3a2a1c; }
.pw-feet  { position: absolute; left: 2px; top: 22px; width: 12px; height: 4px; background: #3a2a1c; }
</style>
