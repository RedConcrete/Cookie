import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  // Receives Steam auth data sent by main.js after Steam init
  onSteamAuth: (callback) => ipcRenderer.on('steam-auth', (_event, data) => callback(data))
})
