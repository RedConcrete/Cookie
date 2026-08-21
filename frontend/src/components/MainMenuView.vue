<template>
  <div class="menu-root">
    <MenuBackground />

    <header class="menu-header">
      <PixelIcon name="cookie" :size="26" />
      <span class="menu-title">COOKIE</span>
    </header>

    <div class="menu-center">
      <div class="menu-card px-panel">
        <div class="menu-body">
          <button class="px-btn menu-btn" @click="emit('start')">
            <ShortcutSlot />{{ t('mainMenuView.startGame') }}
          </button>
          <button class="px-btn menu-btn" @click="dialog = 'settings'">
            <ShortcutSlot />{{ t('mainMenuView.settingsButton') }}
          </button>
          <button class="px-btn menu-btn" @click="dialog = 'credits'">
            <ShortcutSlot />{{ t('mainMenuView.creditsButton') }}
          </button>
        </div>
      </div>
    </div>

    <div class="menu-footer-left">
      <NestedTooltip :content="t('mainMenuView.discordButton')" silent instant>
        <a
          class="px-btn menu-discord-btn" :href="DISCORD_URL" target="_blank" rel="noopener noreferrer"
          :aria-label="t('mainMenuView.discordButton')"
        >
          <ShortcutSlot /><PixelIcon name="discord" :size="18" />
        </a>
      </NestedTooltip>
    </div>

    <SettingsDialog v-if="dialog === 'settings'" @close="dialog = null" />
    <CreditsDialog  v-if="dialog === 'credits'"  @close="dialog = null" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import MenuBackground from './MenuBackground.vue'
import SettingsDialog from './SettingsDialog.vue'
import CreditsDialog from './CreditsDialog.vue'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { useAudio } from '../composables/useAudio.js'
import { DISCORD_URL } from '../discord.js'
import NestedTooltip from './NestedTooltip.vue'

const emit = defineEmits(['start'])

const { t } = useI18n()
const audio = useAudio()
const dialog = ref(null)

// Soll schon im Menu laufen, nicht erst nach dem ersten Klick (App.vue's
// globaler mousedown-Handler ist der Browser-Autoplay-Fallback fuer den
// Fall, dass der Browser diesen Versuch hier ohne User-Geste ablehnt).
// two_left_socks ist exklusiv fuers Menue -- setMusicMode('menu') schaltet
// bei Rueckkehr aus dem Spiel (Idle-Timeout) von der Ingame-Playlist zurueck.
onMounted(() => { audio.setMusicMode('menu'); audio.startMusic() })
</script>

<style scoped>
.menu-root {
  position: relative;
  width: 100%; height: 100vh;
  overflow: hidden;
  display: flex; flex-direction: column;
}

.menu-header {
  position: relative; z-index: 5;
  display: flex; align-items: center; gap: 10px;
  padding: 14px 20px;
  background: var(--px-wood); border-bottom: 4px solid var(--px-ink);
  box-shadow: inset 0 3px 0 var(--px-wood-lt);
}
.menu-title { font-family: 'Silkscreen', monospace; font-size: 18px; letter-spacing: 2px; color: var(--px-gold); }

.menu-center {
  position: relative; z-index: 5;
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 24px;
}

.menu-card { width: 380px; max-width: 92vw; }
.menu-body { padding: 28px; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.menu-btn { width: 100%; font-size: 15px; padding: 14px 32px; }

.menu-footer-left {
  position: fixed;
  left: 20px; bottom: 20px;
  z-index: 20;
  display: flex; gap: 10px;
}
.menu-discord-btn {
  width: 44px; height: 44px; padding: 0;
  display: flex; align-items: center; justify-content: center;
  background: #534664; text-decoration: none;
}
</style>
