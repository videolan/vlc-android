import PageDownloads from './pages/PageDownloads'
import PageMediaUpload from './pages/PageMediaUpload'

const routes = [
    { path: '/', component: PageDownloads, name: 'Home' },
    { path: '/upload', component: PageMediaUpload, name: "MediaUpload" }
]

export default routes;