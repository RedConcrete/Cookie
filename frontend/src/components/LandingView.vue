<template>
  <div class="landing-root">
    <MenuBackground />

    <header class="landing-header">
      <PixelIcon name="cookie" :size="26" />
      <span class="landing-title">COOKIE</span>
    </header>

    <div class="landing-center">
      <div class="landing-card px-panel">
        <div class="px-titlebar">
          <span>{{ t('landingView.welcomeTitle') }}</span>
        </div>
        <div class="landing-body">
          <p class="landing-text">{{ t('landingView.description') }}</p>
          <button class="px-btn landing-login-btn" @click="loginWithSteam">
            <ShortcutSlot />
            <PixelIcon name="steam" :size="14" style="vertical-align:-2px;margin-right:6px" />{{ t('landingView.loginButton') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import MenuBackground from './MenuBackground.vue'
import PixelIcon from './pixel/PixelIcon.vue'
import ShortcutSlot from './pixel/ShortcutSlot.vue'
import { BASE_URL } from '../services/api.js'

const { t } = useI18n()

// Echte Navigation (kein Router/fetch) -- Steam OpenID ist ein klassischer
// Browser-Redirect-Flow, siehe backend AuthController#steamLogin.
function loginWithSteam() {
  window.location.href = `${BASE_URL}/api/v1/auth/steam/login`
}
</script>

<style scoped>
.landing-root {
  position: relative;
  width: 100%; height: 100vh;
  overflow: hidden;
  display: flex; flex-direction: column;
}

.landing-header {
  position: relative; z-index: 5;
  display: flex; align-items: center; gap: 10px;
  padding: 14px 20px;
  background: var(--px-wood); border-bottom: 4px solid var(--px-ink);
  box-shadow: inset 0 3px 0 var(--px-wood-lt);
}
.landing-title { font-family: 'Silkscreen', monospace; font-size: 18px; letter-spacing: 2px; color: var(--px-gold); }

.landing-center {
  position: relative; z-index: 5;
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 24px;
}

.landing-card { width: 440px; max-width: 92vw; }
.landing-body { padding: 20px; display: flex; flex-direction: column; gap: 16px; align-items: center; text-align: center; }
.landing-text { font-size: 15px; color: var(--px-ink-txt); line-height: 1.55; }
.landing-login-btn { font-size: 13px; padding: 12px 22px; }
</style>
