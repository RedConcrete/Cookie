<template>
  <div
    ref="viewEl"
    class="hof-root"
    @mousedown="panStart"
    @mousemove="onMouseMove"
    @mouseup="panEnd"
    @mouseleave="onMouseLeaveRoot"
  >

    <!-- ══ HUD (fixed overlay, outside canvas so it stays put while panning) ══ -->
    <div class="hud">
      <div class="hud-chips">
        <PixelInfoPopover :rows="cookieRows" :title="t('farmGridView.cookiesTitle')" side="below-left" :width="272" :z="95">
          <div class="hud-chip hud-chip-cookie">
            <PixelIcon name="cookie" :size="24" />
            <div class="hud-chip-val">{{ fmt(playerStore.cookies) }}</div>
            <div class="hud-chip-label">{{ t('farmGridView.cookiesTitle') }}</div>
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

        <PixelInfoPopover :rows="citizenRows" :title="t('farmGridView.residentsTitle')" side="below-left" :width="240" :z="95">
          <div class="hud-chip hud-chip-clickable" @click="dialog = 'citizens'">
            <PixelIcon name="einw" :size="24" />
            <div class="hud-chip-val">{{ playerStore.ownedCitizens }}/{{ playerStore.maxCitizens }}</div>
            <div class="hud-chip-label">{{ t('farmGridView.residentsLabel') }}</div>
          </div>
        </PixelInfoPopover>
      </div>

      <PixelInfoPopover :rows="netWorthRows" :title="t('farmGridView.netWorthTitle')" side="below-right" :width="276" :z="95" class="hud-networth-wrap">
        <div class="hud-networth" @click="dialog = 'networth'" :title="t('farmGridView.netWorthHint')">
          <ShortcutSlot :key-label="actionHotkeysEnabled ? actionKeyLabel(actionKeys.networth) : ''" />
          <div class="hud-networth-label">{{ t('farmGridView.netWorthTitle') }}</div>
          <div class="hud-networth-val">{{ fmtBig(playerStore.netWorth) }}</div>
        </div>
      </PixelInfoPopover>

      <div class="hud-actions">
        <button
          v-if="playerStore.skillTree.skillPoints > 0"
          class="px-btn hud-skillpoint-star"
          @click="dialog = 'skilltree'"
        ><PixelIcon name="stern" :size="16" /></button>
        <div class="hud-menu-wrap" ref="hudMenuRef">
          <button class="px-btn" @click="menuOpen = !menuOpen" :title="t('farmGridView.menuTitle')">
            &#9776;
            <ShortcutSlot />
          </button>
          <div v-if="menuOpen" class="hud-menu">
            <button class="hud-menu-item" @click="selectMenu('profile')">
              <span class="hud-menu-avatar">
                <img v-if="hudAvatarSrc" :src="hudAvatarSrc" alt="" @error="hudAvatarError = true" />
                <PixelIcon v-else name="einw" :size="14" />
              </span>
              {{ t('farmGridView.profileTitle') }}
              <ShortcutSlot />
            </button>
            <button class="hud-menu-item" @click="selectMenu('skilltree')">{{ t('farmGridView.skillTreeLabel') }}<ShortcutSlot /></button>
            <button class="hud-menu-item" @click="selectMenu('stats')">{{ t('farmGridView.statsLabel') }}<ShortcutSlot /></button>
            <button class="hud-menu-item" @click="selectMenu('leaderboard')">{{ t('farmGridView.leaderboardLabel') }}<ShortcutSlot /></button>
            <button class="hud-menu-item" @click="selectMenu('settings')">{{ t('farmGridView.settingsTitle') }}<ShortcutSlot /></button>
            <button v-if="isDev" class="hud-menu-item hud-menu-dev" @click="selectDevReset">{{ t('farmGridView.devResetLabel') }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ══ World canvas (pannable + zoomable, no overflow clip) ══ -->
    <div ref="canvasEl" class="hof-canvas" :style="canvasStyle">

      <!-- Grass world — base tile, bounded to WORLD -->
      <div class="grass-world"></div>

      <!-- Scattered ground decoration: small tufts/stones -->
      <img v-for="(d, i) in groundDecos" :key="'deco'+i" class="ground-decor" :src="d.src"
           :style="{ left: d.x + 'px', top: d.y + 'px', width: d.size + 'px', height: d.size + 'px' }" alt="" />

      <!-- ══ Buildings ══ -->
      <BuildingFrame
        v-for="b in buildings" :key="b.id"
        :building-id="b.id"
        :base="BASE[b.id]" :title="b.title" :icon="b.icon" :rate="b.overlayRate" :workers="b.workers"
        :rows="buildingRows(b)" :note="b.note" :side="b.side" :scene-height="SCENE_H[b.id]"
        :drop-ok="(pos) => dropOk(b.id, pos)"
        :zoom="zoom"
        :offset="buildingOffsets[b.id]"
        :ref="(el) => onBuildingFrameRef(b.id, el)"
        :harvest-blocked="isProductionBlocked(b)"
        :pending-amount="livePending(b)"
        :storage-capacity="b.storageCapacity"
        :resource-icon="RESOURCE_ICON[b.resource]"
        :class="{ 'building-idle': (playerStore.workersIdle || isProductionBlocked(b)) && isBuildingOwned(b.id) }"
        @open="onOpenBuilding(b)"
        @harvest-start="b.resource && !isStorageFull && startHarvest(b.id, b.resource)"
        @harvest-stop="b.resource && stopHarvest(b.resource)"
        @moved="onBuildingMoved(b.id, $event)"
        @collect="onCollectBuilding(b)"
      >
        <component
          :is="b.comp" :workers="b.workers" :idle="isProductionBlocked(b)"
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
      <button class="cam-center" :title="t('farmGridView.centerTitle')" @click="resetView"><PixelIcon name="zentrieren" :size="18" /><ShortcutSlot key-label="␣" /></button>
    </div>
    <div class="zoom-readout">{{ Math.round(zoom * 100) }} %</div>

    <!-- Floating build button (bottom-right) -->
    <button class="build-fab" :title="t('farmGridView.buildTitle')" @click="dialog = 'buildshop'">+<ShortcutSlot /></button>

    <!-- Mobile bottom nav -->
    <div class="mobile-nav">
      <button v-for="n in mobileNavItems" :key="n.labelKey" class="mobile-nav-item" @click="n.action">
        <PixelIcon :name="n.icon" :size="20" />
        <span>{{ t(n.labelKey) }}</span>
        <ShortcutSlot />
      </button>
    </div>

    <!-- Dialogs -->
    <MarketDialog       v-if="dialog === 'market'"      @close="dialog = null" />
    <BakeDialog         v-if="dialog === 'bake'"        @close="dialog = null" />
    <BuildingDetailDialog v-if="detailBuilding"         :building="detailBuilding" @close="detailBuilding = null" />
    <SkillTreeDialog    v-if="dialog === 'skilltree'"    @close="dialog = null" />
    <StatsDialog        v-if="dialog === 'stats'"        @close="dialog = null" />
    <LeaderboardDialog  v-if="dialog === 'leaderboard'" @close="dialog = null" />
    <SettingsDialog     v-if="dialog === 'settings'"    @close="dialog = null" />
    <PlayerProfileDialog v-if="dialog === 'profile'"   :steamId="playerStore.steamId" @close="dialog = null" />
    <NetWorthDialog     v-if="dialog === 'networth'"    :steamId="playerStore.steamId" @close="dialog = null" />
    <OrdenDialog        v-if="dialog === 'badges'"      :steamId="playerStore.steamId" :isAdmin="isDev" @close="dialog = null" />
    <BuildShopDialog    v-if="dialog === 'buildshop'"   @close="dialog = null" />
    <CitizenDialog      v-if="dialog === 'citizens'"    @close="dialog = null" />
    <RathausDialog      v-if="dialog === 'rathaus'"     @close="dialog = null" />
    <LagerDialog        v-if="dialog === 'lager'"       @close="dialog = null" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { useBakeStore } from '../stores/bake.js'
import { harvestResource, trade, adminResetPlayer, avatarSrc, collectBuilding } from '../services/api.js'
import { fmt, fmt2, fmtBig } from '../utils/formatNumber.js'
import { spawnFarmNumber } from '../composables/useFarmNumbers.js'
import { useCameraControls } from '../composables/useCameraControls.js'
import { useActionHotkeys } from '../composables/useActionHotkeys.js'
import FarmNumbers from '../components/FarmNumbers.vue'
import PixelIcon from '../components/pixel/PixelIcon.vue'
import PixelInfoPopover from '../components/pixel/PixelInfoPopover.vue'
import ShortcutSlot from '../components/pixel/ShortcutSlot.vue'
import BuildingFrame from '../components/buildings/BuildingFrame.vue'
import TravelingWorker from '../components/buildings/TravelingWorker.vue'
import { BASE, HGT, SCENE_H, WORLD, dropOk as dropOkLayout, snapOffset } from '../components/buildings/farmLayout.js'
import { BUILDING_INFO, RESOURCE_ICON, buildingTitle, resourceLabel } from '../components/buildings/buildingInfo.js'
import grassTile from '../assets/tiles/grass.png'
const grassBg = `url(${grassTile})`

import decoTuftDark from '../assets/tiles/decor/deco_tuft_dark.png'
import decoTuftLight from '../assets/tiles/decor/deco_tuft_light.png'

// Camera can roam 2.5x the building-layout box; the grass itself is rendered
// 3x as big (centered around the same point) so its edge is never reachable.
const CAMERA_RANGE = { w: WORLD.w * 2.5, h: WORLD.h * 2.5 }
const GRASS_SIZE    = { w: WORLD.w * 3, h: WORLD.h * 3 }

// Deterministic PRNG (mulberry32) so the scattered ground decoration is
// stable across reloads instead of jumping around every render.
function mulberry32(seed) {
  return function () {
    seed |= 0; seed = (seed + 0x6D2B79F5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const DECOS = [decoTuftDark, decoTuftLight]

function rectsOverlapPad(a, b, pad) {
  return a.x < b.x + b.w + pad && b.x - pad < a.x + a.w &&
         a.y < b.y + b.h + pad && b.y - pad < a.y + a.h
}
const buildingRects = Object.entries(BASE).map(([id, b]) => ({ ...b, h: SCENE_H[id] || 120 }))
function freeSpot(x, y, w, h) {
  return !buildingRects.some(r => rectsOverlapPad({ x, y, w, h }, r, 12))
}

// Scatter tufts across the *entire* grass area (not just the building box)
// so they cover the ground everywhere the camera can actually roam.
const rand = mulberry32(20260804)
const marginX = (GRASS_SIZE.w - WORLD.w) / 2
const marginY = (GRASS_SIZE.h - WORLD.h) / 2
const groundDecos = []
for (let i = 0; i < 320; i++) {
  const x = -marginX + rand() * GRASS_SIZE.w
  const y = -marginY + rand() * GRASS_SIZE.h
  const size = 24 + rand() * 16
  if (!freeSpot(x, y, size, size)) continue
  groundDecos.push({ x, y, size, src: DECOS[Math.floor(rand() * DECOS.length)] })
}

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
import SkillTreeDialog from '../components/SkillTreeDialog.vue'
import StatsDialog from '../components/StatsDialog.vue'
import LeaderboardDialog from '../components/LeaderboardDialog.vue'
import SettingsDialog from '../components/SettingsDialog.vue'
import PlayerProfileDialog from '../components/PlayerProfileDialog.vue'
import NetWorthDialog from '../components/NetWorthDialog.vue'
import OrdenDialog from '../components/OrdenDialog.vue'
import BuildShopDialog from '../components/BuildShopDialog.vue'
import CitizenDialog from '../components/CitizenDialog.vue'
import RathausDialog from '../components/RathausDialog.vue'
import LagerDialog from '../components/LagerDialog.vue'

const { t } = useI18n()
const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const bakeStore   = useBakeStore()
const isDev = playerStore.steamId === 'DEV_PLAYER_001'

// Avatar: falls back to the placeholder icon if the cached image ever fails
// to load, instead of showing a broken image forever.
const hudAvatarError = ref(false)
const hudAvatarSrc = computed(() => hudAvatarError.value ? null : avatarSrc(playerStore.avatarUrl))
watch(() => playerStore.avatarUrl, () => { hudAvatarError.value = false })

const dialog = ref(null)
const detailBuilding = ref(null)
const menuOpen = ref(false)
const hudMenuRef = ref(null)

function selectMenu(name) {
  dialog.value = name
  menuOpen.value = false
}
function selectDevReset() {
  devReset()
  menuOpen.value = false
}
function onDocumentClick(e) {
  if (menuOpen.value && hudMenuRef.value && !hudMenuRef.value.contains(e.target)) menuOpen.value = false
}
onMounted(() => document.addEventListener('mousedown', onDocumentClick))
onUnmounted(() => document.removeEventListener('mousedown', onDocumentClick))

const viewEl   = ref(null)
const canvasEl = ref(null)

// Track each building's current drag offset for collision detection + number spawning
// Persisted per player so moved buildings stay put across reloads.
const OFFSETS_KEY = `cookieBuildingOffsets_${playerStore.steamId}`
function loadOffsets() {
  try { return JSON.parse(localStorage.getItem(OFFSETS_KEY)) ?? {} } catch { return {} }
}
const savedOffsets = loadOffsets()
const buildingOffsets = reactive(
  Object.fromEntries(Object.keys(BASE).map(id => {
    const raw = savedOffsets[id] ?? { x: 0, y: 0 }
    return [id, snapOffset(BASE[id], raw)]
  }))
)
function onBuildingMoved(id, offset) {
  buildingOffsets[id] = offset
  localStorage.setItem(OFFSETS_KEY, JSON.stringify(buildingOffsets))
}

// Actual rendered DOM nodes per building (keyed by id) -- used to nudge the camera to a
// pixel-exact center on Rathaus in resetView(), since BASE/HGT are only approximate
// (BuildingFrame's real height includes its label bar, which HGT doesn't precisely track).
const buildingFrameEls = {}
function onBuildingFrameRef(id, el) {
  buildingFrameEls[id] = el ? (el.$el ?? el) : null
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
      return {
        id, comp: SCENE_COMP[id], ...BUILDING_INFO[id], title: buildingTitle(id, t), workers: owned.workers ?? 0,
        pendingAmount: owned.pendingAmount ?? 0, storageCapacity: owned.storageCapacity ?? 0,
        passiveRatePerSec: owned.passiveRatePerSec ?? 0, lastSettledAtEpochMs: owned.lastSettledAtEpochMs ?? 0,
      }
    })
    .filter(Boolean)
)

// Client-side ticker for the collect badge's fill bar -- purely local extrapolation from the
// last server-settled snapshot (owned.pendingAmount/lastSettledAtEpochMs), no extra polling.
const liveNow = ref(Date.now())
let liveNowTimer = null

function livePending(b) {
  if (!b.storageCapacity) return 0
  if (playerStore.workersIdle || isStorageFull.value) return b.pendingAmount
  const elapsedSeconds = Math.max(0, (liveNow.value - (b.lastSettledAtEpochMs || liveNow.value)) / 1000)
  return Math.min(b.storageCapacity, b.pendingAmount + b.passiveRatePerSec * elapsedSeconds)
}

// Verhindert ueberlappende Collect-Requests auf dasselbe Gebaeude (schnelles Mehrfachklicken) --
// ohne das laesst sich der serverseitige Lost-Update/Cooldown-Fix (siehe docs/ROADMAP.md)
// clientseitig durch parallele Requests umgehen bzw. fuehrt zu verwirrenden 409/400-Antworten.
const collectingBuildingIds = reactive(new Set())

async function onCollectBuilding(b) {
  if (collectingBuildingIds.has(b.id)) return
  collectingBuildingIds.add(b.id)
  const key = b.resource?.toLowerCase()
  const before = key ? (playerStore[key] ?? 0) : 0
  try {
    const updated = await collectBuilding(playerStore.steamId, b.id)
    playerStore.updateFromDto(updated)
    await playerStore.loadBuildings()
    if (key) {
      const gained = (playerStore[key] ?? 0) - before
      if (gained > 0) {
        const off = buildingOffsets[b.id] || { x: 0, y: 0 }
        const base = BASE[b.id]
        if (base) spawnFarmNumber(gained, base.x + off.x + base.w / 2, base.y + off.y + 60, { icon: RESOURCE_ICON[b.resource] })
      }
    }
  } catch {
    // Cooldown-Ablehnung (400) oder Optimistic-Lock-Konflikt (409) -- einfach ignorieren,
    // naechster Klick nach Ablauf des Cooldowns geht wieder durch.
  } finally {
    collectingBuildingIds.delete(b.id)
  }
}

// Only owned/built buildings should block a placement -- buildingOffsets carries an entry
// for all 10 building slots regardless of ownership (see its init above), so an unbuilt
// building's default BASE position used to act as an invisible obstacle: nothing was
// rendered there, but dropOk still rejected drops onto it.
function dropOk(id, pos) {
  const ownedOffsets = Object.fromEntries(
    Object.entries(buildingOffsets).filter(([bid]) => isBuildingOwned(bid))
  )
  return dropOkLayout(id, pos, ownedOffsets)
}

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
  { anim:'wander',  dur:9,  delay:0,   legDur:0.45, hat:'#6dba79', skin:'#fff1a9', torso:'#534664', dx:40,  dy:150 },
  { anim:'wander2', dur:11, delay:1.5, legDur:0.55, hat:'#b74132', skin:'#ebb85b', torso:'#764032', dx:100, dy:168 },
  { anim:'wander',  dur:13, delay:3,   legDur:0.6,  hat:'#56642e', skin:'#fff1a9', torso:'#a15c34', dx:160, dy:156 },
  { anim:'wander2', dur:10, delay:2,   legDur:0.5,  hat:'#a15c34', skin:'#ebb85b', torso:'#402e2b', dx:90,  dy:180 },
  { anim:'wander',  dur:8,  delay:4,   legDur:0.4,  hat:'#6f6e72', skin:'#fff1a9', torso:'#402e2b', dx:150, dy:176 },
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
// Shared warehouse cap across all 6 resources -- no auto-sell of overflow anymore
// (2026-08-07), so once this is true, hover-harvest/passive production simply stops
// granting anything until the player frees up space (sell, upgrade Lager).
const isStorageFull = computed(() => totalResources.value >= playerStore.totalResourceCap)
function isProductionBlocked(b) { return Boolean(b.resource) && isStorageFull.value }

const mobileNavItems = [
  { labelKey: 'farmGridView.navHome',        icon: 'haus',  action: () => { dialog.value = null; resetView() } },
  { labelKey: 'farmGridView.navMarket',      icon: 'stand', action: () => { dialog.value = 'market' } },
  { labelKey: 'farmGridView.navBake',        icon: 'ofen',  action: () => { dialog.value = 'bake' } },
  { labelKey: 'farmGridView.navShop',        icon: 'shop',  action: () => { dialog.value = 'skilltree' } },
  { labelKey: 'farmGridView.navLeaderboard', icon: 'pokal', action: () => { dialog.value = 'leaderboard' } },
]

function onOpenBuilding(b) {
  if (b.id === 'ofen')    dialog.value = 'bake'
  else if (b.id === 'markt')   dialog.value = 'market'
  else if (b.id === 'rathaus') dialog.value = 'rathaus'
  else if (b.id === 'lager')   dialog.value = 'lager'
  else detailBuilding.value = b
}

// ── HUD data ─────────────────────────────────────────────
const cookieRows = computed(() => [
  { k: t('farmGridView.rowStock'), v: fmt(playerStore.cookies) + ' C', color: 'w' },
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
    name: r.name, icon: r.icon, label: resourceLabel(r.name, t),
    val: fmt(amount),
    rows: [
      { k: t('farmGridView.rowStock'), v: fmt(amount), color: 'w' },
      { k: t('farmGridView.rowMarketPrice'), v: fmt2(price) + ' C', color: 'y' },
      { k: t('farmGridView.rowSellValue'), v: fmt2(sellVal) + ' C', color: 'g' },
    ],
  }
}))

