<template>
  <div class="idle-view">
    <div class="hex-grid">
      <div
        v-for="res in resources"
        :key="res.name"
        class="hex-card"
        :class="{ 'harvest-flash': flashing[res.name] }"
        @mouseenter="onHover(res.name)"
      >
        <div class="hex-card-img">{{ res.emoji }}</div>
        <div class="hex-card-label">{{ res.label }}</div>
        <div class="hex-card-hint">+1 bei Hover</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { harvestResource } from '../services/api.js'

const playerStore = usePlayerStore()

const resources = [
  { name: 'SUGAR',     label: 'Zucker',     emoji: '🍬' },
  { name: 'FLOUR',     label: 'Mehl',       emoji: '🌾' },
  { name: 'EGGS',      label: 'Eier',       emoji: '🥚' },
  { name: 'BUTTER',    label: 'Butter',     emoji: '🧈' },
  { name: 'CHOCOLATE', label: 'Schokolade', emoji: '🍫' },
  { name: 'MILK',      label: 'Milch',      emoji: '🥛' },
]

const flashing = reactive({})
const cooldown = reactive({})

async function onHover(name) {
  if (cooldown[name]) return
  cooldown[name] = true
  flashing[name] = true
  setTimeout(() => { flashing[name] = false }, 300)
  setTimeout(() => { cooldown[name] = false }, 600)

  try {
    const updated = await harvestResource(playerStore.steamId, name)
    playerStore.updateFromDto(updated)
  } catch (e) {
    console.error('[Harvest] failed', e)
  }
}
</script>
