import VideoList from './pages/VideoList'
import AudioList from './pages/AudioList'
import PageDownloads from './pages/PageDownloads'
import PageMediaUpload from './pages/PageMediaUpload'

const routes = [
    { path: '/', component: VideoList, name: 'VideoList' },
    { path: '/audio', component: AudioList, name: 'AudioList' },
    { path: '/logs', component: PageDownloads, name: 'Logs' },
    { path: '/upload', component: PageMediaUpload, name: "MediaUpload" }
]

export default routes;