const citizenRows = computed(() => [
  { k: t('farmGridView.rowSettled'), v: `${playerStore.ownedCitizens}/${playerStore.maxCitizens}`, color: 'w' },
  { k: t('farmGridView.rowFree'), v: playerStore.idleCitizens, color: 'g' },
])

const netWorthRows = computed(() => [
  { k: t('farmGridView.rowCookies'),   v: fmt2(playerStore.nwCookies) + ' C', color: 'w' },
  { k: t('farmGridView.rowResources'), v: fmt2(playerStore.nwResources) + ' C', color: 'w' },
  { k: t('farmGridView.rowSkillTree'), v: fmt2(playerStore.nwSkillTreeValue) + ' C', color: 'w' },
  { k: t('farmGridView.rowTotal'),     v: fmtBig(playerStore.netWorth), color: 'g' },
])

// ── Pan + zoom ───────────────────────────────────────────
const panX = ref(0)
const panY = ref(0)
const zoom = ref(0.85)
const MIN_ZOOM = 0.4
const MAX_ZOOM = 1.3

const canvasStyle = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}))

// Camera can't pan past the edge of WORLD — once a world edge is flush with
// the viewport edge, dragging further is clamped (no panning into the void).
function clampPan() {
  const vw = viewEl.value?.clientWidth  ?? CAMERA_RANGE.w
  const vh = viewEl.value?.clientHeight ?? CAMERA_RANGE.h
  const maxX = Math.max(0, (CAMERA_RANGE.w * zoom.value - vw) / 2)
  const maxY = Math.max(0, (CAMERA_RANGE.h * zoom.value - vh) / 2)
  panX.value = Math.min(maxX, Math.max(-maxX, panX.value))
  panY.value = Math.min(maxY, Math.max(-maxY, panY.value))
}

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
  clampPan()
}
function panEnd() { dragging = false }

