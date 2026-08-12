<template>
  <div class="stv-root">
    <div v-if="loading" class="stv-loading"><LoadingIndicator /></div>

    <template v-else>
      <!-- ── Baum: pan + zoom ──────────────────────────────── -->
      <div
        ref="viewEl"
        class="stv-viewport"
        @mousedown="panStart"
        @mousemove="panMove"
        @mouseup="panEnd"
        @mouseleave="panEnd"
        @wheel="onWheel"
      >
        <div class="stv-canvas" :style="canvasStyle">
          <svg class="stv-edges" :width="WORLD_SIZE" :height="WORLD_SIZE">
            <line
              v-for="e in edgeLines" :key="e.id"
              :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2"
              :class="['stv-edge', `stv-edge-${e.state}`]"
            />
          </svg>

          <PixelInfoPopover
            v-for="n in tree.nodes" :key="n.id"
            :rows="nodeRows(n)" :title="nodeName(n)" :note="nodeDesc(n)" :width="290"
            :style="nodeWrapStyle(n)"
          >
            <button
              class="stv-node"
              :class="[
                `stv-node-${nodeState(n)}`,
                `stv-branch-${(n.branch || '').toLowerCase()}`,
                { 'stv-node-nopoints': nodeState(n) === 'allocatable' && tree.skillPoints < 1 },
                { 'stv-node-keystone': n.nodeTier === 'KEYSTONE' },
                { 'stv-node-notable': n.nodeTier === 'NOTABLE' },
              ]"
              :disabled="!n.root && (!canAllocate(n) || allocating)"
              @click="onNodeClick(n)"
            >
              <PixelIcon :name="nodeIcon(n)" :size="iconSize(n)" />
            </button>
          </PixelInfoPopover>
        </div>

        <!-- ── Verfuegbare Skill-Punkte: immer sichtbar, nicht erst im Kauf-Popup
             (Playtest-Feedback) ──────────────────────────────── -->
        <div v-if="tree.skillPoints > 0" class="stv-points-badge" @click="buyDialogOpen = true">
          <PixelIcon name="upgrade" :size="16" />
          <span class="stv-hud-val">{{ tree.skillPoints }}</span>
          <span class="stv-hud-label">{{ t('skillTreeView.skillPointsLabel') }}</span>
        </div>

        <!-- ── Kamera zentrieren ──────────────────────────────── -->
        <div class="stv-cam-controls">
          <button class="stv-cam-btn" @click="resetView" :title="t('skillTreeView.centerTitle')"><ShortcutSlot /><PixelIcon name="zentrieren" :size="18" /></button>
          <div class="stv-cam-hint">{{ t('skillTreeView.centerHint') }}</div>
        </div>

        <div v-if="notice" class="stv-notice" :class="{ error: noticeError }">{{ notice }}</div>
      </div>

      <!-- ── Skill-Punkte kaufen: Popup, öffnet per Klick auf die Wurzel ── -->
      <div v-if="buyDialogOpen" class="stv-buy-overlay" @click.self="buyDialogOpen = false">
        <div class="stv-buy-panel px-panel">
          <div class="px-titlebar">
            <span>{{ t('skillTreeView.skillPointsLabel') }}</span>
            <button class="px-close" @click="buyDialogOpen = false"><ShortcutSlot />&times;</button>
          </div>
          <div class="stv-buy-panel-body">
            <div class="stv-hud-points">
              <PixelIcon name="upgrade" :size="18" />
              <span class="stv-hud-val">{{ tree.skillPoints }}</span>
              <span class="stv-hud-label">{{ t('skillTreeView.skillPointsLabel') }}</span>
            </div>
            <button
              class="px-btn px-btn-accent"
              :disabled="!canAfford || buying"
              @click="buyPoint"
            >
              <ShortcutSlot />{{ t('skillTreeView.buyPointLabel') }} &middot; {{ fmt(tree.nextPointCost) }}
              <PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" />
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { buySkillPoint, allocateSkillNode, getPlayer } from '../services/api.js'
import { fmt2 as fmt } from '../utils/formatNumber.js'
import { resourceLabel } from './buildings/buildingInfo.js'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelInfoPopover from './pixel/PixelInfoPopover.vue'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const { t, locale } = useI18n()
const playerStore = usePlayerStore()

