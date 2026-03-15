import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import MarketView from './views/MarketView.vue'
import IdleView from './views/IdleView.vue'
import './assets/styles/main.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: IdleView },
    { path: '/market', component: MarketView }
  ]
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
