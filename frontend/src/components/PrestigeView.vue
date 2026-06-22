<template>
  <div class="prestige-view">
    <h3>Prestige</h3>

    <div v-if="loading" class="p-loading">Lade...</div>

    <template v-else-if="status">
      <!-- Aktueller Status -->
      <div class="p-stats">
        <div class="p-stat">
          <div class="p-stat-label">Prestige-Level</div>
          <div class="p-stat-val">{{ status.prestigeLevel }}</div>
        </div>
        <div class="p-stat">
          <div class="p-stat-label">Multiplikator</div>
          <NestedTooltip :content="[
            { text: '×' + fmt(status.multiplier) + ' auf Ernte & Backen' },
            { text: ' | Formel: ', tooltip: '1 + (0.1 × Prestige-Level)' },
            { text: '1 + 0.1×' + status.prestigeLevel },
          ]">
            <div class="p-stat-val accent">×{{ fmt(status.multiplier) }}</div>
          </NestedTooltip>
        </div>
        <div class="p-stat">
          <div class="p-stat-label">Resets gesamt</div>
          <div class="p-stat-val">{{ status.totalPrestiges }}</div>
        </div>
      </div>

      <!-- Fortschritt zur Schwelle -->
      <div class="p-progress-section">
        <div class="p-progress-labels">
          <span>Net Worth</span>
          <span class="accent">{{ fmtBig(status.currentNetWorth) }} / {{ fmtBig(status.threshold) }}</span>
        </div>
        <div class="p-bar">
          <div class="p-bar-fill" :style="{ width: Math.min(100, progress) + '%' }"></div>
        </div>
        <div class="p-progress-hint">
          <template v-if="status.canPrestige">Bereit für Prestige!</template>
          <template v-else>Noch {{ fmtBig(status.threshold - status.currentNetWorth) }} fehlen</template>
        </div>
      </div>

      <!-- Nächster Multiplikator -->
      <div class="p-next">
        Nach Prestige: <strong>×{{ fmt(nextMultiplier) }}</strong> auf alle Ernte- und Backerträge
      </div>

      <!-- Reset-Warnung -->
      <div class="p-warning">
        ⚠ Reset: Cookies, Ressourcen und alle Upgrades werden auf 0 gesetzt.<br>
        Prestige-Level und Lifetime-Statistiken bleiben erhalten.
      </div>

      <!-- Bestätigung -->
      <template v-if="status.canPrestige">
        <div v-if="!confirming" class="p-actions">
          <button class="btn-prestige" @click="confirming = true">Prestige ausführen</button>
        </div>
        <div v-else class="p-confirm">
          <p>Wirklich alles zurücksetzen?</p>
          <div class="p-confirm-btns">
            <button class="btn-prestige" :disabled="busy" @click="execute">
              {{ busy ? '...' : 'Ja, Prestige!' }}
            </button>
            <button class="btn-cancel" @click="confirming = false">Abbrechen</button>
          </div>
        </div>
      </template>
      <div v-else class="p-locked">Prestige noch nicht verfügbar</div>
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

const progress = computed(() => {
  if (!status.value) return 0
  return (status.value.currentNetWorth / status.value.threshold) * 100
})

const nextMultiplier = computed(() => {
  if (!status.value) return 1
  return 1 + 0.1 * (status.value.prestigeLevel + 1)
})

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
    // Spielerdaten aktualisieren
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
.prestige-view { min-width: 340px; }
h3 { margin-bottom: 20px; }

.p-loading { color: var(--text-muted); text-align: center; padding: 24px; }

.p-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 20px;
}
.p-stat {
  background: var(--surface2);
  border-radius: 10px;
  padding: 10px 12px;
  text-align: center;
}
.p-stat-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; margin-bottom: 4px; }
.p-stat-val   { font-size: 18px; font-weight: 700; color: var(--text); }
.p-stat-val.accent { color: #f0a030; }

.p-progress-section { margin-bottom: 16px; }
.p-progress-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 6px;
  color: var(--text-muted);
}
.accent { color: var(--accent); font-weight: 700; }
.p-bar {
  height: 10px;
  background: var(--surface2);
  border-radius: 5px;
  overflow: hidden;
}
.p-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), #f0a030);
  border-radius: 5px;
  transition: width 0.4s ease;
}
.p-progress-hint {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 5px;
  text-align: right;
}

.p-next {
  font-size: 13px;
  color: var(--text);
  background: var(--surface2);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 14px;
}

.p-warning {
  font-size: 11px;
  color: var(--error);
  background: rgba(184,50,50,0.08);
  border: 1px solid rgba(184,50,50,0.25);
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  line-height: 1.6;
}

.p-actions, .p-confirm { text-align: center; }
.p-confirm p { font-size: 13px; font-weight: 700; margin-bottom: 10px; color: var(--text); }
.p-confirm-btns { display: flex; gap: 10px; justify-content: center; }

.btn-prestige {
  padding: 10px 24px;
  background: linear-gradient(135deg, #C97B2A, #f0a030);
  border: none;
  border-radius: 20px;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.btn-prestige:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-prestige:hover:not(:disabled) { opacity: 0.85; }

.btn-cancel {
  padding: 10px 20px;
  background: var(--surface2);
  border: 1px solid var(--border);
  border-radius: 20px;
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
}

.p-locked {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 12px;
  background: var(--surface2);
  border-radius: 8px;
}
</style>
