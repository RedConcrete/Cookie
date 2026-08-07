<template>
  <div class="app" @mousedown="onMouseDown" @mouseover="onMouseOver">
    <ServerUnavailableView v-if="showServerUnavailable" @retry="retry" />
    <LandingView v-else-if="blocked" />
    <MainMenuView v-else-if="mainMenuReady && !started" @start="startGame" />

    <template v-else>
      <div v-if="playerStore.loading" class="status-overlay">
        <LoadingIndicator :size="48" />
      </div>
      <div v-else-if="playerStore.error" class="status-overlay error">
        {{ t('common.error') }}: {{ playerStore.error }}
      </div>
      <RouterView v-else />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePlayerStore } from './stores/player.js'
import { useAudio } from './composables/useAudio.js'
import LoadingIndicator from './components/pixel/LoadingIndicator.vue'
import LandingView from './components/LandingView.vue'
import MainMenuView from './components/MainMenuView.vue'
import ServerUnavailableView from './components/ServerUnavailableView.vue'

const playerStore = usePlayerStore()
const audio       = useAudio()
const { t } = useI18n()

const blocked = ref(false)
const mainMenuReady   = ref(false)
const started         = ref(false)
const configUnreachable = ref(false)
const authInfo = ref({ steamId: null, name: null })

const showServerUnavailable = computed(() => configUnreachable.value || playerStore.serverUnavailable)

async function startGame() {
  started.value = true
  await playerStore.init(authInfo.value.steamId, authInfo.value.name)
}

function retry() {
  location.reload()
}

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
  const params = new URLSearchParams(location.search)
  // Dev-Bypass zum Angucken der Landing-Page: http://localhost:5173/?landing
  // (normal ueberspringt dev-mode=true auf dem Backend sie immer)
  if (params.has('landing')) {
    blocked.value = true
    return
  }
  // Dev-Bypass zum Angucken des Main-Menus: http://localhost:5173/?menu
  if (params.has('menu')) {
    authInfo.value = { steamId: 'DEV_PLAYER_001', name: null }
    mainMenuReady.value = true
    return
  }
  if (window.electronAPI) {
    window.electronAPI.onSteamAuth(({ steamId, name }) => {
      authInfo.value = { steamId, name }
      mainMenuReady.value = true
    })
    return
  }
  try {
    const res = await fetch((import.meta.env.VITE_API_BASE_URL || 'http://localhost:9876') + '/api/v1/config')
    const cfg = await res.json()
    if (cfg.devMode) {
      authInfo.value = { steamId: 'DEV_PLAYER_001', name: null }
      mainMenuReady.value = true
    } else {
      blocked.value = true
    }
  } catch {
    configUnreachable.value = true
  }
})
</script>

<style>
.app { height: 100vh; overflow: hidden; }
</style>
