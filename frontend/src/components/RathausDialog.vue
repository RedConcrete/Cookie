<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="rh-panel">
      <div class="rh-head">
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
        </div>

        <!-- Worker assignment list -->
        <div class="rh-section-label">{{ t('rathausDialog.assignSectionLabel') }}</div>
        <PixelScrollBox class="rh-assign-list">
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
import { buyBuilding } from '../services/api.js'
import { BUILDING_INFO, buildingTitle } from './buildings/buildingInfo.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())

const upgrading   = ref(false)
const notice      = ref('')
const noticeError = ref(false)

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
  background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
}
.rh-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3);
}
.rh-head-title { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-ink-txt); flex: 1; }

.rh-body { padding: 18px; display: flex; flex-direction: column; gap: 14px; }

.rh-stats { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 8px; }
.rh-stat { padding: 10px; background: var(--px-cream); border: 3px solid var(--px-brown2); text-align: center; }
.rh-stat-label { font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-tan-hd); margin-bottom: 4px; }
.rh-stat-val   { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-paper-txt); }
.rh-idle { color: var(--px-red) !important; }
.rh-cost { color: var(--px-red) !important; }

.rh-section-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); letter-spacing: 1px; }

.rh-assign-list { max-height: 240px; }
.rh-assign-rows { display: flex; flex-direction: column; gap: 6px; }
.rh-assign-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.rh-assign-idle { opacity: 0.75; }
.rh-assign-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-ink-txt); flex: 1; }
.rh-assign-workers { display: flex; gap: 4px; }
.rh-dot { width: 10px; height: 10px; background: var(--px-brown2); border: 2px solid var(--px-ink); }
.rh-dot-active { background: var(--px-green-txt); border-color: #56642e; }
.rh-assign-count { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-muted); min-width: 28px; text-align: right; }
.rh-empty { font-size: 13px; color: var(--px-muted); padding: 12px; text-align: center; }

.rh-upgrade { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.rh-upgrade-info { flex: 1; }
.rh-upgrade-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); }
.rh-upgrade-desc  { font-size: 13px; color: var(--px-tan-ink); margin-top: 3px; }
.rh-notice { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-green-txt); }
.rh-notice.error  { color: var(--px-red); }
</style>
