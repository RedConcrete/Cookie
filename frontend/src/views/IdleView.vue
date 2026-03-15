<template>
  <div class="idle-view">
    <div class="hex-grid">
      <div
        v-for="res in resources"
        :key="res.name"
        class="hex-card"
        :class="{ 'harvest-flash': flashing[res.name] }"
        @mouseenter="startHarvest(res.name)"
        @mouseleave="stopHarvest(res.name)"
      >
        <div class="hex-card-img">
          <img :src="res.icon" :alt="res.label" />
        </div>
        <div class="hex-card-label">{{ res.label }}</div>
        <div class="hex-card-hint">+1/s bei Hover</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { harvestResource } from '../services/api.js'
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()

const resources = [
  { name: 'SUGAR',     label: 'Zucker',     icon: sugarIcon  },
  { name: 'FLOUR',     label: 'Mehl',       icon: flourIcon  },
  { name: 'EGGS',      label: 'Eier',       icon: eggsIcon   },
  { name: 'BUTTER',    label: 'Butter',     icon: butterIcon },
  { name: 'CHOCOLATE', label: 'Schokolade', icon: chocoIcon  },
  { name: 'MILK',      label: 'Milch',      icon: milkIcon   },
]

const flashing  = reactive({})
const intervals = reactive({})

async function doHarvest(name) {
  flashing[name] = true
  setTimeout(() => { flashing[name] = false }, 300)
  try {
    const updated = await harvestResource(playerStore.steamId, name)
    playerStore.updateFromDto(updated)
  } catch (e) {
    console.error('[Harvest] failed', e)
  }
}

function startHarvest(name) {
  if (intervals[name]) return
  doHarvest(name)
  intervals[name] = setInterval(() => doHarvest(name), 1000)
}

function stopHarvest(name) {
  clearInterval(intervals[name])
  intervals[name] = null
}
</script>

<style scoped>
.hex-card-img img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 12px;
}
</style>
