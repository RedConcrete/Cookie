<template>
  <div
    ref="viewEl"
    class="hof-root"
    @mousedown="panStart"
    @mousemove="panMove"
    @mouseup="panEnd"
    @mouseleave="panEnd"
  >

    <!-- ══ HUD (fixed overlay, outside canvas so it stays put while panning) ══ -->
    <div class="hud">
      <div class="hud-chips">
        <PixelInfoPopover :rows="cookieRows" title="COOKIES" side="below-left" :width="272" :z="95">
          <div class="hud-chip hud-chip-cookie">
            <PixelIcon name="cookie" :size="24" />
            <div class="hud-chip-val">{{ fmt(playerStore.cookies) }}</div>
          </div>
        </PixelInfoPopover>

        <PixelInfoPopover
          v-for="r in hudResources" :key="r.name"
          :rows="r.rows" :title="r.label" :icon="r.icon" side="below-left" :width="272" :z="95"
        >
          <div class="hud-chip">
            <PixelIcon :name="r.icon" :size="24" />
            <div class="hud-chip-val">{{ r.val }}</div>
            <div class="hud-chip-label">{{ r.label }}</div>
          </div>
        </PixelInfoPopover>

        <PixelInfoPopover :rows="citizenRows" title="EINWOHNER" side="below-left" :width="240" :z="95">
          <div class="hud-chip hud-chip-clickable" @click="dialog = 'citizens'">
            <PixelIcon name="einw" :size="24" />
            <div class="hud-chip-val">{{ playerStore.ownedCitizens }}/{{ playerStore.maxCitizens }}</div>
            <div class="hud-chip-label">EINW.</div>
          </div>
        </PixelInfoPopover>
      </div>

      <PixelInfoPopover :rows="netWorthRows" title="NET WORTH" side="below-right" :width="276" :z="95" class="hud-networth-wrap">
        <div class="hud-networth" @click="dialog = 'networth'" title="Net Worth Verlauf">
          <div class="hud-networth-label">NET WORTH</div>
          <div class="hud-networth-val">{{ fmtBig(playerStore.netWorth) }}</div>
        </div>
      </PixelInfoPopover>

      <div class="hud-actions">
        <button v-if="isDev" class="px-btn hud-dev-btn hud-desktop-only" @click="devReset" title="DEV: Reset">&#8635; DEV</button>
        <button v-if="isDev" class="px-btn hud-dev-btn hud-desktop-only" @click="dialog = 'admin'" title="DEV: Admin-Panel">&#9881; ADMIN</button>
        <button class="px-btn px-btn-accent hud-desktop-only" @click="dialog = 'upgrades'">UPGRADES</button>
        <button class="px-btn hud-desktop-only" @click="dialog = 'prestige'">PRESTIGE</button>
        <button class="px-btn hud-desktop-only" @click="dialog = 'leaderboard'">RANGLISTE</button>
        <button class="px-btn" @click="dialog = 'settings'" title="Einstellungen">&#9776;</button>
        <button class="hud-avatar" @click="dialog = 'profile'" title="Profil">
          <PixelIcon name="einw" :size="20" />
        </button>
      </div>
    </div>

    <!-- ══ World canvas (pannable + zoomable, no overflow clip) ══ -->
    <div ref="canvasEl" class="hof-canvas" :style="canvasStyle">

      <!-- Grass world — large enough to cover any pan distance -->
      <div class="grass-world"></div>

      <!-- ══ Buildings ══ -->
      <BuildingFrame
        v-for="b in buildings" :key="b.id"
        :building-id="b.id"
        :base="BASE[b.id]" :title="b.title" :icon="b.icon" :rate="b.overlayRate" :workers="b.workers"
        :rows="buildingRows(b)" :note="b.note" :side="b.side" :scene-height="SCENE_H[b.id]"
        :drop-ok="(pos) => dropOk(b.id, pos)"
        :zoom="zoom"
        :offset="buildingOffsets[b.id]"
        :class="{ 'building-idle': playerStore.workersIdle && isBuildingOwned(b.id) }"
        @open="onOpenBuilding(b)"
        @harvest-start="b.resource && startHarvest(b.id, b.resource)"
        @harvest-stop="b.resource && stopHarvest(b.resource)"
        @moved="onBuildingMoved(b.id, $event)"
      >
        <component
          :is="b.comp" :workers="b.workers"
          v-bind="b.id === 'rathaus' ? { idleCount: playerStore.idleCitizens, idleWarn: playerStore.workersIdle } : {}"
        />
      </BuildingFrame>

      <!-- Dynamic idle wanderers (one per idle citizen, max 5 shown) — follow the Rathaus when it's moved -->
      <template v-for="(w, i) in idleWanderers" :key="i">
        <div class="idle-wanderer" :style="w.style">
          <TravelingWorker :travel-anim="w.anim" :travel-dur="w.dur" :travel-delay="w.delay" :leg-dur="w.legDur" :hat="w.hat" :skin="w.skin" :torso="w.torso" />
        </div>
      </template>

      <FarmNumbers />
    </div>

    <!-- ══ Camera controls (outside canvas, fixed overlay) ══ -->
    <div class="cam-controls">
      <button class="cam-center" title="Zentrieren (LEERTASTE)" @click="resetView">&#8857;</button>
      <div class="cam-hint">ZENTRIEREN &middot; LEERTASTE</div>
    </div>
    <div class="zoom-readout">{{ Math.round(zoom * 100) }} %</div>

    <!-- Floating build button (bottom-right) -->
    <button class="build-fab" title="Gebäude bauen" @click="dialog = 'buildshop'">+</button>

    <!-- Mobile bottom nav -->
    <div class="mobile-nav">
      <button v-for="n in mobileNavItems" :key="n.label" class="mobile-nav-item" @click="n.action">
        <PixelIcon :name="n.icon" :size="20" />
        <span>{{ n.label }}</span>
      </button>
    </div>

    <!-- Dialogs -->
    <MarketDialog       v-if="dialog === 'market'"      @close="dialog = null" />
    <BakeDialog         v-if="dialog === 'bake'"        @close="dialog = null" />
    <BuildingDetailDialog v-if="detailBuilding"         :building="detailBuilding" @close="detailBuilding = null" />
    <UpgradeDialog      v-if="dialog === 'upgrades'"    @close="dialog = null" />
    <PrestigeDialog     v-if="dialog === 'prestige'"    @close="dialog = null" />
    <LeaderboardDialog  v-if="dialog === 'leaderboard'" @close="dialog = null" />
    <SettingsDialog     v-if="dialog === 'settings'"    @close="dialog = null" />
    <PlayerProfileDialog v-if="dialog === 'profile'"   :steamId="playerStore.steamId" @close="dialog = null" />
    <NetWorthDialog     v-if="dialog === 'networth'"    :steamId="playerStore.steamId" @close="dialog = null" />
    <OrdenDialog        v-if="dialog === 'badges'"      :steamId="playerStore.steamId" @close="dialog = null" />
    <BuildShopDialog    v-if="dialog === 'buildshop'"   @close="dialog = null" />
    <CitizenDialog      v-if="dialog === 'citizens'"    @close="dialog = null" />
    <RathausDialog      v-if="dialog === 'rathaus'"     @close="dialog = null" />
    <LagerDialog        v-if="dialog === 'lager'"       @close="dialog = null" />
    <AdminDialog        v-if="dialog === 'admin'"       @close="dialog = null" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { useBakeStore } from '../stores/bake.js'