// AoE-style edge scrolling: cursor parked near a viewport edge pans the camera
// that way, same speed/feel as WASD (see camTick below). Tracked separately from
// drag-panning above -- onMouseMove runs both every move, dragging wins while held.
function onMouseMove(e) {
  panMove(e)
  edgePointer.x = e.clientX
  edgePointer.y = e.clientY
  edgePointer.active = true
  if (camActiveEdge()) startCamLoop()
}
function onMouseLeaveRoot() {
  panEnd()
  edgePointer.active = false
}

// Centers the camera on the Rathaus (accounting for it having been dragged elsewhere)
// instead of the world rect's geometric center, which sits in empty grass below the
// building cluster (see WORLD's comment in farmLayout.js) and isn't where a player
// actually wants to land when hitting "center". Two-step: an approximate jump from
// BASE/HGT (so there's no visible mid-flight jump once the DOM measurement lands),
// then a pixel-exact nudge from the Rathaus's real rendered bounding box -- HGT is
// only an approximation of BuildingFrame's true height (label bar included), so the
// approximate step alone lands a few px off.
function resetView() {
  zoom.value = 0.85
  const off = buildingOffsets.rathaus || { x: 0, y: 0 }
  const rathausCenterX = BASE.rathaus.x + off.x + BASE.rathaus.w / 2
  const rathausCenterY = BASE.rathaus.y + off.y + HGT.rathaus / 2
  panX.value = -zoom.value * (rathausCenterX - WORLD.w / 2)
  panY.value = -zoom.value * (rathausCenterY - WORLD.h / 2)
  clampPan()
  nextTick(centerExactlyOnRathaus)
}

