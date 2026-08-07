<template>
  <div class="unavail-root">
    <MenuBackground />

    <div class="unavail-center">
      <div class="unavail-card px-panel">
        <div class="px-titlebar">
          <span>{{ t('serverUnavailableView.title') }}</span>
        </div>
        <div class="unavail-body">
          <p class="unavail-text">{{ t('serverUnavailableView.description') }}</p>
          <a class="px-btn unavail-discord-btn" :href="DISCORD_URL" target="_blank" rel="noopener noreferrer">
            <ShortcutSlot />{{ t('serverUnavailableView.discordButton') }}
          </a>
          <button class="px-btn unavail-retry-btn" @click="emit('retry')">
            <ShortcutSlot />{{ t('serverUnavailableView.retryButton') }}
          </button>
        </div>
      </div>
    </div>

    <LanguagePicker />
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import MenuBackground from './MenuBackground.vue'
import LanguagePicker from './LanguagePicker.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { DISCORD_URL } from '../discord.js'

const emit = defineEmits(['retry'])
const { t } = useI18n()
</script>

<style scoped>
.unavail-root {
  position: relative;
  width: 100%; height: 100vh;
  overflow: hidden;
  display: flex; flex-direction: column;
}

.unavail-center {
  position: relative; z-index: 5;
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 24px;
}

.unavail-card { width: 440px; max-width: 92vw; }
.unavail-body { padding: 20px; display: flex; flex-direction: column; gap: 14px; align-items: center; text-align: center; }
.unavail-text { font-size: 15px; color: var(--px-ink-txt); line-height: 1.55; }
.unavail-discord-btn { font-size: 13px; padding: 12px 22px; background: #534664; text-decoration: none; }
.unavail-retry-btn { font-size: 12px; padding: 8px 18px; }
</style>
