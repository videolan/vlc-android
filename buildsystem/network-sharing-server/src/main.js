import { createApp } from 'vue'
import App from './App.vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './scss/app.scss'
import routes from './routes'
import { createPinia } from 'pinia'
import { i18n, loadLanguageAsync } from './i18n'
import vlcUtils  from './plugins/vlcUtils'

const router = createRouter({
    history: createWebHashHistory(),
    routes,
})

router.beforeEach((to, from, next) => {
    const lang = to.params.lang
    loadLanguageAsync(lang).then(() =>
        next()
    )
})



const pinia = createPinia()

createApp(App).use(router).use(i18n).use(pinia).use(vlcUtils).mount('#app')

