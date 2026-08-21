import { ref, watch } from 'vue'

// Music — keep as streaming Audio (large files, one at a time)
import music1 from '../assets/Music/Caketown 1.mp3'
import music2 from '../assets/Music/Deliciously Sour.mp3'
import music4 from '../assets/Music/Shake and Bake.mp3'
import music5 from '../assets/Music/Snowland.mp3'
import music6 from '../assets/Music/headscratcher.mp3'
import music7 from '../assets/Music/my_street.ogg'
import menuTrack from '../assets/Music/two_left_socks.ogg'

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

// two_left_socks ist exklusiv fuers Hauptmenue (kein Shuffle) -- deshalb
// separat von der Ingame-Playlist gehalten. Namen hier statt aus der URL
// abgeleitet (Vite haengt beim Build einen Hash an den Dateinamen).
const GAME_TRACKS = [
  { url: music1, name: 'Caketown 1' },
  { url: music2, name: 'Deliciously Sour' },
  { url: music4, name: 'Shake and Bake' },
  { url: music5, name: 'Snowland' },
  { url: music6, name: 'Headscratcher' },
  { url: music7, name: 'My Street' },
]
const MENU_TRACK = { url: menuTrack, name: 'Two Left Socks' }

function loadNum(key, fallback) {
  const v = parseFloat(localStorage.getItem(key))
  return isNaN(v) ? fallback : v
}

// ── Singleton state ──────────────────────────────────────
const musicVolume = ref(loadNum('cookieMusicVol', 0.35))
const sfxVolume   = ref(loadNum('cookieSfxVol',   0.55))
const musicMuted  = ref(localStorage.getItem('cookieMusicMuted') === 'true')
const sfxMuted    = ref(localStorage.getItem('cookieSfxMuted')   === 'true')
// Reaktiv fuers Einstellungen-Menue (Now-Playing-Panel) -- Name des gerade laufenden
// Tracks (Ingame-Shuffle oder Menue-Track), leer solange nichts spielt.
const currentTrackName = ref('')
// Reaktiv (statt frueher plain let), damit Now-Playing/Vor-Zurueck-Buttons wissen, ob
// gerade der Ingame-Shuffle laeuft ('game') oder der exklusive Menue-Track ('menu').
const musicMode = ref('menu')

// ── Music (streaming HTMLAudioElement) ──────────────────
let musicEl      = null
let shuffled     = []
let trackIdx     = 0
let musicStarted = false
// Abgespielte Ingame-Tracks in Reihenfolge + Zeiger darauf -- ermoeglicht "Track
// zurueck" (zu einem schon gespielten Track) getrennt vom Shuffle-Bag, der nur neue
// Tracks zieht. "Vor" bewegt den Zeiger erst durch schon bekannte History-Eintraege
// (falls per "Zurueck" dorthin gesprungen wurde), erst am Ende wird neu gezogen.
let history    = []
let historyPos = -1

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function pickNextFromBag() {
  if (!shuffled.length || trackIdx >= shuffled.length) { shuffled = shuffle(GAME_TRACKS); trackIdx = 0 }
  return shuffled[trackIdx++]
}

function playTrack(track) {
  if (musicEl) { musicEl.pause(); musicEl.onended = null }
  musicEl = new Audio(track.url)
  musicEl.volume = musicMuted.value ? 0 : musicVolume.value
  musicEl.onended = nextTrack
  currentTrackName.value = track.name
  // Browser-Autoplay-Policy kann den allerersten play() ohne User-Geste
  // ablehnen (z.B. Musikstart schon beim Main-Menu-Mount) -- musicStarted
  // zuruecksetzen, damit der naechste startMusic()-Aufruf (z.B. der erste
  // Klick irgendwo in der App) es erneut versucht statt fuer den Rest der
  // Session stumm zu bleiben.
  musicEl.play().catch(() => { musicStarted = false })
}

function nextTrack() {
  if (historyPos < history.length - 1) {
    historyPos++
  } else {
    history.push(pickNextFromBag())
    historyPos = history.length - 1
  }
  playTrack(history[historyPos])
}

function playMenuTrack() {
  if (musicEl) { musicEl.pause(); musicEl.onended = null }
  musicEl = new Audio(MENU_TRACK.url)
  musicEl.loop = true
  musicEl.volume = musicMuted.value ? 0 : musicVolume.value
  currentTrackName.value = MENU_TRACK.name
  musicEl.play().catch(() => { musicStarted = false })
}

function playForMode() {
  if (musicMode.value === 'menu') playMenuTrack(); else nextTrack()
}

function startMusic() {
  if (musicStarted) return
  musicStarted = true
  ensureCtx()
  playForMode()
}

// Wechselt zwischen Hauptmenue-Track (two_left_socks, exklusiv & geloopt) und
// Ingame-Shuffle-Playlist -- kein Track darf im jeweils anderen Kontext laufen.
function setMusicMode(mode) {
  if (mode === musicMode.value) return
  musicMode.value = mode
  if (musicStarted) playForMode()
}

// Manuelles Vor/Zurueck fuers Einstellungen-Menue. Im Menue-Modus gibt es nur den
// einen exklusiven, geloopten Track -- nichts zum Wechseln, also no-op statt
// versehentlich einen Ingame-Track ueber den Menue-Track zu legen.
function skipTrack() {
  if (musicMode.value !== 'game') return
  nextTrack()
}
function prevTrack() {
  if (musicMode.value !== 'game' || historyPos <= 0) return
  historyPos--
  playTrack(history[historyPos])
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
    musicVolume, sfxVolume, musicMuted, sfxMuted, musicMode, currentTrackName,
    startMusic, setMusicMode, skipTrack, prevTrack, playClick, playHover,
    playBookOpen, playBookClose, playCoins, playChop,
  }
}
