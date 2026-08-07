<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="ld-panel">
      <div class="ld-head">
        <PixelIcon name="lager" :size="28" />
        <div class="ld-head-title">{{ t('lagerDialog.title', { level: lagerLevel }) }}</div>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <div class="ld-body">
        <!-- Capacity overview -->
        <div class="ld-cap-row">
          <div class="ld-cap-label">{{ t('lagerDialog.capacityLabel') }}</div>
          <div class="ld-cap-val">{{ fmtK(totalResources) }} / {{ fmtK(playerStore.totalResourceCap) }}</div>
        </div>
        <div class="ld-bar-outer">
          <div class="ld-bar-fill" :style="{ width: totalPct + '%', background: totalPct > 90 ? 'var(--px-red)' : 'var(--px-gold)' }"></div>
        </div>

        <!-- Per-resource rows -->
        <div class="ld-resources">
          <div v-for="r in resourceRows" :key="r.key" class="ld-res-row">
            <PixelIcon :name="r.icon" :size="16" />
            <div class="ld-res-name">{{ r.label }}</div>
            <div class="ld-res-bar-wrap">
              <div class="ld-res-bar">
                <div class="ld-res-fill" :style="{ width: r.pct + '%' }"></div>
              </div>
            </div>
            <div class="ld-res-val">{{ r.amount.toFixed(1) }}</div>
            <div class="ld-res-price">{{ r.price.toFixed(3) }} C</div>
          </div>
        </div>

        <!-- Wage info -->
        <div class="ld-info">
          <div class="ld-info-text">
            {{ t('lagerDialog.wageLine', { level: lagerLevel, wage: lagerWage.toFixed(1) }) }}
            <span v-if="lagerLevel === 1" class="ld-free">{{ t('lagerDialog.free') }}</span>
          </div>
        </div>

        <!-- Upgrade -->
        <div class="ld-upgrade">
          <div class="ld-upgrade-info">
            <div class="ld-upgrade-label">{{ t('lagerDialog.upgradeLevelLine', { from: lagerLevel, to: lagerLevel + 1 }) }}</div>
            <div class="ld-upgrade-desc">{{ t('lagerDialog.upgradeDesc', { bonus: fmtK(storageBonus) }) }} {{ upgradeCost.toFixed(0) }} <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" /></div>
          </div>
          <button
            class="px-btn px-btn-accent"
            :disabled="upgrading || playerStore.cookies < upgradeCost"
            @click="upgradeLager"
          >{{ t('lagerDialog.upgradeBtn') }}</button>
        </div>
        <div v-if="notice" class="ld-notice" :class="{ error: noticeError }">{{ notice }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { buyBuilding } from '../services/api.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const audio = useAudio()
const { t } = useI18n()

onMounted(() => audio.playBookOpen())

const upgrading   = ref(false)
const notice      = ref('')
const noticeError = ref(false)

const lagerData    = computed(() => playerStore.ownedBuildings.find(b => b.id === 'lager'))
const lagerLevel   = computed(() => lagerData.value?.level ?? 1)
const upgradeCost  = computed(() => lagerData.value?.nextLevelCost ?? 800)
const storageBonus = computed(() => lagerData.value?.storageCapBonus ?? 1000)
const lagerWage    = computed(() => lagerData.value?.wagePerMin ?? 0)

const RESOURCES = [
  { key: 'sugar',     labelKey: 'lagerDialog.resSugar',     icon: 'zucker', name: 'SUGAR'     },
  { key: 'flour',     labelKey: 'lagerDialog.resFlour',     icon: 'mehl',   name: 'FLOUR'     },
  { key: 'eggs',      labelKey: 'lagerDialog.resEggs',      icon: 'eier',   name: 'EGGS'      },
  { key: 'butter',    labelKey: 'lagerDialog.resButter',    icon: 'butter', name: 'BUTTER'    },
  { key: 'chocolate', labelKey: 'lagerDialog.resChocolate', icon: 'schoko', name: 'CHOCOLATE' },
  { key: 'milk',      labelKey: 'lagerDialog.resMilk',      icon: 'milch',  name: 'MILK'      },
]

const resourceRows = computed(() => RESOURCES.map(r => ({
  ...r,
  label: t(r.labelKey),
  amount: playerStore[r.key] ?? 0,
  pct: Math.min(100, ((playerStore[r.key] ?? 0) / playerStore.totalResourceCap) * 100),
  price: marketStore.priceOf(r.name),
})))

const totalResources = computed(() =>
  RESOURCES.reduce((s, r) => s + (playerStore[r.key] ?? 0), 0)
)
const totalPct = computed(() =>
  Math.min(100, (totalResources.value / playerStore.totalResourceCap) * 100)
)

function fmtK(v) {
  if (v >= 1000) return (v / 1000).toFixed(1) + 'K'
  return Number(v).toFixed(0)
}

async function upgradeLager() {
  if (upgrading.value) return
  upgrading.value = true
  notice.value = ''
  try {
    const updated = await buyBuilding(playerStore.steamId, 'lager')
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
    // Refresh cap in store
    const newLager = updated.find(b => b.id === 'lager')
    if (newLager) playerStore.totalResourceCap = 100 + newLager.level * 1000
    notice.value = t('lagerDialog.upgradeSuccess', { level: lagerLevel.value })
    noticeError.value = false
  } catch {
    notice.value = t('lagerDialog.notEnoughCookies')
    noticeError.value = true
  } finally {
    upgrading.value = false
    setTimeout(() => { notice.value = '' }, 2500)
  }
}
</script>

<style scoped>
.ld-panel {
  width: 520px; max-width: 96vw;
  background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
}
.ld-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3);
}
.ld-head-title { font-family: 'Silkscreen', monospace; font-size: 13px; color: var(--px-ink-txt); flex: 1; }

.ld-body { padding: 18px; display: flex; flex-direction: column; gap: 14px; }

.ld-cap-row { display: flex; justify-content: space-between; align-items: center; }
.ld-cap-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); }
.ld-cap-val   { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-paper-txt); }
.ld-bar-outer { height: 14px; background: var(--px-ink); border: 3px solid var(--px-ink); }
.ld-bar-fill  { height: 100%; transition: width 0.3s; }

.ld-resources { display: flex; flex-direction: column; gap: 6px; }
.ld-res-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.ld-res-name { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-ink-txt); min-width: 68px; }
.ld-res-bar-wrap { flex: 1; }
.ld-res-bar { height: 8px; background: var(--px-ink); border: 2px solid var(--px-ink); }
.ld-res-fill { height: 100%; background: var(--px-gold); }
.ld-res-val   { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-gold-txt); min-width: 48px; text-align: right; }
.ld-res-price { font-family: 'Silkscreen', monospace; font-size: 8px; color: var(--px-muted); min-width: 52px; text-align: right; }

.ld-info { padding: 10px 12px; background: #fff1a9; border: 3px solid var(--px-orange); font-size: 13px; color: var(--px-wood-lt); line-height: 1.5; }
.ld-info-text  { font-size: 12px; }
.ld-free { color: var(--px-green-txt); font-weight: bold; }

.ld-upgrade { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.ld-upgrade-info { flex: 1; }
.ld-upgrade-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-gold); }
.ld-upgrade-desc  { font-size: 13px; color: var(--px-tan-ink); margin-top: 3px; }
.ld-notice { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-green-txt); }
.ld-notice.error  { color: var(--px-red); }
</style>
