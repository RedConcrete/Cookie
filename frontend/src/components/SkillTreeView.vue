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
        <div ref="canvasEl" class="stv-canvas" :style="canvasStyle">
          <svg class="stv-edges" :width="WORLD_SIZE" :height="WORLD_SIZE">
            <line
              v-for="e in edgeLines" :key="e.id"
              :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2"
              :class="['stv-edge', `stv-edge-${e.state}`, { 'stv-edge-search-path': searching && searchPathEdgeKeys.has(e.key) }]"
            />
          </svg>

          <PixelInfoPopover
            v-for="n in tree.nodes" :key="n.id"
            :rows="nodeRows(n)" :title="nodeName(n)" :note="nodeDesc(n)" :width="290"
            :style="nodeWrapStyle(n)"
          >
            <!-- Nur waehrend eines laufenden Requests disabled -- ein dauerhaft disabled-
                 Button (z.B. fuer gesperrte Knoten) feuert in Chrome keine mousemove-Events
                 mehr, was das Kamera-Pan (siehe unten) beim Drueberziehen einfrieren liess. -->
            <button
              class="stv-node"
              :class="[
                `stv-node-${nodeState(n)}`,
                `stv-branch-${(n.branch || '').toLowerCase()}`,
                { 'stv-node-nopoints': nodeState(n) === 'allocatable' && tree.skillPoints < 1 },
                { 'stv-node-keystone': n.nodeTier === 'KEYSTONE' },
                { 'stv-node-notable': n.nodeTier === 'NOTABLE' },
                { 'stv-node-search-match': searching && matchesSearch(n) },
                { 'stv-node-search-dim': searching && !matchesSearch(n) },
                { 'stv-node-respec-marked': respecMode && respecSelection.includes(n.id) },
                { 'stv-node-respec-dim': respecMode && (n.root || nodeState(n) !== 'allocated') },
              ]"
              :disabled="allocating || respeccing"
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
          <button class="stv-cam-btn" @click="resetView"><ShortcutSlot /><PixelIcon name="zentrieren" :size="18" /></button>
          <div class="stv-cam-hint">{{ t('skillTreeView.centerHint') }}</div>
        </div>

        <!-- ── Minimap: kompletter Baum verkleinert + Sichtfenster-Rechteck, Klick springt
             die Kamera dorthin ── -->
        <div
          ref="minimapEl" class="stv-minimap"
          :style="{ width: minimapSize + 'px', height: minimapSize + 'px' }"
          @mousedown.stop @click="onMinimapClick"
        >
          <svg class="stv-minimap-edges" :width="minimapSize" :height="minimapSize">
            <line
              v-for="e in minimapEdgeLines" :key="e.id"
              :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2"
              :class="['stv-minimap-edge', `stv-minimap-edge-${e.state}`]"
            />
          </svg>
          <div
            v-for="n in tree.nodes" :key="n.id"
            class="stv-minimap-dot"
            :class="{ 'stv-minimap-dot-allocated': n.allocated, 'stv-minimap-dot-root': n.root }"
            :style="minimapDotStyle(n)"
          />
          <div v-if="minimapViewportRect" class="stv-minimap-viewport" :style="minimapViewportStyle" />
          <div
            class="stv-minimap-resize"
            @pointerdown="minimapResizeStart" @pointermove="minimapResizeMove" @pointerup="minimapResizeEnd"
          ></div>
        </div>

        <!-- ── Respec-Modus: oben links, gleicher Stil wie der Admin-Editor
             (px-btn-Toggle analog zu SkillTreeAdminDialog's "Verbinden"-Modus) ── -->
        <div class="stv-toolbar" @mousedown.stop>
          <button
            class="px-btn"
            :class="{ 'px-btn-accent': respecMode }"
            @click="toggleRespecMode"
          ><ShortcutSlot />{{ t('skillTreeView.respecModeLabel') }}</button>
          <div v-if="respecMode" class="stv-hint">{{ t('skillTreeView.respecModeHint') }}</div>
        </div>

        <!-- ── Suche: zentriert wie im Admin-Editor (.sta-search-box), oberhalb des
             Skillpunkte-Badges statt daneben ── -->
        <div class="stv-search-box" @mousedown.stop>
          <input
            type="text" class="stv-search-input"
            v-model="searchQuery"
            :placeholder="t('skillTreeView.searchPlaceholder')"
          />
          <button v-if="searchQuery" class="stv-search-clear" @click="searchQuery = ''">&times;</button>
        </div>

        <!-- ── Respec-Bestätigungsleiste: erscheint sobald mindestens ein Knoten markiert ist ── -->
        <div v-if="respecMode && respecSelection.length > 0" class="stv-respec-bar px-panel" @mousedown.stop>
          <span class="stv-respec-count">{{ t('skillTreeView.respecSelectedCount', { count: respecSelection.length }) }}</span>
          <span class="stv-respec-cost">{{ fmt(respecTotalCost) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" /></span>
          <button class="px-btn" @click="respecSelection = []">{{ t('skillTreeView.respecClearLabel') }}</button>
          <button
            class="px-btn px-btn-accent"
            :disabled="!canAffordRespec || respeccing"
            @click="confirmRespecBatch"
          >{{ t('skillTreeView.respecConfirmLabel') }}</button>
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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { buySkillPoint, allocateSkillNode, deallocateSkillNode, getPlayer } from '../services/api.js'
import { fmt2 as fmt } from '../utils/formatNumber.js'
import { resourceLabel } from './buildings/buildingInfo.js'
import { useCameraControls } from '../composables/useCameraControls.js'
import { useGamepadCursor } from '../composables/useGamepadCursor.js'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelInfoPopover from './pixel/PixelInfoPopover.vue'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const { t, locale } = useI18n()
const playerStore = usePlayerStore()

const loading   = ref(true)
const buying    = ref(false)
const allocating = ref(false)
const respeccing  = ref(false)
const notice      = ref('')
const noticeError = ref(false)
const buyDialogOpen = ref(false)

// Respec-Modus: Toggle wie "Verbinden" im Admin-Editor (SkillTreeAdminDialog.vue) --
// solange aktiv, waehlt ein Klick auf einen freigeschalteten Knoten ihn nur zur
// Mehrfachauswahl an/ab, statt normal zu allozieren. Bestaetigen respecced alle markierten
// auf einmal (nacheinander, siehe confirmRespecBatch).
const respecMode = ref(false)
const respecSelection = ref([])

// Suche: durchsucht immer beide Sprachen gleichzeitig (nicht nur die aktive UI-Sprache), damit
// z.B. "Milch" auch im EN-Modus gefunden wird. Filtert nicht durch Ausblenden (würde Kanten/
// Baum-Struktur optisch zerreißen), sondern per Highlight/Dim-Klasse auf den Knoten selbst.
const searchQuery = ref('')
const searching = computed(() => searchQuery.value.trim().length > 0)
function matchesSearch(n) {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return true
  return [n.nameDe, n.nameEn, n.descriptionDe, n.descriptionEn].some(s => (s || '').toLowerCase().includes(q))
}

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
  if (n.icon) return n.icon
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
  if (state === 'allocated') {
    rows.push({ k: t('skillTreeView.statusLabel'), v: t('skillTreeView.statusAllocated'), color: 'g' })
    if (respecMode.value && !n.root) {
      const hint = respecSelection.value.includes(n.id) ? 'skillTreeView.respecUnmarkHint' : 'skillTreeView.respecMarkHint'
      rows.push({ k: t('skillTreeView.respecLabel'), v: t(hint), color: 'y' })
    }
    return rows
  }
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
  if (respecMode.value) { toggleRespecSelect(n); return }
  if (n.root) { buyDialogOpen.value = true; return }
  allocate(n)
}

function toggleRespecMode() {
  respecMode.value = !respecMode.value
  respecSelection.value = []
}

function toggleRespecSelect(n) {
  if (n.root || nodeState(n) !== 'allocated') return
  const idx = respecSelection.value.indexOf(n.id)
  if (idx === -1) respecSelection.value = [...respecSelection.value, n.id]
  else respecSelection.value = respecSelection.value.filter(id => id !== n.id)
}

const respecTotalCost = computed(() => respecSelection.value.length * (tree.value.respecCostFlat ?? 0))
const canAffordRespec = computed(() => playerStore.cookies >= respecTotalCost.value)

// Bestaetigen respecced mehrere Knoten in EINEM Rutsch, der Backend-Endpunkt entfernt aber nur
// einen pro Aufruf (siehe docs/plans/2026-08-10-done-skillbaum-respec.md -- bewusste
// Design-Entscheidung, kein Bulk-Endpoint). Reihenfolge ist deshalb wichtig: ein Elternknoten
// darf erst entfernt werden, NACHDEM alle mit-ausgewaehlten Kindknoten schon weg sind, sonst
// meldet der Server "wuerde X abschneiden", obwohl die komplette Auswahl zusammen eigentlich
// gueltig waere. Tiefe ab root (nur ueber aktuell allozierte Knoten) bestimmt die Reihenfolge:
// tiefste zuerst.
function nodeDepthsFromRoot() {
  const byId = Object.fromEntries(tree.value.nodes.map(n => [n.id, n]))
  const depth = { root: 0 }
  const queue = ['root']
  while (queue.length) {
    const cur = queue.shift()
    for (const e of (tree.value.edges || [])) {
      const other = e.from === cur ? e.to : (e.to === cur ? e.from : null)
      if (!other || depth[other] !== undefined) continue
      const otherNode = byId[other]
      if (!otherNode || !otherNode.allocated) continue
      depth[other] = depth[cur] + 1
      queue.push(other)
    }
  }
  return depth
}

async function confirmRespecBatch() {
  if (respeccing.value || respecSelection.value.length === 0 || !canAffordRespec.value) return
  respeccing.value = true
  const depths = nodeDepthsFromRoot()
  const order = [...respecSelection.value].sort((a, b) => (depths[b] ?? -1) - (depths[a] ?? -1))
  try {
    for (const nodeId of order) {
      playerStore.skillTree = await deallocateSkillNode(playerStore.steamId, nodeId)
      respecSelection.value = respecSelection.value.filter(id => id !== nodeId)
    }
    const dto = await getPlayer(playerStore.steamId)
    playerStore.updateFromDto(dto)
    respecMode.value = false
  } catch (e) {
    flash(e.message, true)
  } finally {
    respeccing.value = false
  }
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
      key: edgeKey(e.from, e.to),
      x1: CENTER + from.x, y1: CENTER + from.y,
      x2: CENTER + to.x,   y2: CENTER + to.y,
      state,
    }
  }).filter(Boolean)
})

