import { createApp } from 'vue'
import App from './App.vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import './scss/app.scss'
import routes from './routes'
import { createPinia } from 'pinia'
import { initI18n } from './i18n'
import vlcUtils from './plugins/vlcUtils'
import VueLazyload from 'vue-lazyload'
import { usePlayerStore } from './stores/PlayerStore'

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})



router.beforeEach((to, from, next) => {
  const store = usePlayerStore()
  if (store.responsivePlayerShowing) {
    store.playqueueShowing = false
    store.responsivePlayerShowing = false
    next(false)
  } else {
    next()
  }
})

const pinia = createPinia()
initI18n().then(function (i18n) {
  createApp(App).use(i18n).use(VueLazyload).use(router).use(pinia).use(vlcUtils).mount('#app')
})

