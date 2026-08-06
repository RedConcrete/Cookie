<template>
  <div class="landing-root">
    <div class="landing-bg"></div>

    <div
      v-for="(w, i) in wanderers" :key="i"
      class="landing-wanderer" :style="w.outerStyle"
    >
      <div :style="w.scaleStyle">
        <TravelingWorker
          :travel-anim="w.anim" :travel-dur="w.dur" :travel-delay="w.delay"
          :leg-dur="w.legDur" :hat="w.hat" :skin="w.skin" :torso="w.torso"
        />
      </div>
    </div>

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
          <button class="px-btn landing-login-btn" disabled>
            <PixelIcon name="steam" :size="14" style="vertical-align:-2px;margin-right:6px" />{{ t('landingView.loginButton') }}
          </button>
          <div class="landing-soon">{{ t('landingView.browserLoginSoon') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import TravelingWorker from './buildings/TravelingWorker.vue'
import PixelIcon from './pixel/PixelIcon.vue'

const { t } = useI18n()

const ANIMS = ['patrol', 'trek', 'wander', 'wander2', 'wander3', 'rowwalk', 'commute']
const HATS   = ['#c78539', '#e67146', '#349c58', '#764032']
const SKINS  = ['#fff1a9', '#ebb85b']
const TORSOS = ['#a15c34', '#6f6e72', '#349c58']

function pick(arr, i) { return arr[i % arr.length] }

const wanderers = Array.from({ length: 7 }, (_, i) => {
  const scale = 2.4 + (i % 3) * 0.5
  return {
    anim: pick(ANIMS, i),
    dur: 6 + (i % 4) * 2,
    delay: i * 0.7,
    legDur: 0.45 + (i % 3) * 0.1,
    hat: pick(HATS, i),
    skin: pick(SKINS, i),
    torso: pick(TORSOS, i + 1),
    outerStyle: {
      position: 'absolute',
      left: `${8 + (i * 13) % 84}%`,
      top: `${20 + (i * 17) % 65}%`,
      zIndex: 1,
    },
    scaleStyle: { transform: `scale(${scale})`, transformOrigin: 'bottom center' },
  }
})
</script>

<style scoped>
.landing-root {
  position: relative;
  width: 100%; height: 100vh;
  overflow: hidden;
  display: flex; flex-direction: column;
}

.landing-bg {
  position: absolute; inset: 0;
  background-color: #7e9432;
  background-image: url('../assets/tiles/grass.png');
  background-size: 100px 100px;
  image-rendering: pixelated;
}

.landing-wanderer { pointer-events: none; opacity: 0.9; }

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
.landing-soon { font-size: 13px; color: var(--px-tan-ink); font-style: italic; }
</style>
