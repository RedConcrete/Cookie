<template>
  <div
    class="bf-root"
    :style="rootStyle"
    @pointerdown="onPointerDown"
  >
    <!-- Hold-to-drag progress bar, shown above the building while pressing -->
    <div class="bf-hold-bar" :style="{ opacity: state.pressing ? 1 : 0, width: `calc(${Math.round(state.progress * 100)}% + 16px)` }"></div>

    <!-- Armed / blocked outline -->
    <div class="bf-outline" :style="{ opacity: state.armed || state.pressing ? 1 : 0, borderColor: blocked ? '#e05a4a' : (state.armed ? '#a9ff88' : '#e8b93c'), background: blocked ? 'rgba(224,90,74,.22)' : 'transparent' }"></div>

    <PixelInfoPopover
      v-if="!customTooltip"
      :rows="rows" :title="title" :icon="icon" :note="note"
      :side="side" :width="popoverWidth" :z="70" bar-placement="edge"
      @enter="emit('harvest-start')" @leave="emit('harvest-stop')"
    >
      <div class="bf-scene" :style="{ height: sceneHeight + 'px' }">
        <slot />
        <div class="bf-overlay">
          <div class="bf-overlay-left">
            <PixelIcon :name="icon" :size="12" />
            <span class="bf-name">{{ title }}</span>
          </div>
          <div class="bf-overlay-right">
            <span class="bf-rate">{{ rate }}</span>
            <span v-if="workers !== null" class="bf-workers">
              <PixelIcon name="einw" :size="12" />{{ workers }}
            </span>
          </div>
        </div>
      </div>
    </PixelInfoPopover>

    <!-- Buildings with their own custom hover-tooltip (e.g. the nested-tooltip demo): overflow
         stays visible so the tooltip tree (rendered by the slot itself) isn't cropped. -->
    <div v-else class="bf-scene bf-scene-custom" :style="{ height: sceneHeight + 'px' }">
      <slot />
      <div class="bf-overlay">
        <div class="bf-overlay-left">
          <PixelIcon :name="icon" :size="12" />
          <span class="bf-name">{{ title }}</span>
        </div>
        <div class="bf-overlay-right">
          <span class="bf-rate">{{ rate }}</span>
          <span v-if="workers !== null" class="bf-workers">
            <PixelIcon name="einw" :size="12" />{{ workers }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PixelIcon from '../pixel/PixelIcon.vue'
import PixelInfoPopover from '../pixel/PixelInfoPopover.vue'
import { useHoldDrag } from '../../composables/useHoldDrag.js'

const props = defineProps({
  base:   { type: Object, required: true },  // { x, y, w, h }
  title:  { type: String, required: true },
  icon:   { type: String, required: true },
  rate:   { type: String, default: '' },
  workers:{ type: [Number, String], default: null },
  rows:   { type: Array, default: () => [] },
  note:   { type: String, default: '' },
  side:   { type: String, default: 'right' },
  sceneHeight: { type: Number, default: 120 },
  dropOk: { type: Function, required: true }, // (pos) => boolean, pos = drag offset {x,y}
  customTooltip: { type: Boolean, default: false },
})
const emit = defineEmits(['open', 'harvest-start', 'harvest-stop'])

const popoverWidth = computed(() => Math.max(250, props.base.w + 70))

const { state, onPointerDown } = useHoldDrag(
  (pos) => props.dropOk(pos),
  () => emit('open'),
)

const blocked = computed(() => state.armed && !props.dropOk(state.pos))

const rootStyle = computed(() => ({
  position: 'absolute',
  left: (props.base.x + state.pos.x) + 'px',
  top:  (props.base.y + state.pos.y) + 'px',
  width: props.base.w + 'px',
  touchAction: 'none',
  cursor: state.armed ? 'grabbing' : 'pointer',
  zIndex: state.armed ? 62 : (state.pressing ? 61 : (props.customTooltip ? 30 : 20)),
  filter: state.armed ? 'brightness(1.1)' : 'none',
}))
</script>

<style scoped>
.bf-root { user-select: none; }

.bf-scene {
  position: relative;
  overflow: hidden;
  border: 4px solid var(--px-ink);
  box-shadow: 0 6px 0 rgba(0,0,0,.4);
}
.bf-scene-custom { border-color: var(--px-orange); cursor: pointer; overflow: visible; }

.bf-overlay {
  position: absolute; left: 0; right: 0; top: 0;
  display: flex; align-items: center; justify-content: space-between; gap: 6px;
  padding: 4px 6px;
  background: rgba(16,11,7,.62);
  z-index: 5;
}
.bf-overlay-left  { display: flex; align-items: center; gap: 5px; min-width: 0; }
.bf-overlay-right { display: flex; align-items: center; gap: 7px; flex-shrink: 0; }
.bf-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: #fff6e0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bf-rate { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-green-txt); white-space: nowrap; }
.bf-workers { display: flex; align-items: center; gap: 3px; font-family: 'Silkscreen', monospace; font-size: 9px; color: #f3e6cc; white-space: nowrap; }

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
</style>