const loading   = ref(true)
const buying    = ref(false)
const allocating = ref(false)
const notice      = ref('')
const noticeError = ref(false)
const buyDialogOpen = ref(false)

function flash(msg, isError = false) {
  notice.value = msg
  noticeError.value = isError
  setTimeout(() => { notice.value = '' }, 2500)
}

const tree = computed(() => playerStore.skillTree)
const canAfford = computed(() => playerStore.cookies >= tree.value.nextPointCost)

const BRANCH_ICON = {
  MILK: 'milch', BAKING: 'ofen', MARKET: 'stand', CORE: 'einw', DISPO: 'lohn',
  SUGAR: 'zucker', FLOUR: 'mehl', EGGS: 'eier', BUTTER: 'butter', CHOCOLATE: 'schoko',
  STORAGE: 'lager',
}
// Nur Keystones bekommen ein eigenes Icon pro Knoten -- Notables/Passives bleiben auf dem
// Branch-Icon, sonst braeuchte jeder kuenftige Notable eines (siehe Plan Fundament, Abschnitt
// Frontend-Aenderungen).
const KEYSTONE_ICON = {
  milk_4: 'keystoneMilk4',
  bake_4: 'keystoneBake4',
  market_4: 'keystoneMarket4',
  dispo_4: 'keystoneDispo4',
  keystone_alleskoenner: 'keystoneAlleskoenner',
  // Rohstoff-Branches (2026-08-12): 2 Keystones je Branch (Ertrags-Pfad "_y3" reused das alte
  // Icon, Lohn-Pfad "_w3" bekommt ein neues).
  sugar_y3: 'keystoneSugar4', sugar_w3: 'keystoneSugarW3',
  flour_y3: 'keystoneFlour4', flour_w3: 'keystoneFlourW3',
  eggs_y3: 'keystoneEggs4', eggs_w3: 'keystoneEggsW3',
  butter_y3: 'keystoneButter4', butter_w3: 'keystoneButterW3',
  chocolate_y3: 'keystoneChocolate4', chocolate_w3: 'keystoneChocolateW3',
  storage_4: 'keystoneStorage4',
}
function nodeIcon(n) {
  if (n.root) return 'stern'
  if (n.nodeTier === 'KEYSTONE') return KEYSTONE_ICON[n.id] || BRANCH_ICON[n.branch] || 'einw'
  return BRANCH_ICON[n.branch] || 'einw'
}
function iconSize(n) {
  if (n.nodeTier === 'KEYSTONE') return 28
  if (n.nodeTier === 'NOTABLE') return 24
  return 22
}

// Knoteninhalt ist admin-editierbarer DB-Content, kein statischer UI-Text -- daher kein
// vue-i18n-JSON-Key, sondern beide Sprachen im DTO, hier reaktiv nach locale ausgewaehlt.
function nodeName(n) { return locale.value === 'de' ? n.nameDe : n.nameEn }
function nodeDesc(n) { return locale.value === 'de' ? n.descriptionDe : n.descriptionEn }

