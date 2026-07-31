<template>
  <!-- The scene itself — harvest fires via BuildingFrame's PixelInfoPopover @enter/@leave -->
  <div class="pond-scene">
    <img class="scene-bg" :src="bgSrc" alt="" />
    <!-- Worker -->
    <div v-if="workers > 0" class="cutter">
      <PixelWorker anim="bend" :dur="0.75" hat="#9fb3c2" torso="#8a5a34"
        :tool="{ anim: 'swing', dur: 0.75, color: '#8d6a3d' }" />
    </div>
    <!-- Hover-zone for nested tooltip — covers whole scene -->
    <div class="pond-hover-zone" @mouseenter="onHoverEnter" @mouseleave="onHoverLeave"></div>
    <div class="pond-bar"></div>
  </div>

  <!-- Depth-0 tooltip: shown on separate hover via SugarPond's own HoverReveal -->
  <div v-if="fieldState.visible" class="pond-tip pond-tip-0"
       @mouseenter="onEnterField" @mouseleave="onLeaveField">
    <div class="pond-tip-title">ZUCKERROHRFELD</div>
    <div class="pond-tip-body">
      Erntet 1.4 Zucker/s. 2 Einwohner arbeiten hier und kosten
      <span class="pond-trigger" @mouseenter="onEnterWage" @mouseleave="onLeaveWage">4 Cookies/min</span> Lohn.
    </div>
    <div class="pond-bar-inline" :style="{ opacity: fieldRunning ? 1 : 0 }">
      <div class="pond-bar-fill" :style="{ width: (fieldState.progress * 100) + '%', background: fieldState.phase === 'drain' ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
    </div>

    <!-- Depth-1 wage tooltip -->
    <div v-if="wageState.visible" class="pond-tip pond-tip-1"
         @mouseenter="onEnterWage" @mouseleave="onLeaveWage">
      <div class="pond-tip-title">LOHN &middot; TIEFE 1</div>
      <div class="pond-tip-body">
        2 Arbeiter &times; 2 C/min. Abzug vom Cookie-Konto jede Minute &mdash; ein Sink neben
        <span class="pond-trigger" @mouseenter="onEnterFee" @mouseleave="onLeaveFee">Marktgebühr</span>.
      </div>
      <div class="pond-bar-inline" :style="{ opacity: wageRunning ? 1 : 0 }">
        <div class="pond-bar-fill" :style="{ width: (wageState.progress * 100) + '%', background: wageState.phase === 'drain' ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
      </div>

      <!-- Depth-2 fee tooltip -->
      <div v-if="feeState.visible" class="pond-tip pond-tip-2">
        <div class="pond-tip-title">GEBÜHR &middot; TIEFE 2</div>
        <div class="pond-tip-body">8 % jedes Verkaufserlöses werden vernichtet statt ausgezahlt.</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import PixelWorker from '../pixel/PixelWorker.vue'
import { useHoverReveal } from '../../composables/useHoverReveal.js'
import bgSrc from '../../assets/buildings/pond.svg'

const props = defineProps({ workers: { type: Number, default: 0 } })
const emit = defineEmits(['harvest-start', 'harvest-stop'])

const { state: fieldState, onEnter: onEnterFieldRaw, onLeave: onLeaveFieldRaw, running: fieldRunning } = useHoverReveal()
const { state: wageState,  onEnter: onEnterWage,     onLeave: onLeaveWageRaw,  running: wageRunning  } = useHoverReveal()
const { state: feeState,   onEnter: onEnterFee,       onLeave: onLeaveFee                             } = useHoverReveal()

// Separate from harvest (handled by BuildingFrame's PixelInfoPopover) — only controls tooltip visibility
function onHoverEnter() { onEnterFieldRaw() }
function onHoverLeave() {
  onLeaveFieldRaw()
  const check = () => { if (fieldState.phase === 'off') { wageState.visible = false; feeState.visible = false } else requestAnimationFrame(check) }
  check()
}
function onEnterField() { onEnterFieldRaw() }
function onLeaveField() {
  onLeaveFieldRaw()
}
function onLeaveWage() {
  onLeaveWageRaw()
  const check = () => { if (wageState.phase === 'off') feeState.visible = false; else requestAnimationFrame(check) }
  check()
}

</script>

<style scoped>
.pond-scene {
  position: absolute; inset: 0;
  cursor: pointer;
}
.scene-bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; image-rendering: pixelated; }

.cutter { position: absolute; left: 110px; bottom: 1px; z-index: 3; }
.pond-hover-zone { position: absolute; inset: 0; z-index: 2; }
.pond-bar { position: absolute; left: 0; right: 0; bottom: 0; height: 7px; background: rgba(16,11,7,.75); display: none; }

/* Nested tooltip panels */
.pond-bar-inline { position: relative; height: 6px; margin-top: 8px; background: var(--px-ink); box-shadow: 0 0 0 2px var(--px-ink); }
.pond-bar-fill   { height: 100%; }

.pond-tip {
  position: absolute; width: 290px;
  background: var(--px-wood);
  border: 4px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #55402a, 0 6px 0 rgba(0,0,0,.4);
  padding: 12px 14px; z-index: 31;
  font-family: 'Pixelify Sans', system-ui, sans-serif;
}
.pond-tip-0 { left: calc(100% + 8px); top: 0; }
.pond-tip-1 { position: relative; left: auto; top: auto; width: 100%; margin-top: 10px; background: #42311f; box-shadow: inset 2px 2px 0 #66492c, 0 6px 0 rgba(0,0,0,.4); z-index: 32; }
.pond-tip-2 { position: relative; left: auto; top: auto; margin-top: 10px; background: #523d27; box-shadow: inset 2px 2px 0 #7a5c38, 0 6px 0 rgba(0,0,0,.4); z-index: 33; }

.pond-tip-title { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); margin-bottom: 8px; }
.pond-tip-body  { font-size: 15px; line-height: 1.6; color: #f3e6cc; }
.pond-trigger   { color: var(--px-gold-lt); border-bottom: 2px dashed var(--px-orange); cursor: pointer; }
</style>
