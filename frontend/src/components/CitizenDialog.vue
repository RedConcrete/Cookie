<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="cd-panel" :style="dialogStyle">
      <div class="cd-head" @pointerdown="onDragStart">
        <PixelIcon name="einw" :size="28" />
        <div class="cd-head-title">{{ t('citizenDialog.title') }}</div>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="cd-body">
        <!-- Status -->
        <div class="cd-section">
          <div class="cd-stat-row">
            <span class="cd-stat-label">{{ t('citizenDialog.total') }}</span>
            <span class="cd-stat-val">{{ playerStore.ownedCitizens }} / {{ playerStore.maxCitizens }}</span>
          </div>
          <div class="cd-stat-row">
            <span class="cd-stat-label">{{ t('citizenDialog.assigned') }}</span>
            <span class="cd-stat-val">{{ playerStore.assignedCitizens }}</span>
          </div>
          <div class="cd-stat-row">
            <span class="cd-stat-label">{{ t('citizenDialog.idle') }}</span>
            <span class="cd-stat-val" :class="{ 'cd-idle': playerStore.idleCitizens > 0 }">{{ playerStore.idleCitizens }}</span>
          </div>
          <div class="cd-stat-row">
            <span class="cd-stat-label">{{ t('citizenDialog.costPerCitizen') }}</span>
            <span class="cd-stat-val">{{ CITIZEN_COST }} <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" /></span>
          </div>
        </div>

        <!-- Rathaus requirements -->
        <div v-if="playerStore.maxCitizens === 0" class="cd-hint cd-hint-warn">
          {{ t('citizenDialog.needTownHallBefore') }} <b>{{ t('citizenDialog.townHall') }}</b> {{ t('citizenDialog.needTownHallAfter') }}<br>
          {{ t('citizenDialog.townHallLevel1Slots') }}
        </div>
        <div v-else class="cd-hint">
          {{ t('citizenDialog.rathausGivesSlots') }}<br>
          {{ t('citizenDialog.citizenCostHint', { cost: CITIZEN_COST }) }} <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" />.
        </div>

        <!-- Buy controls -->
        <div class="cd-buy" v-if="playerStore.maxCitizens > 0">
          <div class="cd-buy-label">{{ t('citizenDialog.buyLabel') }}</div>
          <div class="cd-buy-row">
            <button class="px-btn" :disabled="buyCount <= 1" @click="buyCount = Math.max(1, buyCount - 1)"><ShortcutSlot />−</button>
            <div class="cd-buy-count">{{ buyCount }}x</div>
            <button class="px-btn" :disabled="buyCount >= canBuyMore" @click="buyCount = Math.min(canBuyMore, buyCount + 1)"><ShortcutSlot />+</button>
            <button
              class="px-btn px-btn-accent cd-buy-btn"
              :disabled="buying || canBuyMore <= 0 || playerStore.cookies < buyCost"
              @click="buy"
            >
              <ShortcutSlot />{{ t('citizenDialog.buyButton', { cost: buyCost.toFixed(0) }) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" />
            </button>
          </div>
          <div v-if="notice" class="cd-notice" :class="{ error: noticeError }">{{ notice }}</div>
        </div>

        <!-- Fire controls -->
        <div class="cd-buy" v-if="playerStore.idleCitizens > 0">
          <div class="cd-buy-label">{{ t('citizenDialog.fireLabel') }}</div>
          <div class="cd-hint">{{ t('citizenDialog.fireHint') }}</div>
          <div class="cd-buy-row">
            <button class="px-btn" :disabled="fireCount <= 1" @click="fireCount = Math.max(1, fireCount - 1)"><ShortcutSlot />−</button>
            <div class="cd-buy-count">{{ fireCount }}x</div>
            <button class="px-btn" :disabled="fireCount >= playerStore.idleCitizens" @click="fireCount = Math.min(playerStore.idleCitizens, fireCount + 1)"><ShortcutSlot />+</button>
            <button
              class="px-btn px-btn-sell cd-buy-btn"
              :disabled="firing || fireCount > playerStore.idleCitizens"
              @click="fire"
            >
              <ShortcutSlot />{{ t('citizenDialog.fireButton', { refund: fireRefund.toFixed(0) }) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" />
            </button>
          </div>
          <div v-if="fireNotice" class="cd-notice" :class="{ error: fireNoticeError }">{{ fireNotice }}</div>
        </div>

        <!-- Wanderer preview -->
        <div class="cd-wanderers" v-if="playerStore.idleCitizens > 0">
          <div class="cd-wanderers-label">{{ t('citizenDialog.wanderersLabel', { count: playerStore.idleCitizens }) }}</div>
          <div class="cd-wanderers-row">
            <div v-for="i in Math.min(5, playerStore.idleCitizens)" :key="i" class="cd-wanderer-icon">
              <PixelWorker anim="bob" :dur="0.8 + i * 0.1" :hat="HATS[i % HATS.length]" />
            </div>
            <span v-if="playerStore.idleCitizens > 5" class="cd-wanderers-more">+{{ playerStore.idleCitizens - 5 }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelWorker from './pixel/PixelWorker.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { useDraggableDialog } from '../composables/useDraggableDialog.js'

const emit = defineEmits(['close'])
const { dialogStyle, onDragStart } = useDraggableDialog()
const playerStore = usePlayerStore()
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())

const buying     = ref(false)
const notice     = ref('')
const noticeError = ref(false)
const buyCount   = ref(1)

const HATS = ['#6dba79', '#b74132', '#56642e', '#a15c34', '#6f6e72']
const CITIZEN_COST = 50
// Server erstattet 50% des urspruenglichen Preises (siehe BuildingService#SELL_REFUND_RATE) --
// hier nur eine grobe Vorschau wie CITIZEN_COST selbst, nicht die echte Preiskurve.
const FIRE_REFUND_RATE = 0.5

const canBuyMore = computed(() => Math.max(0, playerStore.maxCitizens - playerStore.ownedCitizens))
const buyCost    = computed(() => buyCount.value * CITIZEN_COST)

async function buy() {
  if (buying.value || canBuyMore.value <= 0) return
  buying.value = true
  notice.value = ''
  try {
    await playerStore.buyCitizenAction(buyCount.value)
    notice.value = t('citizenDialog.bought', { count: buyCount.value })
    noticeError.value = false
    buyCount.value = 1
  } catch {
    notice.value = t('citizenDialog.buyError')
    noticeError.value = true
  } finally {
    buying.value = false
    setTimeout(() => { notice.value = '' }, 2500)
  }
}

const firing         = ref(false)
const fireNotice      = ref('')
const fireNoticeError = ref(false)
const fireCount       = ref(1)
const fireRefund = computed(() => fireCount.value * CITIZEN_COST * FIRE_REFUND_RATE)

async function fire() {
  if (firing.value || fireCount.value > playerStore.idleCitizens) return
  firing.value = true
  fireNotice.value = ''
  try {
    await playerStore.fireCitizenAction(fireCount.value)
    fireNotice.value = t('citizenDialog.fired', { count: fireCount.value })
    fireNoticeError.value = false
    fireCount.value = 1
  } catch {
    fireNotice.value = t('citizenDialog.fireError')
    fireNoticeError.value = true
  } finally {
    firing.value = false
    setTimeout(() => { fireNotice.value = '' }, 2500)
  }
}
</script>

<style scoped>
.cd-panel {
  width: 420px; max-width: 95vw;
  background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
}
.cd-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3);
  cursor: move; touch-action: none; user-select: none;
}
@media (max-width: 860px) { .cd-head { cursor: default; } }
.cd-head-title { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); flex: 1; }

.cd-body { padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.cd-section { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.cd-stat-row { display: flex; justify-content: space-between; font-size: 14px; color: var(--px-wood2); }
.cd-stat-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-wood); }
.cd-stat-val { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-ink-txt); }
.cd-idle { color: var(--px-red) !important; }

.cd-hint { padding: 10px 12px; background: #fff1a9; border: 3px solid var(--px-orange); font-size: 13px; color: var(--px-wood2); line-height: 1.5; }
.cd-hint-warn { background: #fff1a9; border-color: var(--px-red); }

.cd-buy { display: flex; flex-direction: column; gap: 10px; }
.cd-buy-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-wood); }
.cd-buy-row { display: flex; align-items: center; gap: 8px; }
.cd-buy-count { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); min-width: 32px; text-align: center; }
.cd-buy-btn { flex: 1; font-size: 10px; }
.cd-buy-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.cd-notice { font-size: 12px; color: var(--px-wood); font-family: 'Silkscreen', monospace; }
.cd-notice.error { color: var(--px-wood); }

.cd-wanderers { padding: 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.cd-wanderers-label { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-wood2); margin-bottom: 10px; }
.cd-wanderers-row { display: flex; gap: 10px; align-items: flex-end; }
.cd-wanderer-icon { width: 20px; }
.cd-wanderers-more { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-wood2); }
</style>