function centerExactlyOnRathaus() {
  const el = buildingFrameEls.rathaus
  if (!el || !viewEl.value) return
  const target = el.getBoundingClientRect()
  const view = viewEl.value.getBoundingClientRect()
  panX.value += (view.left + view.width / 2) - (target.left + target.width / 2)
  panY.value += (view.top + view.height / 2) - (target.top + target.height / 2)
  clampPan()
}

function onWheel(e) {
  e.preventDefault()
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * factor))
  clampPan()
}

// ── Camera movement (continuous while held, same feel as mouse-drag) ──
// Keybinds + speed are user-configurable in SettingsDialog, shared via useCameraControls.
const { cameraKeys, cameraSpeed } = useCameraControls()
const { actionKeys, actionGamepadButtons, enabled: actionHotkeysEnabled, keyLabel: actionKeyLabel } = useActionHotkeys()
const camPressed = { up: false, down: false, left: false, right: false }
let camFrame = null
let lastFrameTime = 0

function camActive() { return camPressed.up || camPressed.down || camPressed.left || camPressed.right || camActiveEdge() }

// ── Edge scrolling (Age of Empires-style): mouse parked at the viewport edge
// pans the camera that direction. Suspended while drag-panning, and while a
// dialog/detail panel is open (same guards as the gamepad stick below).
const EDGE_MARGIN = 4
const edgePointer = { x: -1, y: -1, active: false }

