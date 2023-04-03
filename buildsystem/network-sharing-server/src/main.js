import { createApp } from 'vue'
import App from './App.vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './scss/app.scss'
import routes from './routes'
import { createPinia } from 'pinia'

const router = createRouter({
    history: createWebHashHistory(),
    routes,
})

const pinia = createPinia()

createApp(App).use(router).use(pinia).mount('#app')

