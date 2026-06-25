<template>
  <div class="profile">
    <div v-if="loading" class="profile-loading">Lade...</div>

    <template v-else-if="data">
      <!-- Header -->
      <div class="profile-header">
        <div class="profile-avatar">👤</div>
        <div class="profile-title">
          <div class="profile-name">{{ data.steamId }}</div>
          <div class="profile-rank">
            <span>Platz #{{ data.rank }}</span>
          </div>
        </div>
        <div class="profile-networth">
          <div class="nw-label">Net Worth</div>
          <div class="nw-value">{{ fmtBig(data.netWorth) }}</div>
        </div>
      </div>

      <!-- Stats -->
      <div class="profile-stats">
        <div class="stat-card">
          <div class="stat-label">Cookies</div>
          <div class="stat-value">{{ fmtBig(data.cookies) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Ressourcenwert</div>
          <div class="stat-value">{{ fmtBig(data.resourceValue) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Upgrade-Wert</div>
          <div class="stat-value">{{ fmtBig(data.upgradeValue) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Cookies gebacken (gesamt)</div>
          <div class="stat-value">{{ fmtBig(data.lifetimeCookiesBaked) }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Prestige</div>
          <div class="stat-value">{{ data.prestigeLevel }}</div>
        </div>
      </div>

      <!-- Upgrades -->
      <div class="profile-section-label">Upgrades</div>
      <div class="profile-upgrades">
        <div
          v-for="u in activeUpgrades"
          :key="u.id"
          class="upgrade-badge"
        >
          <span class="badge-name">{{ u.name }}</span>
          <span class="badge-level">Stufe {{ u.currentLevel }}</span>
        </div>
        <div v-if="activeUpgrades.length === 0" class="no-upgrades">Keine Upgrades</div>
      </div>

      <!-- Season-Historie -->
      <template v-if="data.seasonHistory?.length">
        <div class="profile-section-label" style="margin-top:20px">Season-Historie</div>
        <table class="season-table">
          <thead>
            <tr>
              <th>Season</th>
              <th>Platz</th>
              <th>Net Worth</th>
              <th>Prestige</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in data.seasonHistory" :key="s.seasonId">
              <td>{{ s.seasonName }}</td>
              <td>#{{ s.finalRank }}</td>
              <td>{{ fmtBig(s.finalNetWorth) }}</td>
              <td>{{ s.prestigeLevelAtEnd }}</td>
            </tr>
          </tbody>
        </table>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getProfile } from '../services/api.js'

const props = defineProps({
  steamId: { type: String, required: true }
})

const data    = ref(null)
const loading = ref(true)

const activeUpgrades = computed(() =>
  (data.value?.upgrades ?? []).filter(u => u.currentLevel > 0)
)

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
.profile-loading { color: var(--text-muted); text-align: center; padding: 24px; }

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 2px solid var(--border);
  margin-bottom: 16px;
}
.profile-avatar { font-size: 48px; line-height: 1; }
.profile-title  { flex: 1; }
.profile-name   { font-size: 16px; font-weight: 700; color: var(--text); word-break: break-all; }
.profile-rank   { font-size: 13px; color: var(--text-muted); margin-top: 4px; }

.profile-networth { text-align: right; }
.nw-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; }
.nw-value { font-size: 22px; font-weight: 700; color: #aaff88; }

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 20px;
}
.stat-card {
  background: var(--surface2);
  border-radius: 10px;
  padding: 10px 12px;
}
.stat-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; margin-bottom: 4px; }
.stat-value { font-size: 15px; font-weight: 700; color: var(--text); }

.profile-section-label {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 8px;
}
.profile-upgrades {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.upgrade-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--surface2);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 4px 10px;
  font-size: 12px;
}
.badge-name  { color: var(--text); }
.badge-level { color: var(--accent); font-weight: 700; }
.no-upgrades { color: var(--text-muted); font-size: 12px; }

.season-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.season-table th, .season-table td {
  padding: 6px 10px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}
.season-table th { color: var(--text-muted); font-size: 10px; text-transform: uppercase; }
.season-table td { color: var(--text); }
</style>
