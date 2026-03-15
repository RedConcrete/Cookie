<template>
  <div class="app">
    <div v-if="blocked" class="status-overlay error">
      Bitte das Spiel über Steam starten.
    </div>

    <template v-else>
      <header class="app-header">
        <ResourceBar />
        <nav class="app-nav">
          <RouterLink to="/" class="nav-link">Markt</RouterLink>
          <RouterLink to="/produktion" class="nav-link">Produktion</RouterLink>
        </nav>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { usePlayerStore } from './stores/player.js'
import ResourceBar from './components/ResourceBar.vue'
import CookieSpinner from './components/CookieSpinner.vue'

const playerStore = usePlayerStore()
const blocked = ref(false)

onMounted(async () => {
  if (window.electronAPI) {
    // Electron: warte auf Steam-Auth via IPC
    window.electronAPI.onSteamAuth(({ steamId }) => {
      playerStore.init(steamId)
    })
    return
  }

  // Browser: Config vom Backend laden
  try {
    const res = await fetch('http://localhost:9876/api/v1/config')
    const { devMode } = await res.json()
    if (devMode) {
      playerStore.init('DEV_PLAYER_001')
    } else {
      blocked.value = true
    }
  } catch {
    // Backend nicht erreichbar
    blocked.value = true
  }
})
</script>
