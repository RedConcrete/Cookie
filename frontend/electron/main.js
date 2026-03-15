import { app, BrowserWindow, ipcMain } from 'electron'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = process.env.NODE_ENV === 'development' || !app.isPackaged

let mainWindow

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

app.whenReady().then(() => {
  createWindow()
  initSteam()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

// ---------------------------------------------------------------------------
// Steam integration
// ---------------------------------------------------------------------------
// Replace this stub with steamworks.js once the package is installed.
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

  // Stub: sends a fake Steam ID for development
  const stubSteamId = 'DEV_PLAYER_001'
  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow.webContents.send('steam-auth', { steamId: stubSteamId })
  })
}
