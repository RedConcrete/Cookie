<template>
  <div class="sv-root">
    <div v-if="loading" class="sv-loading"><LoadingIndicator /></div>

    <template v-else>
      <!-- ── Wirtschaft ─────────────────────────────────── -->
      <div class="sv-panel">
        <div class="sv-panel-title">{{ t('statsView.economyTitle') }}</div>
        <div class="sv-tile-grid">
          <div class="sv-tile">
            <PixelIcon name="cookie" :size="20" />
            <div class="sv-tile-label">{{ t('statsView.cookies') }}</div>
            <div class="sv-tile-val">{{ fmtBig(playerStore.nwCookies) }}</div>
          </div>
          <div class="sv-tile">
            <PixelIcon name="mehl" :size="20" />
            <div class="sv-tile-label">{{ t('statsView.resourceValue') }}</div>
            <div class="sv-tile-val">{{ fmtBig(playerStore.nwResources) }}</div>
          </div>
          <div class="sv-tile">
            <PixelIcon name="upgrade" :size="20" />
            <div class="sv-tile-label">{{ t('statsView.skillTreeValue') }}</div>
            <div class="sv-tile-val">{{ fmtBig(playerStore.nwSkillTreeValue) }}</div>
          </div>
          <div class="sv-tile sv-tile-accent">
            <PixelIcon name="cookie" :size="20" />
            <div class="sv-tile-label">{{ t('statsView.netWorth') }}</div>
            <div class="sv-tile-val">{{ fmtBig(playerStore.netWorth) }}</div>
          </div>
        </div>
        <div v-if="stats" class="sv-subrow">
          <div class="sv-subrow-item">
            <span class="sv-subrow-label">{{ t('statsView.marketBought') }}</span>
            <span class="sv-subrow-val">{{ fmtBig(stats.lifetimeCookiesSpentOnMarket) }} C</span>
          </div>
          <div class="sv-subrow-item">
            <span class="sv-subrow-label">{{ t('statsView.marketSold') }}</span>
            <span class="sv-subrow-val">{{ fmtBig(stats.lifetimeCookiesEarnedFromMarket) }} C</span>
          </div>
        </div>
      </div>

      <!-- ── Produktion im Überblick ────────────────────── -->
      <div class="sv-panel">
        <div class="sv-panel-title">{{ t('statsView.productionTitle') }}</div>
        <div class="sv-building-table">
          <div class="sv-building-row sv-building-head">
            <div>{{ t('statsView.colBuilding') }}</div>
            <div>{{ t('statsView.colLevel') }}</div>
            <div>{{ t('statsView.colWorkers') }}</div>
            <div>{{ t('statsView.colPassive') }}</div>
            <div>{{ t('statsView.colWage') }}</div>
          </div>
          <div v-for="b in productionBuildings" :key="b.id" class="sv-building-row">
            <div class="sv-building-name"><PixelIcon :name="iconFor(b.id)" :size="16" />{{ buildingTitle(b.id, t) }}</div>
            <div>{{ b.level }}</div>
            <div>{{ b.workers }}/{{ b.maxWorkers }}</div>
            <div>{{ b.passiveRatePerSec ? '+' + b.passiveRatePerSec.toFixed(2) + '/s' : '—' }}</div>
            <div>{{ b.wagePerMin ? b.wagePerMin.toFixed(1) + ' C/min' : '—' }}</div>
          </div>
          <div v-if="!productionBuildings.length" class="sv-empty">{{ t('statsView.noBuildings') }}</div>
        </div>

        <div class="sv-panel-subtitle">{{ t('statsView.activeBonusesTitle') }}</div>
        <div v-if="stats" class="sv-bonus-grid">
          <div v-for="r in RESOURCES" :key="r.name" class="sv-bonus-chip">
            <PixelIcon :name="r.icon" :size="16" />
            <span>{{ pct(stats[r.bonusKey]) }}</span>
          </div>
          <div class="sv-bonus-chip">
            <PixelIcon name="ofen" :size="16" />
            <span>{{ t('statsView.bakeOutput') }} {{ pct(stats.bakeOutputBonus) }}</span>
          </div>
          <div class="sv-bonus-chip sv-bonus-chip-negative">
            <PixelIcon name="stand" :size="16" />
            <span>{{ t('statsView.marketFee') }} {{ (stats.effectiveMarketFeeRate * 100).toFixed(1) }}%</span>
          </div>
        </div>
      </div>

      <!-- ── Lifetime-Zähler ────────────────────────────── -->
      <div class="sv-panel">
        <div class="sv-panel-title">{{ t('statsView.lifetimeTitle') }}</div>
        <div class="sv-tile-grid sv-tile-grid-6">
          <div v-for="r in RESOURCES" :key="r.name" class="sv-tile">
            <PixelIcon :name="r.icon" :size="20" />
            <div class="sv-tile-label">{{ resourceLabel(r.name, t) }}</div>
            <div class="sv-tile-val">{{ fmtBig(stats?.[r.harvestKey] ?? 0) }}</div>
          </div>
        </div>
        <div class="sv-subrow">
          <div class="sv-subrow-item">
            <span class="sv-subrow-label">{{ t('statsView.cookiesBaked') }}</span>
            <span class="sv-subrow-val">{{ fmtBig(stats?.lifetimeCookiesBaked ?? 0) }}</span>
          </div>
          <div class="sv-subrow-item">
            <span class="sv-subrow-label">{{ t('statsView.prestigeCount') }}</span>
            <span class="sv-subrow-val">{{ stats?.totalPrestiges ?? 0 }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { getPlayerStats } from '../services/api.js'
import { buildingTitle, resourceLabel } from './buildings/buildingInfo.js'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelIcon from './pixel/PixelIcon.vue'

const { t } = useI18n()
const playerStore = usePlayerStore()

const stats   = ref(null)
const loading = ref(true)

const RESOURCES = [
  { name: 'SUGAR',     icon: 'zucker', harvestKey: 'lifetimeSugarHarvested',     bonusKey: 'harvestYieldSugarBonus' },
  { name: 'FLOUR',     icon: 'mehl',   harvestKey: 'lifetimeFlourHarvested',     bonusKey: 'harvestYieldFlourBonus' },
  { name: 'EGGS',      icon: 'eier',   harvestKey: 'lifetimeEggsHarvested',      bonusKey: 'harvestYieldEggsBonus' },
  { name: 'BUTTER',    icon: 'butter', harvestKey: 'lifetimeButterHarvested',    bonusKey: 'harvestYieldButterBonus' },
  { name: 'CHOCOLATE', icon: 'schoko', harvestKey: 'lifetimeChocolateHarvested', bonusKey: 'harvestYieldChocolateBonus' },
  { name: 'MILK',      icon: 'milch',  harvestKey: 'lifetimeMilkHarvested',      bonusKey: 'harvestYieldMilkBonus' },
]

const PRODUCTION_IDS = ['pond', 'hof', 'huhn', 'butter', 'kakao', 'kuh']
const ICON_BY_ID = { pond: 'zucker', hof: 'mehl', huhn: 'eier', butter: 'butter', kakao: 'schoko', kuh: 'milch' }
function iconFor(id) { return ICON_BY_ID[id] ?? 'cookie' }

const productionBuildings = computed(() =>
  playerStore.ownedBuildings.filter(b => PRODUCTION_IDS.includes(b.id) && b.level > 0)
)

function fmtBig(v) {
  if (!v) return '0'
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v).toFixed(1)
}
function pct(v) { return (v >= 0 ? '+' : '') + (v * 100).toFixed(1) + '%' }

