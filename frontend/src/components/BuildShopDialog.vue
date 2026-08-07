<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="bs-panel">
      <div class="bs-head">
        <PixelIcon name="lager" :size="28" />
        <div class="bs-head-title">{{ t('buildShopDialog.title') }}</div>
        <button class="px-close" @click="emit('close')"><ShortcutSlot />&times;</button>
      </div>

      <div class="bs-notice" v-if="notice" :class="{ error: noticeError }">{{ notice }}</div>

      <PixelScrollBox class="bs-list">
        <div
          v-for="b in buildings" :key="b.id"
          class="bs-row"
          :class="{ owned: b.level > 0 && !b.canUpgrade }"
        >
          <div class="bs-row-icon">
            <PixelIcon :name="BUILDING_INFO[b.id]?.icon ?? 'haus'" :size="28" />
          </div>
          <div class="bs-row-info">
            <div class="bs-row-name">{{ b.name }}</div>
            <div class="bs-row-sub">
              <span class="bs-wage">{{ t('buildShopDialog.wage', { amount: b.wagePerMin }) }}</span>
              <span v-if="b.storageCapBonus > 0" class="bs-cap">{{ t('buildShopDialog.storageBonus', { amount: fmtK(b.storageCapBonus) }) }}</span>
            </div>
            <div v-if="b.level > 0" class="bs-level">{{ t('buildShopDialog.level', { level: b.level }) }}</div>
          </div>
          <div class="bs-row-action">
            <template v-if="b.level > 0 && !b.canUpgrade">
              <div class="bs-owned-badge"><PixelIcon name="check" :size="10" style="vertical-align:-1px;margin-right:3px" />{{ t('buildShopDialog.built') }}</div>
            </template>
            <template v-else>
              <div class="bs-cost">{{ fmtK(b.nextLevelCost) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" /></div>
              <button
                class="px-btn px-btn-accent bs-buy-btn"
                :disabled="buying === b.id || playerStore.cookies < b.nextLevelCost"
                @click="buy(b.id)"
              >
                {{ b.level === 0 ? t('buildShopDialog.build') : t('buildShopDialog.upgrade') }}
              </button>
            </template>
          </div>
        </div>
      </PixelScrollBox>

      <div class="bs-footer">
        <span>{{ t('buildShopDialog.cookiesLabel') }} <b>{{ fmt(playerStore.cookies) }}</b></span>
        <span>{{ t('buildShopDialog.storageCapLabel') }} <b>{{ fmtK(playerStore.totalResourceCap) }}</b></span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { buyBuilding } from '../services/api.js'
import { BUILDING_INFO } from './buildings/buildingInfo.js'
import { fmt } from '../utils/formatNumber.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const audio = useAudio()
const { t } = useI18n()

const buying      = ref(null)
const notice      = ref('')
const noticeError = ref(false)

// Always reflects live store data (reactive)
const buildings = computed(() => playerStore.ownedBuildings)

function fmtK(v) {
  if (v >= 1000) return (v / 1000).toFixed(1) + 'K'
  return String(Math.round(v))
}

async function buy(buildingId) {
  if (buying.value) return
  buying.value = buildingId
  notice.value = ''
  try {
    const updated = await buyBuilding(playerStore.steamId, buildingId)
    // Update store directly so the computed buildings refreshes
    playerStore.ownedBuildings.splice(0, playerStore.ownedBuildings.length, ...updated)
    const b = updated.find(x => x.id === buildingId)
    notice.value = t('buildShopDialog.built_notice', { name: b?.name ?? buildingId })
    noticeError.value = false
  } catch (e) {
    const cost = buildings.value.find(x => x.id === buildingId)?.nextLevelCost?.toFixed(0) ?? '?'
    notice.value = t('buildShopDialog.notEnoughCookies', { cost })
    noticeError.value = true
  } finally {
    buying.value = null
    setTimeout(() => { notice.value = '' }, 3000)
  }
}

onMounted(async () => {
  audio.playBookOpen()
  if (!playerStore.ownedBuildings.length) await playerStore.loadBuildings()
})
</script>

<style scoped>
.bs-panel {
  width: 560px; max-width: 96vw; max-height: 85vh;
  background: var(--px-cream2); border: 4px solid var(--px-ink);
  box-shadow: inset -3px -3px 0 var(--px-tan), inset 3px 3px 0 var(--px-cream), 0 10px 0 rgba(0,0,0,.45);
  display: flex; flex-direction: column;
}

.bs-head {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 4px solid var(--px-ink); background: var(--px-cream3); flex-shrink: 0;
}
.bs-head-title { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); flex: 1; }

.bs-notice {
  padding: 8px 18px; font-size: 13px; color: #56642e; background: #fff1a9;
  border-bottom: 3px solid var(--px-green); font-family: 'Silkscreen', monospace; font-size: 10px;
}
.bs-notice.error { color: #764032; background: #fff1a9; border-color: var(--px-red); }

.bs-list { flex: 1 1 auto; min-height: 0; }

.bs-row {
  display: flex; align-items: center; gap: 12px; padding: 12px 18px;
  border-bottom: 3px solid var(--px-brown2);
}
.bs-row.owned { opacity: 0.65; }
.bs-row:last-child { border-bottom: none; }

.bs-row-icon { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: var(--px-cream3); border: 3px solid var(--px-brown2); }
.bs-row-info { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.bs-row-name { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-ink-txt); }
.bs-row-sub  { display: flex; gap: 10px; }
.bs-wage     { font-size: 12px; color: var(--px-red); }
.bs-cap      { font-size: 12px; color: var(--px-green-txt); }
.bs-level    { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-gold); }

.bs-row-action { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex-shrink: 0; min-width: 110px; }
.bs-cost { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-gold); display: flex; align-items: center; }
.bs-buy-btn { font-size: 10px; padding: 4px 10px; }
.bs-buy-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.bs-owned-badge { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-green-txt); padding: 4px 8px; background: #fff1a9; border: 2px solid var(--px-green); }

.bs-footer {
  padding: 10px 18px; display: flex; justify-content: space-between;
  border-top: 4px solid var(--px-ink); background: var(--px-cream3);
  font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-muted);
  flex-shrink: 0;
}
.bs-footer b { color: var(--px-gold-txt); }
</style>
