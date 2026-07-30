<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="ld-box px-panel">
      <div class="px-titlebar">
        <span>RANGLISTE</span>
        <button class="px-close" @click="emit('close')">&times;</button>
      </div>
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
.ld-box { width: 580px; max-width: 95vw; max-height: 90vh; overflow: auto; }
</style>
