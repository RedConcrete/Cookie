<template>
  <div class="rc-inner">

    <!-- ── Left: recipe index ─────────────────────────── -->
    <div class="rc-left">
      <div class="rc-left-title">REZEPTBUCH</div>
      <div class="rc-rule"></div>

      <div v-if="!recipes.length" class="rc-loading">Lade…</div>

      <nav class="rc-index">
        <button
          v-for="(r, i) in recipes" :key="r.id" class="rc-entry"
          :class="{ active: selectedId === r.id }" @click="selectRecipe(r.id)"
        >
          <span class="rc-entry-name">{{ i + 1 }}. {{ r.name }}</span>
          <span class="rc-entry-time">{{ formatDuration(r.bakeDurationSeconds) }}</span>
        </button>
      </nav>
    </div>

    <!-- ── Right: selected recipe ─────────────────────── -->
    <div class="rc-right px-scroll">
      <button class="px-close rc-close" @click="emit('close')">&times;</button>

      <template v-if="selected">
        <div class="rc-heading">{{ selected.name.toUpperCase() }}</div>

        <div>
          <div class="rc-label">ZUTATEN (&times;{{ batches }} BATCH)</div>
          <div class="rc-ingredients">
            <div v-for="ing in usedIngredients" :key="ing.key" class="rc-ing-row">
              <PixelIcon :name="ing.icon" :size="20" />
              <span class="rc-ing-name">{{ ing.label }}</span>
              <span class="rc-ing-qty" :class="{ insufficient: playerStore[ing.key] < ing.total }">{{ ing.total }}</span>
              <span class="rc-ing-price">&times; {{ fmtP(ing.price) }}</span>
              <span class="rc-ing-sum">= {{ fmt2(ing.cost) }}</span>
            </div>
          </div>
          <div class="rc-cost-row"><span>ZUTATEN GESAMT</span><span>{{ fmt2(ingredientMarketCost) }} C</span></div>
        </div>

        <template v-if="!activeJob">
          <div>
            <div class="rc-profit-row" :class="bakeBetter ? 'winner' : ''">
              <span>Backen</span>
              <span>{{ fmt2(bakeOutput) }} <PixelIcon name="cookie" :size="12" /> </span>
            </div>
            <div class="rc-profit-row" :class="!bakeBetter ? 'winner' : ''">
              <span>Ressourcen verkaufen</span>
              <span>{{ fmt2(ingredientSellValue) }} <PixelIcon name="cookie" :size="12" /> </span>
            </div>
            <div class="rc-profit-diff" :class="bakeBetter ? 'pos' : 'neg'">
              {{ bakeBetter ? '+' : '' }}{{ fmt2(bakeOutput - ingredientSellValue) }} C durch Backen
            </div>
          </div>

          <div class="rc-batches">
            <span>Batches</span>
            <div class="rc-stepper">
              <button @click="batches = Math.max(1, batches - 1)">&minus;</button>
              <span class="rc-stepper-val">{{ batches }}</span>
              <button @click="batches++">+</button>
            </div>
            <span class="rc-stock-hint">BESTAND: {{ stockBatches }} MÖGLICH</span>
          </div>
        </template>
      </template>

      <div v-else class="rc-empty">&larr; Rezept auswählen</div>

      <div class="rc-bake-section">
        <!-- No active job: normal "pick a batch size and start" form -->
        <template v-if="selected && !activeJob">
          <div class="rc-bake-meta">
            <span>{{ selected.output * batches }} Cookies</span>
            <span>{{ formatDuration(selected.bakeDurationSeconds * batches) }}</span>
          </div>
          <button class="px-btn px-btn-accent rc-bake-btn" :disabled="!canBake || busy" @click="startBake">
            {{ busy ? 'STARTET…' : 'BACKEN STARTEN' }}
          </button>
        </template>

        <!-- Job in progress: replaces the start form with a live progress bar until claimed -->
        <div v-if="activeJob" class="rc-progress">
          <div class="rc-progress-meta">
            <span>{{ activeJob.recipe?.name }} &middot; {{ activeJob.batches }}&times;</span>
            <span v-if="!activeJob.done">{{ formatDuration(activeJob.remainingSeconds) }}</span>
            <span v-else class="rc-done">FERTIG!</span>
          </div>
          <div class="rc-progress-track"><div class="rc-progress-bar" :style="{ width: progressPct + '%' }"></div></div>
          <button class="px-btn px-btn-buy rc-bake-btn" :disabled="!activeJob.done || busy" @click="claim">
            {{ busy ? '…' : `+${activeJob.totalCookies} COOKIES EINLÖSEN` }}
          </button>
        </div>

        <div v-if="feedback" class="rc-feedback" :class="feedbackType">{{ feedback }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import { useBakeStore }   from '../stores/bake.js'
import { bakeStart, bakeClaim } from '../services/api.js'
import { spawnFarmNumber } from '../composables/useFarmNumbers.js'
import PixelIcon from './pixel/PixelIcon.vue'

const emit = defineEmits(['close'])

const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const bakeStore   = useBakeStore()

const SELL_FEE = 0.08

const INGREDIENTS = [
  { key: 'sugar',     label: 'Zucker',     icon: 'zucker', market: 'SUGAR'     },
  { key: 'flour',     label: 'Mehl',       icon: 'mehl',   market: 'FLOUR'     },
  { key: 'eggs',      label: 'Eier',       icon: 'eier',   market: 'EGGS'      },
  { key: 'butter',    label: 'Butter',     icon: 'butter', market: 'BUTTER'    },
  { key: 'chocolate', label: 'Schokolade', icon: 'schoko', market: 'CHOCOLATE' },
  { key: 'milk',      label: 'Milch',      icon: 'milch',  market: 'MILK'      },
]

const recipes    = computed(() => playerStore.recipes)
const activeJob  = computed(() => bakeStore.status)
const selectedId = ref(null)
const batches    = ref(1)
const busy       = ref(false)
const feedback   = ref('')
const feedbackType = ref('ok')

const selected = computed(() => recipes.value.find(r => r.id === selectedId.value) ?? null)

function selectRecipe(id) { selectedId.value = id }

const usedIngredients = computed(() => {
  if (!selected.value) return []
  return INGREDIENTS
    .map(ing => {
      const amount = selected.value[ing.key] ?? 0
      const total  = amount * batches.value
      const price  = marketStore.priceOf(ing.market)
      return { ...ing, amount, total, price, cost: total * price }
    })
    .filter(ing => ing.amount > 0)
})

const ingredientMarketCost = computed(() => usedIngredients.value.reduce((s, i) => s + i.cost, 0))
const ingredientSellValue  = computed(() => usedIngredients.value.reduce((s, i) => s + i.cost * (1 - SELL_FEE), 0))
const bakeOutput  = computed(() => selected.value ? selected.value.output * batches.value : 0)
const bakeBetter  = computed(() => bakeOutput.value >= ingredientSellValue.value)

const stockBatches = computed(() => {
  if (!selected.value) return 0
  const limits = usedIngredients.value.map(ing => ing.amount > 0 ? Math.floor(playerStore[ing.key] / ing.amount) : Infinity)
  return Math.max(0, Math.min(...limits))
})

const canBake = computed(() => {
  if (!selected.value || batches.value < 1) return false
  return usedIngredients.value.every(ing => playerStore[ing.key] >= ing.total)
})

const progressPct = computed(() => {
  const job = activeJob.value
  if (!job?.recipe) return 0
  const total = job.recipe.bakeDurationSeconds * job.batches
  if (total === 0) return 100
  return Math.min(100, ((total - job.remainingSeconds) / total) * 100)
})

async function startBake() {
  busy.value = true
  // Snapshot before the request -- ingredients are deducted server-side
  // immediately, but bakeStart() only returns the job status (not the player),
  // so without this the HUD wouldn't show the drop until something else
  // happened to refresh the player. Deduct locally right away instead.
  const spent = usedIngredients.value
  try {
    await bakeStart(playerStore.steamId, selectedId.value, batches.value)
    for (const ing of spent) {
      playerStore[ing.key] = Math.max(0, (playerStore[ing.key] ?? 0) - ing.total)
    }
    await bakeStore.poll() // swaps the form below for the progress bar
  } catch (e) { showFeedback(e.message, 'err') }
  finally     { busy.value = false }
}

async function claim() {
  busy.value = true
  const earned = activeJob.value?.totalCookies ?? 0
  try {
    const updated = await bakeClaim(playerStore.steamId)
    playerStore.updateFromDto(updated)
    spawnFarmNumber(Math.round(earned), 360, 470, { crit: earned >= 200, color: '#c78539', icon: 'cookie' })
    await bakeStore.poll()
    showFeedback(`+${earned} Cookies!`, 'ok')
  } catch (e) { showFeedback(e.message, 'err') }
  finally     { busy.value = false }
}

function showFeedback(msg, type) {
  feedback.value = msg; feedbackType.value = type
  setTimeout(() => { feedback.value = '' }, 3000)
}

function formatDuration(s) {
  if (s <= 0) return '0s'
  const m = Math.floor(s / 60); const sec = s % 60
  return m > 0 ? `${m}m ${sec}s` : `${sec}s`
}
function fmt2(v) { return Number(v ?? 0).toFixed(2) }
function fmtP(v) { return Number(v ?? 0).toFixed(4) }

onMounted(() => { if (recipes.value.length) selectedId.value = recipes.value[0].id })
</script>

<style scoped>
.rc-inner { display: flex; width: 100%; height: 100%; font-family: 'Silkscreen', monospace; }

.rc-left {
  width: 260px; flex-shrink: 0; background: var(--px-cream3);
  padding: 20px 16px; display: flex; flex-direction: column; gap: 14px;
  border-right: 6px solid var(--px-brown);
}
.rc-left-title { font-family: 'Silkscreen', monospace; font-size: 14px; color: var(--px-ink-txt); text-align: center; }
.rc-rule { height: 3px; background: var(--px-brown2); }
.rc-loading { color: var(--px-tan-ink); font-style: italic; font-size: 13px; }
.rc-index { flex: 1; display: flex; flex-direction: column; gap: 6px; }
.rc-entry {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 11px 12px; background: var(--px-cream); border: 3px solid var(--px-brown2);
  font-size: 16px; color: var(--px-ink-txt); cursor: pointer; text-align: left;
}
.rc-entry:hover { background: #fff1a9; }
.rc-entry.active { background: var(--px-gold); border-color: var(--px-ink); }
.rc-entry-time { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); }

.rc-right {
  flex: 1; background: var(--px-cream); padding: 22px 24px; display: flex; flex-direction: column; gap: 16px;
  position: relative; overflow-y: auto;
}
.rc-close { position: absolute; top: 12px; right: 14px; }
.rc-heading { font-family: 'Silkscreen', monospace; font-size: 18px; color: var(--px-ink-txt); padding-right: 24px; }
.rc-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); letter-spacing: 1px; margin-bottom: 8px; }

