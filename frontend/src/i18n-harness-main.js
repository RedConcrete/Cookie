import { createApp } from 'vue'
import { i18n } from './i18n/index.js'
import SettingsDialog from './components/SettingsDialog.vue'
import './assets/styles/main.css'
import './assets/styles/pixel.css'

const app = createApp(SettingsDialog)
app.use(i18n)
app.mount('#app')
