<template>
  <div class="book-inner">

    <!-- ── Linke Seite: Rezept-Index ───────────────────── -->
    <div class="page-left">
      <div class="page-header">
        <div class="page-deco">✦ ✦ ✦</div>
        <div class="page-title">Rezeptbuch</div>
        <div class="page-rule"></div>
      </div>

      <div v-if="!recipes.length" class="index-loading">Lade…</div>

      <nav class="recipe-index">
        <button
          v-for="(r, i) in recipes"
          :key="r.id"
          class="index-entry"
          :class="{ active: selectedId === r.id }"
          @click="selectRecipe(r.id)"
        >
          <span class="index-num">{{ i + 1 }}.</span>
          <span class="index-name">{{ r.name }}</span>
          <span class="index-arrow" v-if="selectedId === r.id">→</span>
        </button>
      </nav>

      <div class="page-footer">
        <div class="page-rule"></div>
        <div class="page-num">I</div>
      </div>
    </div>

    <!-- ── Buchrücken ──────────────────────────────────── -->
    <div class="book-spine"></div>

    <!-- ── Rechte Seite ───────────────────────────────── -->
    <div class="page-right">
      <button class="book-close" @click="emit('close')">✕</button>

      <template v-if="selected">
        <div class="recipe-heading">{{ selected.name }}</div>
        <div class="page-rule"></div>

        <!-- Zutaten mit Live-Marktkosten -->
        <div class="section-label">Zutaten <span class="section-note">(× {{ batches }} Batch)</span></div>
        <div class="ingredients">
          <template v-for="ing in usedIngredients" :key="ing.key">
            <div class="ing-row">
              <img :src="ing.icon" class="ing-icon" :alt="ing.label" />
              <span class="ing-name">{{ ing.label }}</span>
              <span class="ing-need-qty" :class="{ insufficient: playerStore[ing.key] < ing.total }">
                {{ ing.total }}
              </span>
              <span class="ing-sep">×</span>
              <span class="ing-price">{{ fmtP(ing.price) }} C</span>
              <span class="ing-cost">=&nbsp;{{ fmt2(ing.cost) }} C</span>
            </div>
          </template>
        </div>

        <!-- Trennstrich + Gesamtkosten -->
        <div class="cost-row">
          <span class="cost-label">Zutaten gesamt</span>
          <span class="cost-val">{{ fmt2(ingredientMarketCost) }} C</span>
        </div>

        <div class="page-rule" style="margin: 6px 0"></div>

        <!-- Rentabilitätsvergleich -->
        <div class="section-label">Lohnt es sich?</div>
        <div class="profit-block">
          <div class="profit-row" :class="bakeBetter ? 'profit-winner' : 'profit-loser'">
            <span class="profit-icon">🍪</span>
            <span class="profit-label">Backen</span>
            <span class="profit-val">{{ fmt2(bakeOutput) }} C</span>
            <span v-if="bakeBetter" class="profit-badge">✓ besser</span>
          </div>
          <div class="profit-row" :class="!bakeBetter ? 'profit-winner' : 'profit-loser'">
            <span class="profit-icon">💰</span>
            <span class="profit-label">Ressourcen verkaufen</span>
            <span class="profit-val">{{ fmt2(ingredientSellValue) }} C</span>
            <span v-if="!bakeBetter" class="profit-badge">✓ besser</span>
          </div>
          <div class="profit-diff" :class="bakeBetter ? 'pos' : 'neg'">
            {{ bakeBetter ? '+' : '' }}{{ fmt2(bakeOutput - ingredientSellValue) }} C durch Backen
          </div>
        </div>

        <!-- Batches + Bestand -->
        <div class="batches-row">
          <span class="batches-label">Batches</span>
          <button class="step-sm" @click="batches = Math.max(1, batches - 1)">−</button>
          <input v-model.number="batches" type="number" min="1" class="batches-input" :disabled="!!activeJob" />
          <button class="step-sm" @click="batches++">+</button>
          <span class="stock-hint">Bestand: {{ stockBatches }} möglich</span>
        </div>
      </template>

      <div v-else class="empty-hint">← Rezept auswählen</div>

      <!-- Backen / Fortschritt -->
      <div class="bake-section">
        <template v-if="selected">
          <div class="bake-meta" v-if="!activeJob">
            <span class="bake-out">🍪 {{ selected.output * batches }} Cookies</span>
            <span class="bake-time">⏱ {{ formatDuration(selected.bakeDurationSeconds * batches) }}</span>
          </div>
          <button v-if="!activeJob" class="btn-bake" :disabled="!canBake || busy" @click="startBake">
            {{ busy ? 'Startet…' : '🔥 Backen starten' }}
          </button>
        </template>

        <div v-if="activeJob" class="progress-block">
          <div class="progress-meta">
            <span>{{ activeJob.recipe?.name }} · {{ activeJob.batches }}×</span>
            <span v-if="!activeJob.done" class="progress-time">{{ formatDuration(activeJob.remainingSeconds) }}</span>
            <span v-else class="progress-done">Fertig! 🎉</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPct + '%' }"></div>
          </div>
          <button class="btn-claim" :disabled="!activeJob.done || busy" @click="claim">
            {{ busy ? '…' : `🍪 +${activeJob.totalCookies} Cookies einlösen` }}
          </button>
        </div>

        <div v-if="feedback" class="book-feedback" :class="feedbackType">{{ feedback }}</div>
      </div>

      <div class="page-footer">
        <div class="page-rule"></div>
        <div class="page-num">II</div>
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
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const emit = defineEmits(['close'])