function camActiveEdge() {
  if (!edgePointer.active || dragging || dialog.value || detailBuilding.value) return false
  const rect = viewEl.value?.getBoundingClientRect()
  if (!rect) return false
  const { x, y } = edgePointer
  if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) return false
  return x - rect.left < EDGE_MARGIN || rect.right - x < EDGE_MARGIN ||
         y - rect.top < EDGE_MARGIN || rect.bottom - y < EDGE_MARGIN
}

function readEdgePan() {
  if (!camActiveEdge()) return { dx: 0, dy: 0 }
  const rect = viewEl.value.getBoundingClientRect()
  const { x, y } = edgePointer
  let dx = 0, dy = 0
  // Same sign convention as camPressed.left/up above (dx/dy += 1).
  if (x - rect.left < EDGE_MARGIN) dx = 1
  else if (rect.right - x < EDGE_MARGIN) dx = -1
  if (y - rect.top < EDGE_MARGIN) dy = 1
  else if (rect.bottom - y < EDGE_MARGIN) dy = -1
  return { dx, dy }
}

// ── Steam Deck / gamepad: left stick pans the camera, same as WASD ──
// (part of the Steam Deck controller roadmap item — see docs/ROADMAP.md §4).
// Browsers only report a gamepad via 'gamepadconnected' once it's actually
// been touched, which conveniently matches "wenn ein Controller genutzt wird".
const GAMEPAD_DEADZONE = 0.2
const gamepadIndex = ref(null)
function onGamepadConnected(e) { gamepadIndex.value = e.gamepad.index; startCamLoop() }
function onGamepadDisconnected(e) { if (gamepadIndex.value === e.gamepad.index) gamepadIndex.value = null }

