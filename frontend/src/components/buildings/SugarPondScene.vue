<template>
  <div class="pond-scene" @mouseenter="onEnterField" @mouseleave="onLeaveField">
    <div class="water"></div>
    <div class="water-top"></div>
    <div class="reeds reeds-l"></div>
    <div class="reeds reeds-r"></div>
    <div class="reeds reeds-top"></div>
    <div class="reeds reeds-bottom"></div>
    <div class="cutter">
      <PixelWorker anim="bend" :dur="0.75" hat="#9fb3c2" torso="#8a5a34"
        :tool="{ anim: 'swing', dur: 0.75, color: '#8d6a3d' }" />
    </div>

    <!-- bottom hover-fill bar for the field-level tooltip -->
    <div class="pond-bar" :style="{ opacity: fieldRunning ? 1 : 0 }">
      <div class="pond-bar-fill" :style="{ width: (fieldState.progress * 100) + '%', background: fieldState.phase === 'drain' ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
    </div>
  </div>

  <!-- Depth 0: field info, with a nested wage trigger -->
  <div v-if="fieldState.visible" class="pond-tip pond-tip-0" @mouseenter="onEnterField" @mouseleave="onLeaveField">
    <div class="pond-tip-title">ZUCKERFELD</div>
    <div class="pond-tip-body">
      Erntet 1.4 Zucker/s solange der Cursor hier ruht. 2 Einwohner arbeiten hier und kosten
      <span class="pond-trigger" @mouseenter="onEnterWage" @mouseleave="onLeaveWage">4 Cookies/min</span>
      Lohn.
    </div>
    <div class="pond-bar-inline" :style="{ opacity: fieldRunning ? 1 : 0 }">
      <div class="pond-bar-fill" :style="{ width: (fieldState.progress * 100) + '%', background: fieldState.phase === 'drain' ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
    </div>

    <!-- Depth 1: wage info, with a nested fee trigger -->
    <div v-if="wageState.visible" class="pond-tip pond-tip-1" @mouseenter="onEnterWage" @mouseleave="onLeaveWage">
      <div class="pond-tip-title">LOHN &middot; TIEFE 1</div>
      <div class="pond-tip-body">
        2 Arbeiter &times; 2 C/min. Wird laufend vom Cookie-Konto abgezogen &mdash; ein weiterer Sink neben
        <span class="pond-trigger" @mouseenter="onEnterFee" @mouseleave="onLeaveFee">Marktgebühr</span>.
      </div>
      <div class="pond-bar-inline" :style="{ opacity: wageRunning ? 1 : 0 }">
        <div class="pond-bar-fill" :style="{ width: (wageState.progress * 100) + '%', background: wageState.phase === 'drain' ? 'var(--px-drain)' : 'var(--px-fill)' }"></div>
      </div>

      <!-- Depth 2: fee info -->
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

const emit = defineEmits(['harvest-start', 'harvest-stop'])

const { state: fieldState, onEnter: onEnterFieldRaw, onLeave: onLeaveFieldRaw, running: fieldRunning } = useHoverReveal()
const { state: wageState,  onEnter: onEnterWage,  onLeave: onLeaveWageRaw,  running: wageRunning }  = useHoverReveal()
const { state: feeState,   onEnter: onEnterFee,   onLeave: onLeaveFee }                             = useHoverReveal()

function onEnterField() {
  onEnterFieldRaw()
  emit('harvest-start')
}

// Leaving depth 0 fully (drain empties) also closes any deeper-open tooltips.
function onLeaveField() {
  onLeaveFieldRaw()
  emit('harvest-stop')
  const check = () => {
    if (fieldState.phase === 'off') { wageState.visible = false; feeState.visible = false }
    else requestAnimationFrame(check)
  }
  check()
}
function onLeaveWage() {
  onLeaveWageRaw()
  const check = () => {
    if (wageState.phase === 'off') feeState.visible = false
    else requestAnimationFrame(check)
  }
  check()
}
</script>

<style scoped>
.pond-scene {
  position: absolute; inset: 0;
  background: #8fae5c;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.06) 0 8px, transparent 8px 16px);
  cursor: pointer;
}
.water     { position: absolute; left: 44px; top: 24px; width: 120px; height: 72px; background: #3f7fb8; box-shadow: 0 0 0 4px #2a5c8a; background-image: repeating-linear-gradient(0deg, rgba(255,255,255,.16) 0 3px, transparent 3px 12px); }
.water-top { position: absolute; left: 44px; top: 24px; width: 120px; height: 10px; background: #5fa3d6; }
.reeds { background-image: repeating-linear-gradient(90deg, #cfe0b0 0 5px, #7d9a41 5px 7px, transparent 7px 11px); }
.reeds-l      { position: absolute; left: 8px;  top: 18px; width: 32px;  height: 84px; background-image: repeating-linear-gradient(90deg, #cfe0b0 0 5px, #7d9a41 5px 7px, transparent 7px 11px), repeating-linear-gradient(0deg, rgba(0,0,0,.16) 0 2px, transparent 2px 12px); }
.reeds-r      { position: absolute; right: 6px; top: 18px; width: 32px;  height: 84px; background-image: repeating-linear-gradient(90deg, #cfe0b0 0 5px, #7d9a41 5px 7px, transparent 7px 11px), repeating-linear-gradient(0deg, rgba(0,0,0,.16) 0 2px, transparent 2px 12px); }
.reeds-top    { position: absolute; left: 44px; top: 6px; width: 120px; height: 16px; }
.reeds-bottom { position: absolute; left: 44px; bottom: 8px; width: 120px; height: 14px; }
.cutter { position: absolute; left: 112px; bottom: 1px; z-index: 3; }

.pond-bar        { position: absolute; left: 0; right: 0; bottom: 0; height: 7px; background: rgba(16,11,7,.75); }
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
.pond-tip-0 { left: 270px; top: 126px; }
.pond-tip-1 { position: relative; left: auto; top: auto; width: 280px; margin-top: 10px; background: #42311f; box-shadow: inset 2px 2px 0 #66492c, 0 6px 0 rgba(0,0,0,.4); z-index: 32; }
.pond-tip-2 { position: relative; left: auto; top: auto; width: 250px; margin-top: 10px; background: #523d27; box-shadow: inset 2px 2px 0 #7a5c38, 0 6px 0 rgba(0,0,0,.4); z-index: 33; }

.pond-tip-title { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); margin-bottom: 8px; }
.pond-tip-body  { font-size: 15px; line-height: 1.6; color: #f3e6cc; }
.pond-trigger   { color: var(--px-gold-lt); border-bottom: 2px dashed var(--px-orange); cursor: pointer; }
</style>
