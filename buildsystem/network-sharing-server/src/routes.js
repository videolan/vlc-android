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
import ArtistDetails from './pages/ArtistDetails'
import AlbumDetails from './pages/AlbumDetails'
import PlaylistDetails from './pages/PlaylistDetails'

const routes = [
  { path: '/', redirect: '/videos', name: 'Home' },
  {
    path: '/videos',
    children: [
      { path: '', component: VideoList, name: 'VideoList', meta: { showDisplayBar: true, showResume: true, showGrouping: true } },
       { path: 'group/:groupId', component: VideoList, name: 'VideoGroupList', meta: { showDisplayBar: true, showFAB: true, playAllType: "video-group" } },
       { path: 'folder/:folderId', component: VideoList, name: 'VideoFolderList', meta: { showDisplayBar: true, showFAB: true, playAllType: "video-folder" } },
    ]
  },
  {
    path: '/audio', redirect: '/audio/artists', name: 'AudioArtists',
    children: [
      { path: 'artists', component: AudioArtists, name: 'AudioArtists', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'albums', component: AudioAlbums, name: 'AudioAlbums', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'tracks', component: AudioTracks, name: 'AudioTracks', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false, showFAB: true, playAllType: "tracks" } },
      { path: 'genres', component: AudioGenres, name: 'AudioGenres', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'artist/:artistId', component: ArtistDetails, name: 'ArtistDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "artist" } },
      { path: 'album/:albumId', component: AlbumDetails, name: 'AlbumDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "album" } },
    ]
  },
  {
    path: '/browse', meta: { showDisplayBar: true },
    children: [
      { path: '', component: BrowseList, name: 'BrowseList', meta: { showDisplayBar: true } },
      { path: ':browseId', component: BrowseChild, name: 'BrowseChild', meta: { showDisplayBar: true, showFAB: true, playAllType: "browser" } },
    ]
  },
  { 
    path: '/playlists', redirect: '/playlists/all', name: 'PlaylistList',
    children : [
      {path: 'all', component: PlaylistList, name: 'PlaylistList', meta: { showDisplayBar: true }},
      { path: 'playlist/:playlistId', component: PlaylistDetails, name: 'PlaylistDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "playlist" } }
    ]
},
  { path: '/search', component: SearchList, name: 'SearchList', meta: { showDisplayBar: false } },

  { path: '/logs', component: PageDownloads, name: 'Logs' },
  { path: '/login', component: LoginPage, name: 'LoginPage' },
  { path: '/login/error', component: LoginPage, name: 'LoginPageError', meta: { showError: true } },
  { path: '/ssl', component: SslPage, name: 'SslPage' },
]

export default routes;