function readGamepadPan() {
  if (gamepadIndex.value === null || dialog.value || detailBuilding.value) return { dx: 0, dy: 0 }
  const pad = navigator.getGamepads?.()[gamepadIndex.value]
  if (!pad) return { dx: 0, dy: 0 }
  const ax = pad.axes[0] ?? 0
  const ay = pad.axes[1] ?? 0
  const mag = Math.hypot(ax, ay)
  if (mag < GAMEPAD_DEADZONE) return { dx: 0, dy: 0 }
  // Rescale past the deadzone so movement starts at 0 right at the edge
  // instead of jumping straight to ~deadzone speed, keeps small tilts controllable.
  const scale = Math.min(1, (mag - GAMEPAD_DEADZONE) / (1 - GAMEPAD_DEADZONE)) / mag
  // Stick axes: left/up are negative -- same sign convention as camPressed.left/up below (dx/dy += 1).
  return { dx: -ax * scale, dy: -ay * scale }
}

// D-pad up/down zooms the camera (Steam Deck has no scroll wheel) -- standard
// mapping puts the D-pad at buttons 12-15, same indices SettingsDialog shows
// as '↑'/'↓' via GAMEPAD_BUTTON_LABELS, but hardcoded here (not user-rebindable)
// since it mirrors the mouse wheel rather than being a discrete UI action.
const DPAD_UP = 12, DPAD_DOWN = 13
const ZOOM_SPEED = 0.8 // multiplicative rate per second, same feel as onWheel's 1.1/0.9 step

function readGamepadZoom() {
  if (gamepadIndex.value === null || dialog.value || detailBuilding.value) return 0
  const pad = navigator.getGamepads?.()[gamepadIndex.value]
  if (!pad) return 0
  if (pad.buttons[DPAD_UP]?.pressed) return -1   // up = zoom out
  if (pad.buttons[DPAD_DOWN]?.pressed) return 1  // down = zoom in
  return 0
}

// ── Gamepad button shortcuts (rebindable in Settings, e.g. open Net Worth) ──
// Tracks each action's previous pressed-state so a held button only fires once.
const gamepadActionPressed = {}
function triggerAction(action) {
  if (!actionHotkeysEnabled.value) return
  if (dialog.value || detailBuilding.value) return
  if (action === 'networth') dialog.value = 'networth'
}
function readGamepadActions() {
  if (gamepadIndex.value === null) return
  const pad = navigator.getGamepads?.()[gamepadIndex.value]
  if (!pad) return
  for (const action of Object.keys(actionGamepadButtons)) {
    const btnIndex = actionGamepadButtons[action]
    const pressed = btnIndex != null && !!pad.buttons[btnIndex]?.pressed
    if (pressed && !gamepadActionPressed[action]) triggerAction(action)
    gamepadActionPressed[action] = pressed
  }
}

function camTick(now) {
  const dt = Math.min(0.05, (now - lastFrameTime) / 1000)
  lastFrameTime = now
  let dx = 0, dy = 0
  if (camPressed.left)  dx += 1
  if (camPressed.right) dx -= 1
  if (camPressed.up)    dy += 1
  if (camPressed.down)  dy -= 1

  const edge = readEdgePan()
  dx += edge.dx
  dy += edge.dy

  if (dx !== 0 && dy !== 0) { dx *= Math.SQRT1_2; dy *= Math.SQRT1_2 }

  const stick = readGamepadPan()
  dx += stick.dx
  dy += stick.dy

  readGamepadActions()

  if (dx !== 0 || dy !== 0) {
    panX.value += dx * cameraSpeed.value * dt
    panY.value += dy * cameraSpeed.value * dt
    clampPan()
  }

  const zoomDir = readGamepadZoom()
  if (zoomDir !== 0) {
    zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * (1 + zoomDir * ZOOM_SPEED * dt)))
    clampPan()
  }
  if (camActive() || gamepadIndex.value !== null) camFrame = requestAnimationFrame(camTick)
  else camFrame = null
}
function startCamLoop() {
  if (camFrame != null) return
  lastFrameTime = performance.now()
  camFrame = requestAnimationFrame(camTick)
}
function stopCamKeys() {
  camPressed.up = camPressed.down = camPressed.left = camPressed.right = false
  edgePointer.active = false
}
// Release held keys if a dialog opens or the window loses focus, so keys
// stuck "down" (e.g. Alt-Tab while holding a movement key) don't pan forever.
watch([dialog, detailBuilding], () => stopCamKeys())

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
  if (e.key === ' ') {
    e.preventDefault()
    resetView()
    return
  }
  if (dialog.value || detailBuilding.value) return
  const k = e.key.toLowerCase()
  const action = Object.keys(actionKeys).find(a => actionKeys[a] === k)
  if (action) {
    e.preventDefault()
    triggerAction(action)
    return
  }
  const dir = Object.keys(cameraKeys).find(d => cameraKeys[d] === k)
  if (dir) {
    camPressed[dir] = true
    startCamLoop()
  }
}
function onKeyup(e) {
  const k = e.key.toLowerCase()
  const dir = Object.keys(cameraKeys).find(d => cameraKeys[d] === k)
  if (dir) camPressed[dir] = false
}