const EFFECT_LABEL_KEY = {
  HARVEST_YIELD: 'skillTreeView.effectHarvestYield',
  BAKE_OUTPUT: 'skillTreeView.effectBakeOutput',
  MARKET_FEE_REDUCTION: 'skillTreeView.effectMarketFeeReduction',
  WAGE_INTEREST_REDUCTION: 'skillTreeView.effectWageInterestReduction',
  RESOURCE_WAGE_REDUCTION: 'skillTreeView.effectResourceWageReduction',
  STORAGE_CAP_BONUS: 'skillTreeView.effectStorageCapBonus',
  BUILDING_BUFFER_BONUS: 'skillTreeView.effectBuildingBufferBonus',
}
function effectLabel(e) {
  const base = t(EFFECT_LABEL_KEY[e.effectType] || e.effectType)
  return e.targetResource ? `${base} (${resourceLabel(e.targetResource, t)})` : base
}
// Downsides sind einfach ein zweiter Effekt mit negativem effectValue (siehe Backend) --
// hier nur farblich unterschieden (Vorteil gruen, Nachteil rot), kein eigener Mechanismus.
function effectRows(n) {
  return (n.effects || []).map(e => ({
    k: effectLabel(e),
    v: `${e.effectValue >= 0 ? '+' : ''}${(e.effectValue * 100).toFixed(2).replace(/\.?0+$/, '')}%`,
    color: e.effectValue >= 0 ? 'b' : 'r',
  }))
}

function nodeState(n) {
  if (n.allocated) return 'allocated'
  if (n.allocatable) return 'allocatable'
  return 'locked'
}

// A node can look "allocatable" (adjacent to the unlocked frontier) even
// with 0 skill points left -- that's intentional (shows what's next), but
// clicking it must not be possible, or the backend's rejection would need
// showing to the player somehow.
function canAllocate(n) {
  return nodeState(n) === 'allocatable' && tree.value.skillPoints >= 1
}

function nodeRows(n) {
  if (n.root) return [{ k: t('skillTreeView.statusLabel'), v: t('skillTreeView.rootBuyHint'), color: 'y' }]
  const rows = effectRows(n)
  const state = nodeState(n)
  if (state === 'allocated') { rows.push({ k: t('skillTreeView.statusLabel'), v: t('skillTreeView.statusAllocated'), color: 'g' }); return rows }
  if (state === 'allocatable') {
    rows.push({ k: t('skillTreeView.costLabel'), v: t('skillTreeView.costOnePoint'), color: 'y' })
    if (tree.value.skillPoints < 1) rows.push({ k: t('skillTreeView.statusLabel'), v: t('skillTreeView.noPointsLeft'), color: 'w' })
    return rows
  }
  rows.push({ k: t('skillTreeView.statusLabel'), v: t('skillTreeView.statusLocked'), color: 'w' })
  return rows
}

async function buyPoint() {
  if (buying.value || !canAfford.value) return
  buying.value = true
  try {
    playerStore.skillTree = await buySkillPoint(playerStore.steamId)
    const dto = await getPlayer(playerStore.steamId)
    playerStore.updateFromDto(dto)
  } catch (e) {
    flash(e.message, true)
  } finally {
    buying.value = false
  }
}

function onNodeClick(n) {
  if (n.root) { buyDialogOpen.value = true; return }
  allocate(n)
}

async function allocate(n) {
  if (allocating.value || !canAllocate(n)) return
  allocating.value = true
  try {
    playerStore.skillTree = await allocateSkillNode(playerStore.steamId, n.id)
  } catch (e) {
    flash(e.message, true)
  } finally {
    allocating.value = false
  }
}

// ── Layout ─────────────────────────────────────────────
const WORLD_SIZE = 1800
const CENTER = WORLD_SIZE / 2
const NODE_SIZE = 56
const NOTABLE_SIZE = 68
const KEYSTONE_SIZE = 80

function nodeSize(n) {
  if (n.nodeTier === 'KEYSTONE') return KEYSTONE_SIZE
  if (n.nodeTier === 'NOTABLE') return NOTABLE_SIZE
  return NODE_SIZE
}

// PixelInfoPopover's own root element ("pip-wrap") is what the tooltip
// positions itself against (getBoundingClientRect() on that element) -- it
// must sit exactly at the node's coordinates itself, not just an inner
// child, or every tooltip anchors to wherever pip-wrap happens to land in
// normal document flow instead of the hovered node.
function nodeWrapStyle(n) {
  const size = nodeSize(n)
  return {
    position: 'absolute',
    left: (CENTER + n.x - size / 2) + 'px',
    top:  (CENTER + n.y - size / 2) + 'px',
    width: size + 'px',
    height: size + 'px',
  }
}

