<template>
  <div class="recipe-card">
    <h3>Cookie backen</h3>

    <div class="recipe-ingredients">
      <div v-for="ing in recipe.ingredients" :key="ing.resource" class="ingredient-row">
        <span class="ingredient-label">{{ ing.label }}</span>
        <span
          class="ingredient-amount"
          :class="{ insufficient: playerStore[ing.resource] < ing.amount }"
        >
          {{ fmt(playerStore[ing.resource]) }} / {{ ing.amount }}
        </span>
      </div>
    </div>

    <div class="recipe-output">
      Ergebnis: <strong>{{ recipe.output }} Cookies</strong>
    </div>

    <div class="recipe-batches-row">
      <label>Batches</label>
      <input v-model.number="batches" type="number" min="1" step="1" class="amount-input" />
    </div>

    <button
      class="btn btn-produce"
      :disabled="!canProduce || busy"
      @click="produce"
    >
      {{ busy ? 'Backen...' : `${batches}x Backen → +${batches * recipe.output} 🍪` }}
    </button>

    <div v-if="feedback" :class="['recipe-feedback', feedbackType]">
      {{ feedback }}
    </div>

    <p class="recipe-note">
      Produktion wird serverseitig validiert.
    </p>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { produceCookies } from '../services/api.js'

const playerStore = usePlayerStore()
const busy = ref(false)
const batches = ref(1)
const feedback = ref('')
const feedbackType = ref('ok')

const recipe = {
  ingredients: [
    { resource: 'sugar',     label: 'Zucker',     amount: 10 },
    { resource: 'flour',     label: 'Mehl',       amount: 10 },
    { resource: 'eggs',      label: 'Eier',       amount: 10 },
    { resource: 'butter',    label: 'Butter',     amount: 10 },
    { resource: 'chocolate', label: 'Schokolade', amount: 10 },
    { resource: 'milk',      label: 'Milch',      amount: 10 }
  ],
  output: 100
}

const canProduce = computed(() =>
  batches.value >= 1 &&
  recipe.ingredients.every(ing => playerStore[ing.resource] >= ing.amount * batches.value)
)

async function produce() {
  busy.value = true
  feedback.value = ''
  try {
    const updated = await produceCookies(playerStore.steamId, batches.value)
    playerStore.updateFromDto(updated)
    feedback.value = `+${batches.value * recipe.output} Cookies gebacken!`
    feedbackType.value = 'ok'
  } catch (e) {
    feedback.value = e.message
    feedbackType.value = 'err'
  } finally {
    busy.value = false
  }
}

function fmt(v) {
  return Number(v).toFixed(1)
}
</script>