// ── Harvest ──────────────────────────────────────────────
// Hovering used to hit the backend every HARVEST_MS (900ms) -- doesn't scale.
// Now the amount is predicted locally each visual tick (same formula the server
// uses, cached boostLevel + prestigeMultiplier) for instant feedback, and only
// synced to the backend every HARVEST_SYNC_MS (or on hover-stop). The backend
// never trusts a client-supplied amount: it derives the real payout itself from
// elapsed server time since the player's last accepted harvest (see
// UserService#harvest) and snaps the store back to that authoritative value on
// every sync, so short-term prediction drift can't accumulate.
const harvestIntervals = {}
const harvestSyncTimers = {}
const harvestDelays = {}
const HARVEST_DELAY_MS = 300
const HARVEST_MS = 900
const HARVEST_SYNC_MS = 3000

// Sum of allocated HARVEST_YIELD skill nodes that apply to this resource --
// mirrors SkillTreeService#getEffectTotal (a node with no targetResource is a
// generalist pick and applies to every resource, one with a targetResource only
// counts for that specific one).
function harvestBonus(resourceName) {
  return (playerStore.skillTree.nodes || [])
    .filter(n => n.allocated && n.effectType === 'HARVEST_YIELD')
    .filter(n => !n.targetResource || n.targetResource.toUpperCase() === resourceName.toUpperCase())
    .reduce((s, n) => s + n.effectValue, 0)
}

// Local prediction for one visual tick -- mirrors UserService#harvest's per-tick
// formula, scoped to a single HARVEST_MS tick instead of a full elapsed-time batch.
// No auto-sell of overflow (2026-08-07): once the shared warehouse is full, toAdd
// caps at 0 and the tick simply grants nothing -- see isStorageFull/ROADMAP.md.
function localHarvestTick(buildingId, name) {
  const predicted = (1.0 + harvestBonus(name)) * playerStore.prestigeMultiplier
  const key = name.toLowerCase()
  const cap = playerStore.totalResourceCap ?? Infinity
  const totalRes = RESOURCES.reduce((s, r) => s + (playerStore[r.key] ?? 0), 0)
  const available = Math.max(0, cap - totalRes)
  const toAdd = Math.min(predicted, available)
  if (toAdd <= 0) return
  playerStore[key] = (playerStore[key] ?? 0) + toAdd
  const off = buildingOffsets[buildingId] || { x: 0, y: 0 }
  // Hover-Ernte-Zahl bewusst immer im selben, NICHT-gelben Ton -- Gold/Gelb (#ebb85b/#fff1a9/
  // #c9c03d in der Pflicht-Palette) bleibt fuer kuenftige kritische Treffer reserviert, damit
  // der Unterschied klar erkennbar bleibt (siehe docs/ROADMAP.md). Echtes Weiss gibt es in der
  // Fruitpunch24-Palette nicht -- #aea47e ist der hellste neutrale, nicht-goldene Ton.
  spawnFarmNumber(toAdd, BASE[buildingId].x + off.x + BASE[buildingId].w / 2, BASE[buildingId].y + off.y + 60, { icon: RESOURCE_ICON[name], color: '#aea47e' })
}

// The actual, authoritative call -- backend computes the real amount from elapsed
// server time and returns the true totals, which we snap the store to (corrects
// any drift from the local prediction above).
async function syncHarvest(name) {
  try {
    const updated = await harvestResource(playerStore.steamId, name)
    playerStore.updateFromDto(updated)
  } catch {}
}

function startHarvest(buildingId, name) {
  if (harvestIntervals[name] || harvestDelays[name]) return
  harvestDelays[name] = setTimeout(() => {
    harvestDelays[name] = null
    localHarvestTick(buildingId, name)
    harvestIntervals[name] = setInterval(() => localHarvestTick(buildingId, name), HARVEST_MS)
    harvestSyncTimers[name] = setInterval(() => syncHarvest(name), HARVEST_SYNC_MS)
  }, HARVEST_DELAY_MS)
}
function stopHarvest(name) {
  clearTimeout(harvestDelays[name]); harvestDelays[name] = null
  clearInterval(harvestIntervals[name]); harvestIntervals[name] = null
  if (harvestSyncTimers[name]) {
    clearInterval(harvestSyncTimers[name])
    harvestSyncTimers[name] = null
    syncHarvest(name) // flush the un-synced tail immediately instead of waiting up to HARVEST_SYNC_MS
  }
}

onMounted(() => {
  bakeStore.start(playerStore.steamId)
  resetView()
  // Drives livePending()'s local fill-bar extrapolation only -- no network call, just
  // re-renders the badge from the last server-settled snapshot (see buildings computed).
  liveNowTimer = setInterval(() => { liveNow.value = Date.now() }, 500)
  viewEl.value.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('keyup', onKeyup)
  window.addEventListener('blur', stopCamKeys)
  window.addEventListener('gamepadconnected', onGamepadConnected)
  window.addEventListener('gamepaddisconnected', onGamepadDisconnected)
})
onUnmounted(() => {
  clearInterval(liveNowTimer)
  Object.values(harvestDelays).forEach(clearTimeout)
  Object.values(harvestIntervals).forEach(clearInterval)
  Object.values(harvestSyncTimers).forEach(clearInterval)
  viewEl.value?.removeEventListener('wheel', onWheel)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('keyup', onKeyup)
  window.removeEventListener('blur', stopCamKeys)
  window.removeEventListener('gamepadconnected', onGamepadConnected)
  window.removeEventListener('gamepaddisconnected', onGamepadDisconnected)
  if (camFrame != null) cancelAnimationFrame(camFrame)
})
</script>

