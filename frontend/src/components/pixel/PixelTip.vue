<template>
  <Teleport to="body">
    <div v-if="visible" ref="tipRef" class="ptip" :class="variant">{{ text }}</div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'

const props = defineProps({
  anchor:  { type: Object, default: null },   // DOM element to position against
  text:    { type: String, default: '' },
  variant: { type: String, default: 'buy' },   // 'buy' (green) | 'sell' (red)
  visible: { type: Boolean, default: false },
})

const tipRef = ref(null)

// Immer im Body verankert (Teleport) statt im scrollbaren Listen-Container --
// sonst schneidet dessen overflow:auto das Popup ab, sobald es am oberen Rand
// nach oben poppen will. z-index allein reicht dafuer nicht, da overflow das
// Kind komplett aus dem Renderbereich clippt, unabhaengig vom Stacking.
async function reposition() {
  const anchorEl = props.anchor
  if (!anchorEl || !props.visible) return
  await nextTick()
  const tip = tipRef.value
  if (!tip) return
  const ar = anchorEl.getBoundingClientRect()
  const tr = tip.getBoundingClientRect()
  const GAP = 6
  let top = ar.top - tr.height - GAP
  if (top < 4) top = ar.bottom + GAP // Platz oben knapp -> unter den Trigger klappen
  let left = ar.left + ar.width / 2 - tr.width / 2
  left = Math.min(Math.max(4, left), window.innerWidth - tr.width - 4)
  tip.style.top = top + 'px'
  tip.style.left = left + 'px'
}

watch(() => props.visible, (v) => { if (v) reposition() })
window.addEventListener('scroll', reposition, true)
onUnmounted(() => window.removeEventListener('scroll', reposition, true))
</script>

<style scoped>
.ptip {
  position: fixed;
  z-index: 700;
  width: max-content;
  max-width: 170px;
  background: var(--px-wood);
  border: 3px solid var(--px-green);
  box-shadow: inset 2px 2px 0 #402e2b, 0 4px 0 rgba(0,0,0,.4);
  padding: 5px 8px;
  font-family: 'Silkscreen', monospace;
  font-size: 9px;
  line-height: 1.4;
  color: #fff1a9;
  text-align: center;
  pointer-events: none;
}
.ptip.sell { border-color: var(--px-red); }
</style>
