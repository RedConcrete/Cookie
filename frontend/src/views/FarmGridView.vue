<template>
  <div
    ref="viewEl"
    class="farm-view"
    @mousedown="panStart"
    @mousemove="panMove"
    @mouseup="panEnd"
    @mouseleave="panEnd"
  >
    <div ref="gridEl" class="farm-grid" :style="gridStyle">

      <!-- Backofen -->
      <BuildingTile
        variant="oven"
        label="Backofen"
        :bakeStatus="bakeStatus"
        class="pos-oven"
        @open="dialog = 'bake'"
      />

      <!-- Markt -->
      <BuildingTile
        variant="market"
        label="Markt"
        class="pos-market"
        @open="dialog = 'market'"
      />

      <!-- Rohstoff-Felder -->
      <BuildingTile
        v-for="res in resources"
        :key="res.name"
        variant="harvest"
        :label="res.label"
        :icon="res.icon"
        :class="res.pos"
        :upgradeBadges="harvestBadges(res.name)"
        @harvest-start="startHarvest(res.name)"
        @harvest-stop="stopHarvest(res.name)"
      />


      <!-- Zahlen fliegen mit dem Grid mit -->
      <FarmNumbers />

    </div>

    <!-- Controls -->
    <div class="farm-controls">
      <button class="ctrl-btn" title="Zentrieren" @click="resetView">⊙</button>
      <button class="ctrl-btn" title="Rein" @click="zoomStep(1.2)">+</button>
      <button class="ctrl-btn" title="Raus" @click="zoomStep(0.8)">−</button>
    </div>

    <!-- Zoom-Anzeige -->
    <div class="farm-hint">{{ Math.round(zoom * 100) }}%</div>

    <!-- Dialoge -->
    <MarketDialog v-if="dialog === 'market'" @close="dialog = null" />
    <BakeDialog   v-if="dialog === 'bake'"   @close="dialog = null" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { harvestResource, bakeStatus as fetchBakeStatus, getUpgrades } from '../services/api.js'
import { spawnFarmNumber } from '../composables/useFarmNumbers.js'
import FarmNumbers from '../components/FarmNumbers.vue'
import BuildingTile from '../components/BuildingTile.vue'
import MarketDialog  from '../components/MarketDialog.vue'
import BakeDialog    from '../components/BakeDialog.vue'
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()
const dialog      = ref(null)
const bakeStatus  = ref(null)
const viewEl      = ref(null)
const gridEl      = ref(null)
const upgrades    = ref([])
// Mausposition im Grid-lokalen Raum (px innerhalb des 720×940 Grids)

// Upgrade-Level-Helper
function upgradeLevel(id) {
  return upgrades.value.find(u => u.id === id)?.currentLevel ?? 0
}

// Badges für ein Harvest-Tile berechnen
function harvestBadges(resourceName) {
  const badges = []
  const boostLvl = upgradeLevel('boost_harvest')
  if (boostLvl > 0) badges.push({ icon: 'Ertrag', level: boostLvl })
  const speedLvl = upgradeLevel('boost_harvest_speed')
  if (speedLvl > 0) badges.push({ icon: 'Tempo', level: speedLvl })
  const autoLvl = upgradeLevel(`auto_${resourceName.toLowerCase()}`)
  if (autoLvl > 0) badges.push({ icon: 'Auto', level: autoLvl })
  return badges.length ? badges : null
}

const resources = [
  { name: 'SUGAR',     label: 'Zucker',     icon: sugarIcon,  pos: 'pos-sugar'     },
  { name: 'FLOUR',     label: 'Mehl',       icon: flourIcon,  pos: 'pos-flour'     },
  { name: 'EGGS',      label: 'Eier',       icon: eggsIcon,   pos: 'pos-eggs'      },
  { name: 'BUTTER',    label: 'Butter',     icon: butterIcon, pos: 'pos-butter'    },
  { name: 'CHOCOLATE', label: 'Schokolade', icon: chocoIcon,  pos: 'pos-chocolate' },
  { name: 'MILK',      label: 'Milch',      icon: milkIcon,   pos: 'pos-milk'      },
]

// ── Pan + Zoom ───────────────────────────────────────────
const panX = ref(0)
const panY = ref(0)
const zoom = ref(1)

const MAX_ZOOM = 1.5
// no hard min zoom — but clamp at 0.05 so it doesn't disappear

const gridStyle = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}))

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
}
function panEnd() { dragging = false }

function applyZoom(newZoom, originX, originY) {
  newZoom = Math.min(MAX_ZOOM, Math.max(0.05, newZoom))
  const ratio = newZoom / zoom.value
  panX.value = originX - ratio * (originX - panX.value)
  panY.value = originY - ratio * (originY - panY.value)
  zoom.value = newZoom
}

function onWheel(e) {
  e.preventDefault()
  const rect = viewEl.value.getBoundingClientRect()
  // Cursor relativ zur Mitte der Farm-View
  const ox = e.clientX - rect.left - rect.width  / 2
  const oy = e.clientY - rect.top  - rect.height / 2
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  applyZoom(zoom.value * factor, ox, oy)
}

function zoomStep(factor) {
  applyZoom(zoom.value * factor, 0, 0)
}

function resetView() {
  panX.value = 0
  panY.value = 0
  zoom.value = 1
}

// ── Harvest ──────────────────────────────────────────────
const harvestIntervals = {}
const harvestDelays    = {}
const HARVEST_DELAY_MS  = 400
const HARVEST_BASE_MS   = 1000
const HARVEST_MIN_MS    = 200

