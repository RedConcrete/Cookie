<template>
  <div class="dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="dialog-box">
      <button class="dialog-close" @click="emit('close')">✕</button>
      <LeaderboardView @view-profile="openProfile" />
    </div>
  </div>

  <PlayerProfileDialog
    v-if="profileSteamId"
    :steamId="profileSteamId"
    @close="profileSteamId = null"
  />
</template>

<script setup>
import { ref } from 'vue'
import LeaderboardView    from './LeaderboardView.vue'
import PlayerProfileDialog from './PlayerProfileDialog.vue'

const emit = defineEmits(['close'])
const profileSteamId = ref(null)

function openProfile(steamId) {
  profileSteamId.value = steamId
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.dialog-box {
  background: var(--surface);
  border: 2px solid var(--border);
  border-radius: 16px;
  width: 580px;
  max-width: 95vw;
  max-height: 90vh;
  overflow: auto;
  position: relative;
  padding: 20px;
}
.dialog-close {
  position: absolute;
  top: 12px; right: 14px;
  background: none;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--text-muted);
}
.dialog-close:hover { color: var(--text); }
</style>
