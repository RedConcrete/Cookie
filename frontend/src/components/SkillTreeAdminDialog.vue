<template>
  <div class="sta-root" @wheel.stop @mousedown.stop @mousemove.stop>
    <NestedTooltip :content="t('skillTreeAdminDialog.title')" silent>
      <button class="sta-close" @click="emit('close')"><ShortcutSlot />&times;</button>
    </NestedTooltip>

    <div v-if="loading" class="sta-loading"><LoadingIndicator /></div>

    <template v-else>
      <div
        ref="viewEl"
        class="sta-viewport"
        @mousedown="panStart"
        @mousemove="panMove"
        @mouseup="panEnd"
        @mouseleave="panLeave"
        @wheel="onWheel"
      >
        <div class="sta-canvas" :style="canvasStyle">
          <svg class="sta-edges" :width="WORLD_SIZE" :height="WORLD_SIZE">
            <g v-for="e in edgeLines" :key="e.id">
              <line :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2" class="sta-edge-hit" @click="deleteEdge(e.id)" />
              <line :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2" class="sta-edge" />
            </g>
          </svg>

          <button
            v-for="n in nodes" :key="n.id"
            class="sta-node"
            :class="{
              'sta-node-root': n.root,
              'sta-node-selected': selectedId === n.id,
              'sta-node-pending': pendingFrom === n.id,
              'sta-node-search-match': searching && matchesSearch(n),
              'sta-node-search-dim': searching && !matchesSearch(n),
            }"
            :style="nodeWrapStyle(n)"
            @mousedown="nodeMouseDown($event, n)"
          >
            <PixelIcon :name="nodeIcon(n)" :size="iconSize(n)" />
          </button>
        </div>

        <div class="sta-toolbar" @mousedown.stop>
          <button
            class="px-btn"
            :class="{ 'px-btn-accent': connectMode }"
            @click="toggleConnectMode"
          ><ShortcutSlot />{{ t('skillTreeAdminDialog.connectModeLabel') }}</button>
          <button
            class="px-btn"
            :class="{ 'px-btn-accent': createMode }"
            @click="toggleCreateMode"
          ><ShortcutSlot />{{ t('skillTreeAdminDialog.createNodeLabel') }}</button>
          <div v-if="connectMode" class="sta-hint">
            {{ pendingFrom ? t('skillTreeAdminDialog.connectHintPick') : t('skillTreeAdminDialog.connectHintFrom') }}
          </div>
          <div v-if="createMode" class="sta-hint">{{ t('skillTreeAdminDialog.createNodeHint') }}</div>
        </div>

        <div class="sta-search-box" @mousedown.stop>
          <input
            type="text" class="sta-search-input"
            v-model="searchQuery"
            :placeholder="t('skillTreeAdminDialog.searchPlaceholder')"
          />
          <button v-if="searchQuery" class="sta-search-clear" @click="searchQuery = ''">&times;</button>
        </div>

        <div v-if="selectedNode && !connectMode" class="sta-info px-panel" @mousedown.stop>
          <div class="sta-info-row"><span>ID</span><strong>{{ selectedNode.id }}</strong></div>
          <div class="sta-info-row"><span>{{ t('skillTreeAdminDialog.branchLabel') }}</span><strong>{{ selectedNode.branch }}</strong></div>
          <div class="sta-info-row"><span>{{ t('skillTreeAdminDialog.tierLabel') }}</span><strong>{{ selectedNode.nodeTier || '—' }}</strong></div>
          <div class="sta-info-row"><span>X / Y</span><strong>{{ selectedNode.x }} / {{ selectedNode.y }}</strong></div>

          <div class="sta-effects-title">{{ t('skillTreeAdminDialog.effectsLabel') }}</div>
          <div v-for="(eff, idx) in selectedNode.effects" :key="idx" class="sta-effect-row">
            <select class="sta-select" v-model="eff.effectType">
              <option v-for="et in EFFECT_TYPES" :key="et.value" :value="et.value">{{ t(et.labelKey) }}</option>
            </select>
            <select class="sta-select" :value="eff.targetResource || ''" @change="eff.targetResource = $event.target.value || null">
              <option value="">{{ t('skillTreeAdminDialog.effectGlobalOption') }}</option>
              <option v-for="r in RESOURCES" :key="r" :value="r">{{ resourceLabel(r, t) }}</option>
            </select>
            <div class="sta-effect-value">
              <NestedTooltip :content="t('skillTreeAdminDialog.signToggleTitle')" silent>
                <button
                  class="sta-sign-btn"
                  :class="{ 'sta-sign-neg': eff.effectValue < 0 }"
                  @click="eff.effectValue = -eff.effectValue"
                >{{ eff.effectValue < 0 ? '−' : '+' }}</button>
              </NestedTooltip>
              <input
                type="number" step="0.001" min="0" class="sta-num-input"
                :value="Math.abs(eff.effectValue)"
                @input="setEffectMagnitude(eff, $event.target.value)"
              />
            </div>
            <NestedTooltip :content="t('skillTreeAdminDialog.removeEffectTitle')" silent>
              <button class="sta-effect-del" @click="selectedNode.effects.splice(idx, 1)">&times;</button>
            </NestedTooltip>
          </div>
          <div class="sta-effects-actions">
            <button class="px-btn" @click="addEffect">{{ t('skillTreeAdminDialog.addEffectLabel') }}</button>
            <button class="px-btn px-btn-accent" @click="saveEffects">{{ t('skillTreeAdminDialog.saveLabel') }}</button>
          </div>
          <button v-if="!selectedNode.root" class="px-btn sta-delete-btn" @click="deleteNode(selectedNode)">{{ t('skillTreeAdminDialog.deleteNodeLabel') }}</button>
        </div>

        <div class="sta-cam-controls" @mousedown.stop>
          <button class="sta-cam-btn" @click="resetView"><ShortcutSlot /><PixelIcon name="zentrieren" :size="18" /></button>
          <div class="sta-cam-hint">{{ t('skillTreeView.centerHint') }}</div>
        </div>

        <div v-if="notice" class="sta-notice" :class="{ error: noticeError }">{{ notice }}</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { adminListSkillNodes, adminListSkillEdges, adminUpdateSkillNode, adminCreateSkillNode, adminDeleteSkillNode, adminCreateSkillEdge, adminDeleteSkillEdge } from '../services/api.js'
