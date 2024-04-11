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
import logger from './plugins/logger'
import { tooltip } from './plugins/tooltip'

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})



router.beforeEach((to, from, next) => {
  const store = usePlayerStore()
  logger.info(`Router: ${from.name} -> ${to.name}`)
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
  createApp(App).directive('tooltip', tooltip).use(i18n).use(VueLazyload).use(router).use(pinia).use(logger).use(vlcUtils).mount('#app')
})

export default router;


