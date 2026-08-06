<template>
  <div class="us-root">
    <div v-if="loading" class="us-loading"><LoadingIndicator /></div>

    <template v-else>
      <div class="us-groups">
        <div v-for="group in topGroups" :key="group.label" class="us-group">
          <div class="us-group-label">{{ group.label.toUpperCase() }}</div>
          <div v-for="u in group.items" :key="u.id" class="us-row">
            <div class="us-info">
              <PixelInfoPopover :rows="tooltipRows(u)" :title="u.name" :note="tooltipNote(u)" :width="260">
                <div class="us-name">{{ u.name }}</div>
              </PixelInfoPopover>
              <div class="us-level">{{ t('upgradeShopView.level') }} {{ u.currentLevel }}<span v-if="u.maxLevel > 0"> / {{ u.maxLevel }}</span></div>
            </div>
            <button class="px-btn px-btn-accent" :disabled="!canAfford(u) || atMax(u) || buying === u.id" @click="buy(u)">
              <template v-if="atMax(u)">{{ t('common.max') }}</template>
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
              <PixelInfoPopover :rows="tooltipRows(u)" :title="u.name" :note="tooltipNote(u)" :width="260">
                <div class="us-name">{{ u.name }}</div>
              </PixelInfoPopover>
              <div class="us-level">{{ t('upgradeShopView.level') }} {{ u.currentLevel }}<span v-if="u.maxLevel > 0"> / {{ u.maxLevel }}</span></div>
            </div>
            <button class="px-btn px-btn-accent" :disabled="!canAfford(u) || atMax(u) || buying === u.id" @click="buy(u)">
              <template v-if="atMax(u)">{{ t('common.max') }}</template>
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
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { buyUpgrade, initGame } from '../services/api.js'
import LoadingIndicator from './pixel/LoadingIndicator.vue'
import PixelInfoPopover from './pixel/PixelInfoPopover.vue'
import PixelIcon from './pixel/PixelIcon.vue'

const { t } = useI18n()
const playerStore = usePlayerStore()
const upgrades = computed(() => playerStore.upgrades)
const loading  = ref(true)
const buying   = ref(null)

const TYPE_LABELS = {
  BOOST_HARVEST: 'upgradeShopView.typeBoostHarvest',
  BOOST_BAKE:    'upgradeShopView.typeBoostBake',
  AUTOMATION:    'upgradeShopView.typeAutomation',
  CAPACITY:      'upgradeShopView.typeCapacity',
}

function makeGroups(types) {
  return types
    .map(type => ({ label: t(TYPE_LABELS[type]), items: upgrades.value.filter(u => u.type === type) }))
    .filter(g => g.items.length > 0)
}

const topGroups    = computed(() => makeGroups(['BOOST_HARVEST', 'BOOST_BAKE', 'CAPACITY']))
const bottomGroups = computed(() => makeGroups(['AUTOMATION']))

function canAfford(u) { return playerStore.cookies >= u.nextLevelCost }
function atMax(u)     { return u.maxLevel > 0 && u.currentLevel >= u.maxLevel }
function fmt(v)       { return Number(v).toFixed(2) }

function tooltipRows(u) {
  const nextLvl = u.currentLevel + 1
  return [
    { k: t('upgradeShopView.tooltipLevel', { level: nextLvl }), v: t('upgradeShopView.tooltipEffect', { effect: u.effectPerLevel * nextLvl }), color: 'g' },
    { k: t('upgradeShopView.tooltipCost'), v: `${fmt(u.nextLevelCost)} C`, color: 'y' },
  ]
}

function tooltipNote(u) {
  return `${u.description} ${t('upgradeShopView.costFormula', { cost: u.nextLevelCost.toFixed(0) })}`
}

async function load() {
  loading.value = true
  try { await playerStore.loadUpgrades() }
  finally { loading.value = false }
}

async function buy(u) {
  buying.value = u.id
  try {
    playerStore.upgrades = await buyUpgrade(playerStore.steamId, u.id)
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
