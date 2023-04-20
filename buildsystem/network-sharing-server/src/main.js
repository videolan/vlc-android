import { createApp } from 'vue'
import App from './App.vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './scss/app.scss'
import routes from './routes'
import { createPinia } from 'pinia'
import { initI18n } from './i18n'
import vlcUtils  from './plugins/vlcUtils'
import VueLazyload from 'vue-lazyload'

const router = createRouter({
    history: createWebHashHistory(),
    routes,
})

const pinia = createPinia()
initI18n().then(function(i18n) {
    createApp(App).use(i18n).use(VueLazyload).use(router).use(pinia).use(vlcUtils).mount('#app')
})

