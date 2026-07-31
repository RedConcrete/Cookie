<template>
  <!-- The scene itself — harvest fires via BuildingFrame's PixelInfoPopover @enter/@leave -->
  <div class="pond-scene">
    <!-- Water pool -->
    <div class="water"></div>
    <div class="water-top"></div>
    <!-- Individual sugar cane stalks, each visually distinct -->
    <div v-for="c in canes" :key="c.id" class="cane" :style="c.style">
      <div class="cane-stalk" :style="{ height: c.h + 'px', background: c.col }"></div>
      <div class="cane-node" :style="{ background: c.nodecol }"></div>
      <div class="cane-leaf-l" :style="{ borderBottomColor: c.leafcol }"></div>
      <div class="cane-leaf-r" :style="{ borderBottomColor: c.leafcol }"></div>
    </div>
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

// Individual cane stalk definitions
const canes = [
  { id:0, style:{left:'8px',bottom:'10px'},  h:60, col:'#5a8a2a', nodecol:'#7ab840', leafcol:'#6db535' },
  { id:1, style:{left:'20px',bottom:'10px'}, h:72, col:'#4a7820', nodecol:'#68a832', leafcol:'#5da025' },
  { id:2, style:{left:'32px',bottom:'10px'}, h:52, col:'#639028', nodecol:'#7fb83c', leafcol:'#70a830' },
  { id:3, style:{left:'50px',bottom:'10px'}, h:68, col:'#52821e', nodecol:'#6ea82e', leafcol:'#62a028' },
  { id:4, style:{left:'62px',bottom:'10px'}, h:78, col:'#5a8a2a', nodecol:'#78b838', leafcol:'#68a830' },
  { id:5, style:{left:'76px',bottom:'10px'}, h:56, col:'#4e7c22', nodecol:'#6aa030', leafcol:'#5e9828' },
  { id:6, style:{left:'164px',bottom:'10px'},h:64, col:'#5a8a2a', nodecol:'#78b838', leafcol:'#68a830' },
  { id:7, style:{left:'176px',bottom:'10px'},h:74, col:'#4a7820', nodecol:'#68a832', leafcol:'#5da025' },
  { id:8, style:{left:'190px',bottom:'10px'},h:58, col:'#639028', nodecol:'#7fb83c', leafcol:'#70a830' },
  { id:9, style:{left:'204px',bottom:'10px'},h:70, col:'#52821e', nodecol:'#6ea82e', leafcol:'#62a028' },
]
</script>

<style scoped>
.pond-scene {
  position: absolute; inset: 0;
  background: #7d9a41;
  background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.04) 0 8px, transparent 8px 16px);
  cursor: pointer;
}
.water     { position: absolute; left: 88px; top: 20px; width: 68px; height: 74px; background: #3f7fb8; box-shadow: 0 0 0 4px #2a5c8a; background-image: repeating-linear-gradient(0deg, rgba(255,255,255,.14) 0 3px, transparent 3px 12px); }
.water-top { position: absolute; left: 88px; top: 20px; width: 68px; height: 10px; background: #5fa3d6; }

/* Individual sugar-cane stalks */
.cane { position: absolute; width: 9px; display: flex; flex-direction: column; align-items: center; }
.cane-stalk { width: 6px; box-shadow: inset 1px 0 0 rgba(255,255,255,.25); }
.cane-node  { width: 9px; height: 4px; margin: 1px 0; box-shadow: 0 0 0 1px rgba(0,0,0,.3); }
.cane-leaf-l { width: 0; height: 0; border-left: 7px solid transparent; border-bottom: 12px solid; transform: rotate(-30deg) translateX(-4px); opacity: 0.9; }
.cane-leaf-r { width: 0; height: 0; border-right: 7px solid transparent; border-bottom: 12px solid; transform: rotate(30deg) translateX(4px); margin-top: -8px; opacity: 0.9; }

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
