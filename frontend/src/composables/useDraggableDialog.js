import { ref, computed } from 'vue'

// Deckt sich mit der Breakpoint-Grenze in pixel.css (.px-dialog-overlay
// wird darunter zum Bottom-Sheet ueber die volle Breite -- Dragging macht
// da keinen Sinn).
const DRAG_DISABLE_WIDTH = 860

export function useDraggableDialog() {
  const offsetX = ref(0)
  const offsetY = ref(0)
  const dragging = ref(false)

  let startX = 0, startY = 0, startOffsetX = 0, startOffsetY = 0
  let moved = false

  const dialogStyle = computed(() => {
    if (!offsetX.value && !offsetY.value) return null
    return { transform: `translate(${offsetX.value}px, ${offsetY.value}px)` }
  })

  function onDragMove(e) {
    offsetX.value = startOffsetX + (e.clientX - startX)
    offsetY.value = startOffsetY + (e.clientY - startY)
    moved = true
  }

  // Verhindert, dass ein Drag, der ueber dem Overlay-Hintergrund endet, dessen
  // @click.self auslöst und den Dialog versehentlich schliesst.
  function suppressClick(e) {
    e.stopPropagation()
    e.preventDefault()
  }

  function onDragEnd() {
    dragging.value = false
    window.removeEventListener('pointermove', onDragMove)
    window.removeEventListener('pointerup', onDragEnd)
    if (moved) window.addEventListener('click', suppressClick, { capture: true, once: true })
  }

  function onDragStart(e) {
    if (window.innerWidth <= DRAG_DISABLE_WIDTH) return
    if (e.target.closest('button, a, input, select, textarea')) return
    dragging.value = true
    moved = false
    startX = e.clientX
    startY = e.clientY
    startOffsetX = offsetX.value
    startOffsetY = offsetY.value
    window.addEventListener('pointermove', onDragMove)
    window.addEventListener('pointerup', onDragEnd)
  }

  return { dialogStyle, dragging, onDragStart }
}
