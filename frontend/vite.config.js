import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: './',
  server: {
    port: 5173,
    host: '0.0.0.0',
    allowedHosts: ['cookie.r3dconcrete.de']
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
