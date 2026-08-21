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
          ref="popupEl"
          class="tt-popup"
          :class="`tt-depth-${depth}`"
          :style="popupStyle"
          @mouseenter="onPopupEnter"
          @mouseleave="onPopupLeave"
        >
          <template v-for="(seg, i) in parsedContent" :key="i">
            <span v-if="!seg.tooltip">{{ seg.text }}</span>
            <NestedTooltip v-else :content="seg.tooltip" :depth="depth + 1" instant>
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
import { ref, computed, nextTick, onUnmounted } from 'vue'
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
  // Kein Appear-/Close-Delay, kein Timeline-Balken -- sofort ein-/ausblenden.
  // Nur fuer einfache, nicht-verschachtelte Text-Tooltips gedacht (kein tt-highlight-
  // Inhalt): der Drain-Delay bei normalen Tooltips existiert, damit die Maus Zeit hat,
  // vom Trigger in den Popup zu wandern (z.B. um einen verlinkten Begriff zu klicken) --
  // bei instant faellt dieser Puffer weg, ein instant-Tooltip darf also nichts enthalten,
  // das man erst noch anklicken/erreichen muesste.
  instant: { type: Boolean, default: false },
})

const visible  = ref(false)
const filling  = ref(false)
const draining = ref(false)
const drainKey = ref(0)
const posX     = ref(0)
const posY     = ref(0)
const popupEl  = ref(null)

let fillTimer  = null
let closeTimer = null

const popupStyle = computed(() => ({ left: posX.value + 'px', top: posY.value + 'px' }))

// posX/posY sind zunaechst nur eine Schaetzung (rechts neben dem Trigger) -- bei Triggern
// nah am rechten/unteren Bildschirmrand (z.B. build-fab) wuerde das echte Popup je nach
// Textlaenge ueber den Viewport hinausragen. Nach dem Rendern (nextTick, Popup existiert
// erst dann im DOM) die tatsaechliche Groesse messen und zurueck auf den sichtbaren
// Bereich klemmen -- darf nie off-screen landen, unabhaengig von Trigger-Position/Textlaenge.
async function clampToViewport() {
  await nextTick()
  const el = popupEl.value
  if (!el) return
  const margin = 8
  const box = el.getBoundingClientRect()
  let x = posX.value
  let y = posY.value
  if (x + box.width > window.innerWidth - margin) x = window.innerWidth - box.width - margin
  if (x < margin) x = margin
  if (y + box.height > window.innerHeight - margin) y = window.innerHeight - box.height - margin
  if (y < margin) y = margin
  posX.value = x
  posY.value = y
}

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

  // .tooltip-trigger ist ein normales inline <span> ohne eigene Groesse -- wenn der
  // eingebettete Slot-Inhalt selbst position:absolute/fixed ist (z.B. build-fab,
  // cam-center, alle px-close-Buttons), traegt er NICHTS zur Box des Spans bei, der
  // kollabiert dann auf 0x0 an Position (0,0) und das Popup landet oben links statt am
  // echten Button. Rect deshalb vom ersten echten Kind-Element nehmen, nicht vom Span
  // selbst -- das ist immer der eigentliche sichtbare Trigger-Inhalt.
  const rect = (e.currentTarget.firstElementChild ?? e.currentTarget).getBoundingClientRect()
  posX.value = rect.right + 8
  posY.value = rect.top

  if (props.instant) {
    if (props.depth === 0) registerGlobal(closeNow)
    visible.value = true
    clampToViewport()
    return
  }

  filling.value = true
  clearTimeout(fillTimer)
  fillTimer = setTimeout(() => {
    filling.value = false
    if (props.depth === 0) registerGlobal(closeNow)
    visible.value = true
    clampToViewport()
  }, APPEAR_DELAY)
}

function onTriggerLeave() {
  if (props.instant) {
    closeNow()
    if (props.depth === 0) unregisterGlobal(closeNow)
    return
  }
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
