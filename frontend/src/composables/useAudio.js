import { ref, watch } from 'vue'

// Music — keep as streaming Audio (large files, one at a time)
import music1 from '../assets/Music/Caketown 1.mp3'
import music2 from '../assets/Music/Deliciously Sour.mp3'
import music3 from '../assets/Music/ElevatorMusic.wav'
import music4 from '../assets/Music/Shake and Bake.mp3'
import music5 from '../assets/Music/Snowland.mp3'

// SFX URLs — fetched once, decoded into AudioBuffers (no repeated HTTP requests)
import clickUrl     from '../assets/Sounds/zipclick.flac'
import hover1Url    from '../assets/Sounds/Hover1.wav'
import hover2Url    from '../assets/Sounds/Hover2.wav'
import hover3Url    from '../assets/Sounds/Hover3.wav'
import bookOpenUrl  from '../assets/Sounds/RPGsounds/OGG/bookOpen.ogg'
import bookCloseUrl from '../assets/Sounds/RPGsounds/OGG/bookClose.ogg'
import coins1Url    from '../assets/Sounds/RPGsounds/OGG/handleCoins.ogg'
import coins2Url    from '../assets/Sounds/RPGsounds/OGG/handleCoins2.ogg'
import chopUrl      from '../assets/Sounds/RPGsounds/OGG/chop.ogg'

const TRACKS = [music1, music2, music3, music4, music5]

function loadNum(key, fallback) {
  const v = parseFloat(localStorage.getItem(key))
  return isNaN(v) ? fallback : v
}

// ── Singleton state ──────────────────────────────────────
const musicVolume = ref(loadNum('cookieMusicVol', 0.35))
const sfxVolume   = ref(loadNum('cookieSfxVol',   0.55))
const musicMuted  = ref(localStorage.getItem('cookieMusicMuted') === 'true')
const sfxMuted    = ref(localStorage.getItem('cookieSfxMuted')   === 'true')

// ── Music (streaming HTMLAudioElement) ──────────────────
let musicEl      = null
let shuffled     = []
let trackIdx     = 0
let musicStarted = false

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function nextTrack() {
  if (!shuffled.length || trackIdx >= shuffled.length) { shuffled = shuffle(TRACKS); trackIdx = 0 }
  if (musicEl) { musicEl.pause(); musicEl.onended = null }
  musicEl = new Audio(shuffled[trackIdx++])
  musicEl.volume = musicMuted.value ? 0 : musicVolume.value
  musicEl.onended = nextTrack
  musicEl.play().catch(() => {})
}

function startMusic() {
  if (musicStarted) return
  musicStarted = true
  ensureCtx()
  nextTrack()
}

// ── AudioContext SFX (pre-decoded, zero network after init) ─
let actx = null
const bufs = {}

function ensureCtx() {
  if (!actx) {
    try { actx = new (window.AudioContext || window.webkitAudioContext)() } catch {}
  }
  if (actx?.state === 'suspended') actx.resume().catch(() => {})
  return actx
}

async function loadBuf(key, url) {
  if (!actx) return
  try {
    const resp = await fetch(url)
    const arr  = await resp.arrayBuffer()
    bufs[key]  = await actx.decodeAudioData(arr)
  } catch {}
}

// Decode all SFX at startup — one HTTP fetch each, then in-memory forever
;(async () => {
  try { actx = new (window.AudioContext || window.webkitAudioContext)() } catch { return }
  await Promise.all([
    loadBuf('click',      clickUrl),
    loadBuf('hover1',     hover1Url),
    loadBuf('hover2',     hover2Url),
    loadBuf('hover3',     hover3Url),
    loadBuf('bookOpen',   bookOpenUrl),
    loadBuf('bookClose',  bookCloseUrl),
    loadBuf('coins1',     coins1Url),
    loadBuf('coins2',     coins2Url),
    loadBuf('chop',       chopUrl),
  ])
})()

function playSfxBuf(key, vol) {
  if (sfxMuted.value) return
  const ctx = ensureCtx()
  const buf = bufs[key]
  if (!ctx || !buf) return
  const src  = ctx.createBufferSource()
  src.buffer = buf
  const gain = ctx.createGain()
  gain.gain.value = Math.min(1, sfxVolume.value * vol)
  src.connect(gain)
  gain.connect(ctx.destination)
  src.start()
}

let hoverCooldown = false

function playClick() { playSfxBuf('click', 1) }

function playHover() {
  if (hoverCooldown) return
  hoverCooldown = true
  setTimeout(() => { hoverCooldown = false }, 80)
  playSfxBuf(`hover${Math.floor(Math.random() * 3) + 1}`, 0.45)
}

function playBookOpen()  { playSfxBuf('bookOpen',  0.8) }
function playBookClose() { playSfxBuf('bookClose', 0.8) }
function playCoins()     { playSfxBuf(Math.random() < 0.5 ? 'coins1' : 'coins2', 0.75) }
function playChop()      { playSfxBuf('chop', 0.6) }

// ── Persist + apply volume changes ──────────────────────
watch(musicVolume, v => {
  localStorage.setItem('cookieMusicVol', v)
  if (musicEl) musicEl.volume = musicMuted.value ? 0 : v
})
watch(sfxVolume, v => localStorage.setItem('cookieSfxVol', v))
watch(musicMuted, v => {
  localStorage.setItem('cookieMusicMuted', v)
  if (musicEl) musicEl.volume = v ? 0 : musicVolume.value
})
watch(sfxMuted, v => localStorage.setItem('cookieSfxMuted', v))

export function useAudio() {
  return {
    musicVolume, sfxVolume, musicMuted, sfxMuted,
    startMusic, playClick, playHover,
    playBookOpen, playBookClose, playCoins, playChop,
  }
}
