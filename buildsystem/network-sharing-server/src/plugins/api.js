const USE_SSL = process.env.VUE_APP_FORCE_SSL === "true" || location.protocol === 'https:'
let API_PORT = "8080"
if (USE_SSL) API_PORT = "8443"
const API_IP = process.env.NODE_ENV === 'development' ? `${process.env.VUE_APP_DEVICE_IP}:${API_PORT}` : `${location.host}`
let API_URL


if (!USE_SSL) {
    API_URL = `http://${API_IP}/`;
} else {

    API_URL = `https://${API_IP}/`;
}

let protocol = "wss"
if (!USE_SSL) {
    protocol = `ws`;
}

export const vlcApi = {
    /**
     * Retrieve the translation API URL
     */
    translation: `${API_URL}translation`,
    /**
     * Ask for a code generation
     */
    code: `${API_URL}code`,
    /**
     * Ask for a secure URL
     */
    secureUrl: `${API_URL}secure-url`,
    /**
     * send client logs to the server
     */
    sendLogs: `${API_URL}logs`,
    /**
     * POST url to send the code too
     */
    verifyCode: `${API_URL}verify-code`,
    /**
     * Retrieve the web socket API URL
     */
    websocket: `${protocol}://${API_IP}/echo`,
    /**
     * Retrieve the web socket API URL
     */
    websocketAuthTicket: `${API_URL}wsticket`,
    /**
     * Retrieve the media upload API URL
     */
    uploadMedia: `${API_URL}upload-media`,
    /**
     * Retrieve the video list API URL
     */
    videoList: (grouping, groupId, folderId) => { 
        var group = ""
        if (groupId) group = `&group=${groupId}`
        var folder = ""
        if (folderId) folder = `&folder=${folderId}`
       return `${API_URL}video-list?grouping=${grouping}${group}${folder}`
    },
    /**
     * Retrieve the artist list API URL
     */
    artistList: `${API_URL}artist-list`,
    /**
     * Retrieve the album list API URL
     */
    albumList: `${API_URL}album-list`,
    /**
     * Retrieve the track list API URL
     */
    trackList: `${API_URL}track-list`,
    /**
     * Retrieve the genre list API URL
     */
    genreList: `${API_URL}genre-list`,
    /**
     * Retrieve the artist details API URL
     */
    artistDetails: (artistId) => { 
        return`${API_URL}artist?id=${artistId}`
    },
    /**
     * Retrieve the album details API URL
     */
    albumDetails: (albumId) => { 
        return`${API_URL}album?id=${albumId}`
    },
    /**
     * Retrieve the genre details API URL
     */
    genreDetails: (genreId) => { 
        return`${API_URL}genre?id=${genreId}`
    },
    /**
     * Retrieve the playlist details API URL
     */
    playlistDetails: (playlistId) => { 
        return`${API_URL}playlist?id=${playlistId}`
    },
    /**
     * Retrieve the playlist list API URL
     */
    playlistList: `${API_URL}playlist-list`,
    /**
     * Retrieve the favorite list API URL
     */
    favoriteList: `${API_URL}favorite-list`,
    /**
     * Retrieve the stream list API URL
     */
    streamList: `${API_URL}stream-list`,
    /**
     * Retrieve the storage list API URL
     */
    storageList: `${API_URL}storage-list`,
    /**
     * Retrieve the network list API URL
     */
    networkList: `${API_URL}network-list`,
    /**
     * Retrieve the log file list API URL
     */
    logfileList: `${API_URL}logfile-list`,
    /**
     * Retrieve the browse folder API URL
     * @param {String} path the folder path
     * @returns the URL
     */
    browseList: (path) => {
        return `${API_URL}browse-list?path=${path}`
    },
    /**
     * Retrieve the app asset icon API URL
     * @param {Number} id the asset id 
     * @param {Number} width the img width
     * @param {Boolean} preventTint if true, preserve the source colors
     * @returns the URL
     */
    appAsset: (id, width, preventTint) => {
        const params = {}
        if (id) params.id = id
        if (width) params.width = width
        if (preventTint) params.preventTint = preventTint
        return `${API_URL}icon?${new URLSearchParams(params).toString()}`
    },
    /**
     * Retrieve the artwork API URL
     * @param {String} artwork the media artworkURL
     * @param {Number} id the media id
     * @param {String} type the media type
     * @param {String} randomizer a randomizer used to force the re-download for the current played media
     * @returns the URL
     */
    artwork: (artwork, id, type, randomizer) => {
        const params = {}
        if (artwork) params.artwork = artwork
        if (id) params.id = id
        if (type) params.type = type
        if (randomizer) params.randomizer = randomizer
        return `${API_URL}artwork?${new URLSearchParams(params).toString()}`
    },
    /**
     * Retrieve the file downlad preparation API URL
     * @param {Number} id the media id
     * @param {Sting} type  the media type
     * @returns the URL
     */
    prepareDownload: (id, type) => {
        const params = {}
        if (id) params.id = id
        if (type) params.type = type
        return `${API_URL}prepare-download?${new URLSearchParams(params).toString()}`
    },
    /**
     * Retrieve the search API URL
     * @param {String} query the search query
     * @returns the URL
     */
    search: (query) => {
        return `${API_URL}search?search=${query}`
    },
    /**
     * Retrieve the file download API URL
     * @param {String} file the file URL sent back by the prepare download API
     * @returns the URL
     */
    download: (file) => {
        return `${API_URL}download?file=${file}`
    },
    /**
     * Retrieve the media play API URL
     * @param {Object} media the media to play
     * @param {String} mediaType the media type
     * @param {Boolean} append append to play queue instead of direct play
     * @param {Boolean} asAudio force playing video as audio
     * @returns the URL
     */
    play: (media, mediaType, append, asAudio) => {
        const params = {}
        if (media.id) params.id = media.id
        if (media.path) params.path = media.path
        if (append) params.append = append
        if (asAudio) params.audio = asAudio
        if (mediaType) params.type = mediaType
        return `${API_URL}play?${new URLSearchParams(params).toString()}`
    },
    /**
     * Retrieve the media play API URL
     * @param {String} type the media container type (video-group, video-folder, ...)
     * @param {String} id the media container id
     * @param {String} path the path of the folder to play
     * @returns the URL
     */
    playAll: (type, id, path) => {
        const params = {}
        if (type) params.type = type
        if (id) params.id = id
        if (path != "") params.path = path
        return `${API_URL}play-all?${new URLSearchParams(params).toString()}`
    },
    /**
     * Retrieve the resume playback API URL
     * @param {Boolean} isAudio 
     * @returns the URL
     */
    resumePlayback: (isAudio) => {
        const params = {}
        if (isAudio) params.audio = isAudio
        return `${API_URL}resume-playback?${new URLSearchParams(params).toString()}`
    },
    /**
     * Download a log file
     */
    downloadLog: `${API_URL}download-logfile?file=`,
}
