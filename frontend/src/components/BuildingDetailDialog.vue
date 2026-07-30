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
          <div class="bd-label">ZUGEWIESENE EINWOHNER</div>
          <div class="bd-crew">
            <div v-for="c in crew" :key="c.name" class="bd-crew-cell" :class="{ idle: c.idle }">
              <PixelWorker variant="work" :anim="c.idle ? 'bob' : bodyAnim" :dur="c.idle ? 1.5 : 1.1"
                :tool="c.idle ? null : { anim: 'tap', dur: 1.1, color: '#9fb3c2' }" />
              <div class="bd-crew-name">{{ c.name }}</div>
              <div class="bd-crew-tag" :class="{ idle: c.idle }">{{ c.idle ? 'IDLE' : building.act || 'AKTIV' }}</div>
            </div>
            <div class="bd-crew-add">+</div>
            <div class="bd-crew-locked">GE&shy;SPERRT</div>
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
          <div class="bd-label">AUSBAU</div>
          <div v-for="u in buildUps" :key="u.name" class="bd-buildup">
            <div>
              <div class="bd-buildup-name">{{ u.name }}</div>
              <div class="bd-buildup-note">{{ u.note }}</div>
            </div>
            <button class="px-btn px-btn-accent" @click="notReady">
              {{ u.cost }}<PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" />
            </button>
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
import PixelIcon from './pixel/PixelIcon.vue'
import PixelWorker from './pixel/PixelWorker.vue'
import { RESOURCE_LABEL } from './buildings/buildingInfo.js'

const props = defineProps({ building: { type: Object, required: true } })
const emit = defineEmits(['close'])

const playerStore = usePlayerStore()

const level = 3
const resourceLabel = computed(() => RESOURCE_LABEL[props.building.resource] ?? props.building.title)

const bodyAnim = computed(() => ({
  hof: 'bend', huhn: 'bend', butter: 'bob', kakao: 'reach', kuh: 'milk', pond: 'bend',
}[props.building.id] ?? 'bob'))

const crewNames = ['ANNA', 'BEN', 'CLARA', 'DIRK']
const crew = computed(() => {
  const n = Math.min(4, props.building.workers || 1)
  return Array.from({ length: n }, (_, i) => ({ name: crewNames[i] ?? `EINW. ${i + 1}`, idle: i === n - 1 && n > 2 }))
})

const wageRow  = computed(() => props.building.rows.find(r => r.k === 'Lohn'))
const yieldRow = computed(() => props.building.rows.find(r => /Passiv/.test(r.k)))

const buildUps = [
  { name: 'Stufe ' + (level + 1), note: '+30 % Basisertrag', cost: '320' },
  { name: 'Lagerausbau', note: `+400 Lagerplatz für ${resourceLabel.value}`, cost: '180' },
  { name: 'Automatisierung', note: 'produziert ohne Hover weiter', cost: '200' },
]

const stock = computed(() => (props.building.resource ? playerStore[props.building.resource.toLowerCase()] ?? 0 : 0))
const storageCap = 1300
const storagePct = computed(() => Math.min(100, (stock.value / storageCap) * 100))
const storageText = computed(() => `${stock.value.toFixed(1)} / ${(storageCap / 1000).toFixed(1)}K`)

const notice = ref('')
function notReady() {
  notice.value = 'Ausbau-System folgt in einem späteren Update.'
  setTimeout(() => { notice.value = '' }, 2500)
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
.bd-crew-cell.idle { background: #f4ecd6; border-color: #c8b18a; }
.bd-crew-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-wood-lt); }
.bd-crew-tag {
  font-family: 'Silkscreen', monospace; font-size: 8px; padding: 2px 4px;
  background: #dff0d0; border: 2px solid var(--px-green); color: #3d6b25;
}
.bd-crew-tag.idle { background: #e8dcbc; border-color: #b9a888; color: #7a6a4e; }
.bd-crew-add, .bd-crew-locked {
  width: 64px; height: 74px; border: 3px dashed var(--px-brown2); display: flex; align-items: center; justify-content: center;
  font-family: 'Silkscreen', monospace; font-size: 20px; color: var(--px-brown2);
}
.bd-crew-locked { border-color: #c8b18a; font-size: 9px; color: #c8b18a; text-align: center; }

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
