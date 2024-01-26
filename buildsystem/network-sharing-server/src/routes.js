import VideoList from './pages/VideoList'
import PlaylistList from './pages/PlaylistList'
import AudioArtists from './pages/AudioArtists'
import AudioAlbums from './pages/AudioAlbums'
import AudioTracks from './pages/AudioTracks'
import AudioGenres from './pages/AudioGenres'
import PageDownloads from './pages/PageDownloads'
import BrowseChild from './pages/BrowseChild'
import BrowseList from './pages/BrowseList'
import SearchList from './pages/SearchList'
import LoginPage from './pages/LoginPage'
import SslPage from './pages/SslPage'

const routes = [
  { path: '/', redirect: '/videos', name: 'Home' },
  { path: '/videos', component: VideoList, name: 'VideoList', meta: { showDisplayBar: true, showResume: true, showGrouping: true } },
  {
    path: '/audio', redirect: '/audio/artists', name: 'AudioArtists',
    children: [
      { path: 'artists', component: AudioArtists, name: 'AudioArtists', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'albums', component: AudioAlbums, name: 'AudioAlbums', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'tracks', component: AudioTracks, name: 'AudioTracks', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'genres', component: AudioGenres, name: 'AudioGenres', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
    ]
  },
  {
    path: '/browse', meta: { showDisplayBar: true },
    children: [
      { path: '', component: BrowseList, name: 'BrowseList', meta: { showDisplayBar: true } },
      { path: ':browseId', component: BrowseChild, name: 'BrowseChild', meta: { showDisplayBar: true } },
    ]
  },
  { path: '/playlists', component: PlaylistList, name: 'PlaylistList', meta: { showDisplayBar: true } },
  { path: '/search', component: SearchList, name: 'SearchList', meta: { showDisplayBar: false } },

  { path: '/logs', component: PageDownloads, name: 'Logs' },
  { path: '/login', component: LoginPage, name: 'LoginPage' },
  { path: '/login/error', component: LoginPage, name: 'LoginPageError', meta: { showError: true } },
  { path: '/ssl', component: SslPage, name: 'SslPage' },
]

export default routes;