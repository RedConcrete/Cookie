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

    <!-- Name bar — normal flow, sits above the scene so it never covers the artwork -->
    <div class="bf-overlay">
      <div class="bf-overlay-left">
        <PixelIcon :name="icon" :size="12" />
        <span class="bf-name">{{ title }}</span>
      </div>
      <div class="bf-overlay-right">
        <!--<span class="bf-rate">{{ rate }}</span>-->
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
      <div class="bf-hover-ring" :class="{ visible: hovering && !state.armed && !state.pressing }"></div>
      <slot />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import PixelIcon from '../pixel/PixelIcon.vue'
import { useHoldDrag } from '../../composables/useHoldDrag.js'
import { TILE_SIZE } from './farmLayout.js'

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
})
const emit = defineEmits(['open', 'harvest-start', 'harvest-stop', 'moved'])

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
</style>
