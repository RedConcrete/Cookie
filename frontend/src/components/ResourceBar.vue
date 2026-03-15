<template>
  <div class="resource-bar">
    <div class="resource-item resource-item-cookies">
      <img :src="cookieIcon" class="resource-cookie-icon" alt="Cookie" />
      <span class="resource-value cookies-value">{{ fmt(playerStore.cookies) }}</span>
    </div>
    <div v-for="res in resources" :key="res.key" class="resource-item">
      <img :src="res.icon" class="resource-icon" :alt="res.label" />
      <div class="resource-text">
        <span class="resource-label">{{ res.label }}</span>
        <span class="resource-value">{{ fmt(playerStore[res.key]) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePlayerStore } from '../stores/player.js'
import cookieIcon    from '../assets/Sprites/RecSprits/BackgroundCookie512.png'
import sugarIcon     from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon     from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon      from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon    from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon     from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon      from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()

const resources = [
  { key: 'sugar',     label: 'Zucker',     icon: sugarIcon  },
  { key: 'flour',     label: 'Mehl',       icon: flourIcon  },
  { key: 'eggs',      label: 'Eier',       icon: eggsIcon   },
  { key: 'butter',    label: 'Butter',     icon: butterIcon },
  { key: 'chocolate', label: 'Schokolade', icon: chocoIcon  },
  { key: 'milk',      label: 'Milch',      icon: milkIcon   },
]

function fmt(v) {
  return Number(v).toFixed(1)
}
</script>

<style scoped>
.resource-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

.resource-item {
  display: flex;
  align-items: center;
  gap: 5px;
  background: rgba(255,255,255,0.12);
  padding: 3px 8px;
  border-radius: 10px;
}

.resource-item-cookies {
  background: rgba(255,200,0,0.25);
  padding: 3px 10px;
}

.resource-cookie-icon { width: 32px; height: 32px; object-fit: contain; }
.resource-icon        { width: 22px; height: 22px; object-fit: contain; }

.resource-text { display: flex; flex-direction: column; }
.resource-label { font-size: 9px; color: #F5EDD6; opacity: 0.75; line-height: 1; }
.resource-value { font-size: 12px; font-weight: 700; color: #FFE680; line-height: 1.2; }
.cookies-value  { font-size: 15px; }
</style>