import { useAudio } from '../composables/useAudio.js'
import { resourceLabel } from './buildings/buildingInfo.js'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import NestedTooltip from './NestedTooltip.vue'

// Effekt-Editor: Dropdown-Optionen wiederverwenden dieselben i18n-Keys wie das Spieler-Info-Panel
// (SkillTreeView.vue EFFECT_LABEL_KEY) statt eigener Duplikate.
const EFFECT_TYPES = [
  { value: 'HARVEST_YIELD', labelKey: 'skillTreeView.effectHarvestYield' },
  { value: 'BAKE_OUTPUT', labelKey: 'skillTreeView.effectBakeOutput' },
  { value: 'MARKET_FEE_REDUCTION', labelKey: 'skillTreeView.effectMarketFeeReduction' },
  { value: 'WAGE_INTEREST_REDUCTION', labelKey: 'skillTreeView.effectWageInterestReduction' },
  { value: 'RESOURCE_WAGE_REDUCTION', labelKey: 'skillTreeView.effectResourceWageReduction' },
  { value: 'STORAGE_CAP_BONUS', labelKey: 'skillTreeView.effectStorageCapBonus' },
  { value: 'BUILDING_BUFFER_BONUS', labelKey: 'skillTreeView.effectBuildingBufferBonus' },
]
const RESOURCES = ['SUGAR', 'FLOUR', 'EGGS', 'BUTTER', 'CHOCOLATE', 'MILK']

const emit = defineEmits(['close'])
const { t } = useI18n()
const audio = useAudio()

const loading = ref(true)
const nodes = ref([])
const edges = ref([])
const selectedId = ref(null)
const connectMode = ref(false)
const pendingFrom = ref(null)
const createMode = ref(false)

const notice = ref('')
const noticeError = ref(false)
function flash(msg, isError = false) {
  notice.value = msg
  noticeError.value = isError
  setTimeout(() => { notice.value = '' }, 2500)
}

