import { ref, watch } from 'vue'

import music1 from '../assets/Music/Caketown 1.mp3'
import music2 from '../assets/Music/Deliciously Sour.mp3'
import music3 from '../assets/Music/ElevatorMusic.wav'
import music4 from '../assets/Music/Shake and Bake.mp3'
import music5 from '../assets/Music/Snowland.mp3'

import clickSfx   from '../assets/Sounds/zipclick.flac'
import hoverSfx1  from '../assets/Sounds/Hover1.wav'
import hoverSfx2  from '../assets/Sounds/Hover2.wav'
import hoverSfx3  from '../assets/Sounds/Hover3.wav'
import bookOpenSfx  from '../assets/Sounds/RPGsounds/OGG/bookOpen.ogg'
import bookCloseSfx from '../assets/Sounds/RPGsounds/OGG/bookClose.ogg'
import coins1Sfx    from '../assets/Sounds/RPGsounds/OGG/handleCoins.ogg'
import coins2Sfx    from '../assets/Sounds/RPGsounds/OGG/handleCoins2.ogg'
import chopSfx      from '../assets/Sounds/RPGsounds/OGG/chop.ogg'

const TRACKS   = [music1, music2, music3, music4, music5]
const HOVER_SFX = [hoverSfx1, hoverSfx2, hoverSfx3]

function loadNum(key, fallback) {
  const v = parseFloat(localStorage.getItem(key))
  return isNaN(v) ? fallback : v
}

// ── Singleton state ──────────────────────────────────────
const musicVolume = ref(loadNum('cookieMusicVol', 0.35))
const sfxVolume   = ref(loadNum('cookieSfxVol',   0.55))
const musicMuted  = ref(localStorage.getItem('cookieMusicMuted') === 'true')
const sfxMuted    = ref(localStorage.getItem('cookieSfxMuted')   === 'true')

let musicEl      = null
let shuffled     = []
let trackIdx     = 0
let musicStarted = false
let hoverCooldown = false

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function nextTrack() {
  if (!shuffled.length || trackIdx >= shuffled.length) {
    shuffled = shuffle(TRACKS)
    trackIdx = 0
  }
  if (musicEl) { musicEl.pause(); musicEl.onended = null }
  musicEl = new Audio(shuffled[trackIdx++])
  musicEl.volume = musicMuted.value ? 0 : musicVolume.value
  musicEl.onended = nextTrack
  musicEl.play().catch(() => {})
}

function startMusic() {
  if (musicStarted) return
  musicStarted = true
  nextTrack()
}

function playClick() {
  if (sfxMuted.value) return
  const a = new Audio(clickSfx)
  a.volume = sfxVolume.value
  a.play().catch(() => {})
}

function playHover() {
  if (sfxMuted.value || hoverCooldown) return
  hoverCooldown = true
  setTimeout(() => { hoverCooldown = false }, 80)
  const sfx = HOVER_SFX[Math.floor(Math.random() * HOVER_SFX.length)]
  const a = new Audio(sfx)
  a.volume = sfxVolume.value * 0.45
  a.play().catch(() => {})
}

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

function playSfx(src, volumeMult = 1) {
  if (sfxMuted.value) return
  const a = new Audio(src)
  a.volume = Math.min(1, sfxVolume.value * volumeMult)
  a.play().catch(() => {})
}

function playBookOpen()  { playSfx(bookOpenSfx,  0.8) }
function playBookClose() { playSfx(bookCloseSfx, 0.8) }
function playCoins()     { playSfx(Math.random() < 0.5 ? coins1Sfx : coins2Sfx, 0.75) }
function playChop()      { playSfx(chopSfx, 0.6) }

export function useAudio() {
  return {
    musicVolume, sfxVolume, musicMuted, sfxMuted,
    startMusic, playClick, playHover,
    playBookOpen, playBookClose, playCoins, playChop,
  }
}
