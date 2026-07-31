<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="bs-panel">
      <div class="bs-head">
        <PixelIcon name="lager" :size="28" />
        <div class="bs-head-title">BAUVORHABEN</div>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <div class="bs-notice" v-if="notice" :class="{ error: noticeError }">{{ notice }}</div>

      <div class="bs-list">
        <div
          v-for="b in buildings" :key="b.id"
          class="bs-row"
          :class="{ owned: b.level > 0 && !b.canUpgrade }"
        >
          <div class="bs-row-icon">
            <PixelIcon :name="BUILDING_INFO[b.id]?.icon ?? 'haus'" :size="28" />
          </div>
          <div class="bs-row-info">
            <div class="bs-row-name">{{ b.name }}</div>
            <div class="bs-row-sub">
              <span class="bs-wage">LOHN {{ b.wagePerMin }} C/MIN</span>
              <span v-if="b.storageCapBonus > 0" class="bs-cap">+{{ fmtK(b.storageCapBonus) }} LAGER</span>
            </div>
            <div v-if="b.level > 0" class="bs-level">LEVEL {{ b.level }}</div>
          </div>
          <div class="bs-row-action">
            <template v-if="b.level > 0 && !b.canUpgrade">
              <div class="bs-owned-badge">&#10003; GEBAUT</div>
            </template>
            <template v-else>
              <div class="bs-cost">{{ fmtK(b.nextLevelCost) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" /></div>
              <button
                class="px-btn px-btn-accent bs-buy-btn"
                :disabled="buying === b.id || playerStore.cookies < b.nextLevelCost"
                @click="buy(b.id)"
              >
                {{ b.level === 0 ? 'BAUEN' : 'AUFSTOCKEN' }}
              </button>
            </template>
          </div>
        </div>
      </div>

      <div class="bs-footer">
        <span>Cookies: <b>{{ fmt(playerStore.cookies) }}</b></span>
        <span>Lager-Cap: <b>{{ fmtK(playerStore.totalResourceCap) }}</b></span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { buyBuilding } from '../services/api.js'
import { BUILDING_INFO } from './buildings/buildingInfo.js'
import PixelIcon from './pixel/PixelIcon.vue'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()

const buying      = ref(null)
const notice      = ref('')
const noticeError = ref(false)

// Always reflects live store data (reactive)
const buildings = computed(() => playerStore.ownedBuildings)

function fmt(v) { return Number(v ?? 0).toFixed(1) }
function fmtK(v) {
  if (v >= 1000) return (v / 1000).toFixed(1) + 'K'
  return String(Math.round(v))
}

async function buy(buildingId) {
  if (buying.value) return
  buying.value = buildingId
  notice.value = ''
  try {
    const updated = await buyBuilding(playerStore.steamId, buildingId)
    // Update store directly so the computed buildings refreshes
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
    const b = updated.find(x => x.id === buildingId)
    notice.value = `${b?.name ?? buildingId} gebaut!`
    noticeError.value = false
  } catch (e) {
    notice.value = 'Nicht genug Cookies. Mindestens ' + (buildings.value.find(x => x.id === buildingId)?.nextLevelCost?.toFixed(0) ?? '?') + ' C nötig.'
    noticeError.value = true
  } finally {
    buying.value = null
    setTimeout(() => { notice.value = '' }, 3000)
  }
}

onMounted(async () => {
  if (!playerStore.ownedBuildings.length) await playerStore.loadBuildings()
})
</script>

<style scoped>
.bs-panel {
  width: 560px; max-width: 96vw; max-height: 85vh;
  background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
  display: flex; flex-direction: column;
}

.bs-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3); flex-shrink: 0;
}
.bs-head-title { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); flex: 1; }

.bs-notice {
  padding: 8px 18px; font-size: 13px; color: #3d6b25; background: #dff0d0;
  border-bottom: 3px solid var(--px-green); font-family: 'Silkscreen', monospace; font-size: 10px;
}
.bs-notice.error { color: #8b1a1a; background: #f0d0d0; border-color: var(--px-red); }

.bs-list { overflow-y: auto; flex: 1; }

.bs-row {
  display: flex; align-items: center; gap: 12px; padding: 12px 18px;
  border-bottom: 3px solid var(--px-brown2);
}
.bs-row.owned { opacity: 0.65; }
.bs-row:last-child { border-bottom: none; }

.bs-row-icon { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.bs-row-info { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.bs-row-name { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-ink-txt); }
.bs-row-sub  { display: flex; gap: 10px; }
.bs-wage     { font-size: 12px; color: var(--px-red); }
.bs-cap      { font-size: 12px; color: var(--px-green-txt); }
.bs-level    { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-gold); }

.bs-row-action { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex-shrink: 0; min-width: 110px; }
.bs-cost { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-gold); display: flex; align-items: center; }
.bs-buy-btn { font-size: 10px; padding: 4px 10px; }
.bs-buy-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.bs-owned-badge { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-green-txt); padding: 4px 8px; background: #dff0d0; border: 2px solid var(--px-green); }

.bs-footer {
  padding: 10px 18px; display: flex; justify-content: space-between;
  border-top: 4px solid var(--px-ink); background: var(--px-cream3);
  font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-muted);
  flex-shrink: 0;
}
.bs-footer b { color: var(--px-gold-txt); }
</style>