import { harvestResource, getUpgrades, trade, adminResetPlayer, getConfig } from '../services/api.js'
import { spawnFarmNumber } from '../composables/useFarmNumbers.js'
import { useHotkeys, keyLabelFromEvent } from '../composables/useHotkeys.js'
import FarmNumbers from '../components/FarmNumbers.vue'
import PixelIcon from '../components/pixel/PixelIcon.vue'
import PixelInfoPopover from '../components/pixel/PixelInfoPopover.vue'
import BuildingFrame from '../components/buildings/BuildingFrame.vue'
import TravelingWorker from '../components/buildings/TravelingWorker.vue'
import { BASE, SCENE_H, dropOk as dropOkLayout } from '../components/buildings/farmLayout.js'
import { BUILDING_INFO, RESOURCE_LABEL, RESOURCE_ICON } from '../components/buildings/buildingInfo.js'

import SugarPondScene from '../components/buildings/SugarPondScene.vue'
import OvenScene      from '../components/buildings/OvenScene.vue'
import TownHallScene  from '../components/buildings/TownHallScene.vue'
import MarketScene    from '../components/buildings/MarketScene.vue'
import WarehouseScene from '../components/buildings/WarehouseScene.vue'
import FarmScene      from '../components/buildings/FarmScene.vue'
import ChickenScene   from '../components/buildings/ChickenScene.vue'
import ButterScene    from '../components/buildings/ButterScene.vue'
import CocoaScene     from '../components/buildings/CocoaScene.vue'
import CowScene       from '../components/buildings/CowScene.vue'

