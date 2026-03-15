<template>
  <div class="resource-bar">
    <div v-for="res in resources" :key="res.key" class="resource-item">
      <span class="resource-label">{{ res.label }}</span>
      <span class="resource-value">{{ fmt(playerStore[res.key]) }}</span>
    </div>
  </div>
</template>

<script setup>
import { usePlayerStore } from '../stores/player.js'

const playerStore = usePlayerStore()

const resources = [
  { key: 'cookies',   label: '🍪 Cookies'   },
  { key: 'sugar',     label: 'Zucker'        },
  { key: 'flour',     label: 'Mehl'          },
  { key: 'eggs',      label: 'Eier'          },
  { key: 'butter',    label: 'Butter'        },
  { key: 'chocolate', label: 'Schokolade'    },
  { key: 'milk',      label: 'Milch'         }
]

function fmt(v) {
  return Number(v).toFixed(2)
}
</script>
