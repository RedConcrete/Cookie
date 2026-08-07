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
          <img :src="res.icon" :alt="t(res.labelKey)" />
        </div>
        <div class="hex-card-label">{{ t(res.labelKey) }}</div>
        <div class="hex-card-hint">{{ t('idleView.hoverHint') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from '../stores/player.js'
import { harvestResource } from '../services/api.js'
import sugarIcon  from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon  from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon   from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon  from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon   from '../assets/Sprites/RecSprits/MilchIcon.png'

const { t } = useI18n()
const playerStore = usePlayerStore()

const resources = [
  { name: 'SUGAR',     labelKey: 'idleView.resourceSugar',     icon: sugarIcon  },
  { name: 'FLOUR',     labelKey: 'idleView.resourceFlour',     icon: flourIcon  },
  { name: 'EGGS',      labelKey: 'idleView.resourceEggs',      icon: eggsIcon   },
  { name: 'BUTTER',    labelKey: 'idleView.resourceButter',    icon: butterIcon },
  { name: 'CHOCOLATE', labelKey: 'idleView.resourceChocolate', icon: chocoIcon  },
  { name: 'MILK',      labelKey: 'idleView.resourceMilk',      icon: milkIcon   },
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
