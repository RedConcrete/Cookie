import { reactive, computed, onUnmounted } from 'vue'

const HOVER_MS      = 700   // fill duration
const DRAIN_SLOW_MS = 2500  // drain after fill complete — tooltip stays visible until 0
const DRAIN_FAST_MS = 100   // drain when mouse leaves before fill — tooltip hides immediately

/**
 * State machine for hover-reveal tooltips.
 *
 * fill       → bar fills over HOVER_MS, tooltip visible
 * hold       → fill reached 1, mouse still in → bar stays full, tooltip stays visible
 * drain-slow → mouse left after hold → bar drains over DRAIN_SLOW_MS, tooltip stays visible
 * drain-fast → mouse left before fill completed → tooltip hides immediately, bar drains quickly
 * off        → hidden
 *
 * Re-entering during any drain resumes fill from current progress.
 * Leaving during drain-slow is ignored — tooltip fades on its own schedule.
 */
export function useHoverReveal() {
  const state = reactive({ visible: false, phase: 'off', progress: 0 })
  let t0  = null
  let raf = null

  function cancel() { cancelAnimationFrame(raf); raf = null }

  function tick() {
    if (state.phase === 'fill') {
      state.progress = Math.min(1, (Date.now() - t0) / HOVER_MS)
      if (state.progress >= 1) {
        state.phase = 'hold'
        return
      }
    } else if (state.phase === 'drain-slow') {
      state.progress = Math.max(0, 1 - (Date.now() - t0) / DRAIN_SLOW_MS)
      if (state.progress <= 0) {
        state.phase = 'off'
        state.visible = false
        return
      }
    } else if (state.phase === 'drain-fast') {
      state.progress = Math.max(0, 1 - (Date.now() - t0) / DRAIN_FAST_MS)
      if (state.progress <= 0) {
        state.phase = 'off'
        return
      }
    } else {
      return
    }
    raf = requestAnimationFrame(tick)
  }

  function onEnter() {
    cancel()
    state.visible = true
    t0 = Date.now() - state.progress * HOVER_MS
    state.phase = 'fill'
    tick()
  }

  function onLeave() {
    // During slow drain: let it run to completion, don't interrupt
    if (state.phase === 'drain-slow' || state.phase === 'off') return
    cancel()
    if (state.phase === 'hold') {
      // Was full while hovering → start the slow drain now that mouse left
      t0 = Date.now()
      state.phase = 'drain-slow'
    } else {
      // Left before fill complete → hide tooltip immediately, drain bar quickly
      state.visible = false
      t0 = Date.now() - (1 - state.progress) * DRAIN_FAST_MS
      state.phase = 'drain-fast'
    }
    tick()
  }

  onUnmounted(cancel)

  const running = computed(() => state.phase !== 'off')
  const pinned  = computed(() => state.phase === 'drain-slow')

  return { state, onEnter, onLeave, running, pinned }
}
