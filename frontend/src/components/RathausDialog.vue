<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="rh-panel" :style="dialogStyle">
      <div class="rh-head" @pointerdown="onDragStart">
        <PixelIcon name="haus" :size="28" />
        <div class="rh-head-title">{{ t('rathausDialog.title', { level: rathausLevel }) }}</div>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="rh-body">
        <!-- Stats row -->
        <div class="rh-stats">
          <div class="rh-stat">
            <div class="rh-stat-label">{{ t('rathausDialog.statPopulation') }}</div>
            <div class="rh-stat-val">{{ playerStore.ownedCitizens }} / {{ playerStore.maxCitizens }}</div>
          </div>
          <div class="rh-stat">
            <div class="rh-stat-label">{{ t('rathausDialog.statAssigned') }}</div>
            <div class="rh-stat-val">{{ playerStore.assignedCitizens }}</div>
          </div>
          <div class="rh-stat">
            <div class="rh-stat-label">{{ t('rathausDialog.statIdle') }}</div>
            <div class="rh-stat-val" :class="{ 'rh-idle': playerStore.idleCitizens > 0 }">{{ playerStore.idleCitizens }}</div>
          </div>
          <div class="rh-stat">
            <div class="rh-stat-label">{{ t('rathausDialog.statWage') }}</div>
            <div class="rh-stat-val rh-cost">{{ playerStore.totalWage.toFixed(1) }} C/MIN</div>
          </div>
          <div class="rh-stat">
            <div class="rh-stat-label">{{ t('rathausDialog.statDebt') }}</div>
            <div class="rh-stat-val" :class="{ 'rh-cost': playerStore.cookies < 0 }">{{ playerStore.cookies < 0 ? fmt(-playerStore.cookies) + ' C' : '—' }}</div>
          </div>
        </div>
        <div v-if="playerStore.cookies < 0" class="rh-debt-hint">
          {{ t('rathausDialog.debtHint', { rate: (playerStore.debtInterestRate * 100).toFixed(1), limit: fmt(playerStore.debtLimit) }) }}
        </div>

        <!-- Tabs: worker assignment vs. billing history -->
        <div class="rh-tabs">
          <button class="rh-tab" :class="{ active: activeTab === 'assign' }" @click="activeTab = 'assign'">{{ t('rathausDialog.tabAssign') }}</button>
          <button class="rh-tab" :class="{ active: activeTab === 'billing' }" @click="selectBillingTab">{{ t('rathausDialog.tabBilling') }}</button>
        </div>

        <!-- Worker assignment list -->
        <PixelScrollBox v-if="activeTab === 'assign'" class="rh-assign-list">
          <div class="rh-assign-rows">
          <div v-for="b in assignedBuildings" :key="b.id" class="rh-assign-row">
            <PixelIcon :name="b.icon" :size="16" />
            <div class="rh-assign-name">{{ b.title }}</div>
            <div class="rh-assign-workers">
              <div v-for="i in b.workers" :key="i" class="rh-dot rh-dot-active"></div>
              <div v-for="i in (b.maxWorkers - b.workers)" :key="'e'+i" class="rh-dot"></div>
            </div>
            <div class="rh-assign-count">{{ b.workers }}/{{ b.maxWorkers }}</div>
          </div>
          <div v-if="playerStore.idleCitizens > 0" class="rh-assign-row rh-assign-idle">
            <PixelIcon name="einw" :size="16" />
            <div class="rh-assign-name">{{ t('rathausDialog.idleRowName') }}</div>
            <div class="rh-assign-count rh-idle">{{ playerStore.idleCitizens }}</div>
          </div>
          <div v-if="playerStore.ownedCitizens === 0" class="rh-empty">{{ t('rathausDialog.emptyState') }}</div>
          </div>
        </PixelScrollBox>

        <!-- Billing history: one row per actual wage deduction (WageScheduler, ~1/min) -->
        <PixelScrollBox v-else class="rh-assign-list">
          <div class="rh-assign-rows">
          <div v-for="entry in wageHistory" :key="entry.id" class="rh-bill-row">
            <div class="rh-bill-head">
              <div class="rh-bill-time">{{ new Date(entry.createdAtEpochMs).toLocaleTimeString() }}</div>
              <div class="rh-bill-total rh-cost">-{{ fmt(entry.totalAmount) }} C</div>
            </div>
            <div class="rh-bill-breakdown">
              {{ Object.entries(entry.breakdown).map(([id, amount]) => `${buildingTitle(id, t)} ${fmt(amount)}`).join(' · ') }}
            </div>
          </div>
          <div v-if="!loadingHistory && wageHistory.length === 0" class="rh-empty">{{ t('rathausDialog.billingEmpty') }}</div>
          </div>
        </PixelScrollBox>

        <!-- Upgrade section -->
        <div class="rh-upgrade">
          <div class="rh-upgrade-info">
            <div class="rh-upgrade-label">{{ t('rathausDialog.upgradeLevelLine', { from: rathausLevel, to: rathausLevel + 1 }) }}</div>
            <div class="rh-upgrade-desc">{{ t('rathausDialog.upgradeDesc') }} {{ upgradeCost.toFixed(0) }} <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" /></div>
          </div>
          <button
            class="px-btn px-btn-accent"
            :disabled="upgrading || playerStore.cookies < upgradeCost"
            @click="upgradeRathaus"
          ><ShortcutSlot />{{ t('rathausDialog.upgradeBtn') }}</button>
        </div>
        <div v-if="notice" class="rh-notice" :class="{ error: noticeError }">{{ notice }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { buyBuilding, getWageHistory } from '../services/api.js'
import { BUILDING_INFO, buildingTitle } from './buildings/buildingInfo.js'
import { useAudio } from '../composables/useAudio.js'
import { fmt } from '../utils/formatNumber.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { useDraggableDialog } from '../composables/useDraggableDialog.js'

const emit = defineEmits(['close'])
const { dialogStyle, onDragStart } = useDraggableDialog()
const playerStore = usePlayerStore()
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())