function harvestIntervalMs() {
  const lvl = upgradeLevel('boost_harvest_speed')
  return Math.max(HARVEST_MIN_MS, HARVEST_BASE_MS - lvl * 100)
}

function tileCenterGridLocal(posClass) {
  if (!gridEl.value) return null
  const gridRect = gridEl.value.getBoundingClientRect()
  const el = gridEl.value.querySelector(`.${posClass}`)
  if (!el) return null
  const r = el.getBoundingClientRect()
  return {
    x: (r.left + r.width  / 2 - gridRect.left) / zoom.value,
    y: (r.top  + r.height / 2 - gridRect.top)  / zoom.value,
  }
}

async function doHarvest(name) {
  try {
    const before  = playerStore[name.toLowerCase()] ?? 0
    const updated = await harvestResource(playerStore.steamId, name)
    playerStore.updateFromDto(updated)
    const gained  = (playerStore[name.toLowerCase()] ?? 0) - before
    if (gained > 0) {
      const res = resources.find(r => r.name === name)
      const pos = res ? tileCenterGridLocal(res.pos) : null
      if (pos) spawnFarmNumber(gained, pos.x, pos.y)
    }
  } catch {}
}
function startHarvest(name) {
  if (harvestIntervals[name] || harvestDelays[name]) return
  harvestDelays[name] = setTimeout(() => {
    harvestDelays[name] = null
    doHarvest(name)
    harvestIntervals[name] = setInterval(() => doHarvest(name), harvestIntervalMs())
  }, HARVEST_DELAY_MS)
}
function stopHarvest(name) {
  clearTimeout(harvestDelays[name])
  harvestDelays[name] = null
  clearInterval(harvestIntervals[name])
  harvestIntervals[name] = null
}

// ── Bake Poll ────────────────────────────────────────────
let bakeTimer = null
async function pollBake() {
  try {
    const s = await fetchBakeStatus(playerStore.steamId)
    bakeStatus.value = s.active ? s : null
  } catch {}
}

let upgradeTimer     = null
let autoHarvestTimer = null

async function loadUpgrades() {
  try { upgrades.value = await getUpgrades(playerStore.steamId) } catch {}
}

// Zahlen für Auto-Harvest spawnen — passend zum 5s Server-Scheduler
function tickAutoHarvestNumbers() {
  if (!gridEl.value) return
  const gridRect = gridEl.value.getBoundingClientRect()
  for (const res of resources) {
    const autoLvl = upgradeLevel(`auto_${res.name.toLowerCase()}`)
    if (autoLvl <= 0) continue
    const amount = autoLvl * 0.5 * 5
    const el = gridEl.value.querySelector(`.${res.pos}`)
    if (!el) continue
    const rect = el.getBoundingClientRect()
    // Tile-Mitte → Grid-lokale Koordinaten
    const cx = (rect.left + rect.width  / 2 - gridRect.left) / zoom.value
    const cy = (rect.top  + rect.height / 2 - gridRect.top)  / zoom.value
    spawnFarmNumber(amount, cx, cy)
  }
}

onMounted(() => {
  pollBake()
  bakeTimer = setInterval(pollBake, 2000)
  loadUpgrades()
  upgradeTimer     = setInterval(loadUpgrades, 10000)
  autoHarvestTimer = setInterval(tickAutoHarvestNumbers, 5000)
  viewEl.value.addEventListener('wheel', onWheel, { passive: false })
})

onUnmounted(() => {
  clearInterval(bakeTimer)
  clearInterval(upgradeTimer)
  clearInterval(autoHarvestTimer)
  Object.values(harvestDelays).forEach(clearTimeout)
  Object.values(harvestIntervals).forEach(clearInterval)
  viewEl.value?.removeEventListener('wheel', onWheel)
})
</script>

<style scoped>
.farm-view {
  position: relative;
  overflow: hidden;
  width: 100%;
  height: calc(100vh - 68px);
  cursor: grab;
  background: radial-gradient(ellipse at center, #e8dfc0 0%, #d4c89a 100%);
}
.farm-view:active { cursor: grabbing; }

.farm-grid {
  position: absolute;
  width: 720px;
  height: 940px;
  will-change: transform;
  left: 50%;
  top: 50%;
  margin-left: -360px;
  margin-top: -470px;
  transform-origin: center center;
}

/* ── Tile positions ─────────────────────────── */
.pos-oven    { top:  30px; left: 285px; }
.pos-market  { top: 270px; left: 285px; }

.pos-sugar     { top:  30px; left:  30px; }
.pos-flour     { top:  30px; left: 540px; }
.pos-eggs      { top: 250px; left: 560px; }
.pos-butter    { top: 470px; left: 400px; }
.pos-chocolate { top: 470px; left: 120px; }
.pos-milk      { top: 250px; left:   0px; }

.pos-shop        { top: 715px; left:  60px; }
.pos-prestige    { top: 715px; left: 285px; }
.pos-leaderboard { top: 715px; left: 510px; }

/* ── Controls ───────────────────────────────── */
.farm-controls {
  position: absolute;
  bottom: 16px;
  left: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  pointer-events: all;
}
.ctrl-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1px solid rgba(61,43,31,0.3);
  background: rgba(255,251,240,0.85);
  color: var(--text);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}
.ctrl-btn:hover { background: var(--surface); }

.farm-hint {
  position: absolute;
  bottom: 16px;
  right: 16px;
  font-size: 11px;
  color: rgba(61,43,31,0.5);
  pointer-events: none;
  font-weight: 600;
}
</style>
