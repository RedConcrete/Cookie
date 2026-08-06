import { defineStore } from 'pinia'
import { ref } from 'vue'
import { bakeStatus } from '../services/api.js'

const SYNC_MS = 15000  // background safety-net poll while a job is active
const TICK_MS = 1000   // local countdown tick, no network -- server already told us remainingSeconds

export const useBakeStore = defineStore('bake', () => {
  const status  = ref(null)   // null = kein aktiver Job (BakeJobStatusDto while one exists)
  let steamId   = null
  let syncTimer = null
  let tickTimer = null

  function rescheduleTick() {
    clearInterval(tickTimer)
    tickTimer = null
    if (status.value && !status.value.done) {
      tickTimer = setInterval(tick, TICK_MS)
    }
  }

  // Counts down locally between server syncs so the progress bar/timer stays
  // smooth without hitting the backend every second. Once it reaches 0, does
  // one immediate poll to confirm completion with the server (source of truth
  // for "done"/claimable, not the local clock).
  function tick() {
    if (!status.value || status.value.done || status.value.remainingSeconds <= 0) return
    status.value = { ...status.value, remainingSeconds: status.value.remainingSeconds - 1 }
    if (status.value.remainingSeconds <= 0) poll()
  }

  async function poll() {
    if (!steamId) return
    try {
      const s = await bakeStatus(steamId)
      status.value = s.active ? s : null
      rescheduleTick()
    } catch (e) {
      // Swallowed on purpose (transient network hiccups shouldn't spam the UI),
      // but logged -- a silent catch here once hid a real backend 500 for good,
      // leaving the progress UI permanently blank with no trace of why.
      console.error('[Bake] Status poll failed', e)
    }
  }

  function start(id) {
    if (syncTimer) return          // läuft schon
    steamId = id
    poll()
    syncTimer = setInterval(poll, SYNC_MS)
  }

  function stop() {
    clearInterval(syncTimer); syncTimer = null
    clearInterval(tickTimer); tickTimer = null
  }

  return { status, start, stop, poll }
})
