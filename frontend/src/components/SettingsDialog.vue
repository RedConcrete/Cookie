<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop @mousedown.stop @mousemove.stop>
    <div class="dialog-box">
      <div class="dialog-header">
        <span class="dialog-title">Einstellungen</span>
        <button class="dialog-close" @click="emit('close')">✕</button>
      </div>
      <div class="settings-body">

        <div class="setting-group">
          <div class="setting-label">
            <span>Musik</span>
            <button class="mute-btn" @click="audio.musicMuted.value = !audio.musicMuted.value">
              {{ audio.musicMuted.value ? '🔇' : '🎵' }}
            </button>
          </div>
          <input
            type="range" min="0" max="1" step="0.01"
            :value="audio.musicVolume.value"
            @input="audio.musicVolume.value = +$event.target.value"
            :disabled="audio.musicMuted.value"
            class="vol-slider"
          />
          <span class="vol-val">{{ Math.round(audio.musicVolume.value * 100) }}%</span>
        </div>

        <div class="setting-group">
          <div class="setting-label">
            <span>Soundeffekte</span>
            <button class="mute-btn" @click="audio.sfxMuted.value = !audio.sfxMuted.value">
              {{ audio.sfxMuted.value ? '🔇' : '🔊' }}
            </button>
          </div>
          <input
            type="range" min="0" max="1" step="0.01"
            :value="audio.sfxVolume.value"
            @input="audio.sfxVolume.value = +$event.target.value"
            :disabled="audio.sfxMuted.value"
            class="vol-slider"
          />
          <span class="vol-val">{{ Math.round(audio.sfxVolume.value * 100) }}%</span>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { useAudio } from '../composables/useAudio.js'
const emit = defineEmits(['close'])
const audio = useAudio()
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}
.dialog-box {
  background: var(--surface);
  border: 2px solid var(--border);
  border-radius: 16px;
  width: 360px;
  max-width: 95vw;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border);
}
.dialog-title { font-size: 14px; font-weight: 700; color: var(--text); }
.dialog-close {
  background: none; border: none; font-size: 18px;
  cursor: pointer; color: var(--text-muted); padding: 2px 6px; border-radius: 4px;
}
.dialog-close:hover { color: var(--text); background: var(--surface2); }

.settings-body { padding: 20px; display: flex; flex-direction: column; gap: 20px; }

.setting-group { display: flex; align-items: center; gap: 10px; }
.setting-label { display: flex; align-items: center; gap: 6px; min-width: 130px; font-size: 13px; color: var(--text); }

.mute-btn {
  background: none; border: 1px solid var(--border); border-radius: 6px;
  cursor: pointer; font-size: 14px; padding: 2px 6px; line-height: 1;
}
.mute-btn:hover { background: var(--surface2); }

.vol-slider { flex: 1; accent-color: var(--accent); cursor: pointer; }
.vol-slider:disabled { opacity: 0.4; cursor: not-allowed; }
.vol-val { min-width: 36px; text-align: right; font-size: 12px; color: var(--text-muted); }
</style>
