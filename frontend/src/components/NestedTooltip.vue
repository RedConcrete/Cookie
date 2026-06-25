<template>
  <span
    class="tooltip-trigger"
    @mouseenter="onTriggerEnter($event)"
    @mouseleave="onTriggerLeave"
  >
    <slot />

    <!-- Phase 1: Füll-Balken unter dem Trigger -->
    <span v-if="filling" class="tt-fill-indicator" :style="{ animationDuration: APPEAR_DELAY + 'ms' }"></span>

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
            <NestedTooltip v-else :content="seg.tooltip" :depth="depth + 1">
              <span class="tt-highlight">{{ seg.text }}</span>
            </NestedTooltip>
          </template>

          <!-- Phase 3: Drain-Balken im Tooltip -->
          <div v-if="draining" class="tt-countdown">
            <div class="tt-countdown-bar" :key="drainKey" :style="{ animationDuration: CLOSE_DELAY + 'ms' }"></div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </span>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import NestedTooltip from './NestedTooltip.vue'
import { registerGlobal, unregisterGlobal } from '../composables/tooltipMutex.js'
import { useAudio } from '../composables/useAudio.js'

const { playHover } = useAudio()

const APPEAR_DELAY = 1000
const CLOSE_DELAY  = 1000

const props = defineProps({
  content: { type: [String, Array], required: true },
  depth:   { type: Number, default: 0 },
  silent:  { type: Boolean, default: false },
})

const visible  = ref(false)
const filling  = ref(false)
const draining = ref(false)
const drainKey = ref(0)
const posX     = ref(0)
const posY     = ref(0)

let fillTimer  = null
let closeTimer = null

const popupStyle = computed(() => ({ left: posX.value + 'px', top: posY.value + 'px' }))

const parsedContent = computed(() =>
  typeof props.content === 'string' ? [{ text: props.content }] : props.content
)

function closeNow() {
  clearTimeout(fillTimer)
  clearTimeout(closeTimer)
  visible.value  = false
  filling.value  = false
  draining.value = false
}

function onTriggerEnter(e) {
  clearTimeout(closeTimer)
  draining.value = false
  if (!props.silent) playHover()

  if (visible.value) return

  const rect = e.currentTarget.getBoundingClientRect()
  posX.value = rect.right + 8
  posY.value = rect.top

  filling.value = true
  clearTimeout(fillTimer)
  fillTimer = setTimeout(() => {
    filling.value = false
    if (props.depth === 0) registerGlobal(closeNow)
    visible.value = true
  }, APPEAR_DELAY)
}

function onTriggerLeave() {
  if (!visible.value) {
    clearTimeout(fillTimer)
    filling.value = false
  } else {
    startDrain()
  }
}

function onPopupEnter() {
  clearTimeout(closeTimer)
  draining.value = false
}

function onPopupLeave() {
  startDrain()
}

function startDrain() {
  draining.value = true
  drainKey.value++
  clearTimeout(closeTimer)
  closeTimer = setTimeout(() => {
    closeNow()
    if (props.depth === 0) unregisterGlobal(closeNow)
  }, CLOSE_DELAY)
}

onUnmounted(() => {
  clearTimeout(fillTimer)
  clearTimeout(closeTimer)
  if (props.depth === 0) unregisterGlobal(closeNow)
})
</script>
