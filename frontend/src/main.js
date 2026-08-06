import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import FarmGridView from './views/FarmGridView.vue'
import { i18n } from './i18n'
import './assets/styles/main.css'
import './assets/styles/pixel.css'
import './assets/styles/NestedTooltip.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: FarmGridView }
  ]
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.mount('#app')
