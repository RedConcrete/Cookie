<template>
  <div class="us-root">
    <div v-if="loading" class="us-loading">Lade...</div>

    <template v-else>
      <div class="us-groups">
        <div v-for="group in topGroups" :key="group.label" class="us-group">
          <div class="us-group-label">{{ group.label.toUpperCase() }}</div>
          <div v-for="u in group.items" :key="u.id" class="us-row">
            <div class="us-info">
              <NestedTooltip :content="tooltipContent(u)">
                <div class="us-name">{{ u.name }}</div>
              </NestedTooltip>
              <div class="us-level">STUFE {{ u.currentLevel }}<span v-if="u.maxLevel > 0"> / {{ u.maxLevel }}</span></div>
            </div>
            <button class="px-btn px-btn-accent" :disabled="!canAfford(u) || atMax(u) || buying === u.id" @click="buy(u)">
              <template v-if="atMax(u)">MAX</template>
              <template v-else>{{ fmt(u.nextLevelCost) }}<PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" /></template>
            </button>
          </div>
        </div>
      </div>

      <div v-for="group in bottomGroups" :key="group.label" class="us-group">
        <div class="us-group-label">{{ group.label.toUpperCase() }}</div>
        <div class="us-auto-grid">
          <div v-for="u in group.items" :key="u.id" class="us-row">
            <div class="us-info">
              <NestedTooltip :content="tooltipContent(u)">
                <div class="us-name">{{ u.name }}</div>
              </NestedTooltip>
              <div class="us-level">STUFE {{ u.currentLevel }}<span v-if="u.maxLevel > 0"> / {{ u.maxLevel }}</span></div>
            </div>
            <button class="px-btn px-btn-accent" :disabled="!canAfford(u) || atMax(u) || buying === u.id" @click="buy(u)">
              <template v-if="atMax(u)">MAX</template>
              <template v-else>{{ fmt(u.nextLevelCost) }}<PixelIcon name="cookie" :size="12" style="margin-left:5px;vertical-align:-2px" /></template>
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { getUpgrades, buyUpgrade, initGame } from '../services/api.js'
import NestedTooltip from './NestedTooltip.vue'
import PixelIcon from './pixel/PixelIcon.vue'

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
.us-root { min-width: 360px; padding: 18px; display: flex; flex-direction: column; gap: 16px; }
.us-loading { color: var(--px-tan-ink); text-align: center; padding: 24px; }

.us-groups { display: grid; grid-template-columns: 1fr 1fr; gap: 0 32px; }
.us-auto-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 32px; }

.us-group { margin-bottom: 6px; }
.us-group-label {
  font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd);
  letter-spacing: 1px; border-bottom: 3px solid var(--px-tan); padding-bottom: 6px; margin-bottom: 10px;
}
.us-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 0; }
.us-name  { font-size: 16px; font-weight: 600; color: var(--px-ink-txt); cursor: help; }
.us-level { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-orange); margin-top: 3px; }
</style>
