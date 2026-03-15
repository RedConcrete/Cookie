<template>
  <div class="market-table-wrapper">
    <table class="market-table">
      <thead>
        <tr>
          <th>Ressource</th>
          <th>Preis (Cookies)</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="res in resources"
          :key="res.name"
          :class="{ selected: selected === res.name }"
          @click="$emit('select', res.name)"
        >
          <td>{{ res.label }}</td>
          <td>{{ fmt(marketStore.priceOf(res.name)) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { useMarketStore } from '../stores/market.js'

defineProps({ selected: { type: String, default: null } })
defineEmits(['select'])

const marketStore = useMarketStore()

const resources = [
  { name: 'SUGAR',     label: 'Zucker'     },
  { name: 'FLOUR',     label: 'Mehl'       },
  { name: 'EGGS',      label: 'Eier'       },
  { name: 'BUTTER',    label: 'Butter'     },
  { name: 'CHOCOLATE', label: 'Schokolade' },
  { name: 'MILK',      label: 'Milch'      }
]

function fmt(v) {
  return Number(v).toFixed(4)
}
</script>
