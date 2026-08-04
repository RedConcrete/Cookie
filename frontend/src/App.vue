<template>
  <div class="app" @mousedown="onMouseDown" @mouseover="onMouseOver">
    <LandingView v-if="blocked" />

    <template v-else>
      <div v-if="playerStore.loading" class="status-overlay">
        <CookieSpinner />
      </div>
      <div v-else-if="playerStore.error" class="status-overlay error">
        Fehler: {{ playerStore.error }}
      </div>
      <RouterView v-else />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { usePlayerStore } from './stores/player.js'
import { useAudio } from './composables/useAudio.js'
import CookieSpinner from './components/CookieSpinner.vue'
import LandingView from './components/LandingView.vue'

const playerStore = usePlayerStore()
const audio       = useAudio()

const blocked = ref(false)

// ── Audio-Events ─────────────────────────────────────────
const INTERACTIVE = new Set(['BUTTON', 'A', 'INPUT', 'SELECT', 'LABEL'])

function onMouseDown() {
  audio.startMusic()
  audio.playClick()
}

function onMouseOver(e) {
  const el = e.target
  if (el.closest('.tile-harvest')) return
  if (
    INTERACTIVE.has(el.tagName) ||
    el.closest('[data-hover]') ||
    el.closest('button') ||
    el.closest('a')
  ) {
    audio.playHover()
  }
}

onMounted(async () => {
  // Dev-Bypass zum Angucken der Landing-Page: http://localhost:5173/?landing
  // (normal ueberspringt dev-mode=true auf dem Backend sie immer)
  if (new URLSearchParams(location.search).has('landing')) {
    blocked.value = true
    return
  }
  if (window.electronAPI) {
    window.electronAPI.onSteamAuth(async ({ steamId, name }) => {
      await playerStore.init(steamId, name)
    })
    return
  }
  try {
    const res = await fetch((import.meta.env.VITE_API_BASE_URL || 'http://localhost:9876') + '/api/v1/config')
    const cfg = await res.json()
    if (cfg.devMode) {
      await playerStore.init('DEV_PLAYER_001')
    } else {
      blocked.value = true
    }
  } catch {
    blocked.value = true
  }
})
</script>

<style>
.app { height: 100vh; overflow: hidden; }
</style>
