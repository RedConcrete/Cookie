<template>
  <div class="lv-root">
    <div v-if="loading" class="lv-loading">Lade...</div>

    <template v-else>
      <div class="lv-head-row">
        <div>#</div><div>SPIELER</div><div>NET WORTH</div><div>COOKIES</div><div>RESSOURCEN</div>
      </div>
      <div
        v-for="entry in board" :key="entry.steamId" class="lv-row"
        :class="{ self: entry.steamId === playerStore.steamId }"
        @click="emit('view-profile', entry.steamId)"
      >
        <div class="lv-rank">#{{ entry.rank }}</div>
        <div class="lv-name">{{ entry.steamId }}</div>
        <div class="lv-nw">{{ fmtBig(entry.netWorth) }}</div>
        <div class="lv-num">{{ fmt(entry.cookies) }}</div>
        <div class="lv-num">{{ fmt(entry.resourceValue) }}</div>
      </div>

      <button class="px-btn px-btn-flat lv-refresh" @click="load">&#8635; AKTUALISIEREN</button>
    </template>
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
.lv-root { min-width: 480px; padding: 16px; }
.lv-loading { color: var(--px-tan-ink); text-align: center; padding: 24px; }

.lv-head-row, .lv-row { display: grid; grid-template-columns: 52px 1fr 100px 90px 100px; gap: 8px; align-items: center; }
.lv-head-row { padding: 9px 10px; background: var(--px-cream3); border: 3px solid var(--px-brown2); font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-tan-hd); }
.lv-row { padding: 11px 10px; border-bottom: 2px solid #e8dcbc; cursor: pointer; }
.lv-row:hover { background: #fff3c4; }
.lv-row.self { background: rgba(74,124,47,.1); font-weight: 700; }

.lv-rank { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }
.lv-name { font-size: 15px; color: var(--px-ink-txt); }
.lv-nw   { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-orange); }
.lv-num  { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-wood-lt); }

.lv-refresh { margin-top: 14px; }
</style>
