<template>
  <div class="resource-bar">

    <!-- Cookies -->
    <NestedTooltip :content="cookieTooltip">
      <div class="resource-item resource-item-cookies">
        <img :src="cookieIcon" class="resource-cookie-icon" alt="Cookie" />
        <span class="resource-value cookies-value">{{ fmt(playerStore.cookies) }}</span>
      </div>
    </NestedTooltip>

    <!-- Ressourcen -->
    <NestedTooltip v-for="res in resources" :key="res.key" :content="resTooltip(res)">
      <div class="resource-item">
        <img :src="res.icon" class="resource-icon" :alt="res.label" />
        <div class="resource-text">
          <span class="resource-label">{{ res.label }}</span>
          <span class="resource-value">{{ fmt(playerStore[res.key]) }}</span>
        </div>
      </div>
    </NestedTooltip>

  </div>
</template>

<script setup>
import { computed } from 'vue'
import { usePlayerStore } from '../stores/player.js'
import { useMarketStore } from '../stores/market.js'
import NestedTooltip from './NestedTooltip.vue'
import cookieIcon    from '../assets/Sprites/RecSprits/BackgroundCookie512.png'
import sugarIcon     from '../assets/Sprites/RecSprits/Zucker.png'
import flourIcon     from '../assets/Sprites/RecSprits/Mehl.png'
import eggsIcon      from '../assets/Sprites/RecSprits/Eier.png'
import butterIcon    from '../assets/Sprites/RecSprits/ButterICon.png'
import chocoIcon     from '../assets/Sprites/RecSprits/SchokiIcon.png'
import milkIcon      from '../assets/Sprites/RecSprits/MilchIcon.png'

const playerStore = usePlayerStore()
const marketStore = useMarketStore()

const resources = [
  { key: 'sugar',     label: 'Zucker',     name: 'SUGAR',     icon: sugarIcon  },
  { key: 'flour',     label: 'Mehl',       name: 'FLOUR',     icon: flourIcon  },
  { key: 'eggs',      label: 'Eier',       name: 'EGGS',      icon: eggsIcon   },
  { key: 'butter',    label: 'Butter',     name: 'BUTTER',    icon: butterIcon },
  { key: 'chocolate', label: 'Schokolade', name: 'CHOCOLATE', icon: chocoIcon  },
  { key: 'milk',      label: 'Milch',      name: 'MILK',      icon: milkIcon   },
]

const cookieTooltip = computed(() => [
  { text: `Cookies: ${fmt(playerStore.cookies)}` },
  { text: `\nNet Worth: ${fmtBig(playerStore.netWorth)}` },
])

function resTooltip(res) {
  const amount   = playerStore[res.key] ?? 0
  const price    = marketStore.priceOf(res.name)
  const sellVal  = amount * price * 0.85   // nach 15% Gebühr
  return [
    { text: `${res.label}: ${fmt(amount)}` },
    { text: `\nMarktpreis: ${price.toFixed(4)} C` },
    { text: `\nVerkaufswert: `, tooltip: 'Menge × Preis × 0.85 (nach Gebühr)' },
    { text: `${fmt2(sellVal)} C` },
  ]
}

function fmt(v)    { return Number(v).toFixed(1) }
function fmt2(v)   { return Number(v).toFixed(2) }
function fmtBig(v) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v ?? 0).toFixed(1)
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
  cursor: default;
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
