<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="bd-panel">
      <div class="bd-head">
        <div class="bd-head-icon">
          <PixelIcon :name="building.icon" :size="32" />
        </div>
        <div class="bd-head-text">
          <div class="bd-head-name">{{ building.title }}</div>
          <div class="bd-head-sub">{{ t('buildingDetailDialog.subtitle', { level, resource: resourceLabelText }) }}</div>
        </div>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="bd-body">
        <div class="bd-col bd-col-crew">
          <div class="bd-label">{{ t('buildingDetailDialog.residents', { count: workerCount, max: maxWorkers }) }}</div>
          <div class="bd-crew">
            <!-- Active worker slots — each has a red X to unassign -->
            <div v-for="i in workerCount" :key="'a'+i" class="bd-crew-cell bd-crew-cell-active">
              <button class="bd-crew-x" @click="adjustWorkers(-1)" :title="t('buildingDetailDialog.remove')">×</button>
              <PixelWorker variant="work"
                :anim="isBuildingIdle ? 'bob' : bodyAnim"
                :dur="1.1"
                :tool="!isBuildingIdle ? { anim: 'tap', dur: 1.1, color: '#aea47e' } : null" />
              <div class="bd-crew-name">{{ crewNames[(i - 1) % crewNames.length] }}</div>
              <div class="bd-crew-tag" :class="{ idle: isBuildingIdle }">
                {{ isBuildingIdle ? t('buildingDetailDialog.idle') : (building.act || t('buildingDetailDialog.active')) }}
              </div>
            </div>
            <!-- Add slot — shown if available citizens exist and slots remain -->
            <button
              v-if="workerCount < maxWorkers && playerStore.idleCitizens > 0"
              class="bd-crew-add"
              @click="adjustWorkers(1)"
              :title="t('buildingDetailDialog.assign')"
            ><ShortcutSlot />+</button>
            <!-- Locked slot hint -->
            <div v-else-if="workerCount < maxWorkers" class="bd-crew-locked">
              {{ playerStore.ownedCitizens === 0 ? t('buildingDetailDialog.noResidents') : t('buildingDetailDialog.allAssigned') }}
            </div>
          </div>

          <div class="bd-stats">
            <div class="bd-stat">
              <div class="bd-stat-label">{{ t('buildingDetailDialog.wage') }}</div>
              <div class="bd-stat-val bd-stat-red">{{ wageRow?.v ?? '—' }}</div>
            </div>
            <div class="bd-stat">
              <div class="bd-stat-label">{{ t('buildingDetailDialog.yield') }}</div>
              <div class="bd-stat-val bd-stat-green">{{ yieldRow?.v ?? '—' }}</div>
            </div>
          </div>

          <div class="bd-hint">{{ t('buildingDetailDialog.hint') }}</div>
        </div>

        <div class="bd-col bd-col-build">
          <div class="bd-label">{{ t('buildingDetailDialog.status') }}</div>
          <div class="bd-buildup">
            <div>
              <div class="bd-buildup-name">{{ t('buildingDetailDialog.levelLabel', { level }) }}</div>
              <div class="bd-buildup-note">{{ level > 0 ? t('buildingDetailDialog.operating') : t('buildingDetailDialog.notBuilt') }}</div>
            </div>
            <div class="bd-level-badge">
              <PixelIcon v-if="level > 0" name="check" :size="14" />
              <span v-else>&mdash;</span>
            </div>
          </div>
          <div class="bd-buildup" v-if="ownedData && ownedData.storageCapBonus">
            <div>
              <div class="bd-buildup-name">{{ t('buildingDetailDialog.storageCap') }}</div>
              <div class="bd-buildup-note">{{ t('buildingDetailDialog.storagePerLevel', { amount: (ownedData.storageCapBonus / 1000).toFixed(0) }) }}</div>
            </div>
          </div>
          <div v-if="level > 0" class="bd-buildup">
            <div>
              <div class="bd-buildup-name">{{ t('buildingDetailDialog.levelTransition', { from: level, to: level + 1 }) }}</div>
              <div class="bd-buildup-note">{{ t('buildingDetailDialog.upgradeCost', { cost: upgradeCost.toFixed(0) }) }} <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" /></div>
            </div>
            <button class="px-btn px-btn-accent" :disabled="upgrading || playerStore.cookies < upgradeCost" @click="upgradeBuilding">
              <ShortcutSlot />{{ t('buildingDetailDialog.upgrade') }}
            </button>
          </div>
          <div v-if="notice" class="bd-notice">{{ notice }}</div>

          <div class="bd-storage">
            <div class="bd-label" style="margin-bottom:8px">{{ t('buildingDetailDialog.storageLevel', { resource: resourceLabelText.toUpperCase() }) }}</div>
            <div class="bd-storage-bar"><div class="bd-storage-fill" :style="{ width: storagePct + '%' }"></div></div>
            <div class="bd-storage-text">{{ storageText }}</div>
            <button class="px-btn px-btn-accent bd-collect-btn" :disabled="collecting || pendingAmount <= 0" @click="collectBuildingAction">
              <ShortcutSlot />{{ t('buildingDetailDialog.collect') }}
            </button>
            <div class="bd-collect-hint">{{ t('buildingDetailDialog.collectHint') }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { changeWorkers, buyBuilding, collectBuilding } from '../services/api.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelWorker from './pixel/PixelWorker.vue'
import { resourceLabel } from './buildings/buildingInfo.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { fmt } from '../utils/formatNumber.js'

const props = defineProps({ building: { type: Object, required: true } })
const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()

// Light local ticker for the pending-amount preview -- purely visual extrapolation between
// building-list refreshes (see pendingAmount below), no extra polling.
const nowTick = ref(Date.now())
let tickTimer = null
onMounted(() => {
  audio.playBookOpen()
  tickTimer = setInterval(() => { nowTick.value = Date.now() }, 1000)
})
onUnmounted(() => clearInterval(tickTimer))

const playerStore = usePlayerStore()

const resourceLabelText = computed(() => resourceLabel(props.building.resource, t) || props.building.title)

// Get real building data from store (level, workers, maxWorkers)
const ownedData = computed(() => playerStore.ownedBuildings.find(b => b.id === props.building.id))
const level     = computed(() => ownedData.value?.level ?? 0)
const workerCount   = computed(() => ownedData.value?.workers ?? 0)
const maxWorkers    = computed(() => ownedData.value?.maxWorkers ?? props.building.workers ?? 1)
const upgradeCost   = computed(() => ownedData.value?.nextLevelCost ?? 0)
const upgrading     = ref(false)

const bodyAnim = computed(() => ({
  hof: 'bend', huhn: 'bend', butter: 'bob', kakao: 'reach', kuh: 'milk', pond: 'bend',
}[props.building.id] ?? 'bob'))

const crewNames = ['ANNA', 'BEN', 'CLARA', 'DIRK', 'EVA', 'FRANK', 'GRETA', 'HANS']

// Echte, live berechnete Werte aus dem Store (PlayerBuildingDto) statt der statischen
// Platzhalter-rows aus buildingInfo.js -- skaliert live mit Stufe/Arbeiterzahl.
const wageRow  = computed(() => ({ v: `${fmt(ownedData.value?.wagePerMin ?? 0)} C/min` }))
const yieldRow = computed(() => ({ v: `+${fmt(ownedData.value?.passiveRatePerSec ?? 0)}/s` }))

// This building's own accrued-and-not-yet-collected amount (like a rent bucket), extrapolated
// locally between building-list refreshes off the last server-settled snapshot -- mirrors
// FarmGridView's livePending().
const storageCapacity = computed(() => ownedData.value?.storageCapacity ?? 0)
// Friert nur bei workersIdle ein (keine Loehne mehr zahlbar) -- NICHT beim vollen
// Hauptlager, das eigene Gebaeude-Lager sammelt unabhaengig davon weiter (bis
// storageCapacity, das deckelt der Math.min unten selbst). Mirrors FarmGridView's
// livePending().
const pendingAmount = computed(() => {
  const d = ownedData.value
  if (!d || !storageCapacity.value) return 0
  if (playerStore.workersIdle) return d.pendingAmount ?? 0
  const elapsedSeconds = Math.max(0, (nowTick.value - (d.lastSettledAtEpochMs || nowTick.value)) / 1000)
  return Math.min(storageCapacity.value, (d.pendingAmount ?? 0) + (d.passiveRatePerSec ?? 0) * elapsedSeconds)
})
const storagePct = computed(() => storageCapacity.value > 0 ? Math.min(100, (pendingAmount.value / storageCapacity.value) * 100) : 0)

// "Produziert dieses Gebaeude gerade wirklich nichts mehr?" -- eigenes Lager voll ODER
// keine Loehne zahlbar. NICHT das Hauptlager (das ist ein gemeinsamer Topf ueber alle 6
// Rohstoffe, siehe player.js' totalResources, und betrifft nur die Hover-Ernte, die
// direkt ins Hauptlager schreibt -- mirrors FarmGridView's isPassiveIdle).
const isBuildingIdle = computed(() =>
  playerStore.workersIdle || pendingAmount.value >= storageCapacity.value
)
const storageText = computed(() => {
  const cap = storageCapacity.value
  return `${pendingAmount.value.toFixed(1)} / ${cap >= 1000 ? (cap / 1000).toFixed(1) + 'K' : cap.toFixed(0)}`
})

const notice = ref('')
async function adjustWorkers(delta) {
  try {
    const updated = await changeWorkers(playerStore.steamId, props.building.id, delta)
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
  } catch (e) {
    notice.value = t('buildingDetailDialog.errorChangingWorkers')
    setTimeout(() => { notice.value = '' }, 2000)
  }
}

const collecting = ref(false)
async function collectBuildingAction() {
  if (collecting.value || pendingAmount.value <= 0) return
  collecting.value = true
  try {
    const updated = await collectBuilding(playerStore.steamId, props.building.id)
    playerStore.updateFromDto(updated)
    await playerStore.loadBuildings()
  } catch (e) {
    notice.value = t('buildingDetailDialog.errorCollecting')
    setTimeout(() => { notice.value = '' }, 2000)
  } finally {
    collecting.value = false
  }
}

async function upgradeBuilding() {
  if (upgrading.value) return
  upgrading.value = true
  try {
    const updated = await buyBuilding(playerStore.steamId, props.building.id)
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
  } catch (e) {
    notice.value = t('buildingDetailDialog.notEnoughCookies')
    setTimeout(() => { notice.value = '' }, 2000)
  } finally {
    upgrading.value = false
  }
}
</script>

<style scoped>
.bd-panel {
  width: 900px; max-width: 95vw;
  background: var(--px-cream2);
  border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
}
.bd-head {
  display: flex; align-items: center; gap: 14px; padding: 16px 20px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3);
}
.bd-head-icon { width: 52px; height: 52px; background: #fff1a9; border: 3px solid var(--px-ink); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.bd-head-text { flex: 1; }
.bd-head-name { font-family: 'Silkscreen', monospace; font-size: 16px; color: var(--px-ink-txt); }
.bd-head-sub  { font-size: 14px; color: var(--px-tan-ink); margin-top: 2px; }

.bd-body { display: grid; grid-template-columns: 1fr 1fr; }
.bd-col { padding: 20px; display: flex; flex-direction: column; gap: 14px; }
.bd-col-crew { border-right: 4px solid var(--px-ink); }
.bd-label { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-tan-hd); letter-spacing: 1px; }

