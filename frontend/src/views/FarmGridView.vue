<template>
  <div
    ref="viewEl"
    class="hof-root"
    @mousedown="panStart"
    @mousemove="panMove"
    @mouseup="panEnd"
    @mouseleave="panEnd"
  >
    <div ref="canvasEl" class="hof-canvas" :style="canvasStyle">

      <!-- ground + paths -->
      <div class="ground"></div>
      <div class="road road-h"></div>
      <div class="road road-v"></div>

      <!-- ══ HUD ══ -->
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
        </div>

        <PixelInfoPopover :rows="netWorthRows" title="NET WORTH" side="below-right" :width="276" :z="95" class="hud-networth-wrap">
          <div class="hud-networth" @click="dialog = 'networth'" title="Net Worth Verlauf">
            <div class="hud-networth-label">NET WORTH</div>
            <div class="hud-networth-val">{{ fmtBig(playerStore.netWorth) }}</div>
          </div>
        </PixelInfoPopover>

        <div class="hud-actions">
          <button class="px-btn px-btn-accent hud-desktop-only" @click="dialog = 'upgrades'">UPGRADES</button>
          <button class="px-btn hud-desktop-only" @click="dialog = 'prestige'">PRESTIGE</button>
          <button class="px-btn hud-desktop-only" @click="dialog = 'leaderboard'">RANGLISTE</button>
          <button class="px-btn" @click="dialog = 'settings'" title="Einstellungen">&#9776;</button>
          <button class="hud-avatar" @click="dialog = 'profile'" title="Profil">
            <span class="hud-avatar-icon">&#128100;</span>
          </button>
        </div>
      </div>

      <!-- ══ Buildings ══ -->
      <BuildingFrame
        v-for="b in buildings" :key="b.id"
        :base="BASE[b.id]" :title="b.title" :icon="b.icon" :rate="b.overlayRate" :workers="b.workers"
        :rows="b.rows" :note="b.note" :side="b.side" :scene-height="SCENE_H[b.id]"
        :drop-ok="(pos) => dropOk(b.id, pos)" :custom-tooltip="b.id === 'pond'"
        @open="onOpenBuilding(b)"
        @harvest-start="b.resource && startHarvest(b.id, b.resource)"
        @harvest-stop="b.resource && stopHarvest(b.resource)"
      >
        <component :is="b.comp" @harvest-start="b.resource && startHarvest(b.id, b.resource)" @harvest-stop="b.resource && stopHarvest(b.resource)" />
      </BuildingFrame>

      <!-- Idle wanderers -->
      <div class="idle-wanderer idle-wanderer-a">
        <TravelingWorker travel-anim="wander" :travel-dur="9" :leg-dur="0.45" hat="#5aa0e0" torso="#4a3f7a" />
      </div>
      <div class="idle-wanderer idle-wanderer-b">
        <TravelingWorker travel-anim="wander2" :travel-dur="11" :travel-delay="1.5" :leg-dur="0.55" hat="#b83232" skin="#e8b489" torso="#6b4f2a" />
      </div>
      <div class="idle-wanderer idle-wanderer-c">
        <TravelingWorker travel-anim="wander" :travel-dur="13" :travel-delay="3" :leg-dur="0.6" hat="#3d6b25" torso="#8a5a34" />
      </div>
      <div class="idle-label">3 IDLE</div>

      <!-- Bauplatz -->
      <div class="build-slot">
        <div class="build-plus">+</div>
        <div>
          <div class="build-title">BAUPLATZ</div>
          <div class="build-cost">500<PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" /></div>
        </div>
      </div>

      <FarmNumbers />

      <!-- Camera controls -->
      <div class="cam-controls">
        <button class="cam-center" title="Zentrieren" @click="resetView">&#8857;</button>
        <div class="cam-hint">ZENTRIEREN &middot; HOTKEY LEER</div>
      </div>
      <div class="zoom-readout">{{ Math.round(zoom * 100) }} %</div>

      <!-- Ticker -->
      <div class="ticker">
        <div class="ticker-pop">EINWOHNER 12/16 &middot; LOHN 24<PixelIcon name="cookie" :size="12" style="margin:0 5px;vertical-align:-2px" />/MIN</div>
        <div class="ticker-net">+18.4 C/S</div>
      </div>
    </div>

    <!-- Mobile bottom nav (Steam Deck / Handy) — replaces the desktop header nav -->
    <div class="mobile-nav">
      <button v-for="n in mobileNavItems" :key="n.label" class="mobile-nav-item" @click="n.action">
        <PixelIcon :name="n.icon" :size="20" />
        <span>{{ n.label }}</span>
      </button>
    </div>

    <!-- Dialogs -->
    <MarketDialog v-if="dialog === 'market'" @close="dialog = null" />
    <BakeDialog   v-if="dialog === 'bake'"   @close="dialog = null" />
    <BuildingDetailDialog v-if="detailBuilding" :building="detailBuilding" @close="detailBuilding = null" />
    <UpgradeDialog      v-if="dialog === 'upgrades'"    @close="dialog = null" />
    <PrestigeDialog     v-if="dialog === 'prestige'"    @close="dialog = null" />
    <LeaderboardDialog  v-if="dialog === 'leaderboard'" @close="dialog = null" />
    <SettingsDialog     v-if="dialog === 'settings'"    @close="dialog = null" />
    <PlayerProfileDialog v-if="dialog === 'profile'" :steamId="playerStore.steamId" @close="dialog = null" />
    <NetWorthDialog v-if="dialog === 'networth'" :steamId="playerStore.steamId" @close="dialog = null" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { useBakeStore } from '../stores/bake.js'