function edgeKey(a, b) { return a < b ? `${a}|${b}` : `${b}|${a}` }

// Sucht-Wegweiser: kuerzester Pfad (Multi-Source-BFS ab ALLEN bereits allozierten Knoten
// gleichzeitig, nicht nur ab root) zu jedem Suchtreffer, der noch nicht alloziiert ist --
// zeigt "von wo ich schon bin, so komme ich am schnellsten dorthin" statt nur den Treffer
// selbst hervorzuheben (Playtest-Feedback).
const searchPathEdgeKeys = computed(() => {
  if (!searching.value) return new Set()
  const edges = tree.value.edges || []
  const adjacency = {}
  for (const e of edges) {
    const k = edgeKey(e.from, e.to)
    ;(adjacency[e.from] ??= []).push({ to: e.to, key: k })
    ;(adjacency[e.to] ??= []).push({ to: e.from, key: k })
  }
  const sources = tree.value.nodes.filter(n => n.allocated).map(n => n.id)
  if (!sources.length) return new Set()

  const prevEdgeKey = {}
  const visited = new Set(sources)
  const queue = [...sources]
  while (queue.length) {
    const cur = queue.shift()
    for (const { to, key } of (adjacency[cur] || [])) {
      if (visited.has(to)) continue
      visited.add(to)
      prevEdgeKey[to] = { from: cur, key }
      queue.push(to)
    }
  }

  const result = new Set()
  for (const n of tree.value.nodes) {
    if (n.allocated || !matchesSearch(n)) continue
    let cur = n.id
    while (prevEdgeKey[cur]) {
      result.add(prevEdgeKey[cur].key)
      cur = prevEdgeKey[cur].from
    }
  }
  return result
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

// Mindestens ein halbes Viewport an Bewegungsspielraum in jede Richtung, auch wenn der
// komplette Baum (weit rausgezoomt) kleiner als der Viewport ist -- sonst waere maxX/maxY
// hier 0 und die Kamera bliebe starr auf der Mitte fixiert, egal wie stark man zieht.
function clampPan() {
  const vw = viewEl.value?.clientWidth  ?? WORLD_SIZE
  const vh = viewEl.value?.clientHeight ?? WORLD_SIZE
  const maxX = Math.max(vw / 2, (WORLD_SIZE * zoom.value - vw) / 2)
  const maxY = Math.max(vh / 2, (WORLD_SIZE * zoom.value - vh) / 2)
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

// ── Minimap (unten rechts) ───────────────────────────────
// Eigene, von .stv-canvas' 1500px-Layout-Groesse unabhaengige Skalierung -- bildet den
// tatsaechlichen Node-Koordinatenbereich (+ Puffer) auf eine feste Minimap-Box ab.
const canvasEl = ref(null)
const minimapEl = ref(null)
const MINIMAP_MIN = 100
const MINIMAP_MAX = 320
const MINIMAP_SIZE_KEY = 'cookie-skilltree-minimap-size'

function loadMinimapSize() {
  const stored = Number(localStorage.getItem(MINIMAP_SIZE_KEY))
  return stored >= MINIMAP_MIN && stored <= MINIMAP_MAX ? stored : 300
}

const minimapSize = ref(loadMinimapSize())
watch(minimapSize, (v) => localStorage.setItem(MINIMAP_SIZE_KEY, String(v)))

// Zentriert auf den Welt-Ursprung (root sitzt fix bei x=0,y=0, siehe
// SkillTreeService#buildNodes -- der ganze Baum ist radial um root aufgebaut), nicht auf
// den Bounding-Box-Mittelpunkt aller Nodes -- der waere bei einem asymmetrisch gewachsenen
// Baum (mehr Branches/Tiefe in eine Richtung als in die andere) verschoben und liesse root
// sichtbar aus der Mitte rutschen statt wie in der Hauptansicht (siehe resetView) zentriert
// zu bleiben.
const treeBounds = computed(() => {
  const nodes = tree.value.nodes || []
  const pad = 60
  if (!nodes.length) return { half: 800 }
  const half = Math.max(1, ...nodes.map(n => Math.max(Math.abs(n.x), Math.abs(n.y)))) + pad
  return { half }
})

const minimapScale = computed(() => minimapSize.value / (treeBounds.value.half * 2))

function toMinimapPx(qx, qy) {
  return {
    x: minimapSize.value / 2 + qx * minimapScale.value,
    y: minimapSize.value / 2 + qy * minimapScale.value,
  }
}

function fromMinimapPx(px, py) {
  return {
    x: (px - minimapSize.value / 2) / minimapScale.value,
    y: (py - minimapSize.value / 2) / minimapScale.value,
  }
}

function minimapDotStyle(n) {
  const p = toMinimapPx(n.x, n.y)
  return { left: p.x + 'px', top: p.y + 'px' }
}

const minimapEdgeLines = computed(() => {
  const byId = Object.fromEntries(tree.value.nodes.map(n => [n.id, n]))
  return (tree.value.edges || []).map((e, i) => {
    const from = byId[e.from]
    const to = byId[e.to]
    if (!from || !to) return null
    let state = 'locked'
    if (from.allocated && to.allocated) state = 'active'
    else if (from.allocated || to.allocated) state = 'available'
    const a = toMinimapPx(from.x, from.y)
    const b = toMinimapPx(to.x, to.y)
    return { id: `${e.from}-${e.to}-${i}`, x1: a.x, y1: a.y, x2: b.x, y2: b.y, state }
  }).filter(Boolean)
})

// Liest die tatsaechliche (bereits transformierte) Bildschirmgeometrie von Viewport +
// Canvas aus, statt die CSS-Transform-Matrix (translate+scale, Pivot bei 50%/50% der
// Canvas-eigenen 1500px-Box) von Hand nachzurechnen -- robust auch wenn Canvas-Groesse
// oder WORLD_SIZE sich mal aendern, ohne beide Stellen synchron halten zu muessen.
const minimapViewportRect = computed(() => {
  // Reagiert auf Kamera-Bewegung -- die eigentliche Neuberechnung liest live DOM-Rects.
  void panX.value; void panY.value; void zoom.value
  const vp = viewEl.value?.getBoundingClientRect()
  const cv = canvasEl.value?.getBoundingClientRect()
  if (!vp || !cv || !cv.width) return null
  const effScale = cv.width / 1500
  function screenToQ(sx, sy) {
    return { x: (sx - cv.left) / effScale - CENTER, y: (sy - cv.top) / effScale - CENTER }
  }
  const qTopLeft = screenToQ(vp.left, vp.top)
  const qBotRight = screenToQ(vp.right, vp.bottom)
  const a = toMinimapPx(qTopLeft.x, qTopLeft.y)
  const b = toMinimapPx(qBotRight.x, qBotRight.y)
  return {
    left: Math.min(a.x, b.x), top: Math.min(a.y, b.y),
    width: Math.abs(b.x - a.x), height: Math.abs(b.y - a.y),
  }
})

const minimapViewportStyle = computed(() => {
  const r = minimapViewportRect.value
  if (!r) return {}
  return { left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px' }
})

let justResized = false
function onMinimapClick(e) {
  if (justResized) { justResized = false; return }
  const rect = minimapEl.value?.getBoundingClientRect()
  const cv = canvasEl.value?.getBoundingClientRect()
  const vp = viewEl.value?.getBoundingClientRect()
  if (!rect || !cv || !vp || !cv.width) return
  const q = fromMinimapPx(e.clientX - rect.left, e.clientY - rect.top)
  const effScale = cv.width / 1500
  const currentScreenX = cv.left + (q.x + CENTER) * effScale
  const currentScreenY = cv.top + (q.y + CENTER) * effScale
  panX.value += (vp.left + vp.width / 2) - currentScreenX
  panY.value += (vp.top + vp.height / 2) - currentScreenY
  clampPan()
}

// Resize-Griff sitzt oben links an der Box (unten rechts bleibt fix am Bildschirmrand
// verankert) -- Ziehen nach oben-links vergroessert, nach unten-rechts verkleinert.
// Pointer Capture statt window-mousemove-Listener: der Ziehweg fuehrt oft ueber
// gesperrte (disabled) Skill-Nodes im Canvas darunter, und Chrome feuert ueber
// disabled-Buttons GAR KEINE mousemove-Events mehr (siehe Kommentar bei .stv-node
// oben zum selben Chrome-Verhalten beim Kamera-Pan) -- Pointer Capture bindet alle
// Folge-Events an den Griff selbst, unabhaengig davon was optisch drunter liegt.
let resizing = false
let resizeStartX = 0
let resizeStartSize = 0
function minimapResizeStart(e) {
  e.preventDefault()
  resizing = true
  justResized = false
  resizeStartX = e.clientX
  resizeStartSize = minimapSize.value
  e.target.setPointerCapture(e.pointerId)
}
function minimapResizeMove(e) {
  if (!resizing) return
  const delta = resizeStartX - e.clientX
  minimapSize.value = Math.min(MINIMAP_MAX, Math.max(MINIMAP_MIN, resizeStartSize + delta))
}
function minimapResizeEnd() {
  resizing = false
  justResized = true
}

// ── Gamepad: mirrors FarmGridView.vue's stick-pan/D-pad-zoom/A-click, but
// self-contained -- this view has its own independent camera (panX/panY/zoom
// above), so it polls the gamepad itself rather than reaching into
// FarmGridView's private gamepadIndex. Shares useGamepadCursor.js's mode/
// cursor state though, so R3 (toggled centrally in FarmGridView.vue) and the
// on-screen cursor overlay behave identically here.
const { cameraSpeed, zoomSpeed } = useCameraControls()
const gamepadCursor = useGamepadCursor()
const GAMEPAD_DEADZONE = 0.2
const DPAD_UP = 12, DPAD_DOWN = 13
const INTERACT_GAMEPAD_BUTTON = 0
let interactButtonPressed = false
let gpFrame = null
let lastGpTime = 0

function gamepadTick(now) {
  const dt = Math.min(0.05, (now - lastGpTime) / 1000)
  lastGpTime = now
  const pad = (navigator.getGamepads?.() ?? []).find(p => p)
  if (pad) {
    if (gamepadCursor.mode.value === 'fixed') {
      const ax = pad.axes[0] ?? 0
      const ay = pad.axes[1] ?? 0
      const mag = Math.hypot(ax, ay)
      if (mag >= GAMEPAD_DEADZONE) {
        const scale = Math.min(1, (mag - GAMEPAD_DEADZONE) / (1 - GAMEPAD_DEADZONE)) / mag
        panX.value += -ax * scale * cameraSpeed.value * dt
        panY.value += -ay * scale * cameraSpeed.value * dt
        clampPan()
      }
      let zoomDir = 0
      if (pad.buttons[DPAD_UP]?.pressed) zoomDir = -1
      else if (pad.buttons[DPAD_DOWN]?.pressed) zoomDir = 1
      if (zoomDir !== 0) {
        zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * (1 + zoomDir * zoomSpeed.value * dt)))
        clampPan()
      }
    }
    // A clicks whatever's at the crosshair/cursor point -- .stv-node buttons
    // (fixed mode, tree centered under the crosshair) and toolbar/search/
    // buy-panel buttons (free mode) are all plain @click handlers.
    const interactPressed = !!pad.buttons[INTERACT_GAMEPAD_BUTTON]?.pressed
    if (interactPressed && !interactButtonPressed) {
      const { x, y } = gamepadCursor.currentPoint.value
      document.elementFromPoint(x, y)?.click()
    }
    interactButtonPressed = interactPressed
  }
  gpFrame = requestAnimationFrame(gamepadTick)
}

onMounted(async () => {
  loading.value = true
  try { await playerStore.loadSkillTree() }
  finally { loading.value = false }
  lastGpTime = performance.now()
  gpFrame = requestAnimationFrame(gamepadTick)
})
onUnmounted(() => {
  if (gpFrame != null) cancelAnimationFrame(gpFrame)
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
  position: absolute; top: 64px; left: 50%; transform: translateX(-50%); z-index: 20;
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
  width: 50px; height: 50px;
  display: flex; align-items: center; justify-content: center;
  background: var(--px-wood3); border: 3px solid var(--px-ink); color: var(--px-cream);
  cursor: pointer; box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.stv-cam-btn:hover { filter: brightness(1.1); }
.stv-cam-hint {
  font-size: 9px; font-family: 'Silkscreen', monospace; color: var(--px-tan);
  background: rgba(16,11,7,.6); padding: 2px 6px; white-space: nowrap;
}

.stv-minimap {
  position: absolute; right: 14px; bottom: 14px; z-index: 20;
  background: var(--px-wood); border: 3px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
  cursor: pointer; overflow: hidden;
}
.stv-minimap-edges { position: absolute; left: 0; top: 0; pointer-events: none; overflow: visible; }
.stv-minimap-edge { stroke-width: 1; }
.stv-minimap-edge-locked    { stroke: var(--px-wood2); opacity: .6; }
.stv-minimap-edge-available { stroke: var(--px-orange-lt); opacity: .8; }
.stv-minimap-edge-active    { stroke: var(--px-gold); }
.stv-minimap-dot {
  position: absolute; width: 4px; height: 4px; margin: -2px;
  background: var(--px-wood2); border-radius: 50%; pointer-events: none;
}
.stv-minimap-dot-allocated { background: var(--px-gold); }
.stv-minimap-dot-root { width: 6px; height: 6px; margin: -3px; background: var(--px-cream); }
.stv-minimap-viewport {
  position: absolute; border: 2px solid var(--px-gold-lt);
  background: rgba(255,241,169,.12); pointer-events: none;
}
.stv-minimap-resize {
  position: absolute; left: 0; top: 0; width: 12px; height: 12px;
  cursor: nwse-resize;
  border-left: 3px solid var(--px-tan); border-top: 3px solid var(--px-tan);
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

/* Sucht-Wegweiser: kuerzester Pfad vom naechsten allozierten Knoten zum Treffer -- nach den
   Status-Regeln oben, damit es bei gleicher Selektor-Spezifitaet immer gewinnt (auch auf einer
   "locked"-Kante). Gleiches Gruen-Puls-Muster wie stv-search-glow bei den Knoten, nur auf
   stroke/drop-shadow statt box-shadow (SVG-Linie statt Div). */
.stv-edge-search-path {
  animation: stv-edge-search-glow 1.3s ease-in-out infinite;
}
@keyframes stv-edge-search-glow {
  0%, 100% { stroke: var(--px-green); stroke-width: 5; opacity: 1; filter: drop-shadow(0 0 3px var(--px-green-lt)); }
  50%      { stroke: var(--px-green-lt); stroke-width: 7; opacity: 1; filter: drop-shadow(0 0 6px var(--px-green)); }
}

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
  cursor: default;
}
.stv-node-allocatable {
  background: var(--px-gold);
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt), 0 0 0 3px var(--px-gold);
}
.stv-node-allocatable:hover { filter: brightness(1.15); }
.stv-node-nopoints { filter: saturate(0.7) brightness(0.85); cursor: default; }
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
}
/* Puls-Glow nur wenn tatsaechlich alloziert -- vorher pulsierte JEDER Keystone auf der Karte
   (auch weit entfernte, noch gesperrte), das sah nach "hier ist was Wichtiges freigeschaltet"
   aus obwohl nichts freigeschaltet war (Spieler-Feedback). Rahmenstaerke/-farbe oben bleibt
   erhalten, damit Keystones auf der Karte weiterhin als solche erkennbar sind, auch gesperrt. */
.stv-node-keystone.stv-node-allocated {
  animation: stv-keystone-glow 2s ease-in-out infinite;
}
@keyframes stv-keystone-glow {
  0%, 100% { box-shadow: 0 0 0 3px var(--px-red), 0 0 10px 2px var(--px-red-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
  50%      { box-shadow: 0 0 0 3px var(--px-gold), 0 0 18px 4px var(--px-gold-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
}

/* Suche: dimmt Nicht-Treffer wie ein "locked"-Knoten (bestehende Optik-Sprache statt einer
   vierten neuen), hebt Treffer stattdessen mit einem gruenen Puls hervor -- Filter kommt NACH
   den Tier-Klassen, damit auch ein gedimmter Keystone erkennbar gedimmt bleibt. */
.stv-node-search-dim {
  filter: saturate(0.4) brightness(0.55);
}
.stv-node-search-match {
  animation: stv-search-glow 1.3s ease-in-out infinite;
}
@keyframes stv-search-glow {
  0%, 100% { box-shadow: 0 0 0 3px var(--px-green), 0 0 8px 2px var(--px-green-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
  50%      { box-shadow: 0 0 0 3px var(--px-green-lt), 0 0 14px 4px var(--px-green), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
}

/* Respec-Modus: nicht-allozierte Knoten sind waehrenddessen nicht anklickbar (siehe
   :disabled-Bindung) und werden entsprechend gedimmt. Fuer Markierung bewusst rot statt dem
   gruenen Such-Glow (visuell klar getrennt: "wird entfernt" vs. "Treffer"). */
.stv-node-respec-dim {
  filter: saturate(0.3) brightness(0.5);
  cursor: default;
}
.stv-node-respec-marked {
  animation: stv-respec-glow 1.1s ease-in-out infinite;
}
@keyframes stv-respec-glow {
  0%, 100% { box-shadow: 0 0 0 3px var(--px-red), 0 0 8px 2px var(--px-red-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
  50%      { box-shadow: 0 0 0 3px var(--px-red-lt), 0 0 14px 4px var(--px-red), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
}

.stv-toolbar {
  position: absolute; top: 14px; left: 14px; z-index: 20;
  display: flex; align-items: center; gap: 10px;
}
.stv-toolbar .px-btn,
.stv-respec-bar .px-btn,
.stv-buy-panel-body .px-btn {
  min-height: 50px;
  display: flex; align-items: center; justify-content: center;
  box-sizing: border-box;
}
.stv-hint {
  font-size: 11px; font-family: 'Silkscreen', monospace; color: var(--px-tan);
  background: rgba(16,11,7,.6); padding: 4px 8px;
}

.stv-respec-bar {
  position: absolute; bottom: 64px; left: 50%; transform: translateX(-50%); z-index: 25;
  display: flex; align-items: center; gap: 12px;
  padding: 10px 14px;
}
.stv-respec-count { font-size: 12px; color: var(--px-tan-ink); }
.stv-respec-cost { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-ink); display: flex; align-items: center; }

.stv-search-box {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%); z-index: 20;
  display: flex; align-items: center; gap: 4px;
  padding: 6px 8px;
  background: var(--px-wood3); border: 3px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.stv-search-input {
  width: 130px; font-family: 'Silkscreen', monospace; font-size: 12px;
  background: var(--px-cream); color: var(--px-ink); border: 2px solid var(--px-ink);
  padding: 4px 6px; outline: none;
}
.stv-search-input::placeholder { color: var(--px-wood); opacity: 0.7; }
.stv-search-clear {
  width: 20px; height: 20px; flex: none;
  font-family: 'Silkscreen', monospace; font-size: 12px; line-height: 1;
  background: var(--px-wood2); color: var(--px-cream); border: 2px solid var(--px-ink);
  cursor: pointer;
}
.stv-search-clear:hover { background: var(--px-red-dk); }
</style>