// Suche: identisches Verhalten zum Spieler-Baum (SkillTreeView.vue) -- durchsucht immer beide
// Sprachen gleichzeitig, hebt Treffer hervor statt sie auszublenden.
const searchQuery = ref('')
const searching = computed(() => searchQuery.value.trim().length > 0)
function matchesSearch(n) {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return true
  return [n.nameDe, n.nameEn, n.descriptionDe, n.descriptionEn].some(s => (s || '').toLowerCase().includes(q))
}

const selectedNode = computed(() => nodes.value.find(n => n.id === selectedId.value) || null)

// Nur Icon pro Branch, keine Branch-Farben -- `stv-branch-*` ist im
// Spieler-Baum (SkillTreeView.vue) selbst bereits ungenutzt (kein CSS
// dafuer), Unterscheidung laeuft dort wie hier rein ueber das Icon.
const BRANCH_ICON = {
  MILK: 'milch', BAKING: 'ofen', MARKET: 'stand', CORE: 'einw', DISPO: 'lohn',
  SUGAR: 'zucker', FLOUR: 'mehl', EGGS: 'eier', BUTTER: 'butter', CHOCOLATE: 'schoko',
  STORAGE: 'lager',
}
function nodeIcon(n) {
  if (n.root) return 'stern'
  return BRANCH_ICON[n.branch] || 'einw'
}
function iconSize(n) {
  if (n.nodeTier === 'KEYSTONE') return 28
  if (n.nodeTier === 'NOTABLE') return 24
  return 22
}

function toggleConnectMode() {
  connectMode.value = !connectMode.value
  pendingFrom.value = null
  createMode.value = false
}

function toggleCreateMode() {
  createMode.value = !createMode.value
  connectMode.value = false
  pendingFrom.value = null
}

function handleNodeClick(n) {
  if (connectMode.value) {
    if (!pendingFrom.value) { pendingFrom.value = n.id; return }
    if (pendingFrom.value === n.id) { pendingFrom.value = null; return }
    createEdge(pendingFrom.value, n.id)
    pendingFrom.value = null
  } else {
    selectedId.value = selectedId.value === n.id ? null : n.id
  }
}

async function createEdge(from, to) {
  try {
    const e = await adminCreateSkillEdge(from, to)
    edges.value.push(e)
    flash(t('skillTreeAdminDialog.edgeCreatedNotice'))
  } catch (err) {
    flash(err.message, true)
  }
}

async function deleteEdge(id) {
  try {
    await adminDeleteSkillEdge(id)
    edges.value = edges.value.filter(e => e.id !== id)
    flash(t('skillTreeAdminDialog.edgeDeletedNotice'))
  } catch (err) {
    flash(err.message, true)
  }
}

async function createNodeAt(worldX, worldY) {
  const id = window.prompt(t('skillTreeAdminDialog.createNodePrompt'))
  if (!id || !id.trim()) return
  const node = {
    id: id.trim(), nameDe: id.trim(), nameEn: id.trim(), descriptionDe: '', descriptionEn: '',
    branch: 'CORE', nodeTier: 'PASSIVE', root: false, requiresAllPrereqs: false,
    x: Math.round(worldX), y: Math.round(worldY), effects: [],
  }
  try {
    const saved = await adminCreateSkillNode(node)
    nodes.value.push(saved)
    selectedId.value = saved.id
    flash(t('skillTreeAdminDialog.nodeCreatedNotice'))
  } catch (err) {
    flash(err.message, true)
  }
}

async function deleteNode(n) {
  if (!n || n.root) return
  if (!window.confirm(t('skillTreeAdminDialog.deleteNodeConfirm', { id: n.id }))) return
  try {
    await adminDeleteSkillNode(n.id)
    nodes.value = nodes.value.filter(x => x.id !== n.id)
    edges.value = edges.value.filter(e => e.fromNode !== n.id && e.toNode !== n.id)
    if (selectedId.value === n.id) selectedId.value = null
    flash(t('skillTreeAdminDialog.nodeDeletedNotice'))
  } catch (err) {
    flash(err.message, true)
  }
}

async function persistNode(n, noticeKey = 'skillTreeAdminDialog.positionSavedNotice') {
  try {
    await adminUpdateSkillNode(n.id, n)
    flash(t(noticeKey))
  } catch (err) {
    flash(err.message, true)
  }
}

