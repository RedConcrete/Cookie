import { reactive, onUnmounted } from 'vue'

const HOLD_MS = 620
const TAP_GRACE_MS = 150 // ms -- wait this long before the progress bar even appears, so a quick click never shows it
const CANCEL_DIST = 6 // px (screen space) — moving this far while still charging aborts the hold

/**
 * Long-press-then-drag mechanic for moving buildings on the Hof grid.
 * Press and hold a building: nothing happens for TAP_GRACE_MS (a plain click
 * releases inside that window and is treated as a tap, no visible feedback).
 * Past that, a progress bar fills over HOLD_MS. Once full, the building is
 * "armed" (green outline) and follows the pointer until release.
 * `dropOk(pos)` decides whether the drop position is valid (bounds / road corridors);
 * an invalid drop snaps back to the last valid position.
 *
 * `snap`, if given ({ origin: {x,y}, size }), snaps `state.pos` to the world
 * tile grid live while dragging (jumps tile-to-tile) instead of following the
 * cursor 1:1 — the building's *absolute* position (origin + pos) is what lands
 * on the grid, so it works regardless of where origin itself sits.
 */
export function useHoldDrag(dropOk, onTap, onDropped, getZoom = () => 1, initialPos = { x: 0, y: 0 }, snap = null) {
  const state = reactive({
    pos: { ...initialPos },
    pressing: false,
    armed: false,
    progress: 0,
  })

  let lastOk = { ...initialPos }
  let raw = { ...initialPos }  // true (unsnapped) cumulative drag position
  let timer = null
  let graceTimer = null
  let t0 = 0
  let wasArmed = false
  let moved = false
  let cancelled = false
  let downX = 0, downY = 0  // screen-space pointerdown origin, to detect move-away during charge

  function clearTimer() { clearInterval(timer); timer = null }
  function clearGraceTimer() { clearTimeout(graceTimer); graceTimer = null }

  // Moving the pointer away while still charging (not armed yet) aborts the
  // hold entirely — it stays aborted even if the button is kept held down.
  function cancelHold() {
    cancelled = true
    clearGraceTimer()
    clearTimer()
    state.pressing = false
    state.progress = 0
    state.armed = false
  }

  function snapped(pos) {
    if (!snap) return pos
    const { origin, size } = snap
    return {
      x: Math.round((origin.x + pos.x) / size) * size - origin.x,
      y: Math.round((origin.y + pos.y) / size) * size - origin.y,
    }
  }

  function onMove(e) {
    if (cancelled) return
    if (!state.armed) {
      const dx = e.clientX - downX, dy = e.clientY - downY
      if (dx * dx + dy * dy > CANCEL_DIST * CANCEL_DIST) cancelHold()
      return
    }
    moved = true
    const z = getZoom() || 1
    raw = { x: raw.x + e.movementX / z, y: raw.y + e.movementY / z }
    state.pos = snapped(raw)
  }

  function onUp() {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    clearGraceTimer()
    clearTimer()
    const ok = dropOk(state.pos)
    if (ok) lastOk = { ...state.pos }
    else state.pos = { ...lastOk }
    onDropped?.({ ...lastOk })
    const wasTap = !wasArmed && !moved && !cancelled
    state.pressing = false
    state.armed = false
    state.progress = 0
    if (wasTap && onTap) onTap()
  }

  // Fired after TAP_GRACE_MS if the pointer is still down and hasn't moved
  // away -- only now does the progress bar appear and the hold-to-arm countdown begin.
  function startCharging() {
    graceTimer = null
    state.pressing = true
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
  }

  function onPointerDown(e) {
    e.preventDefault()
    state.pressing = false
    state.progress = 0
    state.armed = false
    wasArmed = false
    moved = false
    cancelled = false
    downX = e.clientX
    downY = e.clientY
    raw = { ...state.pos }
    clearTimer()
    clearGraceTimer()
    graceTimer = setTimeout(startCharging, TAP_GRACE_MS)
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
  }

  onUnmounted(() => {
    clearGraceTimer()
    clearTimer()
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
  })

  return { state, onPointerDown, isBlocked: (fn) => fn(state.pos) === false }
}
