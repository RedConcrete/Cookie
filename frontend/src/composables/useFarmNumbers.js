import { ref } from 'vue'

const numbers  = ref([])
const stacks   = {}
let nextId = 0

const COLORS = ['#E24B4A', '#ef9f27', '#D4537E', '#7F77DD', '#1D9E75']
const SIZES  = [14, 16, 18, 20, 22]

const RISE_DURATION  = 0.45
const HOLD_DURATION  = 0.5
const FADE_DURATION  = 0.4
const TOTAL_DURATION = RISE_DURATION + HOLD_DURATION + FADE_DURATION
const RISE_PX        = 135
const STACK_STEP     = 0

export function spawnFarmNumber(value, x, y, { crit = false, color = null } = {}) {
  const id    = nextId++
  const c     = crit ? '#E24B4A' : (color ?? COLORS[Math.floor(Math.random() * COLORS.length)])
  const size  = crit ? 24 : SIZES[Math.floor(Math.random() * SIZES.length)]

  // Horizontaler Drift: ±60px
  const vx     = (Math.random() * 240 - 120)
  const restX  = x + vx * RISE_DURATION
  // Rotation in Flugrichtung: links → negativ, rechts → positiv, max ±25°
  const rotate = Math.max(-25, Math.min(25, vx * 0.18))

  const key = `${Math.round(x / 40) * 40}_${Math.round(y / 40) * 40}`
  if (!stacks[key]) stacks[key] = 0
  const stackOffset = stacks[key]
  stacks[key] += STACK_STEP

  const restY = y - RISE_PX - stackOffset

  numbers.value.push({
    id, key,
    label: '+' + (Number.isInteger(value) ? value : Number(value).toFixed(1)),
    startX: x, restX,
    startY: y, restY,
    rotate,
    size, color: c, crit,
    currentX: x, currentY: y, opacity: 1, progress: 0,
  })

  const entry = numbers.value[numbers.value.length - 1]

  let startTs = null
  function step(ts) {
    if (!startTs) startTs = ts
    const p = (ts - startTs) / 1000
    entry.progress = p

    if (p < RISE_DURATION) {
      const t    = p / RISE_DURATION
      const ease = 1 - (1 - t) * (1 - t)
      entry.currentX = entry.startX + (entry.restX - entry.startX) * ease
      entry.currentY = entry.startY + (entry.restY - entry.startY) * ease
      entry.opacity  = 1
    } else if (p < RISE_DURATION + HOLD_DURATION) {
      entry.currentX = entry.restX
      entry.currentY = entry.restY
      entry.opacity  = 1
    } else {
      const fp = (p - RISE_DURATION - HOLD_DURATION) / FADE_DURATION
      entry.currentX = entry.restX
      entry.currentY = entry.restY
      entry.opacity  = Math.max(0, 1 - fp)
    }

    if (p >= TOTAL_DURATION) {
      numbers.value = numbers.value.filter(n => n.id !== id)
      stacks[key] = Math.max(0, (stacks[key] ?? STACK_STEP) - STACK_STEP)
      if (stacks[key] === 0) delete stacks[key]
      return
    }
    requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

export function useFarmNumbers() {
  return { numbers }
}
