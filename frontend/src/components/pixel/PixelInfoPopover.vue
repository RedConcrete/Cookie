<template>
  <div ref="wrapRef" class="pip-wrap" @mouseenter="handleEnter" @mouseleave="handleLeave">
    <slot />

    <Teleport to="body">
      <div v-if="state.visible" ref="panelRef" class="pip-panel" :style="{ ...posStyle, width: width + 'px', zIndex: z }">
        <div v-if="barPlacement !== 'none'" class="pip-bar">
          <div class="pip-bar-fill" :style="{ width: (state.progress * 100) + '%', background: state.phase.startsWith('drain') ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
        </div>

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
        <div v-if="pinned" class="pip-pin">{{ t('pixelInfoPopover.pinnedHint') }}</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import PixelIcon from './PixelIcon.vue'
import { useHoverReveal } from '../../composables/useHoverReveal.js'

const { t } = useI18n()

const props = defineProps({
  rows:  { type: Array, default: () => [] },   // [{ k, v, color }]  color: g|y|w|o|m|b
  title: { type: String, default: '' },
  icon:  { type: String, default: '' },
  note:  { type: String, default: '' },
  side:  { type: String, default: 'right' },   // right | left | below-left | below-right
  width: { type: Number, default: 268 },
  // Panel wird per Teleport nach <body> gehaengt -- stapelt also gegen Dialoge
  // (.px-dialog-overlay hat z-index:500), nicht mehr nur gegen Geschwister im
  // urspruenglichen Elternelement. Default entsprechend darueber.
  z:     { type: Number, default: 600 },
  barPlacement: { type: String, default: 'chip' }, // any value shows the bar, 'none' hides it
})

const emit = defineEmits(['enter', 'leave'])
const { state, onEnter, onLeave, pinned } = useHoverReveal()

function handleEnter() { onEnter(); emit('enter') }
function handleLeave() { onLeave(); emit('leave') }

const COLORS = { g: '#aea47e', y: '#ebb85b', w: '#fff1a9', o: '#c78539', m: '#6f6e72', b: '#6dba79' }
function colorFor(c) { return COLORS[c] || COLORS.w }

const panelRef = ref(null)
const wrapRef  = ref(null)
const posStyle = ref({})

// Panel wird per Teleport nach <body> gehaengt (sonst schneidet ein Dialog mit
// overflow:auto/hidden das Panel ab) -- Position daher ueber getBoundingClientRect
// des Trigger-Elements berechnet statt ueber CSS, die relativ zum Wrap-Div waere.
function positionFor(side, rect) {
  const GAP = 12, GAP_BELOW = 14
  if (side === 'left')        return { left: rect.left - GAP - props.width + 'px', top: rect.top + 'px' }
  if (side === 'below-left')  return { left: rect.left + 'px', top: rect.bottom + GAP_BELOW + 'px' }
  if (side === 'below-right') return { left: rect.right - props.width + 'px', top: rect.bottom + GAP_BELOW + 'px' }
  return { left: rect.right + GAP + 'px', top: rect.top + 'px' } // right (default)
}

watch(() => state.visible, async (vis) => {
  if (!vis) return
  const wrap = wrapRef.value
  if (!wrap) return
  const wrapRect = wrap.getBoundingClientRect()
  let s = props.side
  posStyle.value = { position: 'fixed', ...positionFor(s, wrapRect) }

  await nextTick()
  const el = panelRef.value
  if (!el) return
  const r  = el.getBoundingClientRect()
  const vw = window.innerWidth
  const vh = window.innerHeight
  if ((s === 'right' || s === 'below-right') && r.right  > vw - 8) s = s === 'right' ? 'left'  : 'below-left'
  if ((s === 'left'  || s === 'below-left')  && r.left   < 8)       s = s === 'left'  ? 'right' : 'below-right'
  if (s.startsWith('below') && r.bottom > vh - 8) s = s === 'below-left' ? 'left' : 'right'
  posStyle.value = { position: 'fixed', ...positionFor(s, wrapRect) }
})
</script>

<style scoped>
.pip-wrap { position: relative; width: 100%; }

.pip-panel {
  background: var(--px-wood);
  border: 4px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #402e2b, 0 6px 0 rgba(0,0,0,.45);
  padding: 12px 14px;
  font-family: 'Silkscreen', monospace;
}

/* Progress bar bleeds to the panel's outer edges, flush with its top border */
.pip-bar { margin: -12px -14px 10px -14px; height: 7px; background: rgba(16,11,7,.75); box-shadow: 0 2px 0 var(--px-ink); overflow: hidden; }
.pip-bar-fill { height: 100%; transition: none; }

.pip-header { display: flex; align-items: center; gap: 7px; margin-bottom: 9px; }
.pip-title  { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); }

.pip-rows { display: flex; flex-direction: column; gap: 5px; }
.pip-row  { display: flex; justify-content: space-between; gap: 10px; font-size: 14px; color: #fff1a9; white-space: nowrap; }
.pip-row span:last-child { font-family: 'Silkscreen', monospace; font-size: 13px; letter-spacing: 0.5px; }

.pip-note { margin-top: 9px; padding-top: 8px; border-top: 3px solid #402e2b; font-size: 13px; line-height: 1.5; color: #aea47e; }
.pip-pin  { margin-top: 8px; font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-green-txt); }
</style>
