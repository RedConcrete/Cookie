import { defineStore } from 'pinia'
import { ref } from 'vue'
import { bakeStatus } from '../services/api.js'

export const useBakeStore = defineStore('bake', () => {
  const status  = ref(null)   // null = kein aktiver Job
  let steamId   = null
  let timer     = null

  async function poll() {
    if (!steamId) return
    try {
      const s = await bakeStatus(steamId)
      status.value = s.active ? s : null
    } catch {}
  }

  function start(id) {
    if (timer) return          // läuft schon
    steamId = id
    poll()
    timer = setInterval(poll, 5000)  // 5s statt 2s, nur einmal global
  }

  function stop() {
    clearInterval(timer)
    timer = null
  }

  return { status, start, stop, poll }
})
