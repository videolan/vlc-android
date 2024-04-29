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
import MLBrowsingDetails from './pages/MLBrowsingDetails'

const routes = [
  { path: '/', redirect: '/videos', name: 'Home' },
  {
    path: '/videos', name: 'Video',
    children: [
      { path: '', component: VideoList, name: 'VideoList', meta: { showDisplayBar: true, showResume: true, showGrouping: true } },
      { path: 'group/:groupId', component: VideoList, name: 'VideoGroupList', meta: { showDisplayBar: true, showFAB: true, playAllType: "video-group", icon: "ic_video" } },
      { path: 'folder/:folderId', component: VideoList, name: 'VideoFolderList', meta: { showDisplayBar: true, showFAB: true, playAllType: "video-folder", icon: "ic_folder" } },
    ]
  },
  {
    path: '/audio', redirect: '/audio/artists', name: 'AudioArtists',
    children: [
      { path: 'artists', component: AudioArtists, name: 'AudioArtists', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'albums', component: AudioAlbums, name: 'AudioAlbums', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'tracks', component: AudioTracks, name: 'AudioTracks', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false, showFAB: true, playAllType: "tracks" } },
      { path: 'genres', component: AudioGenres, name: 'AudioGenres', meta: { showDisplayBar: true, isAudio: true, showResume: true, showGrouping: false } },
      { path: 'artist/:artistId', component: MLBrowsingDetails, name: 'ArtistDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "artist", icon: "ic_no_artist" } },
      { path: 'album/:albumId', component: MLBrowsingDetails, name: 'AlbumDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "album", icon: "ic_album" } },
      { path: 'genre/:genreId', component: MLBrowsingDetails, name: 'GenreDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "genre", icon: "ic_genre" } },
    ]
  },
  {
    path: '/browse', name: 'Browse', meta: { showDisplayBar: true },
    children: [
      { path: '', component: BrowseList, name: 'BrowseList', meta: { showDisplayBar: true } },
      { path: ':browseId', component: BrowseChild, name: 'BrowseChild', meta: { showDisplayBar: true, showFAB: true, playAllType: "browser" } },
    ]
  },
  {
    path: '/playlists', redirect: '/playlists/all', name: 'Playlist',
    children: [
      { path: 'all', component: PlaylistList, name: 'PlaylistList', meta: { showDisplayBar: true } },
      { path: 'playlist/:playlistId', component: MLBrowsingDetails, name: 'PlaylistDetails', meta: { showDisplayBar: true, isAudio: false, showResume: false, showGrouping: false, showFAB: true, playAllType: "playlist", icon: "ic_playlist" } }
    ]
  },
  { path: '/search', component: SearchList, name: 'SearchList', meta: { showDisplayBar: false } },

  { path: '/logs', component: PageDownloads, name: 'Logs' },
  { path: '/login', component: LoginPage, name: 'LoginPage' },
  { path: '/login/error', component: LoginPage, name: 'LoginPageError', meta: { showError: true } },
  { path: '/ssl', component: SslPage, name: 'SslPage' },
]

export default routes;