import MarketDialog from '../components/MarketDialog.vue'
import BakeDialog from '../components/BakeDialog.vue'
import BuildingDetailDialog from '../components/BuildingDetailDialog.vue'
import UpgradeDialog from '../components/UpgradeDialog.vue'
import PrestigeDialog from '../components/PrestigeDialog.vue'
import LeaderboardDialog from '../components/LeaderboardDialog.vue'
import SettingsDialog from '../components/SettingsDialog.vue'
import PlayerProfileDialog from '../components/PlayerProfileDialog.vue'
import NetWorthDialog from '../components/NetWorthDialog.vue'
import OrdenDialog from '../components/OrdenDialog.vue'
import BuildShopDialog from '../components/BuildShopDialog.vue'
import CitizenDialog from '../components/CitizenDialog.vue'
import RathausDialog from '../components/RathausDialog.vue'
import LagerDialog from '../components/LagerDialog.vue'
import AdminDialog from '../components/AdminDialog.vue'

const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const bakeStore   = useBakeStore()
const { state: hotkeyState } = useHotkeys()

const isDev = playerStore.steamId === 'DEV_PLAYER_001'
const sellFeeRate = ref(0.08)

const dialog = ref(null)
const detailBuilding = ref(null)
const viewEl   = ref(null)
const canvasEl = ref(null)
const upgrades = ref([])

// Track each building's current drag offset for collision detection + number spawning
// Persisted per player so moved buildings stay put across reloads.
const OFFSETS_KEY = `cookieBuildingOffsets_${playerStore.steamId}`
function loadOffsets() {
  try { return JSON.parse(localStorage.getItem(OFFSETS_KEY)) ?? {} } catch { return {} }
}
const savedOffsets = loadOffsets()
const buildingOffsets = reactive(
  Object.fromEntries(Object.keys(BASE).map(id => [id, savedOffsets[id] ?? { x: 0, y: 0 }]))
)
function onBuildingMoved(id, offset) {
  buildingOffsets[id] = offset
  localStorage.setItem(OFFSETS_KEY, JSON.stringify(buildingOffsets))
}

const SCENE_COMP = {
  pond: SugarPondScene, ofen: OvenScene, rathaus: TownHallScene, markt: MarketScene,
  lager: WarehouseScene, hof: FarmScene, huhn: ChickenScene, butter: ButterScene,
  kakao: CocoaScene, kuh: CowScene,
}

const buildings = computed(() =>
  Object.keys(BUILDING_INFO)
    .map(id => {
      const owned = playerStore.ownedBuildings.find(b => b.id === id)
      if (!owned || owned.level === 0) return null
      return { id, comp: SCENE_COMP[id], ...BUILDING_INFO[id], workers: owned.workers ?? 0 }
    })
    .filter(Boolean)
)

function dropOk(id, pos) { return dropOkLayout(id, pos, buildingOffsets) }

async function devReset() {
  try {
    await adminResetPlayer(playerStore.steamId)
    await playerStore.init(playerStore.steamId)
  } catch (e) { console.error('[devReset]', e) }
}

// Which buildings this player owns (level > 0 from backend)
function isBuildingOwned(id) {
  const b = playerStore.ownedBuildings.find(x => x.id === id)
  return b ? b.level > 0 : false
}

// Use real owned-building data for rows if available, else fall back to static BUILDING_INFO
function buildingRows(b) {
  const owned = playerStore.ownedBuildings.find(x => x.id === b.id)
  if (!owned || owned.level === 0) return b.rows
  return b.rows
}

const totalWorkers = computed(() => playerStore.assignedCitizens)

