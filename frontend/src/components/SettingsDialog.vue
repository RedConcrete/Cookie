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
            <span class="sd-hotkeys-hint">Zeile anklicken, dann Taste drücken</span>
          </div>
          <div class="sd-hotkey-list px-scroll">
            <div v-for="h in hotkeys.state.bindings" :key="h.id" class="sd-hotkey-row" @click="hotkeys.startEditing(h.id)" @keydown="hotkeys.state.editingId === h.id && hotkeys.assign(h.id, $event)" tabindex="0">
              <span>{{ h.action }}</span>
              <span class="sd-hotkey-key" :class="{ editing: hotkeys.state.editingId === h.id, dup: hotkeys.isDuplicate(h.id) }">
                {{ hotkeys.state.editingId === h.id ? 'TASTE DRÜCKEN …' : h.key }}
              </span>
            </div>
          </div>
          <div class="sd-hotkeys-foot">
            <button class="px-btn px-btn-flat" @click="hotkeys.reset()">ZURÜCKSETZEN</button>
            <span class="sd-hotkeys-note">Doppelbelegung wird rot markiert</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAudio } from '../composables/useAudio.js'
import { useHotkeys } from '../composables/useHotkeys.js'
import PixelIcon from './pixel/PixelIcon.vue'

const emit = defineEmits(['close'])
const audio = useAudio()
const hotkeys = useHotkeys()
const wageWarning = ref(true)
const pixelSnap = ref(false)

onMounted(() => audio.playBookOpen())
</script>

<style scoped>
.sd-box { width: 420px; max-width: 95vw; max-height: 90vh; overflow: auto; }
.sd-body { padding: 18px; display: flex; flex-direction: column; gap: 16px; }

.sd-slider-row { display: flex; align-items: center; gap: 10px; }
.sd-slider-label { width: 130px; font-size: 15px; color: var(--px-ink-txt); display: flex; align-items: center; gap: 6px; }
.sd-mute { background: none; border: none; cursor: pointer; display: inline-flex; align-items: center; }
.sd-slider { flex: 1; accent-color: var(--px-orange); }
.sd-slider-val { width: 40px; text-align: right; font-family: 'Silkscreen', monospace; font-size: 12px; color: var(--px-ink-txt); }

.sd-toggle-row { display: flex; align-items: center; justify-content: space-between; font-size: 15px; color: var(--px-ink-txt); }
.sd-toggle { width: 64px; height: 30px; background: var(--px-tan); border: 3px solid var(--px-ink); display: flex; padding: 2px; cursor: pointer; }
.sd-toggle.on { background: var(--px-green); justify-content: flex-end; }
.sd-toggle-knob { width: 26px; height: 100%; background: var(--px-cream2); border: 2px solid var(--px-ink); }

.sd-hotkeys { border-top: 3px solid var(--px-tan); padding-top: 14px; }
.sd-hotkeys-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.sd-label { font-family: 'Silkscreen', monospace; font-size: 10px; color: var(--px-tan-hd); letter-spacing: 1px; }
.sd-hotkeys-hint { font-size: 13px; color: var(--px-tan-ink); }

.sd-hotkey-list { max-height: 206px; overflow-y: auto; border: 3px solid var(--px-brown2); background: var(--px-cream2); }
.sd-hotkey-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px; border-bottom: 2px solid #e8dcbc; font-size: 15px; color: var(--px-ink-txt); cursor: pointer; }
.sd-hotkey-row:hover { background: #fff3c4; }
.sd-hotkey-key {
  font-family: 'Silkscreen', monospace; font-size: 10px; padding: 6px 9px; min-width: 64px; text-align: center;
  background: var(--px-cream3); border: 3px solid var(--px-ink); box-shadow: inset -2px -2px 0 #b9a276, inset 2px 2px 0 var(--px-cream);
  color: var(--px-ink-txt);
}
.sd-hotkey-key.editing { background: var(--px-gold); }
.sd-hotkey-key.dup { color: var(--px-red); }

.sd-hotkeys-foot { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.sd-hotkeys-note { margin-left: auto; font-size: 13px; color: var(--px-tan-ink); }
</style>
