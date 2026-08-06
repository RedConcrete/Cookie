<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="sd-box px-panel">
      <div class="px-titlebar">
        <span>{{ t('settings.title') }}</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <PixelScrollBox class="sd-scroll">
      <div class="sd-body">
        <PixelSection :title="t('settings.language')">
          <div class="sd-lang-row">
            <button
              v-for="lang in SUPPORTED_LOCALES" :key="lang"
              class="px-btn sd-lang-btn"
              :class="{ active: locale === lang }"
              @click="setLocale(lang)"
            >{{ lang.toUpperCase() }}</button>
          </div>
        </PixelSection>

        <PixelSection :title="t('settings.volume')">
          <div class="sd-slider-row">
            <div class="sd-slider-label">{{ t('settings.music') }} <button class="sd-mute" @click="audio.musicMuted.value = !audio.musicMuted.value"><PixelIcon :name="audio.musicMuted.value ? 'mute' : 'music'" :size="14" /></button></div>
            <input type="range" min="0" max="1" step="0.01" :value="audio.musicVolume.value"
              @input="audio.musicVolume.value = +$event.target.value" :disabled="audio.musicMuted.value" class="sd-slider"
              :style="{ '--fill': musicFillPct + '%' }" />
            <span class="sd-slider-val">{{ Math.round(audio.musicVolume.value * 100) }}%</span>
          </div>

          <div class="sd-slider-row">
            <div class="sd-slider-label">{{ t('settings.sfx') }} <button class="sd-mute" @click="audio.sfxMuted.value = !audio.sfxMuted.value"><PixelIcon :name="audio.sfxMuted.value ? 'mute' : 'sound'" :size="14" /></button></div>
            <input type="range" min="0" max="1" step="0.01" :value="audio.sfxVolume.value"
              @input="audio.sfxVolume.value = +$event.target.value" :disabled="audio.sfxMuted.value" class="sd-slider"
              :style="{ '--fill': sfxFillPct + '%' }" />
            <span class="sd-slider-val">{{ Math.round(audio.sfxVolume.value * 100) }}%</span>
          </div>
        </PixelSection>

        <PixelSection :title="t('settings.hotkeys')">
          <div class="sd-hotkey-row">
            <span>{{ t('settings.closeDialog') }}</span>
            <span class="sd-hotkey-key">ESC</span>
          </div>
          <div class="sd-hotkey-row">
            <span>{{ t('settings.centerFarm') }}</span>
            <span class="sd-hotkey-key">{{ t('settings.spaceKey') }}</span>
          </div>
        </PixelSection>

        <PixelSection :title="t('settings.camera')">
          <div class="sd-slider-row sd-cam-speed-row">
            <div class="sd-slider-label">{{ t('settings.speed') }}</div>
            <input type="range" min="120" max="1200" step="20" :value="camera.cameraSpeed.value"
              @input="camera.cameraSpeed.value = +$event.target.value" class="sd-slider"
              :style="{ '--fill': camSpeedFillPct + '%' }" />
            <span class="sd-slider-val">{{ camera.cameraSpeed.value }}</span>
          </div>

          <div class="sd-hotkey-row" v-for="d in camDirections" :key="d.dir">
            <span>{{ t(d.labelKey) }}</span>
            <button
              class="sd-hotkey-key sd-hotkey-btn"
              :class="{ listening: listeningFor === d.dir }"
              @click="startListen(d.dir)"
            >{{ listeningFor === d.dir ? t('settings.pressKey') : camera.keyLabel(camera.cameraKeys[d.dir]) }}</button>
          </div>
        </PixelSection>

        <button v-if="isElectron" class="px-btn sd-exit-btn" @click="exitGame">{{ t('settings.exitGame') }}</button>
      </div>
      </PixelScrollBox>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAudio } from '../composables/useAudio.js'
import { useCameraControls } from '../composables/useCameraControls.js'
import { setLocale, SUPPORTED_LOCALES } from '../i18n/index.js'
import PixelIcon from './pixel/PixelIcon.vue'
import PixelSection from './pixel/PixelSection.vue'
import PixelScrollBox from './pixel/PixelScrollBox.vue'

const emit = defineEmits(['close'])
const audio = useAudio()
const camera = useCameraControls()
const { t, locale } = useI18n()

const musicFillPct = computed(() => audio.musicVolume.value * 100)
const sfxFillPct   = computed(() => audio.sfxVolume.value * 100)

