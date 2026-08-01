<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="cd-panel">
      <div class="cd-head">
        <PixelIcon name="einw" :size="28" />
        <div class="cd-head-title">EINWOHNER</div>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <div class="cd-body">
        <!-- Status -->
        <div class="cd-section">
          <div class="cd-stat-row">
            <span class="cd-stat-label">Gesamt</span>
            <span class="cd-stat-val">{{ playerStore.ownedCitizens }} / {{ playerStore.maxCitizens }}</span>
          </div>
          <div class="cd-stat-row">
            <span class="cd-stat-label">Zugewiesen</span>
            <span class="cd-stat-val">{{ playerStore.assignedCitizens }}</span>
          </div>
          <div class="cd-stat-row">
            <span class="cd-stat-label">Idle</span>
            <span class="cd-stat-val" :class="{ 'cd-idle': playerStore.idleCitizens > 0 }">{{ playerStore.idleCitizens }}</span>
          </div>
          <div class="cd-stat-row">
            <span class="cd-stat-label">Kosten/Einw.</span>
            <span class="cd-stat-val">50 <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" /></span>
          </div>
        </div>

        <!-- Rathaus requirements -->
        <div v-if="playerStore.maxCitizens === 0" class="cd-hint cd-hint-warn">
          Baue zuerst das <b>RATHAUS</b> um Einwohner kaufen zu können.<br>
          Rathaus Stufe 1 = 4 Einwohner-Slots.
        </div>
        <div v-else class="cd-hint">
          Jedes Rathaus-Level gibt 4 Einwohner-Slots.<br>
          Einwohner kosten je 50 <PixelIcon name="cookie" :size="12" style="vertical-align:-2px" />.
        </div>

        <!-- Buy controls -->
        <div class="cd-buy" v-if="playerStore.maxCitizens > 0">
          <div class="cd-buy-label">KAUFEN</div>
          <div class="cd-buy-row">
            <button class="px-btn" :disabled="buyCount <= 1" @click="buyCount = Math.max(1, buyCount - 1)">−</button>
            <div class="cd-buy-count">{{ buyCount }}x</div>
            <button class="px-btn" :disabled="buyCount >= canBuyMore" @click="buyCount = Math.min(canBuyMore, buyCount + 1)">+</button>
            <button
              class="px-btn px-btn-accent cd-buy-btn"
              :disabled="buying || canBuyMore <= 0 || playerStore.cookies < buyCost"
              @click="buy"
            >
              KAUFEN · {{ buyCost.toFixed(0) }}<PixelIcon name="cookie" :size="12" style="margin-left:4px;vertical-align:-2px" />
            </button>
          </div>
          <div v-if="notice" class="cd-notice" :class="{ error: noticeError }">{{ notice }}</div>
        </div>

        <!-- Wanderer preview -->
        <div class="cd-wanderers" v-if="playerStore.idleCitizens > 0">
          <div class="cd-wanderers-label">{{ playerStore.idleCitizens }} WANDERN HERUM</div>
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
import { usePlayerStore } from '../stores/player.js'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelWorker from './pixel/PixelWorker.vue'

const emit = defineEmits(['close'])
const playerStore = usePlayerStore()
const audio = useAudio()

onMounted(() => audio.playBookOpen())

const buying     = ref(false)
const notice     = ref('')
const noticeError = ref(false)
const buyCount   = ref(1)

const HATS = ['#5aa0e0', '#b83232', '#3d6b25', '#8b5a2b', '#7a50b0']
const CITIZEN_COST = 50

const canBuyMore = computed(() => Math.max(0, playerStore.maxCitizens - playerStore.ownedCitizens))
const buyCost    = computed(() => buyCount.value * CITIZEN_COST)

async function buy() {
  if (buying.value || canBuyMore.value <= 0) return
  buying.value = true
  notice.value = ''
  try {
    await playerStore.buyCitizenAction(buyCount.value)
    notice.value = `${buyCount.value} Einwohner gekauft!`
    noticeError.value = false
    buyCount.value = 1
  } catch {
    notice.value = 'Nicht genug Cookies oder Fehler.'
    noticeError.value = true
  } finally {
    buying.value = false
    setTimeout(() => { notice.value = '' }, 2500)
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
}
.cd-head-title { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); flex: 1; }

.cd-body { padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.cd-section { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.cd-stat-row { display: flex; justify-content: space-between; font-size: 14px; color: var(--px-wood-lt); }
.cd-stat-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); }
.cd-stat-val { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-paper-txt); }
.cd-idle { color: var(--px-red) !important; }

.cd-hint { padding: 10px 12px; background: #fff3c4; border: 3px solid var(--px-orange); font-size: 13px; color: var(--px-wood-lt); line-height: 1.5; }
.cd-hint-warn { background: #ffe0e0; border-color: var(--px-red); }

.cd-buy { display: flex; flex-direction: column; gap: 10px; }
.cd-buy-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); }
.cd-buy-row { display: flex; align-items: center; gap: 8px; }
.cd-buy-count { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-paper-txt); min-width: 32px; text-align: center; }
.cd-buy-btn { flex: 1; font-size: 10px; }
.cd-buy-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.cd-notice { font-size: 12px; color: #3d6b25; font-family: 'Silkscreen', monospace; }
.cd-notice.error { color: var(--px-red); }

.cd-wanderers { padding: 12px; background: var(--px-cream); border: 3px solid var(--px-brown2); }
.cd-wanderers-label { font-family: 'Silkscreen', monospace; font-size: 9px; color: var(--px-tan-hd); margin-bottom: 10px; }
.cd-wanderers-row { display: flex; gap: 10px; align-items: flex-end; }
.cd-wanderer-icon { width: 20px; }
.cd-wanderers-more { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-muted); }
</style>
