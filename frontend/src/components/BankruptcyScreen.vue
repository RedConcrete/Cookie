<template>
  <div class="bkr-screen">
    <div class="bkr-grayscale" :class="{ active: revealed }"></div>
    <div class="bkr-veil" :class="{ active: revealed }"></div>
    <div class="bkr-content" :class="{ active: revealed }">
      <p class="bkr-title">{{ line }}</p>
      <button class="px-btn px-btn-sell bkr-retry" :disabled="resetting" @click="onRetry">
        <ShortcutSlot />{{ resetting ? t('common.loading') : t('bankruptcyScreen.retry') }}
      </button>
      <p v-if="errorMsg" class="bkr-error">{{ errorMsg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { hardResetPlayer } from '../services/api.js'
import { usePlayerStore } from '../stores/player.js'
import { useAudio } from '../composables/useAudio.js'
import ShortcutSlot from './pixel/ShortcutSlot.vue'

const emit = defineEmits(['close'])
const { t } = useI18n()
const playerStore = usePlayerStore()
const audio = useAudio()

const resetting = ref(false)
const errorMsg = ref(null)
const revealed = ref(false)

// Ein zufaelliger Spruch pro Bankrott (dark-souls-artige Flavor-Texte), einmal beim
// Mount gezogen statt reaktiv -- soll waehrend der Anzeige nicht wechseln.
const LINE_COUNT = 15
const line = t(`bankruptcyScreen.lines.${Math.floor(Math.random() * LINE_COUNT)}`)

// Bildschirm faded ueber CSS-Transitions unten von Grau ueber Schwarz zum Text
// (dark-souls-artiges "gestorben"-Gefuehl), Musik sackt parallel auf tieferen
// Pitch/leise ab (siehe enterBankruptcyMood) -- beides bleibt haengen, bis der
// Spieler ueber den Button selbst neu startet.
onMounted(() => {
  audio.enterBankruptcyMood()
  requestAnimationFrame(() => { revealed.value = true })
})

async function onRetry() {
  resetting.value = true
  errorMsg.value = null
  try {
    await hardResetPlayer(playerStore.steamId)
    window.dispatchEvent(new CustomEvent('cookie:return-to-menu'))
    emit('close')
  } catch (e) {
    errorMsg.value = t('bankruptcyScreen.error')
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.bkr-screen {
  position: fixed;
  inset: 0;
  z-index: 900;
  pointer-events: auto;
}

.bkr-grayscale {
  position: absolute;
  inset: 0;
  backdrop-filter: grayscale(0);
  transition: backdrop-filter 1.3s linear;
}
.bkr-grayscale.active { backdrop-filter: grayscale(1); }

.bkr-veil {
  position: absolute;
  inset: 0;
  background: var(--px-ink);
  opacity: 0;
  transition: opacity 1.3s linear 1s;
}
.bkr-veil.active { opacity: 1; }

.bkr-content {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 1.1s ease-in 2.3s;
}
.bkr-content.active {
  opacity: 1;
  pointer-events: auto;
}

.bkr-title {
  font-family: 'Silkscreen', monospace;
  font-size: 28px;
  letter-spacing: 3px;
  color: var(--px-red);
  text-shadow: 3px 3px 0 var(--px-red-dk);
  text-align: center;
  padding: 0 24px;
}

.bkr-retry { font-size: 14px; padding: 14px 22px; }

.bkr-error { color: var(--px-red-lt); font-size: 12px; }
</style>