const upgrading   = ref(false)
const notice      = ref('')
const noticeError = ref(false)

const activeTab     = ref('assign')
const wageHistory   = ref([])
const loadingHistory = ref(false)

// Historie erst beim ersten Öffnen des Tabs laden (kein Live-Polling nötig -- Dialog wird
// bei Bedarf neu geöffnet, siehe Plan).
async function selectBillingTab() {
  activeTab.value = 'billing'
  if (wageHistory.value.length > 0) return
  loadingHistory.value = true
  try {
    wageHistory.value = await getWageHistory(playerStore.steamId)
  } catch {
    wageHistory.value = []
  } finally {
    loadingHistory.value = false
  }
}

const rathausData = computed(() => playerStore.ownedBuildings.find(b => b.id === 'rathaus'))
const rathausLevel = computed(() => rathausData.value?.level ?? 1)
const upgradeCost  = computed(() => rathausData.value?.nextLevelCost ?? 800)

const assignedBuildings = computed(() =>
  playerStore.ownedBuildings
    .filter(b => b.maxWorkers > 0)
    .map(b => ({ ...b, ...BUILDING_INFO[b.id], title: buildingTitle(b.id, t) }))
)

async function upgradeRathaus() {
  if (upgrading.value) return
  upgrading.value = true
  notice.value = ''
  try {
    const updated = await buyBuilding(playerStore.steamId, 'rathaus')
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
    notice.value = t('rathausDialog.upgradeSuccess', { level: rathausLevel.value })
    noticeError.value = false
  } catch {
    notice.value = t('rathausDialog.notEnoughCookies')
    noticeError.value = true
  } finally {
    upgrading.value = false
    setTimeout(() => { notice.value = '' }, 2500)
  }
}
</script>

<style scoped>
.rh-panel {
  width: 500px; max-width: 96vw;
  background: var(--px-cream); border: 4px solid var(--px-ink);
  box-shadow: 0 10px 0 rgba(0,0,0,.45);
}
.rh-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-wood);
  cursor: move; touch-action: none; user-select: none;
}
@media (max-width: 860px) { .rh-head { cursor: default; } }
.rh-head-title { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-cream); flex: 1; }

.rh-body { padding: 18px; display: flex; flex-direction: column; gap: 14px; }

.rh-stats { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.rh-debt-hint { font-size: 11px; color: var(--px-tan-ink); font-style: italic; }
.rh-stat { padding: 10px; background: var(--px-cream); border: 3px solid var(--px-brown2); text-align: center; }
.rh-stat-label { font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-wood); margin-bottom: 4px; }
.rh-stat-val   { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }
.rh-idle { color: var(--px-red) !important; }
.rh-cost { color: var(--px-red) !important; }

.rh-tabs { display: flex; gap: 6px; }
.rh-tab {
  flex: 1; padding: 8px; border: 3px solid var(--px-brown2); background: var(--px-cream);
  font-family: 'Silkscreen', monospace; font-size: 10px; letter-spacing: 1px; color: var(--px-tan-ink);
  cursor: pointer;
}
.rh-tab.active { background: var(--px-gold); border-color: var(--px-ink); color: var(--px-ink-txt); }

.rh-assign-list { max-height: 240px; }
.rh-assign-rows { display: flex; flex-direction: column; gap: 6px; }
.rh-assign-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.rh-assign-idle { opacity: 0.75; }
.rh-assign-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-ink-txt); flex: 1; }
.rh-assign-workers { display: flex; gap: 4px; }
.rh-dot { width: 10px; height: 10px; background: var(--px-brown2); border: 2px solid var(--px-ink); }
.rh-dot-active { background: var(--px-green-txt); border-color: #56642e; }
.rh-assign-count { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-wood2); min-width: 28px; text-align: right; }
.rh-empty { font-size: 13px; color: var(--px-wood2); padding: 12px; text-align: center; }

.rh-bill-row { padding: 8px 10px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.rh-bill-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.rh-bill-time { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-ink); }
.rh-bill-total { font-family: 'Silkscreen', monospace; font-size: 11px; }
.rh-bill-breakdown { font-size: 11px; color: var(--px-tan-ink); margin-top: 4px; }

.rh-upgrade { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.rh-upgrade-info { flex: 1; }
.rh-upgrade-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-wood); }
.rh-upgrade-desc  { font-size: 13px; color: var(--px-wood); margin-top: 3px; }
.rh-notice { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-green-panel); }
.rh-notice.error  { color: var(--px-red); }
</style>