// Magnitude-Input haelt immer den Betrag, das Vorzeichen kommt separat vom Sign-Toggle-Button --
// verhindert dass Tippen im Feld ein bestehendes Minus verliert.
function setEffectMagnitude(eff, rawValue) {
  const abs = Math.abs(Number(rawValue) || 0)
  eff.effectValue = eff.effectValue < 0 ? -abs : abs
}

function addEffect() {
  if (!selectedNode.value) return
  if (!selectedNode.value.effects) selectedNode.value.effects = []
  selectedNode.value.effects.push({ effectType: EFFECT_TYPES[0].value, targetResource: null, effectValue: 0.01 })
}

async function saveEffects() {
  if (!selectedNode.value) return
  await persistNode(selectedNode.value, 'skillTreeAdminDialog.effectsSavedNotice')
}

// ── Node-Drag: eigener mousedown-Handler pro Node (stoppt Propagation, damit
// der Viewport nicht gleichzeitig die Kamera schwenkt), Bewegungs-Schwelle
// entscheidet Klick (Auswahl/Verbinden) vs. Ziehen (Position). ──
const DRAG_THRESHOLD = 5

function nodeMouseDown(e, n) {
  // stopPropagation zuerst, auch fuer die Root-Node (kein Drag) -- sonst wuerde der Klick zum
  // Viewport durchbubbeln und der neue "Klick auf Hintergrund waehlt ab"-Handler (panEnd) die
  // gerade erst per handleNodeClick gesetzte Auswahl im selben Zug wieder aufheben.
  e.stopPropagation()
  if (n.root) { handleNodeClick(n); return }
  e.preventDefault()
  const startX = e.clientX
  const startY = e.clientY
  const startNX = n.x
  const startNY = n.y
  let moved = false

  function onMove(ev) {
    const dx = (ev.clientX - startX) / zoom.value
    const dy = (ev.clientY - startY) / zoom.value
    if (!moved && Math.hypot(ev.clientX - startX, ev.clientY - startY) > DRAG_THRESHOLD) moved = true
    if (moved) {
      n.x = Math.round(startNX + dx)
      n.y = Math.round(startNY + dy)
    }
  }
  function onUp() {
    window.removeEventListener('mousemove', onMove, true)
    window.removeEventListener('mouseup', onUp, true)
    if (moved) persistNode(n)
    else handleNodeClick(n)
  }
  // Capture-Phase noetig: .sta-root hat @mousemove.stop (verhindert Bubbling
  // zum HUD-Pan-Handler von FarmGridView), das wuerde sonst auch diese
  // Bubble-Phase-Listener auf window nie erreichen lassen.
  window.addEventListener('mousemove', onMove, true)
  window.addEventListener('mouseup', onUp, true)
}

// ── Layout (identisch zu SkillTreeView.vue) ─────────────────────
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
  const byId = Object.fromEntries(nodes.value.map(n => [n.id, n]))
  return edges.value.map(e => {
    const from = byId[e.fromNode]
    const to   = byId[e.toNode]
    if (!from || !to) return null
    return { id: e.id, x1: CENTER + from.x, y1: CENTER + from.y, x2: CENTER + to.x, y2: CENTER + to.y }
  }).filter(Boolean)
})

// ── Pan + zoom (identisch zu SkillTreeView.vue) ──
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
let panStartX = 0, panStartY = 0
function panStart(e) {
  if (e.button !== 0) return
  dragging = true
  lastX = e.clientX
  lastY = e.clientY
  panStartX = e.clientX
  panStartY = e.clientY
}
function panMove(e) {
  if (!dragging) return
  panX.value += e.clientX - lastX
  panY.value += e.clientY - lastY
  lastX = e.clientX
  lastY = e.clientY
  clampPan()
}
// Klick auf leeren Hintergrund (kein Drag, siehe DRAG_THRESHOLD wie beim Node-Drag) waehlt die
// aktuell ausgewaehlte Node ab. Toolbar/Info-Panel/Kamera-Controls stoppen ihr eigenes mousedown
// (siehe Template), sonst wuerde z.B. Klick auf "Speichern" im Effekt-Editor die Node sofort
// wieder abwaehlen.
function panEnd(e) {
  if (dragging && e) {
    const moved = Math.hypot(e.clientX - panStartX, e.clientY - panStartY) > DRAG_THRESHOLD
    if (!moved) {
      if (createMode.value) {
        const vpRect = viewEl.value.getBoundingClientRect()
        const worldX = (e.clientX - vpRect.left - vpRect.width / 2 - panX.value) / zoom.value
        const worldY = (e.clientY - vpRect.top - vpRect.height / 2 - panY.value) / zoom.value
        createNodeAt(worldX, worldY)
        createMode.value = false
      } else {
        selectedId.value = null
      }
    }
  }
  dragging = false
}
function panLeave() { dragging = false }
function resetView() { panX.value = 0; panY.value = 0; zoom.value = 0.55 }

