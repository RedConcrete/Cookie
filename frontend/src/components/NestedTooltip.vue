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
// Anker statt fixer left/top-Koordinate -- je Achse ist immer nur EINE der beiden Seiten
// gesetzt (die andere null), damit CSS von der jeweils richtigen Seite aus wachsen kann.
// So steht die Position schon vorm ersten Frame fest (aus der Trigger-Position + den
// bekannten Popup-Groessengrenzen unten berechnet) -- kein "erst falsch rendern, dann
// nach dem Messen ruckartig verschieben" mehr, das sichtbar geflackert hat.
const anchorLeft   = ref(null)
const anchorRight  = ref(null)
const anchorTop    = ref(null)
const anchorBottom = ref(null)

let fillTimer  = null
let closeTimer = null

const popupStyle = computed(() => {
  const style = {}
  if (anchorLeft.value  != null) style.left  = anchorLeft.value  + 'px'; else style.right  = anchorRight.value  + 'px'
  if (anchorTop.value   != null) style.top   = anchorTop.value   + 'px'; else style.bottom = anchorBottom.value + 'px'
  return style
})

// Passt .tt-popups max-width (340px, siehe NestedTooltip.css) auf keiner Seite -> auf die
// Gegenseite klappen. Hoehe ist inhaltsabhaengig (keine feste CSS-Grenze), 200px ist ein
// grosszuegiger Schaetzwert ueber allem, was hier je vorkommt (auch mehrzeilige Tooltips +
// Countdown-Balken) -- lieber einmal unnoetig klappen als real ueberlaufen.
const POPUP_MAX_W = 340
const POPUP_EST_H = 200
const EDGE_MARGIN = 8

function positionPopup(rect) {
  if (rect.right + EDGE_MARGIN + POPUP_MAX_W <= window.innerWidth - EDGE_MARGIN) {
    anchorLeft.value = rect.right + EDGE_MARGIN
    anchorRight.value = null
  } else {
    anchorLeft.value = null
    anchorRight.value = window.innerWidth - rect.left + EDGE_MARGIN
  }
  if (rect.top + POPUP_EST_H <= window.innerHeight - EDGE_MARGIN) {
    anchorTop.value = rect.top
    anchorBottom.value = null
  } else {
    anchorTop.value = null
    anchorBottom.value = window.innerHeight - rect.bottom
  }
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
  positionPopup(rect)

  if (props.instant) {
    if (props.depth === 0) registerGlobal(closeNow)
    visible.value = true
    return
  }

  filling.value = true
  clearTimeout(fillTimer)
  fillTimer = setTimeout(() => {
    filling.value = false
    if (props.depth === 0) registerGlobal(closeNow)
    visible.value = true
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
