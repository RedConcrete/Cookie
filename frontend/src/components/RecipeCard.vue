<template>
  <div class="recipe-card">
    <h3>Cookie backen</h3>

    <!-- Rezept-Auswahl -->
    <div class="recipe-tabs">
      <button
        v-for="r in recipes"
        :key="r.id"
        class="recipe-tab"
        :class="{ active: selectedId === r.id }"
        :disabled="!!activeJob"
        @click="selectedId = r.id"
      >
        {{ r.name }}
      </button>
    </div>

    <!-- Aktives Rezept -->
    <template v-if="selected">
      <div class="recipe-ingredients">
        <div v-for="ing in ingredients(selected)" :key="ing.key" class="ingredient-row">
          <span class="ingredient-label">{{ ing.label }}</span>
          <span class="ingredient-amount" :class="{ insufficient: playerStore[ing.key] < ing.amount * batches }">
            {{ fmt(playerStore[ing.key]) }} / {{ ing.amount * batches }}
          </span>
        </div>
      </div>

      <div class="recipe-output">
        Ergebnis: <strong>{{ selected.output * batches }} Cookies</strong>
        &nbsp;·&nbsp;
        <span class="recipe-time">⏱ {{ formatDuration(selected.bakeDurationSeconds * batches) }}</span>
      </div>

      <div class="recipe-batches-row">
        <label>Batches</label>
        <input v-model.number="batches" type="number" min="1" step="1" class="amount-input" :disabled="!!activeJob" />
      </div>
    </template>

    <!-- Kein aktiver Job: Start-Button -->
    <button
      v-if="!activeJob"
      class="btn btn-produce"
      :disabled="!canBake || busy"
      @click="startBake"
    >
      {{ busy ? 'Startet...' : 'Backen starten' }}
    </button>

    <!-- Aktiver Job: Fortschritt -->
    <div v-if="activeJob" class="bake-progress">
      <div class="bake-progress-label">
        {{ activeJob.recipe.name }} · {{ activeJob.batches }}x
        <span v-if="!activeJob.done"> — noch {{ formatDuration(activeJob.remainingSeconds) }}</span>
        <span v-else class="bake-done"> — fertig!</span>
      </div>
      <div class="bake-progress-bar">
        <div class="bake-progress-fill" :style="{ width: progressPct + '%' }"></div>
      </div>
      <button class="btn btn-claim" :disabled="!activeJob.done || busy" @click="claim">
        {{ busy ? '...' : `+${activeJob.totalCookies} Cookies einlösen` }}
      </button>
    </div>

    <div v-if="feedback" :class="['recipe-feedback', feedbackType]">{{ feedback }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { getRecipes, bakeStart, bakeStatus, bakeClaim } from '../services/api.js'
import { spawnFarmNumber } from '../composables/useFarmNumbers.js'

const playerStore = usePlayerStore()
const recipes     = ref([])
const selectedId  = ref(null)
const batches     = ref(1)
const activeJob   = ref(null)
const busy        = ref(false)
const feedback    = ref('')
const feedbackType = ref('ok')

const selected = computed(() => recipes.value.find(r => r.id === selectedId.value))

const INGREDIENT_KEYS = [
  { key: 'sugar',     label: 'Zucker'     },
  { key: 'flour',     label: 'Mehl'       },
  { key: 'eggs',      label: 'Eier'       },
  { key: 'butter',    label: 'Butter'     },
  { key: 'chocolate', label: 'Schokolade' },
  { key: 'milk',      label: 'Milch'      },
]

function ingredients(recipe) {
  return INGREDIENT_KEYS.map(({ key, label }) => ({ key, label, amount: recipe[key] }))
}

const canBake = computed(() => {
  if (!selected.value || batches.value < 1) return false
  return INGREDIENT_KEYS.every(({ key }) => playerStore[key] >= selected.value[key] * batches.value)
})

// Fortschritt in % (0–100)
const progressPct = computed(() => {
  if (!activeJob.value || !activeJob.value.recipe) return 0
  const total = activeJob.value.recipe.bakeDurationSeconds * activeJob.value.batches
  if (total === 0) return 100
  return Math.min(100, ((total - activeJob.value.remainingSeconds) / total) * 100)
})

async function startBake() {
  busy.value = true
  try {
    const status = await bakeStart(playerStore.steamId, selectedId.value, batches.value)
    activeJob.value = status
    showFeedback('Backen gestartet!', 'ok')
  } catch (e) {
    showFeedback(e.message, 'err')
  } finally {
    busy.value = false
  }
}

async function claim() {
  busy.value = true
  const cookiesEarned = activeJob.value?.totalCookies ?? 0
  try {
    const updated = await bakeClaim(playerStore.steamId)
    playerStore.updateFromDto(updated)
    // Zahl mittig im Viewport spawnen
    spawnFarmNumber(Math.round(cookiesEarned), 360, 470,
      { crit: cookiesEarned >= 200, color: '#ef9f27' })
    activeJob.value = null
    showFeedback(`+${cookiesEarned} Cookies!`, 'ok')
  } catch (e) {
    showFeedback(e.message, 'err')
  } finally {
    busy.value = false
  }
}

function showFeedback(msg, type) {
  feedback.value = msg
  feedbackType.value = type
  setTimeout(() => { feedback.value = '' }, 3000)
}

function formatDuration(seconds) {
  if (seconds <= 0) return '0s'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return m > 0 ? `${m}m ${s}s` : `${s}s`
}

function fmt(v) { return Number(v).toFixed(1) }

// Status-Poll solange ein Job läuft
let pollTimer = null

async function pollStatus() {
  try {
    const status = await bakeStatus(playerStore.steamId)
    activeJob.value = status.active ? status : null
  } catch {}
}

onMounted(async () => {
  recipes.value = await getRecipes()
  if (recipes.value.length > 0) selectedId.value = recipes.value[0].id
  await pollStatus()
  pollTimer = setInterval(pollStatus, 2000)
})

onUnmounted(() => clearInterval(pollTimer))
</script>

<style scoped>
.recipe-tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}
.recipe-tab {
  padding: 5px 14px;
  border-radius: 6px;
  border: 1px solid #3a3a55;
  background: transparent;
  color: #aaa;
  cursor: pointer;
  font-size: 13px;
}
.recipe-tab.active {
  background: #2c2c50;
  color: #e2e2f0;
  border-color: #6a6a90;
}
.recipe-tab:disabled { opacity: 0.5; cursor: not-allowed; }

.recipe-time { color: #aaa; font-size: 13px; }

.bake-progress { margin-top: 12px; }
.bake-progress-label { font-size: 13px; color: #ccc; margin-bottom: 6px; }
.bake-done { color: #5f9; font-weight: 700; }
.bake-progress-bar {
  height: 8px;
  background: #2a2a3a;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 10px;
}
.bake-progress-fill {
  height: 100%;
  background: #7F77DD;
  border-radius: 4px;
  transition: width 0.5s linear;
}
.btn-claim {
  width: 100%;
  padding: 8px;
  background: #3a5f3a;
  border: 1px solid #5a9a5a;
  color: #aeffae;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.btn-claim:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