import { harvestResource, getUpgrades } from '../services/api.js'
import { spawnFarmNumber } from '../composables/useFarmNumbers.js'
import FarmNumbers from '../components/FarmNumbers.vue'
import PixelIcon from '../components/pixel/PixelIcon.vue'
import PixelInfoPopover from '../components/pixel/PixelInfoPopover.vue'
import BuildingFrame from '../components/buildings/BuildingFrame.vue'
import TravelingWorker from '../components/buildings/TravelingWorker.vue'
import { BASE, SCENE_H, dropOk as dropOkLayout } from '../components/buildings/farmLayout.js'
import { BUILDING_INFO, RESOURCE_LABEL } from '../components/buildings/buildingInfo.js'

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

const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const bakeStore   = useBakeStore()

const dialog = ref(null)
const detailBuilding = ref(null)
const viewEl   = ref(null)
const canvasEl = ref(null)
const upgrades = ref([])

const SCENE_COMP = {
  pond: SugarPondScene, ofen: OvenScene, rathaus: TownHallScene, markt: MarketScene,
  lager: WarehouseScene, hof: FarmScene, huhn: ChickenScene, butter: ButterScene,
  kakao: CocoaScene, kuh: CowScene,
}

const buildings = Object.keys(BUILDING_INFO).map(id => ({ id, comp: SCENE_COMP[id], ...BUILDING_INFO[id] }))

function dropOk(id, pos) { return dropOkLayout(id, pos) }

const mobileNavItems = [
  { label: 'HOF',    icon: 'haus',  action: () => { dialog.value = null; resetView() } },
  { label: 'MARKT',  icon: 'stand', action: () => { dialog.value = 'market' } },
  { label: 'BACKEN', icon: 'ofen',  action: () => { dialog.value = 'bake' } },
  { label: 'SHOP',   icon: 'shop',  action: () => { dialog.value = 'upgrades' } },
  { label: 'RANG',   icon: 'pokal', action: () => { dialog.value = 'leaderboard' } },
]

