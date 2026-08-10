<template>
  <div
    class="bf-root"
    :style="rootStyle"
    @pointerdown="onPointerDown"
  >
    <!-- Hold-to-drag progress bar -->
    <div class="bf-hold-bar" :style="{ opacity: state.pressing ? 1 : 0, width: `calc(${Math.round(state.progress * 100)}% + 16px)` }"></div>

    <!-- Armed / blocked outline -->
    <div
    class="bf-outline"
    :style="{ opacity: state.armed || state.pressing ? 1 : 0, borderColor: blocked ? '#e67146' : (state.armed ? '#aea47e' : '#ebb85b'), background: blocked ? 'rgba(224,90,74,.22)' : 'transparent' }">
  </div>

    <!-- Hover ring: wraps the whole card (label + scene), same footprint as
         the drag outline above -- scoping it to just .bf-scene made its -8px
         inset bleed upward into the label bar instead of framing it cleanly.
         Red instead of green while blocked (e.g. Lager voll -- see FarmGridView's
         isStorageFull), so hovering a production building signals "won't help"
         instead of implying it's still harvesting. -->
    <div class="bf-hover-ring" :class="{ visible: hovering && !state.armed && !state.pressing, blocked: harvestBlocked }"></div>

    <!-- Brief explanation while blocked -- only shown on actual hover, not a
         persistent badge, per design: "kurz" (Lager-voll popover). -->
    <div v-if="harvestBlocked && hovering" class="bf-blocked-notice">{{ t('buildingFrame.storageFullNotice') }}</div>

    <!-- Collect badge -- shown whenever the building has accrued anything, so it can be
         collected straight from the map without opening the detail dialog (like collecting
         rent). Stops pointerdown/click from reaching useHoldDrag's drag/open handling. -->
    <button
      v-if="pendingAmount > 0"
      class="bf-collect-badge"
      :class="{ full: pendingAmount >= storageCapacity, shake: collectShake }"
      @pointerdown.stop
      @click.stop="emit('collect')"
    >
      <PixelIcon v-if="resourceIcon" :name="resourceIcon" :size="14" />
      <span class="bf-collect-amount">{{ fmt(pendingAmount) }}</span>
    </button>

    <!-- Name bar — normal flow, sits above the scene so it never covers the artwork -->
    <div class="bf-overlay">
      <div class="bf-overlay-left">
        <PixelIcon :name="icon" :size="12" />
        <span class="bf-name">{{ title }}</span>
      </div>
      <div class="bf-overlay-right">
        <!--<span class="bf-rate">{{ rate }}</span>-->
        <span v-if="passiveIdle && Number(workers) > 0" class="bf-idle-tag">{{ t('buildingFrame.idleBadge') }}</span>
        <span v-if="workers !== null" class="bf-workers">
          <PixelIcon name="einw" :size="12" />{{ workers }}
        </span>
      </div>
    </div>

    <!-- Hover over the scene = harvest (no tooltip; @open still handles click) -->
    <div
      class="bf-scene bf-scene-custom" :style="{ height: sceneHeight + 'px' }"
      @mouseenter="onSceneEnter" @mouseleave="onSceneLeave"
    >
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PixelIcon from '../pixel/PixelIcon.vue'
import { useHoldDrag } from '../../composables/useHoldDrag.js'
import { TILE_SIZE } from './farmLayout.js'
import { fmt } from '../../utils/formatNumber.js'

const { t } = useI18n()

const props = defineProps({
  buildingId: { type: String, default: '' },
  base:       { type: Object, required: true },  // { x, y, w }
  title:      { type: String, required: true },
  icon:       { type: String, required: true },
  rate:       { type: String, default: '' },
  workers:    { type: [Number, String], default: null },
  rows:       { type: Array, default: () => [] },
  note:       { type: String, default: '' },
  side:       { type: String, default: 'right' },
  sceneHeight:{ type: Number, default: 120 },
  dropOk:     { type: Function, required: true },
  zoom:       { type: Number, default: 1 },
  offset:     { type: Object, default: () => ({ x: 0, y: 0 }) },  // last saved drag offset
  // Storage-full (or similar) -- this production building's hover-harvest currently
  // grants nothing. Purely visual here (red ring + notice); FarmGridView owns the
  // actual gating of whether harvest-start does anything. Named distinctly from the
  // pre-existing local `blocked` computed below (invalid-drag-drop-position), which
  // a same-named prop would silently shadow in the template. Tied to the shared main
  // storage (hover-harvest writes straight into it) -- NOT the same thing as
  // passiveIdle below, this building's own local storage can still have room even
  // while the main storage (and therefore hover-harvest) is full.
  harvestBlocked: { type: Boolean, default: false },
  // This building's own passive/worker production isn't accumulating anything right
  // now (its own local storage is full, or wages can't be paid) -- drives the
  // INAKTIV badge. Independent of harvestBlocked/the main storage.
  passiveIdle: { type: Boolean, default: false },
  // Collect-badge (accrued passive resource, collectible without opening the dialog).
  pendingAmount:   { type: Number, default: 0 },
  storageCapacity: { type: Number, default: 0 },
  resourceIcon:    { type: String, default: '' },
  // Kurzes "Nein, geht nicht"-Wackeln statt Einsammeln -- Hauptlager ist voll, siehe
  // FarmGridView's shakeCollectButton (Server wuerde ohnehin 0 gutschreiben).
  collectShake:    { type: Boolean, default: false },
})
const emit = defineEmits(['open', 'harvest-start', 'harvest-stop', 'moved', 'collect'])