.bd-crew { display: flex; gap: 8px; flex-wrap: wrap; }
.bd-crew-cell {
  width: 64px; padding: 10px 0 7px; background: var(--px-cream3); border: 3px solid var(--px-brown2);
  display: flex; flex-direction: column; align-items: center; gap: 7px;
}
.bd-crew-cell-active { position: relative; background: var(--px-cream3); border-color: var(--px-brown2); }
.bd-crew-cell-active .bd-crew-tag.idle { background: #fff1a9; border-color: #aea47e; color: #6f6e72; }
.bd-crew-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-wood-lt); }
.bd-crew-tag {
  font-family: 'Silkscreen', monospace; font-size: 8px; padding: 2px 4px;
  background: #fff1a9; border: 2px solid var(--px-green); color: #56642e;
}
.bd-crew-tag.idle     { background: #fff1a9; border-color: #aea47e; color: #6f6e72; }
.bd-crew-tag.inactive { background: #fff1a9; border-color: #aea47e; color: #6f6e72; }
.bd-crew-x {
  position: absolute; top: 2px; right: 2px; width: 16px; height: 16px;
  background: var(--px-red); border: 2px solid #764032; color: #fff1a9;
  font-size: 11px; line-height: 1; cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-weight: bold; padding: 0; z-index: 5;
}
.bd-crew-x:hover { background: #b74132; }
.bd-crew-add {
  position: relative;
  width: 64px; height: 74px; border: 3px dashed var(--px-green); background: rgba(60,100,40,.12);
  display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 24px; color: var(--px-green); cursor: pointer;
}
.bd-crew-add:hover { background: var(--px-green-panel); }
.bd-crew-locked {
  width: 64px; height: 74px; border: 3px dashed #aea47e;
  display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 8px; color: #aea47e; text-align: center; padding: 4px;
}
.bd-level-badge { font-family: 'Silkscreen', monospace; font-size: 18px; color: var(--px-green-txt); }

.bd-stats { display: flex; gap: 10px; }
.bd-stat { flex: 1; padding: 10px 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.bd-stat-label { font-size: 12px; color: var(--px-tan-ink); }
.bd-stat-val   { font-family: 'Silkscreen', monospace; font-size: 15px; }
.bd-stat-red   { color: var(--px-red); }
.bd-stat-green { color: #56642e; }
.bd-hint { padding: 12px; background: #fff1a9; border: 3px solid var(--px-orange); font-size: 14px; line-height: 1.55; color: var(--px-wood-lt); }

.bd-buildup { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.bd-buildup-name { font-size: 16px; font-weight: 600; color: var(--px-ink-txt); }
.bd-buildup-note { font-size: 13px; color: var(--px-tan-ink); }
.bd-notice { font-size: 13px; color: var(--px-tan-ink); font-style: italic; }

.bd-storage { margin-top: auto; padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.bd-storage-bar { height: 16px; background: var(--px-ink); border: 3px solid var(--px-ink); }
.bd-storage-fill { height: 100%; background: var(--px-gold); }
.bd-storage-text { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-wood-lt); margin-top: 6px; }
.bd-collect-btn { width: 100%; margin-top: 10px; }
.bd-collect-hint { font-size: 11px; color: var(--px-tan-ink); font-style: italic; margin-top: 6px; }
</style>