.rc-ingredients { display: flex; flex-direction: column; gap: 2px; }
.rc-ing-row { display: grid; grid-template-columns: 24px 1fr 46px 70px 70px; align-items: center; gap: 8px; padding: 7px 8px; background: var(--px-cream2); border: 2px solid #fff1a9; }
.rc-ing-name  { font-size: 15px; color: var(--px-ink-txt); }
.rc-ing-qty   { font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-orange); }
.rc-ing-qty.insufficient { color: var(--px-red); }
.rc-ing-price { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-tan-ink); }
.rc-ing-sum   { font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-ink-txt); text-align: right; }
.rc-cost-row  { display: flex; justify-content: space-between; padding: 9px 8px; font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); border-top: 3px solid var(--px-brown2); margin-top: 4px; }

.rc-profit-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; background: var(--px-cream2); border: 3px solid var(--px-tan); margin-bottom: 6px; font-size: 16px; color: var(--px-tan-ink); }
.rc-profit-row.winner { background: #fff1a9; border-color: var(--px-green); color: var(--px-ink-txt); font-weight: 600; }
.rc-profit-diff { font-family: 'Silkscreen', monospace; font-size: 11px; text-align: center; margin-top: 4px; }
.rc-profit-diff.pos { color: #56642e; }
.rc-profit-diff.neg { color: var(--px-red); }

.rc-batches { display: flex; align-items: center; gap: 12px; font-size: 15px; color: var(--px-tan-ink); }
.rc-stepper { display: flex; align-items: center; border: 3px solid var(--px-ink); }
.rc-stepper button { padding: 8px 12px; background: var(--px-cream3); font-family: 'Silkscreen', monospace; font-size: 13px; border: none; cursor: pointer; color: var(--px-ink-txt); }
.rc-stepper-val { padding: 8px 18px; background: var(--px-cream); font-family: 'Silkscreen', monospace; font-size: 13px; }
.rc-stock-hint { margin-left: auto; font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-tan-ink); }

.rc-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: var(--px-tan-ink); font-style: italic; font-size: 15px; }

.rc-bake-section { margin-top: auto; display: flex; flex-direction: column; gap: 8px; }
.rc-bake-meta { display: flex; gap: 12px; font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-tan-ink); }
.rc-bake-btn { width: 100%; text-align: center; }

.rc-progress { display: flex; flex-direction: column; gap: 8px; }
.rc-progress-meta { display: flex; justify-content: space-between; font-family: 'Silkscreen', monospace; font-size: 11px; color: var(--px-tan-ink); }
.rc-done { color: #56642e; }
/* Same recipe as BuildingFrame.vue's .bf-hold-bar (the move/drag progress
   bar): a single growing gold rectangle with a dark ink halo, no track. */
.rc-progress-track { height: 14px; }
.rc-progress-bar { height: 100%; background: var(--px-gold); box-shadow: 0 0 0 3px var(--px-ink); transition: width .3s linear; }

.rc-feedback { font-family: 'Silkscreen', monospace; font-size: 11px; text-align: center; }
.rc-feedback.ok  { color: #56642e; }
.rc-feedback.err { color: var(--px-red); }
</style>
