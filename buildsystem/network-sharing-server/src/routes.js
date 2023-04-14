import VideoList from './pages/VideoList'
import PlaylistList from './pages/PlaylistList'
import AudioArtists from './pages/AudioArtists'
import AudioAlbums from './pages/AudioAlbums'
import AudioTracks from './pages/AudioTracks'
import AudioGenres from './pages/AudioGenres'
import PageDownloads from './pages/PageDownloads'
import PageMediaUpload from './pages/PageMediaUpload'

const routes = [
    { path: '/', redirect: '/videos', name: 'Home' },
    { path: '/videos', component: VideoList, name: 'VideoList' },
    { path: '/playlists', component: PlaylistList, name: 'PlaylistList' },
    { path: '/audio', redirect: '/audio/artists',  name: 'AudioArtists',  
    children: [
        { path: 'artists', component: AudioArtists, name: 'AudioArtists' },
        { path: 'albums', component: AudioAlbums, name: 'AudioAlbums' },
        { path: 'tracks', component: AudioTracks, name: 'AudioTracks' },
        { path: 'genres', component: AudioGenres, name: 'AudioGenres' },
    ]},
   
    { path: '/logs', component: PageDownloads, name: 'Logs' },
    { path: '/upload', component: PageMediaUpload, name: "MediaUpload" }
]

export default routes;