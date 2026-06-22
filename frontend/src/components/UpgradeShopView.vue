<template>
  <div class="upgrade-shop">
    <h3>Upgrade-Shop</h3>

    <div v-if="loading" class="shop-loading">Lade...</div>

    <div v-else>
      <!-- Boosts + Kapazität: 2 Spalten -->
      <div class="groups-grid">
        <div v-for="group in topGroups" :key="group.label" class="upgrade-group">
          <div class="group-label">{{ group.label }}</div>
          <div v-for="u in group.items" :key="u.id" class="upgrade-row">
            <div class="upgrade-info">
              <NestedTooltip :content="tooltipContent(u)">
                <div class="upgrade-name">{{ u.name }}</div>
              </NestedTooltip>
              <div class="upgrade-level">
                Stufe {{ u.currentLevel }}<span v-if="u.maxLevel > 0"> / {{ u.maxLevel }}</span>
              </div>
            </div>
            <button class="btn-upgrade" :disabled="!canAfford(u) || atMax(u) || buying === u.id" @click="buy(u)">
              <template v-if="atMax(u)">Max</template>
              <template v-else>{{ fmt(u.nextLevelCost) }} C</template>
            </button>
          </div>
        </div>
      </div>

      <!-- Automatisierung: volle Breite, 2-Spalten-Rows -->
      <div v-for="group in bottomGroups" :key="group.label" class="upgrade-group">
        <div class="group-label">{{ group.label }}</div>
        <div class="auto-grid">
          <div v-for="u in group.items" :key="u.id" class="upgrade-row">
            <div class="upgrade-info">
              <NestedTooltip :content="tooltipContent(u)">
                <div class="upgrade-name">{{ u.name }}</div>
              </NestedTooltip>
              <div class="upgrade-level">
                Stufe {{ u.currentLevel }}<span v-if="u.maxLevel > 0"> / {{ u.maxLevel }}</span>
              </div>
            </div>
            <button class="btn-upgrade" :disabled="!canAfford(u) || atMax(u) || buying === u.id" @click="buy(u)">
              <template v-if="atMax(u)">Max</template>
              <template v-else>{{ fmt(u.nextLevelCost) }} C</template>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { getUpgrades, buyUpgrade, initGame } from '../services/api.js'
import NestedTooltip from './NestedTooltip.vue'

const playerStore = usePlayerStore()
const upgrades = ref([])
const loading  = ref(true)
const buying   = ref(null)

const TYPE_LABELS = {
  BOOST_HARVEST: 'Boosts — Ernte',
  BOOST_BAKE:    'Boosts — Backen',
  AUTOMATION:    'Automatisierung',
  CAPACITY:      'Kapazität',
}

function makeGroups(types) {
  return types
    .map(type => ({ label: TYPE_LABELS[type], items: upgrades.value.filter(u => u.type === type) }))
    .filter(g => g.items.length > 0)
}

const topGroups    = computed(() => makeGroups(['BOOST_HARVEST', 'BOOST_BAKE', 'CAPACITY']))
const bottomGroups = computed(() => makeGroups(['AUTOMATION']))

function canAfford(u) { return playerStore.cookies >= u.nextLevelCost }
function atMax(u)     { return u.maxLevel > 0 && u.currentLevel >= u.maxLevel }
function fmt(v)       { return Number(v).toFixed(2) }

function tooltipContent(u) {
  const nextLvl = u.currentLevel + 1
  return [
    { text: u.description },
    { text: ` | Stufe ${nextLvl}: +${u.effectPerLevel * nextLvl} Effekt` },
    { text: ' | Kosten: ', tooltip: `Formel: ${u.nextLevelCost.toFixed(0)} × 1.15^Stufe` },
    { text: `${fmt(u.nextLevelCost)} C` },
  ]
}

async function load() {
  loading.value = true
  try { upgrades.value = await getUpgrades(playerStore.steamId) }
  finally { loading.value = false }
}

async function buy(u) {
  buying.value = u.id
  try {
    upgrades.value = await buyUpgrade(playerStore.steamId, u.id)
    // Spieler-Daten nach Kauf neu laden
    const data = await initGame(playerStore.steamId, 1)
    playerStore.updateFromDto(data.user)
  } catch (e) {
    alert(e.message)
  } finally {
    buying.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.upgrade-shop { min-width: 360px; }
h3 { margin-bottom: 16px; color: var(--text); }

.shop-loading { color: var(--text-muted); text-align: center; padding: 24px; }

/* Boosts + Kapazität: 2 Spalten nebeneinander */
.groups-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 32px;
}

/* Automatisierung: 2-Spalten-Grid für die Rows */
.auto-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 32px;
}

.upgrade-group { margin-bottom: 20px; }
.group-label {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-muted);
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border);
}

.upgrade-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
}
.upgrade-row:last-child { border-bottom: none; }

.upgrade-info { flex: 1; min-width: 0; }
.upgrade-name { font-weight: 700; font-size: 13px; color: var(--text); }
.upgrade-desc { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.upgrade-level { font-size: 11px; color: var(--accent); margin-top: 3px; font-weight: 600; }

.btn-upgrade {
  white-space: nowrap;
  padding: 6px 12px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: var(--surface2);
  color: var(--text);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
  transition: background 0.15s;
}
.btn-upgrade:hover:not(:disabled) { background: var(--accent); color: #fff; border-color: var(--accent); }
.btn-upgrade:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