onMounted(async () => {
  loading.value = true
  try { stats.value = await getPlayerStats(playerStore.steamId) }
  finally { loading.value = false }
})
</script>

<style scoped>
.sv-root { padding: 24px; display: flex; flex-direction: column; gap: 18px; max-width: 1100px; margin: 0 auto; }
.sv-loading { padding: 60px; display: flex; justify-content: center; color: var(--px-tan-ink); }

.sv-panel { background: var(--px-cream2); border: 4px solid var(--px-ink); padding: 16px 18px; }
.sv-panel-title {
  font-family: 'Silkscreen', monospace; font-size: 12px; letter-spacing: 1px;
  color: var(--px-ink-txt); margin-bottom: 12px;
}
.sv-panel-subtitle {
  font-family: 'Silkscreen', monospace; font-size: 10px; letter-spacing: 1px;
  color: var(--px-tan-hd); margin: 14px 0 8px;
}

.sv-tile-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.sv-tile-grid-6 { grid-template-columns: repeat(6, 1fr); }
.sv-tile {
  background: var(--px-cream); border: 3px solid var(--px-tan);
  padding: 10px; display: flex; flex-direction: column; align-items: flex-start; gap: 4px;
}
.sv-tile-accent { border-color: var(--px-orange); background: #fff1a9; }
.sv-tile-label { font-size: 11px; color: var(--px-tan-ink); }
.sv-tile-val { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); }

.sv-subrow { display: flex; gap: 18px; margin-top: 12px; flex-wrap: wrap; }
.sv-subrow-item { display: flex; gap: 6px; align-items: baseline; }
.sv-subrow-label { font-size: 12px; color: var(--px-tan-ink); }
.sv-subrow-val { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-ink-txt); }

.sv-building-table { display: flex; flex-direction: column; gap: 3px; }
.sv-building-row {
  display: grid; grid-template-columns: 1.6fr 0.6fr 0.8fr 0.9fr 1fr;
  gap: 8px; padding: 7px 10px; align-items: center; background: var(--px-cream);
}
.sv-building-head {
  background: var(--px-wood); color: var(--px-gold);
  font-family: 'Silkscreen', monospace; font-size: 9px;
}
.sv-building-row:not(.sv-building-head) { font-size: 13px; color: var(--px-ink-txt); border-bottom: 2px solid #fff1a9; }
.sv-building-name { display: flex; align-items: center; gap: 6px; }
.sv-empty { padding: 10px; font-size: 13px; color: var(--px-tan-ink); background: var(--px-cream); }

.sv-bonus-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.sv-bonus-chip {
  display: flex; align-items: center; gap: 6px; padding: 7px 10px;
  background: var(--px-cream); border: 3px solid var(--px-brown2);
  font-family: 'Silkscreen', monospace; font-size: 12px; color: #56642e;
}
.sv-bonus-chip-negative { border-color: var(--px-red); color: var(--px-red); }
</style>
