<template>
  <span
    class="tooltip-trigger"
    @mouseenter="onTriggerEnter($event)"
    @mouseleave="onTriggerLeave"
  >
    <slot />

    <Teleport to="body">
      <Transition name="tt-fade">
        <div
          v-if="visible"
          class="tt-popup"
          :class="`tt-depth-${depth}`"
          :style="popupStyle"
          @mouseenter="onPopupEnter"
          @mouseleave="onPopupLeave"
        >
          <template v-for="(seg, i) in parsedContent" :key="i">
            <span v-if="!seg.tooltip">{{ seg.text }}</span>
            <NestedTooltip
              v-else
              :content="seg.tooltip"
              :depth="depth + 1"
            >
              <span class="tt-highlight">{{ seg.text }}</span>
            </NestedTooltip>
          </template>
        </div>
      </Transition>
    </Teleport>
  </span>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import NestedTooltip from './NestedTooltip.vue'

const CLOSE_DELAY = 1000

const props = defineProps({
  content: { type: [String, Array], required: true },
  depth:   { type: Number, default: 0 },
})

const visible  = ref(false)
const posX     = ref(0)
const posY     = ref(0)

let closeTimer = null

const popupStyle = computed(() => ({
  left: posX.value + 'px',
  top:  posY.value + 'px',
}))

// Content kann String oder Array sein:
// Array: [{ text }, { text, tooltip: string|array }]
const parsedContent = computed(() => {
  if (typeof props.content === 'string') return [{ text: props.content }]
  return props.content
})

function onTriggerEnter(e) {
  clearTimeout(closeTimer)
  const rect = e.currentTarget.getBoundingClientRect()
  posX.value = rect.right + window.scrollX + 8
  posY.value = rect.top  + window.scrollY
  visible.value = true
}

function onTriggerLeave() {
  scheduleClose()
}

function onPopupEnter() {
  clearTimeout(closeTimer)
}

function onPopupLeave() {
  scheduleClose()
}

function scheduleClose() {
  clearTimeout(closeTimer)
  closeTimer = setTimeout(() => { visible.value = false }, CLOSE_DELAY)
}

onUnmounted(() => clearTimeout(closeTimer))
</script>
