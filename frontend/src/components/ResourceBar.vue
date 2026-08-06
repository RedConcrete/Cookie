<template>
  <div class="resource-bar">

    <!-- Cookies -->
    <NestedTooltip :content="cookieTooltip" silent>
      <div class="resource-item resource-item-cookies">
        <img :src="cookieIcon" class="resource-cookie-icon" alt="Cookie" />
        <span class="resource-value cookies-value">{{ fmt(playerStore.cookies) }}</span>
      </div>
    </NestedTooltip>

    <!-- Ressourcen -->
    <NestedTooltip v-for="res in resources" :key="res.key" :content="resTooltip(res)" silent>
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
import { useI18n } from 'vue-i18n'
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
const { t } = useI18n()

const resources = computed(() => [
  { key: 'sugar',     label: t('resourceBar.sugar'),     name: 'SUGAR',     icon: sugarIcon  },
  { key: 'flour',     label: t('resourceBar.flour'),     name: 'FLOUR',     icon: flourIcon  },
  { key: 'eggs',      label: t('resourceBar.eggs'),      name: 'EGGS',      icon: eggsIcon   },
  { key: 'butter',    label: t('resourceBar.butter'),    name: 'BUTTER',    icon: butterIcon },
  { key: 'chocolate', label: t('resourceBar.chocolate'), name: 'CHOCOLATE', icon: chocoIcon  },
  { key: 'milk',      label: t('resourceBar.milk'),      name: 'MILK',      icon: milkIcon   },
])

const cookieTooltip = computed(() => [
  { text: t('resourceBar.cookiesLine', { value: fmt(playerStore.cookies) }) },
  { text: t('resourceBar.netWorthLine', { value: fmtBig(playerStore.netWorth) }) },
])

function resTooltip(res) {
  const amount   = playerStore[res.key] ?? 0
  const price    = marketStore.priceOf(res.name)
  const sellVal  = amount * price * 0.85   // nach 15% Gebühr
  return [
    { text: t('resourceBar.resourceLine', { label: res.label, amount: fmt(amount) }) },
    { text: t('resourceBar.marketPriceLine', { price: price.toFixed(4) }) },
    { text: t('resourceBar.sellValueLabel'), tooltip: t('resourceBar.sellValueExplain') },
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
.resource-label { font-size: 9px; color: #fff1a9; opacity: 0.75; line-height: 1; }
.resource-value { font-size: 12px; font-weight: 700; color: #fff1a9; line-height: 1.2; }
.cookies-value  { font-size: 15px; }
</style>
