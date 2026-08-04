<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="sd-box px-panel px-scroll">
      <div class="px-titlebar">
        <span>EINSTELLUNGEN</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>

      <div class="sd-body">
        <div class="sd-slider-row">
          <div class="sd-slider-label">Musik <button class="sd-mute" @click="audio.musicMuted.value = !audio.musicMuted.value"><PixelIcon :name="audio.musicMuted.value ? 'mute' : 'music'" :size="14" /></button></div>
          <input type="range" min="0" max="1" step="0.01" :value="audio.musicVolume.value"
            @input="audio.musicVolume.value = +$event.target.value" :disabled="audio.musicMuted.value" class="sd-slider" />
          <span class="sd-slider-val">{{ Math.round(audio.musicVolume.value * 100) }}%</span>
        </div>

        <div class="sd-slider-row">
          <div class="sd-slider-label">Soundeffekte <button class="sd-mute" @click="audio.sfxMuted.value = !audio.sfxMuted.value"><PixelIcon :name="audio.sfxMuted.value ? 'mute' : 'sound'" :size="14" /></button></div>
          <input type="range" min="0" max="1" step="0.01" :value="audio.sfxVolume.value"
            @input="audio.sfxVolume.value = +$event.target.value" :disabled="audio.sfxMuted.value" class="sd-slider" />
          <span class="sd-slider-val">{{ Math.round(audio.sfxVolume.value * 100) }}%</span>
        </div>

        <div class="sd-toggle-row">
          <span>Lohn-Warnung anzeigen</span>
          <button class="sd-toggle" :class="{ on: wageWarning }" @click="wageWarning = !wageWarning"><span class="sd-toggle-knob"></span></button>
        </div>
        <div class="sd-toggle-row">
          <span>Pixel-Zoom rasten</span>
          <button class="sd-toggle" :class="{ on: pixelSnap }" @click="pixelSnap = !pixelSnap"><span class="sd-toggle-knob"></span></button>
        </div>

        <div class="sd-hotkeys">
          <div class="sd-hotkeys-head">
            <span class="sd-label">TASTENKÜRZEL</span>
          </div>
          <div class="sd-hotkey-row">
            <span>Dialog schließen</span>
            <span class="sd-hotkey-key">ESC</span>
          </div>
          <div class="sd-hotkey-row">
            <span>Hof zentrieren</span>
            <span class="sd-hotkey-key">LEER</span>
          </div>
        </div>

        <button v-if="isElectron" class="px-btn sd-exit-btn" @click="exitGame">SPIEL BEENDEN</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAudio } from '../composables/useAudio.js'
import PixelIcon from './pixel/PixelIcon.vue'

const emit = defineEmits(['close'])
const audio = useAudio()
const wageWarning = ref(true)
const pixelSnap = ref(false)

onMounted(() => audio.playBookOpen())

// window.close() geht nur im gepackten Electron-Fenster -- im Browser (Dev-Modus,
// spaeter Web-Build) blockt die Browser-Sicherheit das fuer nicht per Script
// geoeffnete Tabs, deshalb Button dort gar nicht erst anzeigen.
const isElectron = !!window.electronAPI

function exitGame() {
  window.close()
}
</script>

<style scoped>
.sd-box { width: 420px; max-width: 95vw; max-height: 90vh; overflow: auto; }
.sd-body { padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.sd-slider-row { display: flex; align-items: center; gap: 10px; }
.sd-slider-label { width: 168px; flex-shrink: 0; font-size: 15px; color: var(--px-ink-txt); display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.sd-mute { background: none; border: none; cursor: pointer; display: inline-flex; align-items: center; }
.sd-slider { flex: 1; accent-color: var(--px-orange); }
.sd-slider-val { width: 40px; text-align: right; font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }

.sd-toggle-row { display: flex; align-items: center; justify-content: space-between; font-size: 15px; color: var(--px-ink-txt); }
.sd-toggle { width: 64px; height: 30px; background: var(--px-tan); border: 3px solid var(--px-ink); display: flex; padding: 2px; cursor: pointer; }
.sd-toggle.on { background: var(--px-green); justify-content: flex-end; }
.sd-toggle-knob { width: 26px; height: 100%; background: var(--px-cream2); border: 2px solid var(--px-ink); }

.sd-hotkeys { border-top: 3px solid var(--px-tan); padding-top: 14px; border: 3px solid var(--px-brown2); background: var(--px-cream2); padding: 10px; }
.sd-hotkeys-head { margin-bottom: 6px; }
.sd-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); letter-spacing: 1px; }

.sd-hotkey-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px; border-bottom: 2px solid #fff1a9; font-size: 15px; color: var(--px-ink-txt); }
.sd-hotkey-row:last-child { border-bottom: none; }
.sd-hotkey-key {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 6px 9px; min-width: 64px; text-align: center;
  background: var(--px-cream3); border: 3px solid var(--px-ink); box-shadow: inset -2px -2px 0 #aea47e, inset 2px 2px 0 var(--px-cream);
  color: var(--px-ink-txt);
}

.sd-exit-btn { background: var(--px-red); }
</style>
