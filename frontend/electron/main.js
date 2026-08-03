import { app, BrowserWindow, nativeImage } from 'electron'
import path from 'path'
import { fileURLToPath } from 'url'
import { createRequire } from 'module'

const require = createRequire(import.meta.url)
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

let mainWindow

// ---------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------

function createWindow() {
  const icon = nativeImage.createFromPath(
    path.join(__dirname, '../src/assets/Sprites/RecSprits/BackgroundCookie512.png')
  )

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    fullscreen: true,
    icon,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false
    },
    title: 'Cookie',
    autoHideMenuBar: true
  })

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173')
    mainWindow.webContents.openDevTools()
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))
  }
}

// ---------------------------------------------------------------------------
// Steam integration
// Steam must be running. Falls back to DEV_PLAYER_001 if unavailable.
// For dev testing outside Steam: place steam_appid.txt (containing 2816100)
// in the frontend/ directory, then launch via npm run electron:dev.
// ---------------------------------------------------------------------------

function initSteam() {
  let steamId = 'DEV_PLAYER_001'

  try {
    const steamworks = require('steamworks.js')
    const client = steamworks.init(2816100)
    steamId = client.localplayer.getSteamId().steamId64.toString()
    console.log('[Steam] Authenticated as', steamId)
  } catch (err) {
    console.warn('[Steam] Not available, using stub ID:', err.message)
  }

  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow.webContents.send('steam-auth', { steamId })
  })
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

app.whenReady().then(() => {
  createWindow()
  initSteam()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
