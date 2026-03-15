<template>
  <div class="app">
    <header class="app-header">
      <ResourceBar />
      <nav class="app-nav">
        <RouterLink to="/" class="nav-link">Produktion</RouterLink>
        <RouterLink to="/market" class="nav-link">Markt</RouterLink>
      </nav>
    </header>

    <main class="app-content">
      <div v-if="playerStore.loading" class="status-overlay">Verbinde...</div>
      <div v-else-if="playerStore.error" class="status-overlay error">
        Fehler: {{ playerStore.error }}
      </div>
      <RouterView v-else />
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { usePlayerStore } from './stores/player.js'
import ResourceBar from './components/ResourceBar.vue'

const playerStore = usePlayerStore()

onMounted(() => {
  // Electron provides Steam ID via IPC.
  // In browser dev mode, fall back to a dev ID.
  if (window.electronAPI) {
    window.electronAPI.onSteamAuth(({ steamId }) => {
      playerStore.init(steamId)
    })
  } else {
    playerStore.init('DEV_PLAYER_001')
  }
})
</script>