// Idle wanderers — up to 5, dynamic based on idle citizens
// Idle citizens gather in front of (below) the Rathaus, wherever it currently is —
// positions are offsets relative to BASE.rathaus so they follow the building when it's moved.
const WANDERER_CONFIGS = [
  { anim:'wander',  dur:9,  delay:0,   legDur:0.45, hat:'#5aa0e0', skin:'#f0c9a0', torso:'#4a3f7a', dx:40,  dy:150 },
  { anim:'wander2', dur:11, delay:1.5, legDur:0.55, hat:'#b83232', skin:'#e8b489', torso:'#6b4f2a', dx:100, dy:168 },
  { anim:'wander',  dur:13, delay:3,   legDur:0.6,  hat:'#3d6b25', skin:'#f0c9a0', torso:'#8a5a34', dx:160, dy:156 },
  { anim:'wander2', dur:10, delay:2,   legDur:0.5,  hat:'#8b5a2b', skin:'#e8b489', torso:'#5a3a22', dx:90,  dy:180 },
  { anim:'wander',  dur:8,  delay:4,   legDur:0.4,  hat:'#7a50b0', skin:'#f0c9a0', torso:'#42311f', dx:150, dy:176 },
]
const idleWanderers = computed(() => {
  const rOff  = buildingOffsets.rathaus || { x: 0, y: 0 }
  const baseX = BASE.rathaus.x + rOff.x
  const baseY = BASE.rathaus.y + rOff.y
  return WANDERER_CONFIGS.slice(0, Math.min(5, playerStore.idleCitizens)).map(w => ({
    ...w,
    style: { left: (baseX + w.dx) + 'px', top: (baseY + w.dy) + 'px' },
  }))
})

const totalResources = computed(() =>
  (playerStore.sugar ?? 0) + (playerStore.flour ?? 0) + (playerStore.eggs ?? 0) +
  (playerStore.butter ?? 0) + (playerStore.chocolate ?? 0) + (playerStore.milk ?? 0)
)

const mobileNavItems = [
  { label: 'HOF',    icon: 'haus',  action: () => { dialog.value = null; resetView() } },
  { label: 'MARKT',  icon: 'stand', action: () => { dialog.value = 'market' } },
  { label: 'BACKEN', icon: 'ofen',  action: () => { dialog.value = 'bake' } },
  { label: 'SHOP',   icon: 'shop',  action: () => { dialog.value = 'upgrades' } },
  { label: 'RANG',   icon: 'pokal', action: () => { dialog.value = 'leaderboard' } },
]

function onOpenBuilding(b) {
  if (b.id === 'ofen')    dialog.value = 'bake'
  else if (b.id === 'markt')   dialog.value = 'market'
  else if (b.id === 'rathaus') dialog.value = 'rathaus'
  else if (b.id === 'lager')   dialog.value = 'lager'
  else detailBuilding.value = b
}

// ── HUD data ─────────────────────────────────────────────
function fmt(v)  { return Number(v ?? 0).toFixed(1) }
function fmt2(v) { return Number(v ?? 0).toFixed(2) }
function fmtBig(v) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v ?? 0).toFixed(1)
}

const cookieRows = computed(() => [
  { k: 'Bestand', v: fmt(playerStore.cookies) + ' C', color: 'w' },
])

const RESOURCES = [
  { name: 'SUGAR',     key: 'sugar',     icon: 'zucker' },
  { name: 'FLOUR',     key: 'flour',     icon: 'mehl'   },
  { name: 'EGGS',      key: 'eggs',      icon: 'eier'   },
  { name: 'BUTTER',    key: 'butter',    icon: 'butter' },
  { name: 'CHOCOLATE', key: 'chocolate', icon: 'schoko' },
  { name: 'MILK',      key: 'milk',      icon: 'milch'  },
]

const hudResources = computed(() => RESOURCES.map(r => {
  const amount = playerStore[r.key] ?? 0
  const price  = marketStore.priceOf(r.name)
  const sellVal = amount * price * 0.92
  return {
    name: r.name, icon: r.icon, label: RESOURCE_LABEL[r.name],
    val: fmt(amount),
    rows: [
      { k: 'Bestand', v: fmt(amount), color: 'w' },
      { k: 'Marktpreis', v: price.toFixed(4) + ' C', color: 'y' },
      { k: 'Verkaufswert', v: fmt2(sellVal) + ' C', color: 'g' },
    ],
  }
}))

