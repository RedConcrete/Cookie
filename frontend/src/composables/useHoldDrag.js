import { reactive, onUnmounted } from 'vue'

const HOLD_MS = 620

/**
 * Long-press-then-drag mechanic for moving buildings on the Hof grid.
 * Press and hold a building: a progress bar fills over HOLD_MS. Once full,
 * the building is "armed" (green outline) and follows the pointer until release.
 * `dropOk(pos)` decides whether the drop position is valid (bounds / road corridors);
 * an invalid drop snaps back to the last valid position.
 */
export function useHoldDrag(dropOk, onTap, onDropped, getZoom = () => 1, initialPos = { x: 0, y: 0 }) {
  const state = reactive({
    pos: { ...initialPos },
    pressing: false,
    armed: false,
    progress: 0,
  })

  let lastOk = { ...initialPos }
  let timer = null
  let t0 = 0
  let wasArmed = false
  let moved = false

  function clearTimer() { clearInterval(timer); timer = null }

  function onMove(e) {
    if (!state.armed) return
    moved = true
    const z = getZoom() || 1
    state.pos = { x: state.pos.x + e.movementX / z, y: state.pos.y + e.movementY / z }
  }

  function onUp() {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    clearTimer()
    const ok = dropOk(state.pos)
    if (ok) lastOk = { ...state.pos }
    else state.pos = { ...lastOk }
    onDropped?.({ ...lastOk })
    const wasTap = !wasArmed && !moved
    state.pressing = false
    state.armed = false
    state.progress = 0
    if (wasTap && onTap) onTap()
  }

  function onPointerDown(e) {
    e.preventDefault()
    state.pressing = true
    state.progress = 0
    state.armed = false
    wasArmed = false
    moved = false
    t0 = Date.now()
    clearTimer()
    timer = setInterval(() => {
      state.progress = Math.min(1, (Date.now() - t0) / HOLD_MS)
      if (state.progress >= 1) {
        clearTimer()
        state.armed = true
        wasArmed = true
        state.pressing = false
      }
    }, 30)
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
  }

  onUnmounted(() => {
    clearTimer()
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  })

  return { state, onPointerDown, isBlocked: (fn) => fn(state.pos) === false }
}
