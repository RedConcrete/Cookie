<template>
  <div class="px-dialog-overlay" @click.self="emit('close')" @wheel.stop>
    <div class="ld-box px-panel px-scroll">
      <div class="px-titlebar">
        <span>{{ t('leaderboardDialog.title') }}</span>
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
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import LeaderboardView    from './LeaderboardView.vue'
import PlayerProfileDialog from './PlayerProfileDialog.vue'
import { useAudio } from '../composables/useAudio.js'

const emit = defineEmits(['close'])
const audio = useAudio()
const { t } = useI18n()
const profileSteamId = ref(null)

onMounted(() => audio.playBookOpen())

function openProfile(steamId) {
  profileSteamId.value = steamId
}
</script>

<style scoped>
.ld-box { width: 760px; max-width: 95vw; max-height: 90vh; overflow: auto; }
</style>
