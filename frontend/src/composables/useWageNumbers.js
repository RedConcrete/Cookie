import { ref } from 'vue'

// Geschwister von useFarmNumbers.js, aber fest auf den Lohnabzug-Anwendungsfall zugeschnitten:
// faellt nach UNTEN weg (Gegenteil der aufsteigenden Ernte-Zahlen), immer rot, immer mit
// Minus-Vorzeichen. Eigene, kleine Engine statt Wiederverwendung, weil Richtung/Farbe/Format
// hier fest verdrahtet sind statt parametrisiert.
const numbers = ref([])
let nextId = 0

const RISE_DURATION  = 0.45
const HOLD_DURATION  = 0.5
const FADE_DURATION  = 0.4
const TOTAL_DURATION = RISE_DURATION + HOLD_DURATION + FADE_DURATION
const FALL_PX        = 70

export function spawnWageNumber(value, x, y) {
  const id = nextId++
  const restY = y + FALL_PX

  numbers.value.push({
    id,
    label: '-' + (Number.isInteger(value) ? value : Number(value).toFixed(1)),
    startX: x, startY: y, restY,
    currentX: x, currentY: y, opacity: 1,
  })

  const entry = numbers.value[numbers.value.length - 1]

  let startTs = null
  function step(ts) {
    if (!startTs) startTs = ts
    const p = (ts - startTs) / 1000

    if (p < RISE_DURATION) {
      const t    = p / RISE_DURATION
      const ease = t * t
      entry.currentY = entry.startY + (entry.restY - entry.startY) * ease
      entry.opacity  = 1
    } else if (p < RISE_DURATION + HOLD_DURATION) {
      entry.currentY = entry.restY
      entry.opacity  = 1
    } else {
      const fp = (p - RISE_DURATION - HOLD_DURATION) / FADE_DURATION
      entry.currentY = entry.restY
      entry.opacity  = Math.max(0, 1 - fp)
    }

    if (p >= TOTAL_DURATION) {
      numbers.value = numbers.value.filter(n => n.id !== id)
      return
    }
    requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

export function useWageNumbers() {
  return { numbers }
}