<style scoped>
.hof-root {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #7e9432;
  cursor: grab;
}

.grass-world {
  position: absolute;
  left: v-bind('(-(GRASS_SIZE.w - WORLD.w) / 2) + "px"');
  top:  v-bind('(-(GRASS_SIZE.h - WORLD.h) / 2) + "px"');
  width: v-bind('GRASS_SIZE.w + "px"'); height: v-bind('GRASS_SIZE.h + "px"');
  background-color: #7e9432;
  background-image: v-bind(grassBg);
  background-size: 100px 100px;
  image-rendering: pixelated;
}
.ground-decor {
  position: absolute;
  image-rendering: pixelated;
  pointer-events: none;
}
.hof-root:active { cursor: grabbing; }

.hof-canvas {
  position: absolute;
  left: 50%; top: 50%;
  width: v-bind('WORLD.w + "px"'); height: v-bind('WORLD.h + "px"');
  margin-left: v-bind('(-WORLD.w / 2) + "px"'); margin-top: v-bind('(-WORLD.h / 2) + "px"');
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
  box-shadow: inset 2px 2px 0 #402e2b;
}
.hud-chip-clickable { cursor: pointer; }
.hud-chip-cookie { padding: 5px 12px; background: var(--px-wood-lt); box-shadow: inset -2px -2px 0 var(--px-wood2), inset 2px 2px 0 var(--px-wood3); }
.hud-chip-val   { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-paper-txt); }
.hud-chip-cookie .hud-chip-val { font-size: 13px; color: var(--px-gold-txt); }
.hud-chip-label { font-size: 11px; color: #aea47e; line-height: 1; }

.hud-networth-wrap { flex: 0 0 auto; margin-left: 16px; max-width: 160px; }
.hud-networth {
  position: relative;
  display: flex; flex-direction: column; padding: 5px 12px; cursor: pointer;
  background: var(--px-green-panel); border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 var(--px-green-panel2);
}
.hud-networth-label { font-size: 11px; color: #aea47e; line-height: 1; }
.hud-networth-val   { font-family: 'Silkscreen', monospace; font-size: 15px; color: var(--px-green-txt); }

.hud-actions { margin-left: auto; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }

/* Zeigt an, dass noch Skill-Punkte zu vergeben sind (Playtest-Feedback) -- verweist per
   Klick direkt in den Skill-Baum, statt dass Spieler das Menue durchsuchen muessen. */
.hud-skillpoint-star {
  display: flex;
  align-items: center;
  justify-content: center;
  animation: hud-star-pulse 1.4s ease-in-out infinite;
}
@keyframes hud-star-pulse {
  0%, 100% { transform: scale(1); }
  50%      { transform: scale(1.18); }
}

.hud-menu-wrap { position: relative; }
.hud-menu {
  position: absolute; top: calc(100% + 8px); right: 0;
  min-width: 190px; background: var(--px-wood); border: 3px solid var(--px-ink);
  box-shadow: 0 6px 0 rgba(0,0,0,.45);
  display: flex; flex-direction: column; padding: 4px; z-index: 60;
}
.hud-menu-item {
  position: relative;
  display: flex; align-items: center; gap: 9px;
  font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-cream);
  background: transparent; border: none; padding: 10px 10px; text-align: left; cursor: pointer;
}
.hud-menu-item:hover { background: var(--px-wood3); }
.hud-menu-dev { color: #fff1a9; border-top: 2px solid var(--px-brown2); margin-top: 2px; padding-top: 12px; }
.hud-menu-avatar {
  flex-shrink: 0; width: 22px; height: 22px; background: var(--px-wood3);
  border: 2px solid var(--px-ink); display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.hud-menu-avatar img { width: 100%; height: 100%; object-fit: cover; }

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
  position: relative;
  width: 48px; height: 48px; background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 #aea47e; display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 18px; color: var(--px-ink-txt); cursor: pointer;
}
.zoom-readout { position: absolute; right: 16px; bottom: 16px; font-family: 'Silkscreen', monospace; font-size: 11px; color: #fff1a9; text-shadow: 2px 2px 0 var(--px-ink); z-index: 50; }

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
  border: 3px solid var(--px-ink); color: var(--px-paper-txt); box-shadow: inset 2px 2px 0 #402e2b;
  display: flex; align-items: center; white-space: nowrap;
}
.ticker-pop.ticker-idle { background: #402e2b; border-color: #764032; color: #e67a84; }
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
  position: relative;
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 6px; border: none; border-right: 2px solid var(--px-ink); background: none; cursor: pointer;
}
.mobile-nav-item span { font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-paper-txt); }

@media (max-width: 860px) {
  .hof-canvas { transform: none !important; left: 0; top: 76px; margin: 0; width: 100%; height: calc(100% - 76px - 78px); overflow: auto; }
  .cam-controls, .zoom-readout, .ticker { display: none; }
  .hud { position: fixed; height: auto; flex-wrap: wrap; padding: 8px 10px; gap: 6px; z-index: 61; }
  .hud-chip-label { display: none; }
  .hud-chip { padding: 4px 6px; }
  .mobile-nav { display: flex; }
  .build-fab { bottom: 94px; }
}
</style>