const { state, onPointerDown } = useHoldDrag(
  (pos) => props.dropOk(pos),
  () => emit('open'),
  (finalPos) => emit('moved', finalPos),
  () => props.zoom,
  props.offset,
  { origin: props.base, size: TILE_SIZE },
)

const blocked = computed(() => state.armed && !props.dropOk(state.pos))

// Light outline while hovering the scene, so it's clear the harvest-on-hover
// is active (suppressed while the drag outline is showing instead).
const hovering = ref(false)
function onSceneEnter() { hovering.value = true; emit('harvest-start') }
function onSceneLeave() { hovering.value = false; emit('harvest-stop') }

const rootStyle = computed(() => ({
  position: 'absolute',
  left: (props.base.x + state.pos.x) + 'px',
  top:  (props.base.y + state.pos.y) + 'px',
  width: props.base.w + 'px',
  touchAction: 'none',
  cursor: state.armed ? 'grabbing' : 'pointer',
  zIndex: state.armed ? 62 : (state.pressing ? 61 : 20),
  filter: state.armed ? 'brightness(1.1)' : 'none',
}))
</script>

<style scoped>
.bf-root { user-select: none; display: flex; flex-direction: column; }

.bf-scene {
  position: relative;
}
/* Custom: overflow visible so nested scene tooltips (e.g. SugarPond) can extend outside */
.bf-scene-custom { overflow: visible; }

.bf-overlay {
  display: flex; align-items: center; justify-content: space-between; gap: 6px;
  padding: 4px 6px;
  background: rgba(16,11,7,.62);
  z-index: 5;
  pointer-events: none;
}
.bf-overlay-left  { display: flex; align-items: center; gap: 5px; min-width: 0; }
.bf-overlay-right { display: flex; align-items: center; gap: 7px; flex-shrink: 0; }
.bf-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: #fff1a9; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bf-rate { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-green-txt); white-space: nowrap; }
.bf-workers { display: flex; align-items: center; gap: 3px; font-family: 'Silkscreen', monospace; font-size: 9px; color: #fff1a9; white-space: nowrap; }
.bf-idle-tag {
  font-family: 'Silkscreen', monospace; font-size: 8px; padding: 2px 4px;
  background: #402e2b; color: #e67a84; border: 2px solid #764032; white-space: nowrap;
}

.bf-hold-bar {
  position: absolute; left: -8px; top: -24px; height: 10px;
  background: var(--px-gold); box-shadow: 0 0 0 3px var(--px-ink);
  pointer-events: none; transition: opacity .1s;
}
.bf-outline {
  position: absolute; inset: -8px;
  border: 4px solid; box-shadow: 0 0 0 3px var(--px-ink);
  pointer-events: none; transition: opacity .1s;
}
.bf-hover-ring {
  position: absolute; inset: -8px;
  border: 4px solid var(--px-green-lt);
  box-shadow: 0 0 0 3px var(--px-ink);
  opacity: 0;
  pointer-events: none;
  transition: opacity .12s;
  z-index: 4;
}
.bf-hover-ring.visible { opacity: 1; }
.bf-hover-ring.blocked { border-color: var(--px-red); }

.bf-collect-badge {
  position: absolute;
  /* Hoeher als .bf-blocked-notice gestapelt (statt exakt gleicher Position) -- beide
     koennen gleichzeitig sichtbar sein (Lager voll + noch was zum Einsammeln), siehe
     docs/ROADMAP.md. Einsammeln bleibt oben, "Lager voll" naeher am Gebaeude. */
  bottom: calc(100% + 40px);
  left: 50%;
  transform: translateX(-50%);
  display: flex; align-items: center; gap: 5px;
  padding: 4px 6px;
  background: var(--px-wood);
  border: 3px solid var(--px-gold);
  box-shadow: inset 1px 1px 0 rgba(255,255,255,.15), 0 3px 0 rgba(0,0,0,.4);
  cursor: pointer;
  z-index: 30;
  animation: bf-collect-bob 1.1s ease-in-out infinite;
}
.bf-collect-badge:hover { filter: brightness(1.15); }
.bf-collect-badge.full { border-color: var(--px-red); }
.bf-collect-amount { font-family: 'Silkscreen', monospace; font-size: 10px; color: #fff1a9; white-space: nowrap; }
.bf-collect-badge.full .bf-collect-amount { color: #e67a84; }
@keyframes bf-collect-bob {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50%      { transform: translateX(-50%) translateY(-3px); }
}
/* Ueberschreibt bf-collect-bob voruebergehend (gleiche Selektor-Basis, aber .shake
   ist spezifischer) -- kurzes Kopfschuetteln statt der bob-Animation, waehrend die
   Klasse anliegt (siehe FarmGridView's shakeCollectButton). */
.bf-collect-badge.shake { animation: bf-collect-shake .4s ease; }
@keyframes bf-collect-shake {
  0%, 100% { transform: translateX(-50%); }
  20%      { transform: translateX(calc(-50% - 7px)); }
  40%      { transform: translateX(calc(-50% + 6px)); }
  60%      { transform: translateX(calc(-50% - 5px)); }
  80%      { transform: translateX(calc(-50% + 3px)); }
}

.bf-blocked-notice {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  width: max-content;
  max-width: 190px;
  background: var(--px-wood);
  border: 3px solid var(--px-red);
  box-shadow: inset 2px 2px 0 #402e2b, 0 4px 0 rgba(0,0,0,.4);
  padding: 6px 9px;
  font-family: 'Silkscreen', monospace;
  font-size: 9px;
  line-height: 1.4;
  color: #fff1a9;
  text-align: center;
  pointer-events: none;
  z-index: 6;
}
</style>