function onOpenBuilding(b) {
  if (b.id === 'ofen') dialog.value = 'bake'
  else if (b.id === 'markt') dialog.value = 'market'
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
  const sellVal = amount * price * 0.92 // net of the market fee, see MarketDialog for the exact rate
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

const netWorthRows = computed(() => [
  { k: 'Cookies',    v: fmt2(playerStore.nwCookies) + ' C', color: 'w' },
  { k: 'Ressourcen', v: fmt2(playerStore.nwResources) + ' C', color: 'w' },
  { k: 'Upgrades',   v: fmt2(playerStore.nwUpgrades) + ' C', color: 'w' },
  { k: 'Summe',      v: fmtBig(playerStore.netWorth), color: 'g' },
])

// ── Pan + zoom (canvas is a fixed 1280×800 pixel-art stage) ─
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

// ── Harvest (hover-to-collect, real API) ────────────────────
const harvestIntervals = {}
const harvestDelays = {}
const HARVEST_DELAY_MS = 300
const HARVEST_MS = 900

async function doHarvest(buildingId, name) {
  try {
    const before = playerStore[name.toLowerCase()] ?? 0
    const updated = await harvestResource(playerStore.steamId, name)
    playerStore.updateFromDto(updated)
    const gained = (playerStore[name.toLowerCase()] ?? 0) - before
    if (gained > 0) {
      spawnFarmNumber(gained, BASE[buildingId].x + BASE[buildingId].w / 2, BASE[buildingId].y + 60)
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
onMounted(() => {
  bakeStore.start(playerStore.steamId)
  loadUpgrades()
  upgradeTimer = setInterval(loadUpgrades, 10000)
  viewEl.value.addEventListener('wheel', onWheel, { passive: false })
})
onUnmounted(() => {
  clearInterval(upgradeTimer)
  Object.values(harvestDelays).forEach(clearTimeout)
  Object.values(harvestIntervals).forEach(clearInterval)
  viewEl.value?.removeEventListener('wheel', onWheel)
})
</script>

<style scoped>
.hof-root {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--px-bg);
  cursor: grab;
}
.hof-root:active { cursor: grabbing; }

.hof-canvas {
  position: absolute;
  left: 50%; top: 50%;
  width: 1280px; height: 800px;
  margin-left: -640px; margin-top: -400px;
  transform-origin: center center;
  border: 4px solid var(--px-ink);
  box-shadow: 0 10px 0 rgba(0,0,0,.5);
  overflow: hidden;
  font-family: 'Pixelify Sans', system-ui, sans-serif;
}

.ground {
  position: absolute; inset: 0;
  background: #7d9a41;
  background-image:
    repeating-linear-gradient(0deg, rgba(0,0,0,.05) 0 32px, rgba(255,255,255,.05) 32px 64px),
    repeating-linear-gradient(90deg, rgba(0,0,0,.05) 0 32px, rgba(255,255,255,.05) 32px 64px);
}
.road { position: absolute; background: #b98f57; background-image: repeating-linear-gradient(90deg, rgba(0,0,0,.07) 0 16px, rgba(255,255,255,.06) 16px 32px); }
.road-h { left: 0; right: 0; top: 392px; height: 64px; border-top: 4px solid #8d6a3d; border-bottom: 4px solid #8d6a3d; }
.road-v { top: 120px; bottom: 0; left: 608px; width: 64px; background-image: repeating-linear-gradient(0deg, rgba(0,0,0,.07) 0 16px, rgba(255,255,255,.06) 16px 32px); border-left: 4px solid #8d6a3d; border-right: 4px solid #8d6a3d; }

/* ── HUD ─────────────────────────────────────────────── */
.hud {
  position: absolute; top: 0; left: 0; right: 0; height: 76px;
  display: flex; align-items: center; gap: 10px; padding: 0 14px;
  background: var(--px-wood); border-bottom: 4px solid var(--px-ink);
  box-shadow: inset 0 3px 0 var(--px-wood-lt);
  z-index: 40;
}
.hud-chips { display: flex; gap: 6px; }
.hud-chip {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 5px 10px; background: var(--px-wood2); border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #57402a;
}
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

.hud-actions { margin-left: auto; display: flex; gap: 8px; align-items: center; }
.hud-avatar {
  width: 52px; height: 52px; background: var(--px-wood3); border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 #6d5133; display: flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.hud-avatar-icon { font-size: 22px; }

/* ── Idle wanderers ──────────────────────────────────── */
.idle-wanderer { position: absolute; z-index: 12; }
.idle-wanderer-a { left: 210px; top: 404px; }
.idle-wanderer-b { left: 700px; top: 426px; }
.idle-wanderer-c { left: 420px; top: 430px; }
.idle-label {
  position: absolute; left: 64px; top: 404px; z-index: 12;
  font-family: 'Silkscreen', monospace; font-size: 9px; padding: 2px 5px;
  background: var(--px-wood2); color: var(--px-muted); border: 2px solid var(--px-ink);
}

/* ── Build slot ──────────────────────────────────────── */
.build-slot {
  position: absolute; left: 900px; top: 716px; width: 180px; height: 66px;
  border: 4px dashed var(--px-paper-txt); background: rgba(16,11,7,.28);
  display: flex; align-items: center; justify-content: center; gap: 10px; z-index: 12;
}
.build-plus  { font-family: 'Silkscreen', monospace; font-size: 22px; color: var(--px-paper-txt); }
.build-title { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-paper-txt); }
.build-cost  { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); margin-top: 3px; }

/* ── Camera / ticker ─────────────────────────────────── */
.cam-controls { position: absolute; left: 16px; bottom: 16px; display: flex; align-items: center; gap: 8px; z-index: 20; }
.cam-center {
  width: 48px; height: 48px; background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 #d3bb8f; display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 18px; color: var(--px-ink-txt); cursor: pointer;
}
.cam-hint { font-family: 'Silkscreen', monospace; font-size: 8px; padding: 3px 5px; background: var(--px-wood2); color: var(--px-muted); border: 2px solid var(--px-ink); }
.zoom-readout { position: absolute; right: 16px; bottom: 16px; font-family: 'Silkscreen', monospace; font-size: 11px; color: #fff6e0; text-shadow: 2px 2px 0 var(--px-ink); z-index: 20; }

.ticker { position: absolute; left: 50%; bottom: 16px; transform: translateX(-50%); display: flex; gap: 8px; z-index: 20; }
.ticker-pop {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 9px 12px; background: var(--px-wood);
  border: 3px solid var(--px-ink); color: var(--px-paper-txt); box-shadow: inset 2px 2px 0 #55402a;
  display: flex; align-items: center;
}
.ticker-net {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 9px 12px; background: var(--px-green-panel);
  border: 3px solid var(--px-ink); color: var(--px-green-txt); box-shadow: inset 2px 2px 0 var(--px-green-panel2);
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

/* ── Mobile (Steam Deck / Handy): bottom-nav instead of header nav, HUD stays
   two-line, camera controls hidden, dialogs act as full-screen sheets ─────── */
@media (max-width: 860px) {
  .hof-canvas { transform: none !important; left: 0; top: 76px; margin: 0; width: 100%; height: calc(100% - 76px - 78px); border-width: 0; overflow: auto; }
  .cam-controls, .zoom-readout, .ticker { display: none; }
  .hud { position: fixed; height: auto; flex-wrap: wrap; padding: 8px 10px; gap: 6px; z-index: 61; }
  .hud-desktop-only { display: none; }
  .hud-chip-label { display: none; }
  .hud-chip { padding: 4px 6px; }
  .mobile-nav { display: flex; }
}
</style>