const citizenRows = computed(() => [
  { k: 'Angesiedelt', v: `${playerStore.ownedCitizens}/${playerStore.maxCitizens}`, color: 'w' },
  { k: 'Frei', v: playerStore.idleCitizens, color: 'g' },
])

const netWorthRows = computed(() => [
  { k: 'Cookies',    v: fmt2(playerStore.nwCookies) + ' C', color: 'w' },
  { k: 'Ressourcen', v: fmt2(playerStore.nwResources) + ' C', color: 'w' },
  { k: 'Upgrades',   v: fmt2(playerStore.nwUpgrades) + ' C', color: 'w' },
  { k: 'Summe',      v: fmtBig(playerStore.netWorth), color: 'g' },
])

// ── Pan + zoom ───────────────────────────────────────────
const panX = ref(0)
const panY = ref(0)
const zoom = ref(0.85)
const MAX_ZOOM = 1.3

const canvasStyle = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}))

let dragging = false
let lastX = 0, lastY = 0
function panStart(e) {
  if (e.button !== 0) return
  if (e.target.closest?.('.bf-root')) return  // building handles its own pointer events
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

function resetView() { panX.value = 0; panY.value = 0; zoom.value = 0.85 }

function onWheel(e) {
  e.preventDefault()
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  zoom.value = Math.min(MAX_ZOOM, Math.max(0.4, zoom.value * factor))
}

// ── Hotkeys ──────────────────────────────────────────────
async function sellAll() {
  for (const r of RESOURCES) {
    const amt = playerStore[r.key] ?? 0
    if (amt <= 0) continue
    try {
      const res = await trade(playerStore.steamId, 'SELL', r.name, amt)
      playerStore.updateFromDto(res.user ?? res)
    } catch {}
  }
}

function onKeydown(e) {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return
  if (e.key === 'Escape' && (dialog.value || detailBuilding.value)) {
    e.preventDefault()
    dialog.value = null
    detailBuilding.value = null
    return
  }
  const label = keyLabelFromEvent(e)
  const binding = hotkeyState.bindings.find(b => b.key === label)
  if (!binding) return
  e.preventDefault()
  const actions = {
    center:      () => resetView(),
    zoomreset:   () => resetView(),
    bake:        () => { dialog.value = 'bake' },
    market:      () => { dialog.value = 'market' },
    upgrades:    () => { dialog.value = 'upgrades' },
    leaderboard: () => { dialog.value = 'leaderboard' },
    settings:    () => { dialog.value = 'settings' },
    recipes:     () => { dialog.value = 'bake' },
    badges:      () => { dialog.value = 'badges' },
    sellall:     () => sellAll(),
  }
  actions[binding.id]?.()
}

// ── Harvest ──────────────────────────────────────────────
const harvestIntervals = {}
const harvestDelays = {}
const HARVEST_DELAY_MS = 300
const HARVEST_MS = 900

async function doHarvest(buildingId, name) {
  try {
    const beforeRes     = playerStore[name.toLowerCase()] ?? 0
    const beforeCookies = playerStore.cookies ?? 0
    const updated = await harvestResource(playerStore.steamId, name)
    playerStore.updateFromDto(updated)
    const gainedRes     = (playerStore[name.toLowerCase()] ?? 0) - beforeRes
    const gainedCookies = (playerStore.cookies ?? 0) - beforeCookies

    // Storage full -> overflow gets auto-sold into cookies instead of stored.
    // Back-compute the harvested amount from the payout so a number still shows.
    let amount = gainedRes
    if (amount <= 0 && gainedCookies > 0) {
      const price = marketStore.priceOf(name)
      if (price > 0) amount = gainedCookies / (price * (1 - sellFeeRate.value))
    }
    if (amount > 0) {
      const off = buildingOffsets[buildingId] || { x: 0, y: 0 }
      spawnFarmNumber(amount, BASE[buildingId].x + off.x + BASE[buildingId].w / 2, BASE[buildingId].y + off.y + 60, { icon: RESOURCE_ICON[name] })
    }
  } catch {}
}
function startHarvest(buildingId, name) {
  if (harvestIntervals[name] || harvestDelays[name]) return
  harvestDelays[name] = setTimeout(() => {
    harvestDelays[name] = null
    doHarvest(buildingId, name)
    harvestIntervals[name] = setInterval(() => doHarvest(buildingId, name), HARVEST_MS)
  }, HARVEST_DELAY_MS)
}
function stopHarvest(name) {
  clearTimeout(harvestDelays[name]); harvestDelays[name] = null
  clearInterval(harvestIntervals[name]); harvestIntervals[name] = null
}

async function loadUpgrades() {
  try { upgrades.value = await getUpgrades(playerStore.steamId) } catch {}
}

let upgradeTimer = null
let passiveTimer  = null

function spawnPassiveNumbers() {
  for (const b of playerStore.ownedBuildings) {
    if (!b.passiveRatePerTick || b.passiveRatePerTick <= 0) continue
    const off = buildingOffsets[b.id] || { x: 0, y: 0 }
    const base = BASE[b.id]
    if (!base) continue
    const resource = BUILDING_INFO[b.id]?.resource
    spawnFarmNumber(b.passiveRatePerTick, base.x + off.x + base.w / 2, base.y + off.y + 60, { icon: RESOURCE_ICON[resource] })
  }
}

onMounted(() => {
  bakeStore.start(playerStore.steamId)
  loadUpgrades()
  getConfig().then(cfg => { sellFeeRate.value = cfg.sellFeeRate ?? 0.08 }).catch(() => {})
  upgradeTimer  = setInterval(loadUpgrades, 10000)
  passiveTimer  = setInterval(spawnPassiveNumbers, 5000)
  viewEl.value.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  clearInterval(upgradeTimer)
  clearInterval(passiveTimer)
  Object.values(harvestDelays).forEach(clearTimeout)
  Object.values(harvestIntervals).forEach(clearInterval)
  viewEl.value?.removeEventListener('wheel', onWheel)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.hof-root {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #6a8535;
  cursor: grab;
}

.grass-world {
  position: absolute;
  left: -5000px; top: -5000px;
  width: 11280px; height: 10800px;
  background: #7d9a41;
  background-image:
    repeating-linear-gradient(0deg, rgba(0,0,0,.05) 0 32px, rgba(255,255,255,.04) 32px 64px),
    repeating-linear-gradient(90deg, rgba(0,0,0,.05) 0 32px, rgba(255,255,255,.04) 32px 64px);
}
.hof-root:active { cursor: grabbing; }

.hof-canvas {
  position: absolute;
  left: 50%; top: 50%;
  width: 1280px; height: 800px;
  margin-left: -640px; margin-top: -400px;
  transform-origin: center center;
  overflow: visible;
}


/* ── HUD (absolutely positioned in root, stays fixed during pan) ─ */
.hud {
  position: absolute; top: 0; left: 0; right: 0; min-height: 76px;
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 8px 14px;
  background: var(--px-wood); border-bottom: 4px solid var(--px-ink);
  box-shadow: inset 0 3px 0 var(--px-wood-lt);
  z-index: 50;
}
.hud-chips { display: flex; flex-wrap: nowrap; gap: 6px; }
.hud-chip {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 5px 10px; background: var(--px-wood2); border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #57402a;
}
.hud-chip-clickable { cursor: pointer; }
.hud-chip-cookie { padding: 5px 12px; background: var(--px-wood-lt); box-shadow: inset 2px 2px 0 #7d5a30; }
.hud-chip-val   { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-paper-txt); }
.hud-chip-cookie .hud-chip-val { font-size: 13px; color: var(--px-gold-txt); }
.hud-chip-label { font-size: 11px; color: #b49b76; line-height: 1; }

.hud-networth-wrap { flex: 0 0 auto; margin-left: 16px; max-width: 160px; }
.hud-networth {
  display: flex; flex-direction: column; padding: 5px 12px; cursor: pointer;
  background: var(--px-green-panel); border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 var(--px-green-panel2);
}
.hud-networth-label { font-size: 11px; color: #a9c48f; line-height: 1; }
.hud-networth-val   { font-family: 'Silkscreen', monospace; font-size: 15px; color: var(--px-green-txt); }

.hud-actions { margin-left: auto; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.hud-dev-btn { background: #3a1a5a !important; color: #c8a8ff !important; border-color: #7a50b0 !important; font-size: 9px !important; }
.hud-avatar {
  flex-shrink: 0;
  width: 52px; height: 52px; background: var(--px-wood3); border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #6d5133; display: flex; align-items: center; justify-content: center;
  cursor: pointer;
}

/* ── Idle wanderers ──────────────────────────────────── */
.idle-wanderer { position: absolute; z-index: 12; }

/* Idle buildings get a subtle desaturated overlay */
.building-idle { filter: saturate(0.5) brightness(0.85); }

/* ── Build slot ──────────────────────────────────────── */
.build-slot {
  position: absolute; left: 900px; top: 716px; width: 180px; height: 66px;
  border: 4px dashed var(--px-paper-txt); background: rgba(16,11,7,.28);
  display: flex; align-items: center; justify-content: center; gap: 10px; z-index: 12;
  cursor: pointer; transition: background 0.15s;
}
.build-slot:hover { background: rgba(16,11,7,.45); }
.build-plus  { font-family: 'Silkscreen', monospace; font-size: 22px; color: var(--px-paper-txt); }
.build-title { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-paper-txt); }
.build-cost  { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); margin-top: 3px; }

/* ── Camera / ticker (outside canvas, fixed overlays) ── */
.cam-controls { position: absolute; left: 16px; bottom: 16px; display: flex; align-items: center; gap: 8px; z-index: 50; }
.cam-center {
  width: 48px; height: 48px; background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 #d3bb8f; display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 18px; color: var(--px-ink-txt); cursor: pointer;
}
.cam-hint { font-family: 'Silkscreen', monospace; font-size: 8px; padding: 3px 5px; background: var(--px-wood2); color: var(--px-muted); border: 2px solid var(--px-ink); }
.zoom-readout { position: absolute; right: 16px; bottom: 16px; font-family: 'Silkscreen', monospace; font-size: 11px; color: #fff6e0; text-shadow: 2px 2px 0 var(--px-ink); z-index: 50; }

.build-fab {
  position: absolute; right: 16px; bottom: 56px; z-index: 55;
  width: 52px; height: 52px; display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 26px; line-height: 1; padding: 0;
  background: var(--px-orange); border: 4px solid var(--px-ink); color: var(--px-cream);
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt), 0 4px 0 rgba(0,0,0,.45);
  cursor: pointer;
}
.build-fab:hover { filter: brightness(1.08); }

.ticker { position: absolute; left: 50%; bottom: 16px; transform: translateX(-50%); display: flex; gap: 8px; z-index: 50; }
.ticker-pop {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 9px 12px; background: var(--px-wood);
  border: 3px solid var(--px-ink); color: var(--px-paper-txt); box-shadow: inset 2px 2px 0 #55402a;
  display: flex; align-items: center; white-space: nowrap;
}
.ticker-pop.ticker-idle { background: #5a1a1a; border-color: #8b3333; color: #ff8888; }
.ticker-cap {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 9px 12px; background: var(--px-green-panel);
  border: 3px solid var(--px-ink); color: var(--px-green-txt); box-shadow: inset 2px 2px 0 var(--px-green-panel2);
  white-space: nowrap;
}

/* ── Mobile bottom nav ───────────────────────────────── */
.mobile-nav {
  display: none;
  position: fixed; left: 0; right: 0; bottom: 0; height: 78px; z-index: 60;
  background: var(--px-wood); border-top: 4px solid var(--px-ink);
}
.mobile-nav-item {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 6px; border: none; border-right: 2px solid var(--px-ink); background: none; cursor: pointer;
}
.mobile-nav-item span { font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-paper-txt); }

@media (max-width: 860px) {
  .hof-canvas { transform: none !important; left: 0; top: 76px; margin: 0; width: 100%; height: calc(100% - 76px - 78px); overflow: auto; }
  .cam-controls, .zoom-readout, .ticker { display: none; }
  .hud { position: fixed; height: auto; flex-wrap: wrap; padding: 8px 10px; gap: 6px; z-index: 61; }
  .hud-desktop-only { display: none; }
  .hud-chip-label { display: none; }
  .hud-chip { padding: 4px 6px; }
  .mobile-nav { display: flex; }
  .build-fab { bottom: 94px; }
}
</style>
