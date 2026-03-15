<template>
  <div class="market-view">
    <div class="market-left">
      <h2>Marktpreise</h2>
      <MarketTable :selected="selectedResource" @select="selectedResource = $event" />
    </div>

    <div class="market-right">
      <PriceChart />
      <TradePanel :selected="selectedResource" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MarketTable from '../components/MarketTable.vue'
import PriceChart from '../components/PriceChart.vue'
import TradePanel from '../components/TradePanel.vue'

const selectedResource = ref(null)
</script>
