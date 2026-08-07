<template>
  <div class="psb-wrap">
    <div ref="contentEl" class="psb-content" @scroll="update">
      <slot />
    </div>
    <div v-if="needsScroll" class="psb-track" @mousedown="onTrackDown">
      <div
        class="psb-thumb"
        :style="{ height: thumbHeightPct + '%', top: thumbTopPct + '%' }"
        @mousedown.stop="onThumbDown"
      ></div>
    </div>
  </div>
</template>

<script setup>
// Native ::-webkit-scrollbar styling can only go so far (thin native chrome,
// no real control over the thumb's box model) -- this instead hides the real
// scrollbar and draws a fake one with the exact same border/bevel treatment
// as .sd-slider, so every scrollable dialog matches the sliders pixel-for-pixel.
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

const contentEl = ref(null)
const needsScroll = ref(false)
const thumbHeightPct = ref(100)
const thumbTopPct = ref(0)

function update() {
  const el = contentEl.value
  if (!el) return
  const { scrollHeight, clientHeight, scrollTop } = el
  needsScroll.value = scrollHeight > clientHeight + 1
  if (!needsScroll.value) return
  thumbHeightPct.value = Math.max(8, (clientHeight / scrollHeight) * 100)
  const maxScroll = scrollHeight - clientHeight
  thumbTopPct.value = maxScroll > 0 ? (scrollTop / maxScroll) * (100 - thumbHeightPct.value) : 0
}

function scrollToRatio(ratio) {
  const el = contentEl.value
  if (!el) return
  el.scrollTop = ratio * (el.scrollHeight - el.clientHeight)
}

function onTrackDown(e) {
  const el = contentEl.value
  if (!el || e.target.classList.contains('psb-thumb')) return
  const rect = e.currentTarget.getBoundingClientRect()
  scrollToRatio((e.clientY - rect.top) / rect.height)
}

let dragging = false
let dragStartY = 0
let dragStartScrollTop = 0

function onThumbDown(e) {
  dragging = true
  dragStartY = e.clientY
  dragStartScrollTop = contentEl.value.scrollTop
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragUp)
}

function onDragMove(e) {
  if (!dragging) return
  const el = contentEl.value
  const trackHeight = el.clientHeight
  const scrollable = el.scrollHeight - el.clientHeight
  const deltaPx = e.clientY - dragStartY
  el.scrollTop = dragStartScrollTop + (deltaPx / trackHeight) * el.scrollHeight
  void scrollable
}

function onDragUp() {
  dragging = false
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragUp)
}

let ro = null
onMounted(async () => {
  await nextTick()
  update()
  // Watch the scroll container itself (viewport resizes) AND its slotted
  // children (e.g. an accordion section expanding) -- the container's own
  // box never changes size on its own, only what's inside it does.
  ro = new ResizeObserver(update)
  if (contentEl.value) {
    ro.observe(contentEl.value)
    for (const child of contentEl.value.children) ro.observe(child)
  }
})
onUnmounted(() => {
  ro?.disconnect()
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragUp)
})

defineExpose({ update })
</script>

<style scoped>
/* No explicit height here on purpose -- height:100% would plug straight into
   flex-basis on the parent flex item and fight its own auto-then-shrink
   sizing. The consumer's wrapping class must constrain height instead (fixed
   height, max-height+overflow, or flex:1 1 auto;min-height:0 in a flex
   column) -- .psb-content then fills that via matching flex sizing, which
   works at any depth without percentage-height resolution surprises. */
.psb-wrap { position: relative; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }

.psb-content {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 16px;
  scrollbar-width: none;
}
.psb-content::-webkit-scrollbar { display: none; }

.psb-track {
  position: absolute; top: 0; right: 0; bottom: 0; width: 14px;
  background: var(--px-cream3);
  border-left: 3px solid var(--px-ink);
}
.psb-thumb {
  position: absolute; left: 0; right: 0;
  min-height: 24px;
  background: var(--px-orange);
  border: 3px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt);
  cursor: pointer;
}
.psb-thumb:hover { background: var(--px-orange-lt); }
</style>
