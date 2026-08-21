import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    host: '0.0.0.0',
    // '.ngrok-free.app'/'.ngrok.app' -- Wildcard fuer Freundes-Tests via scripts/start.sh --ngrok
    // (zufaellige Subdomain bei jedem Start, siehe docs)
    allowedHosts: ['cookie.r3dconcrete.de', '.ngrok-free.app', '.ngrok.app']
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