const playerStore = usePlayerStore()
const marketStore = useMarketStore()
const bakeStore   = useBakeStore()

const SELL_FEE = 0.15   // Standardgebühr (könnte auch aus config kommen)

const INGREDIENTS = [
  { key: 'sugar',     label: 'Zucker',     icon: sugarIcon,  market: 'SUGAR'     },
  { key: 'flour',     label: 'Mehl',       icon: flourIcon,  market: 'FLOUR'     },
  { key: 'eggs',      label: 'Eier',       icon: eggsIcon,   market: 'EGGS'      },
  { key: 'butter',    label: 'Butter',     icon: butterIcon, market: 'BUTTER'    },
  { key: 'chocolate', label: 'Schokolade', icon: chocoIcon,  market: 'CHOCOLATE' },
  { key: 'milk',      label: 'Milch',      icon: milkIcon,   market: 'MILK'      },
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

// Nur Zutaten mit amount > 0, angereichert mit Marktdaten
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

// Gesamte Marktkosten der Zutaten (Kaufpreis)
const ingredientMarketCost = computed(() =>
  usedIngredients.value.reduce((s, i) => s + i.cost, 0)
)

// Verkaufswert der Zutaten (nach Gebühr)
const ingredientSellValue = computed(() =>
  usedIngredients.value.reduce((s, i) => s + i.cost * (1 - SELL_FEE), 0)
)

// Cookie-Output als Wert (1 Cookie = 1 C)
const bakeOutput = computed(() =>
  selected.value ? selected.value.output * batches.value : 0
)

const bakeBetter = computed(() => bakeOutput.value >= ingredientSellValue.value)

// Wie viele Batches kann man mit aktuellem Bestand machen?
const stockBatches = computed(() => {
  if (!selected.value) return 0
  const limits = usedIngredients.value.map(ing =>
    ing.amount > 0 ? Math.floor(playerStore[ing.key] / ing.amount) : Infinity
  )
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
  try {
    await bakeStart(playerStore.steamId, selectedId.value, batches.value)
    await bakeStore.poll()
    showFeedback('Backen gestartet!', 'ok')
  } catch (e) { showFeedback(e.message, 'err') }
  finally     { busy.value = false }
}

async function claim() {
  busy.value = true
  const earned = activeJob.value?.totalCookies ?? 0
  try {
    const updated = await bakeClaim(playerStore.steamId)
    playerStore.updateFromDto(updated)
    spawnFarmNumber(Math.round(earned), 360, 470, { crit: earned >= 200, color: '#ef9f27' })
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
function fmt(v)  { return Number(v ?? 0).toFixed(1) }
function fmt2(v) { return Number(v ?? 0).toFixed(2) }
function fmtP(v) { return Number(v ?? 0).toFixed(4) }

onMounted(() => {
  if (recipes.value.length) selectedId.value = recipes.value[0].id
})
</script>

<style scoped>
.book-inner { display: flex; width: 100%; height: 100%; }

/* ── Linke Seite ─────────────────────────────────────── */
.page-left {
  width: 220px; flex-shrink: 0;
  background: #f0e6cc;
  display: flex; flex-direction: column;
  padding: 20px 16px 14px;
}
.page-header { text-align: center; margin-bottom: 14px; }
.page-deco   { font-size: 10px; color: #c9a96e; letter-spacing: 4px; margin-bottom: 4px; }
.page-title  { font-size: 16px; font-weight: 700; color: #3b2410; font-family: Georgia, serif; }
.page-rule   { border: none; border-top: 1px solid #c9a96e; margin: 8px 0; }

.recipe-index { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.index-entry {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 10px; border: none; border-radius: 4px;
  background: transparent; color: #5c3d1e;
  cursor: pointer; text-align: left; font-size: 13px;
  font-family: Georgia, serif; transition: background 0.15s;
}
.index-entry:hover { background: rgba(180,130,60,0.15); }
.index-entry.active { background: #c9a96e; color: #2a1a08; font-weight: 700; }
.index-num   { color: #9a7040; font-size: 11px; width: 18px; flex-shrink: 0; }
.index-name  { flex: 1; }
.index-arrow { color: #8B5e3c; font-size: 12px; }
.index-loading { color: #9a7040; font-style: italic; font-size: 12px; padding: 8px 0; }

.page-footer { margin-top: auto; text-align: center; }
.page-num { font-size: 10px; color: #9a7040; margin-top: 4px; font-family: Georgia, serif; }

/* ── Buchrücken ──────────────────────────────────────── */
.book-spine {
  width: 14px; flex-shrink: 0;
  background: linear-gradient(to right, #3a2008, #6b4520, #3a2008);
}

/* ── Rechte Seite ────────────────────────────────────── */
.page-right {
  flex: 1; background: #faf6ed;
  display: flex; flex-direction: column;
  padding: 18px 20px 14px;
  position: relative; overflow: hidden;
}
.book-close {
  position: absolute; top: 10px; right: 12px;
  background: none; border: none; font-size: 16px;
  cursor: pointer; color: #9a7040; z-index: 10;
}
.book-close:hover { color: #3b2410; }

.recipe-heading {
  font-size: 19px; font-weight: 700; color: #3b2410;
  font-family: Georgia, serif; padding-right: 24px;
}
.section-label {
  font-size: 10px; font-weight: 700; text-transform: uppercase;
  letter-spacing: 1px; color: #9a7040; margin: 8px 0 4px;
}
.section-note { font-weight: 400; text-transform: none; letter-spacing: 0; }

/* ── Zutaten ─────────────────────────────────────────── */
.ingredients { display: flex; flex-direction: column; gap: 3px; }
.ing-row {
  display: flex; align-items: center; gap: 5px; font-size: 12px;
}
.ing-icon     { width: 16px; height: 16px; object-fit: contain; flex-shrink: 0; }
.ing-name     { width: 68px; flex-shrink: 0; color: #5c3d1e; }
.ing-need-qty { width: 28px; text-align: right; font-weight: 700; color: #3b2410; flex-shrink: 0; }
.ing-need-qty.insufficient { color: #b84a28; }
.ing-sep      { color: #9a7040; }
.ing-price    { width: 52px; color: #7a5c3c; font-size: 11px; }
.ing-cost     { flex: 1; text-align: right; font-weight: 700; color: #3b2410; font-size: 11px; }

/* ── Kosten-Zeile ────────────────────────────────────── */
.cost-row {
  display: flex; justify-content: space-between; align-items: center;
  margin-top: 4px; padding: 3px 0;
  font-size: 12px;
}
.cost-label { color: #7a5c3c; }
.cost-val   { font-weight: 700; color: #3b2410; }

/* ── Rentabilität ────────────────────────────────────── */
.profit-block { display: flex; flex-direction: column; gap: 3px; }
.profit-row {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 8px; border-radius: 6px; font-size: 12px;
  border: 1px solid transparent;
}
.profit-winner { background: rgba(74,124,53,0.12); border-color: #4a7c35; }
.profit-loser  { background: rgba(0,0,0,0.04); color: #9a7040; }
.profit-icon   { font-size: 14px; flex-shrink: 0; }
.profit-label  { flex: 1; }
.profit-val    { font-weight: 700; }
.profit-badge  { font-size: 10px; color: #4a7c35; font-weight: 700; }
.profit-diff   {
  font-size: 11px; font-style: italic; text-align: center;
  margin-top: 2px; padding: 2px 0;
}
.profit-diff.pos { color: #4a7c35; }
.profit-diff.neg { color: #b84a28; }

/* ── Batches ─────────────────────────────────────────── */
.batches-row {
  display: flex; align-items: center; gap: 6px;
  margin-top: 8px; font-size: 12px; color: #5c3d1e;
}
.batches-label { color: #7a5c3c; }
.step-sm {
  width: 22px; height: 22px; border: 1px solid #c9a96e;
  border-radius: 4px; background: transparent; color: #5c3d1e;
  cursor: pointer; font-size: 14px; line-height: 1;
  display: flex; align-items: center; justify-content: center;
}
.step-sm:hover { background: #c9a96e; color: #fff; }
.batches-input {
  width: 44px; padding: 2px 4px; border: 1px solid #c9a96e;
  border-radius: 4px; background: transparent; color: #3b2410;
  font-size: 12px; text-align: center; -moz-appearance: textfield;
}
.batches-input::-webkit-outer-spin-button,
.batches-input::-webkit-inner-spin-button { -webkit-appearance: none; }
.stock-hint { margin-left: auto; color: #9a7040; font-size: 11px; }

.empty-hint {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: #9a7040; font-style: italic; font-family: Georgia, serif; font-size: 14px;
}

/* ── Backen-Bereich ──────────────────────────────────── */
.bake-section { margin-top: auto; }
.bake-meta {
  display: flex; gap: 12px; font-size: 12px; color: #5c3d1e;
  margin-bottom: 6px;
}
.bake-out  { font-weight: 700; }
.bake-time { color: #9a7040; }

.btn-bake {
  width: 100%; padding: 9px; border: 2px solid #8B5e3c;
  border-radius: 6px; background: #c9853a; color: #fff8ee;
  font-size: 13px; font-weight: 700; font-family: Georgia, serif;
  cursor: pointer; transition: background 0.15s;
}
.btn-bake:hover:not(:disabled) { background: #b5732a; }
.btn-bake:disabled { opacity: 0.4; cursor: not-allowed; }

.progress-block { display: flex; flex-direction: column; gap: 5px; }
.progress-meta  { display: flex; justify-content: space-between; font-size: 12px; color: #5c3d1e; }
.progress-time  { color: #9a7040; }
.progress-done  { color: #4a7c35; font-weight: 700; }
.progress-bar {
  height: 8px; background: rgba(180,130,60,0.2);
  border-radius: 4px; border: 1px solid #c9a96e; overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(to right, #c9853a, #e8a84a);
  border-radius: 4px; transition: width 0.5s linear;
}
.btn-claim {
  width: 100%; padding: 7px; border: 2px solid #4a7c35;
  border-radius: 6px; background: #5a9c40; color: #f0fff0;
  font-size: 12px; font-weight: 700; cursor: pointer;
}
.btn-claim:disabled { opacity: 0.4; cursor: not-allowed; }

.book-feedback { font-size: 12px; margin-top: 5px; text-align: center; font-style: italic; }
.book-feedback.ok  { color: #4a7c35; }
.book-feedback.err { color: #b84a28; }
</style>
