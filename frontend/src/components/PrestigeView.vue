<template>
  <div class="pv-root">
    <div v-if="loading" class="pv-loading">Lade...</div>

    <template v-else-if="status">
      <div class="pv-stats">
        <div class="pv-stat">
          <div class="pv-stat-label">PRESTIGE-LEVEL</div>
          <div class="pv-stat-val">{{ status.prestigeLevel }}</div>
        </div>
        <div class="pv-stat">
          <div class="pv-stat-label">MULTIPLIKATOR</div>
          <NestedTooltip :content="[
            { text: '×' + fmt(status.multiplier) + ' auf Ernte & Backen' },
            { text: ' | Formel: ', tooltip: '1 + (0.1 × Prestige-Level)' },
            { text: '1 + 0.1×' + status.prestigeLevel },
          ]">
            <div class="pv-stat-val accent">&times;{{ fmt(status.multiplier) }}</div>
          </NestedTooltip>
        </div>
        <div class="pv-stat">
          <div class="pv-stat-label">RESETS GESAMT</div>
          <div class="pv-stat-val">{{ status.totalPrestiges }}</div>
        </div>
      </div>

      <div class="pv-progress">
        <div class="pv-progress-labels"><span>NET WORTH</span><span class="accent">{{ fmtBig(status.currentNetWorth) }} / {{ fmtBig(status.threshold) }}</span></div>
        <div class="pv-bar"><div class="pv-bar-fill" :style="{ width: Math.min(100, progress) + '%' }"></div></div>
        <div class="pv-progress-hint">
          <template v-if="status.canPrestige">BEREIT FÜR PRESTIGE!</template>
          <template v-else>NOCH {{ fmtBig(status.threshold - status.currentNetWorth) }} FEHLEN</template>
        </div>
      </div>

      <div class="pv-next">Nach Prestige: <strong>&times;{{ fmt(nextMultiplier) }}</strong> auf alle Ernte- und Backerträge</div>

      <div class="pv-warning">Reset: Cookies, Ressourcen, Upgrades, Einwohner und alle zusätzlich gebauten Gebäude. Prestige-Level und Lifetime-Statistiken bleiben.</div>

      <template v-if="status.canPrestige">
        <div v-if="!confirming" class="pv-actions">
          <button class="px-btn px-btn-accent" @click="confirming = true">PRESTIGE AUSFÜHREN</button>
        </div>
        <div v-else class="pv-confirm">
          <p>Wirklich alles zurücksetzen?</p>
          <div class="pv-confirm-btns">
            <button class="px-btn px-btn-accent" :disabled="busy" @click="execute">{{ busy ? '...' : 'JA, PRESTIGE!' }}</button>
            <button class="px-btn px-btn-flat" @click="confirming = false">ABBRECHEN</button>
          </div>
        </div>
      </template>
      <div v-else class="pv-locked">NOCH NICHT VERFÜGBAR</div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { getPrestigeStatus, doPrestige, initGame } from '../services/api.js'
import NestedTooltip from './NestedTooltip.vue'

const playerStore = usePlayerStore()
const status     = ref(null)
const loading    = ref(true)
const confirming = ref(false)
const busy       = ref(false)

const progress = computed(() => !status.value ? 0 : (status.value.currentNetWorth / status.value.threshold) * 100)
const nextMultiplier = computed(() => !status.value ? 1 : 1 + 0.1 * (status.value.prestigeLevel + 1))

async function load() {
  loading.value = true
  try { status.value = await getPrestigeStatus(playerStore.steamId) }
  finally { loading.value = false }
}

async function execute() {
  busy.value = true
  try {
    status.value = await doPrestige(playerStore.steamId)
    confirming.value = false
    const data = await initGame(playerStore.steamId, 1)
    playerStore.updateFromDto(data.user)
    await playerStore.refreshNetWorth()
  } catch (e) {
    alert(e.message)
  } finally {
    busy.value = false
  }
}

function fmt(v)    { return Number(v).toFixed(2) }
function fmtBig(v) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(1) + 'K'
  return Number(v).toFixed(0)
}

onMounted(load)
</script>

<style scoped>
.pv-root { min-width: 340px; padding: 18px; display: flex; flex-direction: column; gap: 16px; }
.pv-loading { color: var(--px-tan-ink); text-align: center; padding: 24px; }

.pv-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.pv-stat { padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); text-align: center; }
.pv-stat-label { font-size: 11px; color: var(--px-tan-ink); }
.pv-stat-val   { font-family: 'Silkscreen', monospace; font-size: 17px; color: var(--px-ink-txt); margin-top: 6px; }
.pv-stat-val.accent { color: var(--px-orange); cursor: help; }

.pv-progress-labels { display: flex; justify-content: space-between; font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-tan-ink); margin-bottom: 6px; }
.accent { color: var(--px-orange); }
.pv-bar { height: 18px; background: var(--px-ink); border: 3px solid var(--px-ink); }
.pv-bar-fill { height: 100%; background: var(--px-orange); }
.pv-progress-hint { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-ink); text-align: right; margin-top: 5px; }

.pv-next { padding: 12px 14px; background: var(--px-cream3); border: 3px solid var(--px-brown2); font-size: 15px; color: var(--px-ink-txt); }
.pv-warning { padding: 12px 14px; background: #f7dede; border: 3px solid var(--px-red); font-size: 14px; line-height: 1.55; color: #7a2020; }

.pv-actions, .pv-confirm { text-align: center; }
.pv-confirm p { font-size: 15px; font-weight: 600; margin-bottom: 10px; color: var(--px-ink-txt); }
.pv-confirm-btns { display: flex; gap: 10px; justify-content: center; }
.pv-locked { padding: 14px; text-align: center; background: var(--px-tan); border: 4px solid var(--px-ink); font-family: 'Silkscreen', monospace; font-size: 13px; color: #6b5533; }
</style>
