import { reactive, onUnmounted } from 'vue'

const HOVER_MS = 700   // fill duration until pinned
const DRAIN_MS = 900   // drain duration after mouse leaves, until close

/**
 * Hover-reveal state machine used for HUD/building info panels in the Hof view.
 *
 * Behaviour (per chat "Tooltip-Verhalten und Lagerverwaltung"):
 * - Panel opens immediately on mouse-enter; a fill bar runs underneath.
 * - Once the bar is full the panel is "pinned" (stays open, bar hidden).
 * - On mouse-leave the same bar drains back down; empty = panel closes.
 * - Re-entering while draining resumes filling from the current point (timer resets).
 */
export function useHoverReveal() {
  const state = reactive({ visible: false, phase: 'off', progress: 0 })
  let t0 = null
  let raf = null

  function tick() {
    if (state.phase === 'fill') {
      const el = Date.now() - t0
      state.progress = Math.min(1, el / HOVER_MS)
      if (state.progress >= 1) { state.phase = 'pin'; return }
    } else if (state.phase === 'drain') {
      const el = Date.now() - t0
      state.progress = Math.max(0, 1 - el / DRAIN_MS)
      if (state.progress <= 0) { state.phase = 'off'; state.visible = false; return }
    } else {
      return
    }
    raf = requestAnimationFrame(tick)
  }

  function onEnter() {
    cancelAnimationFrame(raf)
    state.visible = true
    t0 = Date.now() - state.progress * HOVER_MS
    state.phase = 'fill'
    tick()
  }

  function onLeave() {
    if (!state.visible) return
    cancelAnimationFrame(raf)
    t0 = Date.now() - (1 - state.progress) * DRAIN_MS
    state.phase = 'drain'
    tick()
  }

  onUnmounted(() => cancelAnimationFrame(raf))

  return {
    state,
    onEnter,
    onLeave,
    get running() { return state.phase === 'fill' || state.phase === 'drain' },
    get pinned()  { return state.phase === 'pin' },
  }
}
