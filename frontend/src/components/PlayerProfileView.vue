<template>
  <div class="pp-root">
    <div v-if="loading" class="pp-loading">Lade...</div>

    <template v-else-if="data">
      <div class="pp-header">
        <div class="pp-avatar"><span>&#128100;</span></div>
        <div class="pp-title">
          <div class="pp-name">{{ data.steamId }}</div>
          <div class="pp-rank">Platz #{{ data.rank }} &middot; Prestige {{ data.prestigeLevel }}</div>
        </div>
        <div class="pp-nw">
          <div class="pp-nw-label">NET WORTH</div>
          <div class="pp-nw-val">{{ fmtBig(data.netWorth) }}</div>
        </div>
      </div>

      <div class="pp-stats">
        <div v-for="s in stats" :key="s.label" class="pp-stat">
          <div class="pp-stat-label">{{ s.label }}</div>
          <div class="pp-stat-val">{{ s.val }}</div>
        </div>
      </div>

      <div class="pp-section">
        <div class="pp-section-head">
          <span class="pp-label">ORDEN &middot; {{ badges.length }}</span>
          <button class="pp-link" @click="ordenOpen = true">ALLE ANSEHEN &rarr;</button>
        </div>
        <div class="pp-badges">
          <div v-for="m in badges.slice(0, 6)" :key="m.id" class="pp-badge-chip" :style="{ background: m.color }" :title="m.name">
            <PixelIcon :name="m.icon" :size="24" />
          </div>
          <div v-if="!badges.length" class="pp-no-badges">Noch keine Orden</div>
        </div>
      </div>

      <div class="pp-section">
        <div class="pp-label">UPGRADES</div>
        <div class="pp-upgrades">
          <div v-for="u in activeUpgrades" :key="u.id" class="pp-upgrade-chip">{{ u.name }} &middot; Stufe {{ u.currentLevel }}</div>
          <div v-if="activeUpgrades.length === 0" class="pp-no-badges">Keine Upgrades</div>
        </div>
      </div>

      <template v-if="data.seasonHistory?.length">
        <div class="pp-label" style="margin-top:6px">SEASON-HISTORIE</div>
        <div class="pp-season-table">
          <div class="pp-season-row pp-season-head"><div>SEASON</div><div>PLATZ</div><div>NET WORTH</div><div>PRESTIGE</div></div>
          <div v-for="s in data.seasonHistory" :key="s.seasonId" class="pp-season-row">
            <div>{{ s.seasonName }}</div><div>#{{ s.finalRank }}</div><div>{{ fmtBig(s.finalNetWorth) }}</div><div>{{ s.prestigeLevelAtEnd }}</div>
          </div>
        </div>
      </template>
    </template>
  </div>

  <OrdenDialog v-if="ordenOpen" :steamId="steamId" @close="ordenOpen = false" />
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getProfile } from '../services/api.js'
import { useBadges } from '../composables/useBadges.js'
import PixelIcon from './pixel/PixelIcon.vue'
import OrdenDialog from './OrdenDialog.vue'

const props = defineProps({ steamId: { type: String, required: true } })

const { badges } = useBadges()
const data    = ref(null)
const loading = ref(true)
const ordenOpen = ref(false)

const activeUpgrades = computed(() => (data.value?.upgrades ?? []).filter(u => u.currentLevel > 0))

const stats = computed(() => !data.value ? [] : [
  { label: 'COOKIES', val: fmtBig(data.value.cookies) },
  { label: 'RESSOURCENWERT', val: fmtBig(data.value.resourceValue) },
  { label: 'UPGRADE-WERT', val: fmtBig(data.value.upgradeValue) },
  { label: 'GEBACKEN (LIFETIME)', val: fmtBig(data.value.lifetimeCookiesBaked) },
])

async function load() {
  loading.value = true
  data.value = null
  try { data.value = await getProfile(props.steamId) }
  finally { loading.value = false }
}

function fmtBig(v) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v).toFixed(1)
}

watch(() => props.steamId, load)
onMounted(load)
</script>

<style scoped>
.pp-root { padding: 16px 18px; display: flex; flex-direction: column; gap: 16px; }
.pp-loading { color: var(--px-tan-ink); text-align: center; padding: 24px; }

.pp-header { display: flex; align-items: center; gap: 14px; padding-bottom: 14px; border-bottom: 3px solid var(--px-tan); }
.pp-avatar { width: 56px; height: 56px; background: var(--px-wood3); border: 3px solid var(--px-ink); display: flex; align-items: center; justify-content: center; font-size: 26px; flex-shrink: 0; }
.pp-title { flex: 1; }
.pp-name { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); word-break: break-all; }
.pp-rank { font-size: 13px; color: var(--px-tan-ink); margin-top: 4px; }
.pp-nw { text-align: right; }
.pp-nw-label { font-size: 11px; color: var(--px-tan-ink); }
.pp-nw-val { font-family: 'Silkscreen', monospace; font-size: 16px; color: #3d6b25; }

.pp-stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.pp-stat { padding: 11px; background: var(--px-cream2); border: 3px solid var(--px-tan); }
.pp-stat-label { font-size: 11px; color: var(--px-tan-ink); line-height: 1.3; }
.pp-stat-val { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); margin-top: 5px; }

.pp-section-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px; }
.pp-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); letter-spacing: 1px; }
.pp-link { font-family: 'Silkscreen', monospace; font-size: 9px; padding: 5px 7px; background: var(--px-cream3); border: 3px solid var(--px-brown2); color: var(--px-tan-hd); cursor: pointer; }
.pp-link:hover { background: #fff3c4; }

.pp-badges { display: flex; gap: 8px; flex-wrap: wrap; }
.pp-badge-chip { width: 38px; height: 38px; border: 3px solid var(--px-ink); box-shadow: inset -2px -2px 0 rgba(0,0,0,.25), inset 2px 2px 0 rgba(255,255,255,.35); display: flex; align-items: center; justify-content: center; }
.pp-no-badges { font-size: 13px; color: var(--px-tan-ink); }

.pp-upgrades { display: flex; gap: 8px; flex-wrap: wrap; }
.pp-upgrade-chip { padding: 7px 10px; background: var(--px-cream3); border: 3px solid var(--px-brown2); font-size: 14px; color: var(--px-ink-txt); }

.pp-season-table { display: flex; flex-direction: column; }
.pp-season-row { display: grid; grid-template-columns: 1fr 70px 90px 70px; gap: 8px; padding: 6px 10px; }
.pp-season-head { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-tan-hd); background: var(--px-cream3); }
.pp-season-row:not(.pp-season-head) { border-bottom: 2px solid #e8dcbc; font-size: 13px; color: var(--px-ink-txt); }
</style>
