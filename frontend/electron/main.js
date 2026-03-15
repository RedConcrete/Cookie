import { app, BrowserWindow, ipcMain } from 'electron'
import { spawn } from 'child_process'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

let mainWindow
let backendProcess = null

// ---------------------------------------------------------------------------
// Window
// ---------------------------------------------------------------------------

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 900,
    minHeight: 600,
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
// In dev mode the backend runs separately via start.bat / build.bat
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
// ---------------------------------------------------------------------------
// Replace this stub with steamworks.js once the package is installed:
// npm install steamworks.js
// Then: const steamworks = require('steamworks.js')
// ---------------------------------------------------------------------------

function initSteam() {
  // TODO: real Steam init
  // const client = steamworks.init(2816100)
  // const steamId = client.localplayer.getSteamId().steamId64.toString()
  // mainWindow.webContents.on('did-finish-load', () => {
  //   mainWindow.webContents.send('steam-auth', { steamId })
  // })

  const stubSteamId = 'DEV_PLAYER_001'
  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow.webContents.send('steam-auth', { steamId: stubSteamId })
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
