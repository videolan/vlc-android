import Vue from 'vue'
import Router from 'vue-router'
import PageDownloads from './pages/PageDownloads.vue'

Vue.use(Router)

export default new Router({
    history: Router.createWebHashHistory(),
    routes: [
        { path: '/', component: PageDownloads, name: 'Logs' },
    ]
})