const edgeLines = computed(() => {
  const byId = Object.fromEntries(tree.value.nodes.map(n => [n.id, n]))
  return (tree.value.edges || []).map((e, i) => {
    const from = byId[e.from]
    const to   = byId[e.to]
    if (!from || !to) return null
    let state = 'locked'
    if (from.allocated && to.allocated) state = 'active'
    else if (from.allocated || to.allocated) state = 'available'
    return {
      id: `${e.from}-${e.to}-${i}`,
      x1: CENTER + from.x, y1: CENTER + from.y,
      x2: CENTER + to.x,   y2: CENTER + to.y,
      state,
    }
  }).filter(Boolean)
})

// ── Pan + zoom (ported from FarmGridView's camera pattern) ──
const viewEl = ref(null)
const panX = ref(0)
const panY = ref(0)
const zoom = ref(0.55)
const MIN_ZOOM = 0.12
const MAX_ZOOM = 1.2

const canvasStyle = computed(() => ({
  transform: `translate(calc(-50% + ${panX.value}px), calc(-50% + ${panY.value}px)) scale(${zoom.value})`,
}))

function clampPan() {
  const vw = viewEl.value?.clientWidth  ?? WORLD_SIZE
  const vh = viewEl.value?.clientHeight ?? WORLD_SIZE
  const maxX = Math.max(0, (WORLD_SIZE * zoom.value - vw) / 2)
  const maxY = Math.max(0, (WORLD_SIZE * zoom.value - vh) / 2)
  panX.value = Math.min(maxX, Math.max(-maxX, panX.value))
  panY.value = Math.min(maxY, Math.max(-maxY, panY.value))
}

let dragging = false
let lastX = 0, lastY = 0
function panStart(e) {
  if (e.button !== 0) return
  dragging = true
  lastX = e.clientX
  lastY = e.clientY
}
function panMove(e) {
  if (!dragging) return
  panX.value += e.clientX - lastX
  panY.value += e.clientY - lastY
  lastX = e.clientX
  lastY = e.clientY
  clampPan()
}
function panEnd() { dragging = false }
function resetView() { panX.value = 0; panY.value = 0; zoom.value = 0.55 }

function onWheel(e) {
  e.preventDefault()
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * factor))
  clampPan()
}

onMounted(async () => {
  loading.value = true
  try { await playerStore.loadSkillTree() }
  finally { loading.value = false }
})
</script>

<style scoped>
.stv-root { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.stv-loading { color: var(--px-tan-ink); text-align: center; padding: 24px; }

.stv-viewport {
  flex: 1; min-height: 0; position: relative; overflow: hidden;
  background: var(--px-bg); cursor: grab;
}
.stv-viewport:active { cursor: grabbing; }

/* Zentrierter, immer sichtbarer Hinweis auf verbleibende Skill-Punkte -- der Kauf-Dialog
   selbst (unten) bleibt weiterhin ein Popup (root-Klick oder Klick hier), damit der Baum
   sonst den vollen Viewport behaelt. */
.stv-points-badge {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%); z-index: 20;
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px;
  background: var(--px-wood); border: 3px solid var(--px-gold);
  box-shadow: inset 1px 1px 0 rgba(255,255,255,.15), 0 4px 0 rgba(0,0,0,.4);
  cursor: pointer;
  animation: stv-points-bob 1.4s ease-in-out infinite;
}
@keyframes stv-points-bob {
  0%, 100% { transform: translateX(-50%) translateY(0); }
  50%      { transform: translateX(-50%) translateY(-3px); }
}
/* Helle Textfarben statt der Standard-.stv-hud-*-Werte (fuer den hellen Buy-Panel-Hintergrund
   gedacht) -- dieses Badge sitzt direkt auf dunklem --px-wood. */
