<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="bd-panel">
      <div class="bd-head">
        <div class="bd-head-icon">
          <PixelIcon :name="building.icon" :size="32" />
        </div>
        <div class="bd-head-text">
          <div class="bd-head-name">{{ building.title }}</div>
          <div class="bd-head-sub">Stufe {{ level }} &middot; liefert {{ resourceLabel }}</div>
        </div>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <div class="bd-body">
        <div class="bd-col bd-col-crew">
          <div class="bd-label">EINWOHNER {{ workerCount }}/{{ maxWorkers }}</div>
          <div class="bd-crew">
            <!-- Active worker slots — each has a red X to unassign -->
            <div v-for="i in workerCount" :key="'a'+i" class="bd-crew-cell bd-crew-cell-active">
              <button class="bd-crew-x" @click="adjustWorkers(-1)" title="Entfernen">×</button>
              <PixelWorker variant="work"
                :anim="playerStore.workersIdle ? 'bob' : bodyAnim"
                :dur="1.1"
                :tool="!playerStore.workersIdle ? { anim: 'tap', dur: 1.1, color: '#9fb3c2' } : null" />
              <div class="bd-crew-name">{{ crewNames[(i - 1) % crewNames.length] }}</div>
              <div class="bd-crew-tag" :class="{ idle: playerStore.workersIdle }">
                {{ playerStore.workersIdle ? 'IDLE' : (building.act || 'AKTIV') }}
              </div>
            </div>
            <!-- Add slot — shown if available citizens exist and slots remain -->
            <button
              v-if="workerCount < maxWorkers && playerStore.idleCitizens > 0"
              class="bd-crew-add"
              @click="adjustWorkers(1)"
              title="Einwohner zuweisen"
            >+</button>
            <!-- Locked slot hint -->
            <div v-else-if="workerCount < maxWorkers" class="bd-crew-locked">
              {{ playerStore.ownedCitizens === 0 ? 'KEINE EINW.' : 'ALLE ZUG.' }}
            </div>
          </div>

          <div class="bd-stats">
            <div class="bd-stat">
              <div class="bd-stat-label">LOHN</div>
              <div class="bd-stat-val bd-stat-red">{{ wageRow?.v ?? '—' }}</div>
            </div>
            <div class="bd-stat">
              <div class="bd-stat-label">ERTRAG</div>
              <div class="bd-stat-val bd-stat-green">{{ yieldRow?.v ?? '—' }}</div>
            </div>
          </div>

          <div class="bd-hint">Jeder zusätzliche Einwohner erhöht den Ertrag und den Lohn. Zu viele Arbeiter bei niedrigem Cookie-Fluss = Minus.</div>
        </div>

        <div class="bd-col bd-col-build">
          <div class="bd-label">STATUS</div>
          <div class="bd-buildup">
            <div>
              <div class="bd-buildup-name">Stufe {{ level }}</div>
              <div class="bd-buildup-note">{{ level > 0 ? 'Gebäude in Betrieb' : 'Noch nicht gebaut' }}</div>
            </div>
            <div class="bd-level-badge">{{ level > 0 ? '&#10003;' : '—' }}</div>
          </div>
          <div class="bd-buildup" v-if="ownedData && ownedData.storageCapBonus">
            <div>
              <div class="bd-buildup-name">Lager-Cap</div>
              <div class="bd-buildup-note">+{{ (ownedData.storageCapBonus / 1000).toFixed(0) }}K pro Level</div>
            </div>
          </div>
          <div v-if="notice" class="bd-notice">{{ notice }}</div>

          <div class="bd-storage">
            <div class="bd-label" style="margin-bottom:8px">LAGERSTAND {{ resourceLabel.toUpperCase() }}</div>
            <div class="bd-storage-bar"><div class="bd-storage-fill" :style="{ width: storagePct + '%' }"></div></div>
            <div class="bd-storage-text">{{ storageText }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { changeWorkers } from '../services/api.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelWorker from './pixel/PixelWorker.vue'
import { RESOURCE_LABEL } from './buildings/buildingInfo.js'

const props = defineProps({ building: { type: Object, required: true } })
const emit = defineEmits(['close'])

const playerStore = usePlayerStore()

const resourceLabel = computed(() => RESOURCE_LABEL[props.building.resource] ?? props.building.title)

// Get real building data from store (level, workers, maxWorkers)
const ownedData = computed(() => playerStore.ownedBuildings.find(b => b.id === props.building.id))
const level     = computed(() => ownedData.value?.level ?? 0)
const workerCount   = computed(() => ownedData.value?.workers ?? 0)
const maxWorkers    = computed(() => ownedData.value?.maxWorkers ?? props.building.workers ?? 1)

const bodyAnim = computed(() => ({
  hof: 'bend', huhn: 'bend', butter: 'bob', kakao: 'reach', kuh: 'milk', pond: 'bend',
}[props.building.id] ?? 'bob'))

const crewNames = ['ANNA', 'BEN', 'CLARA', 'DIRK', 'EVA', 'FRANK', 'GRETA', 'HANS']

const wageRow  = computed(() => props.building.rows.find(r => r.k === 'Lohn'))
const yieldRow = computed(() => props.building.rows.find(r => /Passiv/.test(r.k)))

const stock = computed(() => (props.building.resource ? playerStore[props.building.resource.toLowerCase()] ?? 0 : 0))
const storageCap = computed(() => playerStore.totalResourceCap)
const storagePct = computed(() => Math.min(100, (stock.value / storageCap.value) * 100))
const storageText = computed(() => {
  const cap = storageCap.value
  return `${stock.value.toFixed(1)} / ${cap >= 1000 ? (cap / 1000).toFixed(1) + 'K' : cap}`
})

const notice = ref('')
async function adjustWorkers(delta) {
  try {
    const updated = await changeWorkers(playerStore.steamId, props.building.id, delta)
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
  } catch (e) {
    notice.value = 'Fehler beim Ändern der Einwohner.'
    setTimeout(() => { notice.value = '' }, 2000)
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
.bd-head-icon { width: 52px; height: 52px; background: #eef6fb; border: 3px solid var(--px-ink); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
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
.bd-crew-cell-active .bd-crew-tag.idle { background: #e8dcbc; border-color: #b9a888; color: #7a6a4e; }
.bd-crew-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-wood-lt); }
.bd-crew-tag {
  font-family: 'Silkscreen', monospace; font-size: 8px; padding: 2px 4px;
  background: #dff0d0; border: 2px solid var(--px-green); color: #3d6b25;
}
.bd-crew-tag.idle     { background: #e8dcbc; border-color: #b9a888; color: #7a6a4e; }
.bd-crew-tag.inactive { background: #ddd8cc; border-color: #b0a898; color: #8a7a6a; }
.bd-crew-x {
  position: absolute; top: 2px; right: 2px; width: 16px; height: 16px;
  background: var(--px-red); border: 2px solid #8b1a1a; color: #fff;
  font-size: 11px; line-height: 1; cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-weight: bold; padding: 0; z-index: 5;
}
.bd-crew-x:hover { background: #c01010; }
.bd-crew-add {
  width: 64px; height: 74px; border: 3px dashed var(--px-green); background: rgba(60,100,40,.12);
  display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 24px; color: var(--px-green); cursor: pointer;
}
.bd-crew-add:hover { background: var(--px-green-panel); }
.bd-crew-locked {
  width: 64px; height: 74px; border: 3px dashed #c8b18a;
  display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 8px; color: #c8b18a; text-align: center; padding: 4px;
}
.bd-level-badge { font-family: 'Silkscreen', monospace; font-size: 18px; color: var(--px-green-txt); }

.bd-stats { display: flex; gap: 10px; }
.bd-stat { flex: 1; padding: 10px 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.bd-stat-label { font-size: 12px; color: var(--px-tan-ink); }
.bd-stat-val   { font-family: 'Silkscreen', monospace; font-size: 15px; }
.bd-stat-red   { color: var(--px-red); }
.bd-stat-green { color: #3d6b25; }
.bd-hint { padding: 12px; background: #fff3c4; border: 3px solid var(--px-orange); font-size: 14px; line-height: 1.55; color: var(--px-wood-lt); }

.bd-buildup { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.bd-buildup-name { font-size: 16px; font-weight: 600; color: var(--px-ink-txt); }
.bd-buildup-note { font-size: 13px; color: var(--px-tan-ink); }
.bd-notice { font-size: 13px; color: var(--px-tan-ink); font-style: italic; }

.bd-storage { margin-top: auto; padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.bd-storage-bar { height: 16px; background: var(--px-ink); border: 3px solid var(--px-ink); }
.bd-storage-fill { height: 100%; background: var(--px-gold); }
.bd-storage-text { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-wood-lt); margin-top: 6px; }
</style>
