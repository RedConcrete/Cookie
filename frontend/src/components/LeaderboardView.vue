<template>
  <div class="leaderboard">
    <h3>Rangliste</h3>

    <div v-if="loading" class="lb-loading">Lade...</div>

    <table v-else class="lb-table">
      <thead>
        <tr>
          <th>#</th>
          <th>Spieler</th>
          <th>Net Worth</th>
          <th>Cookies</th>
          <th>Ressourcen</th>
          <th>Upgrades</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="entry in board"
          :key="entry.steamId"
          class="lb-row"
          :class="{ 'lb-self': entry.steamId === playerStore.steamId }"
          @click="emit('view-profile', entry.steamId)"
        >
          <td class="lb-rank">
            <span>#{{ entry.rank }}</span>
          </td>
          <td class="lb-id">{{ entry.steamId }}</td>
          <td class="lb-nw">{{ fmtBig(entry.netWorth) }}</td>
          <td>{{ fmt(entry.cookies) }}</td>
          <td>{{ fmt(entry.resourceValue) }}</td>
          <td>{{ fmt(entry.upgradeValue) }}</td>
        </tr>
      </tbody>
    </table>

    <button class="btn-refresh" @click="load">↻ Aktualisieren</button>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { getLeaderboard } from '../services/api.js'

const playerStore = usePlayerStore()
const board   = ref([])
const loading = ref(true)
const emit    = defineEmits(['view-profile'])

async function load() {
  loading.value = true
  try {
    board.value = await getLeaderboard()
    // eigenen Eintrag nehmen → Header-Anzeige mit demselben Snapshot synchronisieren
    const own = board.value.find(e => e.steamId === playerStore.steamId)
    if (own) playerStore.netWorth = own.netWorth
  } finally {
    loading.value = false
  }
}

function fmt(v)    { return Number(v).toFixed(1) }
function fmtBig(v) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v).toFixed(1)
}

onMounted(load)
</script>

<style scoped>
.leaderboard { min-width: 480px; }
h3 { margin-bottom: 16px; }

.lb-loading { color: var(--text-muted); text-align: center; padding: 24px; }

.lb-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.lb-table th {
  background: var(--surface2);
  padding: 8px 10px;
  text-align: left;
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 700;
  text-transform: uppercase;
}
.lb-table td { padding: 8px 10px; border-top: 1px solid var(--border); }

.lb-row { cursor: pointer; transition: background 0.1s; }
.lb-row:hover td { background: var(--surface2); }
.lb-self td { background: rgba(100,200,100,0.1); font-weight: 700; }

.lb-rank { font-size: 16px; width: 40px; text-align: center; }
.lb-id   { color: var(--text-muted); font-size: 11px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lb-nw   { color: var(--accent); font-weight: 700; }

.btn-refresh {
  margin-top: 12px;
  padding: 5px 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: var(--surface2);
  color: var(--text);
  cursor: pointer;
  font-size: 12px;
}
.btn-refresh:hover { background: var(--accent); color: #fff; border-color: var(--accent); }
</style>