.stv-points-badge .stv-hud-val   { color: #fff1a9; }
.stv-points-badge .stv-hud-label { color: #aea47e; }

/* Buy-point popup, opened by clicking the root node -- no permanent HUD
   bar sitting over the canvas, so the tree gets the full viewport. */
.stv-buy-overlay {
  position: fixed; inset: 0; z-index: 40;
  background: rgba(16,11,7,.55);
  display: flex; align-items: center; justify-content: center;
}
.stv-buy-panel { width: 320px; max-width: 90vw; }
.stv-buy-panel-body {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 16px 18px;
}
.stv-hud-points { display: flex; align-items: center; gap: 6px; }
.stv-hud-val { font-family: 'Silkscreen', monospace; font-size: 15px; color: #56642e; }
.stv-hud-label { font-size: 11px; color: var(--px-tan-ink); }

/* Clearly visible + labeled, same treatment as FarmGridView's cam-controls. */
.stv-cam-controls {
  position: absolute; left: 14px; bottom: 14px; z-index: 20;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.stv-cam-btn {
  position: relative;
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: var(--px-wood3); border: 3px solid var(--px-ink); color: var(--px-cream);
  cursor: pointer; box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.stv-cam-btn:hover { filter: brightness(1.1); }
.stv-cam-hint {
  font-size: 9px; font-family: 'Silkscreen', monospace; color: var(--px-tan);
  background: rgba(16,11,7,.6); padding: 2px 6px; white-space: nowrap;
}

.stv-notice {
  position: absolute; bottom: 14px; left: 50%; transform: translateX(-50%);
  padding: 9px 16px; background: var(--px-cream); border: 3px solid var(--px-green);
  color: #56642e; font-size: 13px; z-index: 25; box-shadow: 0 6px 0 rgba(0,0,0,.4);
}
.stv-notice.error { border-color: var(--px-red); color: var(--px-red-dk); }

.stv-canvas {
  position: absolute; left: 50%; top: 50%;
  width: 1500px; height: 1500px;
  transform-origin: center center;
}

.stv-edges { position: absolute; left: 0; top: 0; pointer-events: none; overflow: visible; }
.stv-edge { stroke-width: 4; }
.stv-edge-locked    { stroke: var(--px-wood2); opacity: 0.5; }
.stv-edge-available { stroke: var(--px-gold); opacity: 0.8; }
.stv-edge-active     { stroke: var(--px-green); opacity: 1; }

.stv-node {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  border: 3px solid var(--px-ink); cursor: pointer;
  box-shadow: inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25);
}
.stv-node:disabled { cursor: default; }

.stv-node-locked {
  background: var(--px-wood2);
  filter: saturate(0.5) brightness(0.7);
}
.stv-node-allocatable {
  background: var(--px-gold);
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt), 0 0 0 3px var(--px-gold);
}
.stv-node-allocatable:hover { filter: brightness(1.15); }
.stv-node-nopoints { filter: saturate(0.7) brightness(0.85); }
.stv-node-nopoints:hover { filter: saturate(0.7) brightness(0.85); }
.stv-node-allocated {
  background: var(--px-green);
  box-shadow: inset -2px -2px 0 var(--px-green-dk), inset 2px 2px 0 var(--px-green-lt);
}

/* Tier-Optik: Rahmenstaerke/-farbe kommt NACH den Status-Klassen, ueberschreibt also nur
   border/box-shadow (Glow), nicht die Hintergrundfarbe -- Status (gruen/gold/grau) bleibt
   bei jedem Tier weiterhin erkennbar. */
.stv-node-notable {
  border-width: 4px;
  border-color: var(--px-gold-lt);
  box-shadow: 0 0 0 2px var(--px-gold), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25);
}
.stv-node-keystone {
  border-width: 5px;
  border-color: var(--px-red-lt);
  animation: stv-keystone-glow 2s ease-in-out infinite;
}
@keyframes stv-keystone-glow {
  0%, 100% { box-shadow: 0 0 0 3px var(--px-red), 0 0 10px 2px var(--px-red-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
  50%      { box-shadow: 0 0 0 3px var(--px-gold), 0 0 18px 4px var(--px-gold-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
}
</style>
