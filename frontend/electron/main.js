import { app, BrowserWindow, nativeImage } from 'electron'
import { spawn } from 'child_process'
import path from 'path'
import { fileURLToPath } from 'url'
import { createRequire } from 'module'

const require = createRequire(import.meta.url)
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

let mainWindow
let backendProcess = null

// ---------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------

function createWindow() {
  const icon = nativeImage.createFromPath(
    path.join(__dirname, '../src/assets/Sprites/BackgroundCookieGameIcon.png')
  )

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
    icon,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
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
// Backend process (only in packaged builds)
// ---------------------------------------------------------------------------

function startBackend() {
  if (isDev) return

  const jarPath = path.join(process.resourcesPath, 'backend', 'app.jar')
  console.log('[Backend] Starting JAR:', jarPath)

  backendProcess = spawn('java', ['-jar', jarPath], {
    env: {
      ...process.env,
      SPRING_DATASOURCE_URL: 'jdbc:postgresql://localhost:5432/cookie',
      SPRING_DATASOURCE_USERNAME: 'postgres',
      SPRING_DATASOURCE_PASSWORD: '1234'
    }
  })

  backendProcess.stdout.on('data', d => process.stdout.write('[Backend] ' + d))
  backendProcess.stderr.on('data', d => process.stderr.write('[Backend] ' + d))

  backendProcess.on('exit', code => {
    console.log('[Backend] Process exited with code', code)
    backendProcess = null
  })
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
  startBackend()
  createWindow()
  initSteam()
})

app.on('window-all-closed', () => {
  if (backendProcess) backendProcess.kill()
  if (process.platform !== 'darwin') app.quit()
})
