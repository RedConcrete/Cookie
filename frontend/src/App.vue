<template>
  <div class="app">
    <div v-if="blocked" class="status-overlay error">
      Bitte das Spiel über Steam starten.
    </div>

    <template v-else>
      <header class="app-header">
        <ResourceBar />
        <NestedTooltip :content="nwTooltip">
          <div class="header-networth">
            <span class="nw-label">Net Worth</span>
            <span class="nw-val">{{ fmtBig(playerStore.netWorth) }}</span>
          </div>
        </NestedTooltip>
        <div class="header-nav">
          <button class="nav-btn" @click="dialog = 'upgrades'">Upgrades</button>
          <button class="nav-btn" @click="dialog = 'prestige'">Prestige</button>
          <button class="nav-btn" @click="dialog = 'leaderboard'">Rangliste</button>
        </div>
      </header>

      <main class="app-content">
        <div v-if="playerStore.loading" class="status-overlay">
          <CookieSpinner />
        </div>
        <div v-else-if="playerStore.error" class="status-overlay error">
          Fehler: {{ playerStore.error }}
        </div>
        <RouterView v-else />
      </main>
    </template>

    <!-- Globale Dialoge (außerhalb RouterView damit sie über allem liegen) -->
    <UpgradeDialog     v-if="dialog === 'upgrades'"    @close="dialog = null" />
    <PrestigeDialog    v-if="dialog === 'prestige'"    @close="dialog = null" />
    <LeaderboardDialog v-if="dialog === 'leaderboard'" @close="dialog = null" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { usePlayerStore } from './stores/player.js'
import { useMarketStore } from './stores/market.js'
import { getUpgrades } from './services/api.js'
import ResourceBar       from './components/ResourceBar.vue'
import CookieSpinner     from './components/CookieSpinner.vue'
import UpgradeDialog     from './components/UpgradeDialog.vue'
import PrestigeDialog    from './components/PrestigeDialog.vue'
import LeaderboardDialog from './components/LeaderboardDialog.vue'
import NestedTooltip     from './components/NestedTooltip.vue'

const playerStore    = usePlayerStore()
const marketStore    = useMarketStore()
const blocked        = ref(false)
const dialog         = ref(null)
const playerUpgrades = ref([])
const sellFeeRate    = ref(0.15)
let upgradeTimer = null

async function loadUpgrades() {
  if (!playerStore.steamId) return
  try { playerUpgrades.value = await getUpgrades(playerStore.steamId) } catch {}
}

function fmtBig(v) {
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M'
  if (v >= 1_000)     return (v / 1_000).toFixed(2) + 'K'
  return Number(v ?? 0).toFixed(1)
}
function fmt2(v) { return Number(v ?? 0).toFixed(2) }

// Live-Ressourcenwert aus aktuellen Marktpreisen berechnen
const liveResourceValue = computed(() => {
  const p   = marketStore.priceOf
  const net = 1 - sellFeeRate.value
  return (
    playerStore.sugar     * p('SUGAR')     * net +
    playerStore.flour     * p('FLOUR')     * net +
    playerStore.eggs      * p('EGGS')      * net +
    playerStore.butter    * p('BUTTER')    * net +
    playerStore.chocolate * p('CHOCOLATE') * net +
    playerStore.milk      * p('MILK')      * net
  )
})

const liveNetWorth = computed(() =>
  playerStore.cookies + liveResourceValue.value + playerStore.nwUpgrades
)

const RESOURCES = [
  { key: 'sugar',     label: 'Zucker',     name: 'SUGAR'     },
  { key: 'flour',     label: 'Mehl',       name: 'FLOUR'     },
  { key: 'eggs',      label: 'Eier',       name: 'EGGS'      },
  { key: 'butter',    label: 'Butter',     name: 'BUTTER'    },
  { key: 'chocolate', label: 'Schokolade', name: 'CHOCOLATE' },
  { key: 'milk',      label: 'Milch',      name: 'MILK'      },
]

const upgradeBreakdown = computed(() => {
  const active = playerUpgrades.value.filter(u => u.totalSpent > 0)
  if (!active.length) return 'Keine Upgrades gekauft'
  return active
    .map(u => `${u.name.padEnd(22)} ${fmt2(u.totalSpent)}`)
    .join('\n')
})

const resourceBreakdown = computed(() => {
  const net = 1 - sellFeeRate.value
  return RESOURCES.map(r => {
    const amount = playerStore[r.key] ?? 0
    const price  = marketStore.priceOf(r.name)
    const value  = amount * price * net
    return `${r.label.padEnd(12)} ${fmtBig(amount)} × ${price.toFixed(4)} × ${fmt2(net)} = ${fmt2(value)}`
  }).join('\n')
})

const nwTooltip = computed(() => [
  { text: `Net Worth: ${fmtBig(liveNetWorth.value)}` },
  { text: `\nCookies:    ${fmt2(playerStore.cookies)}` },
  { text: `\nRessourcen: ${fmt2(liveResourceValue.value)}`,
    tooltip: resourceBreakdown.value },
  { text: `\nUpgrades:   ${fmt2(playerStore.nwUpgrades)}`,
    tooltip: upgradeBreakdown.value },
])

onMounted(async () => {
  if (window.electronAPI) {
    window.electronAPI.onSteamAuth(async ({ steamId }) => {
      await playerStore.init(steamId)
      loadUpgrades()
      upgradeTimer = setInterval(loadUpgrades, 15000)
    })
    return
  }
  try {
    const res = await fetch('http://localhost:9876/api/v1/config')
    const cfg = await res.json()
    const { devMode } = cfg
    if (devMode) {
      sellFeeRate.value = cfg.sellFeeRate ?? 0.15
      await playerStore.init('DEV_PLAYER_001')
      loadUpgrades()
      upgradeTimer = setInterval(loadUpgrades, 15000)
    } else {
      blocked.value = true
    }
  } catch {
    blocked.value = true
  }
})

onUnmounted(() => clearInterval(upgradeTimer))
</script>