function onWheel(e) {
  e.preventDefault()
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  zoom.value = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom.value * factor))
  clampPan()
}

onMounted(async () => {
  audio.playBookOpen()
  loading.value = true
  try {
    const [n, e] = await Promise.all([adminListSkillNodes(), adminListSkillEdges()])
    nodes.value = n
    edges.value = e
  } catch (err) {
    flash(err.message, true)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.sta-root {
  position: fixed;
  inset: 0;
  z-index: 500;
  background: var(--px-cream);
}
.sta-loading { color: var(--px-tan-ink); text-align: center; padding: 24px; }

.sta-close {
  position: absolute; top: 14px; right: 14px; z-index: 30;
  font-family: 'Silkscreen', monospace; font-size: 16px;
  padding: 6px 12px;
  border: 3px solid var(--px-ink); background: var(--px-wood3); color: var(--px-cream);
  cursor: pointer;
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.sta-close:hover { background: var(--px-red-dk); }

.sta-viewport {
  position: absolute; inset: 0; overflow: hidden;
  background: var(--px-bg); cursor: grab;
}
.sta-viewport:active { cursor: grabbing; }

.sta-canvas {
  position: absolute; left: 50%; top: 50%;
  width: 1800px; height: 1800px;
  transform-origin: center center;
}

.sta-edges { position: absolute; left: 0; top: 0; overflow: visible; }
.sta-edge { stroke: var(--px-gold); stroke-width: 4; opacity: 0.8; pointer-events: none; }
.sta-edge-hit { stroke: transparent; stroke-width: 14; cursor: pointer; }
.sta-edge-hit:hover + .sta-edge { stroke: var(--px-red); }

.sta-node {
  position: absolute;
  display: flex; align-items: center; justify-content: center;
  border: 3px solid var(--px-ink); cursor: grab;
  background: var(--px-wood2);
  box-shadow: inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25);
}
.sta-node:active { cursor: grabbing; }
.sta-node-root { background: var(--px-gold); cursor: pointer; }
.sta-node-selected { box-shadow: 0 0 0 3px var(--px-gold), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
.sta-node-pending { box-shadow: 0 0 0 3px var(--px-green), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }

/* Suche: gleiche Optik-Sprache wie SkillTreeView.vue (Spieler-Baum) -- gedimmt statt
   ausgeblendet, damit die Kanten/Baum-Struktur nicht optisch zerreisst. */
.sta-node-search-dim { filter: saturate(0.4) brightness(0.55); }
.sta-node-search-match { animation: sta-search-glow 1.3s ease-in-out infinite; }
@keyframes sta-search-glow {
  0%, 100% { box-shadow: 0 0 0 3px var(--px-green), 0 0 8px 2px var(--px-green-lt), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
  50%      { box-shadow: 0 0 0 3px var(--px-green-lt), 0 0 14px 4px var(--px-green), inset -2px -2px 0 rgba(0,0,0,.3), inset 2px 2px 0 rgba(255,255,255,.25); }
}

.sta-toolbar {
  position: absolute; top: 14px; left: 14px; z-index: 20;
  display: flex; align-items: center; gap: 10px;
}
.sta-hint {
  font-size: 11px; font-family: 'Silkscreen', monospace; color: var(--px-tan);
  background: rgba(16,11,7,.6); padding: 4px 8px;
}

.sta-search-box {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%); z-index: 20;
  display: flex; align-items: center; gap: 4px;
  padding: 6px 8px;
  background: var(--px-wood3); border: 3px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.sta-search-input {
  width: 160px; font-family: 'Silkscreen', monospace; font-size: 12px;
  background: var(--px-cream); color: var(--px-ink); border: 2px solid var(--px-ink);
  padding: 4px 6px; outline: none;
}
.sta-search-input::placeholder { color: var(--px-tan-ink); opacity: 0.7; }
.sta-search-clear {
  width: 20px; height: 20px; flex: none;
  font-family: 'Silkscreen', monospace; font-size: 12px; line-height: 1;
  background: var(--px-wood2); color: var(--px-cream); border: 2px solid var(--px-ink);
  cursor: pointer;
}
.sta-search-clear:hover { background: var(--px-red-dk); }

.sta-info {
  position: absolute; top: 14px; right: 60px; z-index: 20;
  width: 260px; padding: 10px 12px;
  display: flex; flex-direction: column; gap: 4px;
  max-height: calc(100vh - 28px); overflow-y: auto;
}
.sta-info-row { display: flex; justify-content: space-between; font-size: 12px; color: var(--px-tan-ink); gap: 8px; }
.sta-info-row strong { color: var(--px-ink); }

.sta-effects-title {
  margin-top: 8px; padding-top: 6px; border-top: 2px solid var(--px-ink);
  font-size: 11px; color: var(--px-tan-ink); text-transform: uppercase;
}
.sta-effect-row {
  display: flex; flex-direction: column; gap: 3px;
  padding: 6px; background: rgba(16,11,7,.06); border: 2px solid var(--px-ink);
}
.sta-select {
  width: 100%; font-family: 'Silkscreen', monospace; font-size: 11px;
  background: var(--px-cream); color: var(--px-ink); border: 2px solid var(--px-ink);
  padding: 3px 4px;
}
.sta-effect-value { display: flex; align-items: center; gap: 4px; }
.sta-sign-btn {
  width: 22px; height: 22px; flex: none;
  font-family: 'Silkscreen', monospace; font-size: 13px; font-weight: bold; line-height: 1;
  background: var(--px-green); color: var(--px-cream); border: 2px solid var(--px-ink);
  cursor: pointer;
}
.sta-sign-btn.sta-sign-neg { background: var(--px-red); }
.sta-num-input {
  flex: 1; min-width: 0; font-family: 'Silkscreen', monospace; font-size: 12px;
  background: var(--px-cream); color: var(--px-ink); border: 2px solid var(--px-ink);
  padding: 3px 4px;
}
.sta-effect-del {
  align-self: flex-end;
  width: 20px; height: 20px; flex: none;
  font-family: 'Silkscreen', monospace; font-size: 12px; line-height: 1;
  background: var(--px-wood3); color: var(--px-cream); border: 2px solid var(--px-ink);
  cursor: pointer;
}
.sta-effect-del:hover { background: var(--px-red-dk); }
.sta-effects-actions { display: flex; gap: 6px; margin-top: 4px; }
.sta-effects-actions .px-btn { flex: 1; font-size: 11px; padding: 6px 4px; }

.sta-delete-btn {
  margin-top: 10px; font-size: 11px; padding: 6px 4px;
  background: var(--px-red); border-color: var(--px-red-dk); color: var(--px-cream);
}
.sta-delete-btn:hover { background: var(--px-red-dk); }

.sta-cam-controls {
  position: absolute; left: 14px; bottom: 14px; z-index: 20;
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.sta-cam-btn {
  position: relative;
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  background: var(--px-wood3); border: 3px solid var(--px-ink); color: var(--px-cream);
  cursor: pointer; box-shadow: inset -2px -2px 0 #402e2b, inset 2px 2px 0 #a15c34;
}
.sta-cam-btn:hover { filter: brightness(1.1); }
.sta-cam-hint {
  font-size: 9px; font-family: 'Silkscreen', monospace; color: var(--px-tan);
  background: rgba(16,11,7,.6); padding: 2px 6px; white-space: nowrap;
}

.sta-notice {
  position: absolute; bottom: 14px; left: 50%; transform: translateX(-50%);
  padding: 9px 16px; background: var(--px-cream); border: 3px solid var(--px-green);
  color: #56642e; font-size: 13px; z-index: 25; box-shadow: 0 6px 0 rgba(0,0,0,.4);
}
.sta-notice.error { border-color: var(--px-red); color: var(--px-red-dk); }
</style>