const camDirections = [
  { dir: 'up',    labelKey: 'settings.dirUp' },
  { dir: 'down',  labelKey: 'settings.dirDown' },
  { dir: 'left',  labelKey: 'settings.dirLeft' },
  { dir: 'right', labelKey: 'settings.dirRight' },
]
const CAM_SPEED_MIN = 120, CAM_SPEED_MAX = 1200
const camSpeedFillPct = computed(() => (camera.cameraSpeed.value - CAM_SPEED_MIN) / (CAM_SPEED_MAX - CAM_SPEED_MIN) * 100)
const listeningFor = ref(null)

function startListen(dir) {
  listeningFor.value = dir
}
// Captured in the capture phase so it wins over FarmGridView's window keydown
// listener, and stopped from bubbling so that listener never sees the key.
function captureKey(e) {
  if (!listeningFor.value) return
  e.preventDefault()
  e.stopPropagation()
  if (e.key === 'Escape') { listeningFor.value = null; return }
  camera.rebind(listeningFor.value, e.key)
  listeningFor.value = null
}

onMounted(() => {
  audio.playBookOpen()
  window.addEventListener('keydown', captureKey, true)
})
onUnmounted(() => window.removeEventListener('keydown', captureKey, true))

// window.close() geht nur im gepackten Electron-Fenster -- im Browser (Dev-Modus,
// spaeter Web-Build) blockt die Browser-Sicherheit das fuer nicht per Script
// geoeffnete Tabs, deshalb Button dort gar nicht erst anzeigen.
const isElectron = !!window.electronAPI

function exitGame() {
  window.close()
}
</script>

<style scoped>
.sd-box { width: 420px; max-width: 95vw; max-height: 90vh; display: flex; flex-direction: column; overflow: hidden; }
.sd-scroll { flex: 1 1 auto; min-height: 0; }
.sd-body { padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.sd-lang-row { display: flex; gap: 10px; padding: 4px 10px 10px; }
.sd-lang-btn { min-width: 64px; opacity: .6; }
.sd-lang-btn.active { opacity: 1; background: var(--px-green); }

.sd-slider-row { display: flex; align-items: center; gap: 10px; }
.sd-slider-label { width: 168px; flex-shrink: 0; font-size: 15px; color: var(--px-ink-txt); display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.sd-mute { background: none; border: none; cursor: pointer; display: inline-flex; align-items: center; }
.sd-slider-val { width: 40px; text-align: right; font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }

/* Pixel-art range slider: blocky filled track + chunky button-like thumb,
   built from the CSS custom prop --fill (0-100, set per-slider in the template)
   instead of the smooth native OS slider look. */
.sd-slider {
  flex: 1;
  -webkit-appearance: none;
  appearance: none;
  height: 16px;
  margin: 3px 0;
  background: linear-gradient(to right, var(--px-green) var(--fill, 0%), var(--px-cream3) var(--fill, 0%));
  border: 3px solid var(--px-ink);
  box-shadow: inset 2px 2px 0 rgba(0,0,0,.25);
  cursor: pointer;
}
.sd-slider:disabled { opacity: .5; cursor: not-allowed; }

.sd-slider::-webkit-slider-runnable-track { -webkit-appearance: none; height: 100%; background: transparent; }
.sd-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 16px; height: 24px;
  margin-top: -7px;
  background: var(--px-orange);
  border: 3px solid var(--px-ink);
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt);
  cursor: pointer;
}
.sd-slider::-moz-range-track { height: 10px; background: transparent; border: none; }
.sd-slider::-moz-range-progress { height: 10px; background: transparent; }
.sd-slider::-moz-range-thumb {
  width: 16px; height: 24px;
  background: var(--px-orange);
  border: 3px solid var(--px-ink);
  border-radius: 0;
  box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt);
  cursor: pointer;
}

.sd-hotkey-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px; border-bottom: 2px solid #fff1a9; font-size: 15px; color: var(--px-ink-txt); }
.sd-hotkey-row:last-child { border-bottom: none; }
.sd-hotkey-key {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 6px 9px; min-width: 64px; text-align: center;
  background: var(--px-cream3); border: 3px solid var(--px-ink); box-shadow: inset -2px -2px 0 #aea47e, inset 2px 2px 0 var(--px-cream);
  color: var(--px-ink-txt);
}
.sd-hotkey-btn { cursor: pointer; }
.sd-hotkey-btn:hover { filter: brightness(1.06); }
.sd-hotkey-btn.listening { background: var(--px-orange); color: var(--px-cream); box-shadow: inset -2px -2px 0 var(--px-orange-dk), inset 2px 2px 0 var(--px-orange-lt); }

.sd-cam-speed-row { padding: 8px 10px 12px; }

.sd-exit-btn { background: var(--px-red); }
</style>
