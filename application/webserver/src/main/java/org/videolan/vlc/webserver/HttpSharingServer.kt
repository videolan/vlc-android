/*
 * ************************************************************************
 *  HttpSharingServer.kt
 * *************************************************************************
 * Copyright © 2023 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.webserver

import android.content.Context
import android.content.res.Resources
import android.media.AudioManager
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.await
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.observeLiveDataUntil
import org.videolan.tools.*
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.providers.BrowserProvider
import org.videolan.vlc.providers.NetworkProvider
import org.videolan.vlc.providers.StorageProvider
import org.videolan.vlc.util.*
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.viewmodels.browser.FavoritesProvider
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.DateFormat
import java.time.Duration
import java.util.*

class HttpSharingServer(private val context: Context) : PlaybackService.Callback {
    private lateinit var engine: NettyApplicationEngine
    private var websocketSession: ArrayList<DefaultWebSocketServerSession> = arrayListOf()
    private var service: PlaybackService? = null
    private val format by lazy {
        object : ThreadLocal<DateFormat>() {
            override fun initialValue() = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
        }
    }
    private val networkSharesLiveData = LiveDataset<MediaLibraryItem>()


    private val _serverStatus = MutableLiveData(ServerStatus.NOT_INIT)
    val serverStatus: LiveData<ServerStatus>
        get() = _serverStatus

    private val _serverConnections = MutableLiveData(listOf<WebServerConnection>())
    val serverConnections: LiveData<List<WebServerConnection>>
        get() = _serverConnections


    private val miniPlayerObserver = androidx.lifecycle.Observer<Boolean> {
        AppScope.launch { websocketSession.forEach { it.send(Frame.Text("Stopped")) } }
    }

    private var auth = false
    private var user = ""
    private var password = ""

    init {
        copyWebServer()
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }
                .onCompletion { service?.removeCallback(this@HttpSharingServer) }
                .launchIn(AppScope)
        _serverStatus.postValue(ServerStatus.STOPPED)
    }


    /**
     * Start the server. Refresh the authentication settings before
     * Also start monitoring the network shares for the web browser
     */
    suspend fun start() {
        auth = Settings.getInstance(context).getBoolean(KEY_WEB_SERVER_AUTH, false)
        user = Settings.getInstance(context).getString(KEY_WEB_SERVER_USER, "") ?: ""
        password = Settings.getInstance(context).getString(KEY_WEB_SERVER_PASSWORD, "")
                ?: ""
        _serverStatus.postValue(ServerStatus.CONNECTING)
        withContext(Dispatchers.IO) {
            engine = generateServer()
            engine.start()
        }

        withContext(Dispatchers.Main) {
            //keep track of the network shares as they are highly asynchronous
            val provider = NetworkProvider(context, networkSharesLiveData, null)
            NetworkMonitor.getInstance(context).connectionFlow.onEach {
                if (it.connected) provider.refresh()
                else networkSharesLiveData.clear()
            }.launchIn(AppScope)
        }
    }

    /**
     * Stop the server and all the websocket connections
     *
     */
    suspend fun stop() {
        _serverStatus.postValue(ServerStatus.STOPPING)
        withContext(Dispatchers.IO) {
            websocketSession.forEach {
                it.close()
            }
            engine.stop()
        }
    }

    /**
     * Get the server files path
     *
     * @return the server file poath
     */
    private fun getServerFiles(): String {
        return "${context.filesDir.path}/server/"
    }

    /**
     * Listen to the [PlaybackService] connection
     *
     * @param service the service to listen
     */
    private fun onServiceChanged(service: PlaybackService?) {
        if (service !== null) {
            this.service = service
            service.addCallback(this)
        } else this.service?.let {
            it.removeCallback(this)
            this.service = null
        }
    }

    /**
     * Copy the web server files to serve
     *
     */
    private fun copyWebServer() {
        File(getServerFiles()).mkdirs()
        FileUtils.copyAssetFolder(context.assets, "dist", "${context.filesDir.path}/server", true)
    }

    /**
     * Ktor plugin intercepting all the requests
     * Used to manage a connected device list
     */
    private val InterceptorPlugin = createApplicationPlugin(name = "VLCInterceptorPlugin") {
        onCall { call ->
            call.request.origin.apply {
                val oldConnections = _serverConnections.value
                if ((oldConnections?.filter { it.ip == remoteHost }?.size ?: 0) == 0) {
                    val connection = WebServerConnection(remoteHost)
                    withContext(Dispatchers.Main) {
                        try {
                            _serverConnections.value = oldConnections!!.toMutableList().apply { add(connection) }
                        } catch (e: Exception) {
                            Log.e("InterceptorPlugin", e.message, e)
                        }
                    }
                }
            }

        }
    }

    /**
     * Generate the server
     *
     * @return the server engine
     */
    private fun generateServer() = embeddedServer(Netty, 8080) {
        install(InterceptorPlugin)
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        if (auth && user.isNotEmpty() && password.isNotEmpty()) install(Authentication) {
            basic("auth-basic") {
                realm = "Access to the '/' path"
                validate { credentials ->
                    if (credentials.name == user && credentials.password == password) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }
        }
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.AccessControlAllowOrigin)
            allowHeader(HttpHeaders.ContentType)
            anyHost()
        }
        install(PartialContent)
        routing {
            if (auth) authenticate("auth-basic") {
                setupRouting()
            } else setupRouting()
            webSocket("/echo", protocol = "player") {
                websocketSession.add(this)
                // Handle a WebSocket session
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val message = frame.readText()
                    when {
                        message == "play" -> service?.play()
                        message == "pause" -> service?.pause()
                        message == "previous" -> service?.previous(false)
                        message == "next" -> service?.next()
                        message == "previous10" -> service?.let { it.seek((it.getTime() - 10000).coerceAtLeast(0), fromUser = true) }
                        message == "next10" -> service?.let { it.seek((it.getTime() + 10000).coerceAtMost(it.length), fromUser = true) }
                        message == "shuffle" -> service?.shuffle()
                        message == "repeat" -> service?.let {
                            when (it.repeatType) {
                                PlaybackStateCompat.REPEAT_MODE_NONE -> {
                                    it.repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
                                }
                                PlaybackStateCompat.REPEAT_MODE_ONE -> if (it.hasPlaylist()) {
                                    it.repeatType = PlaybackStateCompat.REPEAT_MODE_ALL
                                } else {
                                    it.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                                }
                                PlaybackStateCompat.REPEAT_MODE_ALL -> {
                                    it.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                                }
                            }
                        }
                        message == "get-volume" -> {
                            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(getVolumeMessage())) } }
                        }
                        message.startsWith("set-volume") -> {
                            val volume = message.split(':')[1].toInt()
                            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let {
                                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                it.setStreamVolume(AudioManager.STREAM_MUSIC, ((volume.toFloat() / 100) * max).toInt(), AudioManager.FLAG_SHOW_UI)

                            }

                        }
                        message.startsWith("playMedia") -> {
                            val index = message.split(':')[1].toInt()
                            service?.playIndex(index)

                        }
                        message.startsWith("deleteMedia") -> {
                            val index = message.split(':')[1].toInt()
                            service?.remove(index)

                        }
                        message.startsWith("moveMediaBottom") -> {
                            val index = message.split(':')[1].toInt()
                            if (index < (service?.playlistManager?.getMediaListSize() ?: 0) - 1)
                                service?.moveItem(index, index + 2)

                        }
                        message.startsWith("moveMediaTop") -> {
                            val index = message.split(':')[1].toInt()
                            if (index > 0)
                                service?.moveItem(index, index - 1)

                        }
                    }
                }
                websocketSession.remove(this)
            }
        }
    }.apply {
        environment.monitor.subscribe(ApplicationStarted) {
            _serverStatus.postValue(ServerStatus.STARTED)
            PlaylistManager.showAudioPlayer.observeForever(miniPlayerObserver)
        }
        environment.monitor.subscribe(ApplicationStopped) {
            AppScope.launch(Dispatchers.Main) {
                PlaylistManager.showAudioPlayer.removeObserver(miniPlayerObserver)
            }
            _serverStatus.postValue(ServerStatus.STOPPED)
        }
    }

    /**
     * Setup the server routing
     *
     */
    private fun Route.setupRouting() {
        val appContext = this@HttpSharingServer.context
        static("") {
            files(getServerFiles())
        }
        // Main end point redirect to index.html
        get("/") {
            call.respondRedirect("index.html", permanent = true)
        }
        get("/index.html") {
            try {
                val html = FileUtils.getStringFromFile("${getServerFiles()}index.html")
                call.respondText(html, ContentType.Text.Html)
            } catch (e: Exception) {
                call.respondText("Failed to load index.html")
            }
        }
        // Upload a file to the device
        post("/upload-media") {
            var fileDescription = ""
            var fileName = ""
            val multipartData = call.receiveMultipart()

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        fileDescription = part.value
                    }
                    is PartData.FileItem -> {
                        File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads").mkdirs()
                        fileName = part.originalFileName as String
                        var fileBytes = part.streamProvider().readBytes()
                        File("${AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI.path}/uploads/$fileName").writeBytes(fileBytes)
                    }
                    else -> {}
                }
            }
            call.respondText("$fileDescription is uploaded to 'uploads/$fileName'")
        }
        // Download a log file
        get("/download-logfile") {
            call.request.queryParameters["file"]?.let { filePath ->
                val file = File(filePath)
                if (file.exists()) {
                    call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString())
                    call.respondFile(File(filePath))
                }
            }
            call.respond(HttpStatusCode.NotFound, "")
        }
        // List all log files
        get("/list-logfiles") {
            val logs = getLogsFiles().sortedBy { File(it).lastModified() }.reversed()

            val jsonArray = JSONArray()
            for (log in logs) {
                val json = JSONObject()
                json.put("path", log)
                json.put("date", format.get()?.format(File(log).lastModified()))
                jsonArray.put(json)
            }
            call.respondText(jsonArray.toString())
        }
        // List of all the videos
        get("/video-list") {
            val videos = appContext.getFromMl { getVideos(Medialibrary.SORT_DEFAULT, false, false, false) }

            val list = ArrayList<PlayQueueItem>()
            videos.forEach { mediaWrapper ->
                list.add(PlayQueueItem(mediaWrapper.id, mediaWrapper.title, mediaWrapper.artist
                        ?: "", mediaWrapper.length, mediaWrapper.artworkMrl
                        ?: "", false, generateResolutionClass(mediaWrapper.width, mediaWrapper.height)
                        ?: ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the albums
        get("/album-list") {
            val albums = appContext.getFromMl { getAlbums(false, false) }

            val list = ArrayList<PlayQueueItem>()
            albums.forEach { album ->
                list.add(PlayQueueItem(album.id, album.title, album.albumArtist
                        ?: "", album.duration, album.artworkMrl
                        ?: "", false, ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the artists
        get("/artist-list") {
            val artists = appContext.getFromMl { getArtists(Settings.getInstance(appContext).getBoolean(KEY_ARTISTS_SHOW_ALL, false), false, false) }

            val list = ArrayList<PlayQueueItem>()
            artists.forEach { artist ->
                list.add(PlayQueueItem(artist.id, artist.title, appContext.resources.getQuantityString(R.plurals.albums_quantity, artist.albumsCount, artist.albumsCount), 0, artist.artworkMrl
                        ?: "", false, ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the audio tracks
        get("/track-list") {
            val tracks = appContext.getFromMl { getAudio(Medialibrary.SORT_DEFAULT, false, false, false) }

            val list = ArrayList<PlayQueueItem>()
            tracks.forEach { track ->
                list.add(PlayQueueItem(track.id, track.title, track.artist
                        ?: "", track.length, track.artworkMrl
                        ?: "", false, generateResolutionClass(track.width, track.height) ?: ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the audio genres
        get("/genre-list") {
            val genres = appContext.getFromMl { getGenres(false, false) }

            val list = ArrayList<PlayQueueItem>()
            genres.forEach { genre ->
                list.add(PlayQueueItem(genre.id, genre.title, appContext.resources.getQuantityString(R.plurals.track_quantity, genre.tracksCount, genre.tracksCount), 0, genre.artworkMrl
                        ?: "", false, ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the playlists
        get("/playlist-list") {
            val playlists = appContext.getFromMl { getPlaylists(Playlist.Type.All, false) }

            val list = ArrayList<PlayQueueItem>()
            playlists.forEach { genre ->
                list.add(PlayQueueItem(genre.id, genre.title, appContext.resources.getQuantityString(R.plurals.track_quantity, genre.tracksCount, genre.tracksCount), 0, genre.artworkMrl
                        ?: "", false, ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the file storages
        get("/storage-list") {
            val dataset = LiveDataset<MediaLibraryItem>()
            val provider = withContext(Dispatchers.Main) {
                StorageProvider(appContext, dataset, null)
            }
            val list = getProviderContent(provider, dataset)
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the file favorites
        get("/favorite-list") {
            val dataset = LiveDataset<MediaLibraryItem>()
            val provider = withContext(Dispatchers.Main) {
                FavoritesProvider(appContext, dataset, AppScope)
            }
            val list = getProviderContent(provider, dataset)
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // List of all the network shares
        get("/network-list") {
            val list = ArrayList<PlayQueueItem>()
            networkSharesLiveData.getList().forEachIndexed { index, mediaLibraryItem ->
                list.add(PlayQueueItem(mediaLibraryItem.id, mediaLibraryItem.title, "", 0, mediaLibraryItem.artworkMrl
                        ?: "", false, ""))
            }
            val gson = Gson()
            call.respondText(gson.toJson(list))
        }
        // Play a media
        get("/play") {
            val type = call.request.queryParameters["type"] ?: "media"
            val append = call.request.queryParameters["append"] == "true"
            val asAudio = call.request.queryParameters["audio"] == "true"
            call.request.queryParameters["id"]?.let { id ->
                val medias = appContext.getFromMl {
                    when (type) {
                        "album" -> getAlbum(id.toLong()).tracks
                        "artist" -> getArtist(id.toLong()).tracks
                        "genre" -> getGenre(id.toLong()).tracks
                        "playlist" -> getPlaylist(id.toLong(), false, false).tracks
                        else -> arrayOf(getMedia(id.toLong()))
                    }
                }
                if (medias.isEmpty()) call.respond(HttpStatusCode.NotFound)
                else {

                    if (asAudio) medias[0].addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                    if (medias[0].type == MediaWrapper.TYPE_VIDEO && !appContext.awaitAppIsForegroung()) {
                        call.respond(HttpStatusCode.Forbidden, appContext.getString(R.string.ns_not_in_foreground))
                    }
                    when {
                        append -> MediaUtils.appendMedia(appContext, medias)
                        else -> MediaUtils.openList(appContext, medias.toList(), 0)
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }
            call.respond(HttpStatusCode.NotFound)
        }
        // Download a media file
        get("/download") {
            call.request.queryParameters["id"]?.let {
                appContext.getFromMl { getMedia(it.toLong()) }?.let { media ->
                    media.uri.path?.let { path ->
                        val file = File(path)
                        call.response.header(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, media.uri.lastPathSegment
                                        ?: "")
                                        .toString()
                        )
                        call.respondFile(file)
                    }
                }
            }
            call.respond(HttpStatusCode.NotFound)
        }
        // Sends an icon
        get("/icon") {
            val idString = call.request.queryParameters["id"]
            val width = call.request.queryParameters["width"]?.toInt() ?: 32

            val id = try {
                appContext.resIdByName(idString, "drawable")
            } catch (e: Resources.NotFoundException) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (id == 0) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, id, width, width), true)?.let {

                call.respondBytes(ContentType.Image.PNG) { it }
                return@get
            }

            call.respond(HttpStatusCode.NotFound)

        }
        // Get the translation string list
        get("/translation") {
            call.respondText(TranslationMapping.generateTranslations(appContext))
        }
        // Get a media artwork
        get("/artwork") {
            if (call.request.queryParameters["type"] in arrayOf("folder", "network")) {
                BitmapUtil.encodeImage(BitmapUtil.vectorToBitmap(appContext, R.drawable.ic_menu_folder, 256, 256), true)?.let {
                    call.respondBytes(ContentType.Image.PNG) { it }
                    return@get
                }
            }
            try {
                val artworkMrl = call.request.queryParameters["artwork"] ?: service?.coverArt
                //check by id and use the ArtworkProvider if provided
                call.request.queryParameters["id"]?.let {
                    val cr = appContext.contentResolver
                    val mediaType = when (call.request.queryParameters["type"]) {
                        "video" -> ArtworkProvider.VIDEO
                        "album" -> ArtworkProvider.ALBUM
                        "artist" -> ArtworkProvider.ARTIST
                        "genre" -> ArtworkProvider.GENRE
                        "playlist" -> ArtworkProvider.PLAYLIST
                        else -> ArtworkProvider.MEDIA
                    }
                    cr.openInputStream(Uri.parse("content://${appContext.applicationContext.packageName}.artwork/$mediaType/0/$it"))?.let { inputStream ->
                        call.respondBytes(ContentType.Image.JPEG) { inputStream.toByteArray() }
                        inputStream.close()
                        return@get
                    }
                }

                //id is not provided, use the artwork query and fallback on the current playing media
                artworkMrl?.let { coverArt ->
                    AudioUtil.readCoverBitmap(Uri.decode(coverArt), 512)?.let { bitmap ->
                        BitmapUtil.convertBitmapToByteArray(bitmap)?.let {

                            call.respondBytes(ContentType.Image.JPEG) { it }
                            return@get
                        }
                    }
                }
                // nothing found . Falling back on the no media bitmap
                appContext.getBitmapFromDrawable(R.drawable.ic_no_media, 512, 512)?.let {

                    BitmapUtil.encodeImage(it, true)?.let {

                        call.respondBytes(ContentType.Image.PNG) { it }
                        return@get
                    }
                }
            } catch (e: Exception) {
                Log.e("networkShareReplace", e.message, e)
            }
            call.respond(HttpStatusCode.NotFound, "")
        }
    }

    /**
     * The the list of all the log files
     *
     * @return a list of file paths
     */
    private suspend fun getLogsFiles(): List<String> = withContext(Dispatchers.IO) {
        val result = ArrayList<String>()
        val folder = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
        val files = folder.listFiles()
        files.forEach {
            if (it.isFile && it.name.startsWith("vlc_logcat_")) result.add(it.path)
        }

        return@withContext result

    }

    /**
     * update callback of the [PlaybackService]
     *
     */
    override fun update() {
        generateNowPlaying()?.let { nowPlaying ->
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(nowPlaying)) } }
        }
        generatePlayQueue()?.let { playQueue ->
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(playQueue)) } }
        }
    }

    /**
     * onMediaEvent callback of the [PlaybackService]
     *
     * @param event the event sent
     */
    override fun onMediaEvent(event: IMedia.Event) {
        generateNowPlaying()?.let { nowPlaying ->
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(nowPlaying)) } }
        }
        generatePlayQueue()?.let { playQueue ->
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(playQueue)) } }
        }
    }

    /**
     * onMediaPlayerEvent callback of the [PlaybackService]
     *
     * @param event the event sent
     */
    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        generateNowPlaying()?.let { nowPlaying ->
            AppScope.launch {
                coroutineContext
                websocketSession.forEach { it.send(Frame.Text(nowPlaying)) }
            }
        }
        generatePlayQueue()?.let { playQueue ->
            AppScope.launch {
                coroutineContext
                websocketSession.forEach { it.send(Frame.Text(playQueue)) }
            }
        }
    }

    /**
     * Get the content from a [BrowserProvider]
     * Gracefully (or not) waits for the [LiveData] to be set before sending the result back
     *
     * @param provider the [BrowserProvider] to use
     * @param dataset the [LiveDataset] to listen to
     * @return a populated list
     */
    private suspend fun getProviderContent(provider: BrowserProvider, dataset: LiveDataset<MediaLibraryItem>): ArrayList<PlayQueueItem> {
        dataset.await()
        val descriptions = ArrayList<Pair<Int, String>>()
        observeLiveDataUntil(1500, provider.descriptionUpdate) { pair ->
            descriptions.add(pair)
            val block = descriptions.size < dataset.getList().size
            block
        }
        val list = ArrayList<PlayQueueItem>()
        dataset.getList().forEachIndexed { index, mediaLibraryItem ->
            val description = try {
                val unparsedDescription = descriptions.first { it.first == index }.second
                val folders = unparsedDescription.getFolderNumber()
                val files = unparsedDescription.getFilesNumber()
                "${context.resources.getQuantityString(org.videolan.vlc.R.plurals.subfolders_quantity, folders, folders)} ${TextUtils.separator} ${context.resources.getQuantityString(org.videolan.vlc.R.plurals.mediafiles_quantity, files, files)}"
            } catch (e: Exception) {
                ""
            }
            list.add(PlayQueueItem(mediaLibraryItem.id, mediaLibraryItem.title, description, 0, mediaLibraryItem.artworkMrl
                    ?: "", false, ""))
        }
        return list
    }

    /**
     * Generate the now playing data to be sent to the client
     *
     * @return a [String] describing the now playing
     */
    private fun generateNowPlaying(): String? {
        service?.let { service ->
            service.currentMediaWrapper?.let { media ->
                val gson = Gson()
                val nowPlaying = NowPlaying(media.title ?: "", media.artist
                        ?: "", service.isPlaying, service.getTime(), service.length, media.id, media.artworkURL
                        ?: "", media.uri.toString(), getVolume())
                return gson.toJson(nowPlaying)

            }
        }
        return null
    }

    /**
     * Generate the play queue data to be sent to the client
     *
     * @return a [String] describing the play queue
     */
    private fun generatePlayQueue(): String? {
        service?.let { service ->
            val list = ArrayList<PlayQueueItem>()
            service.playlistManager.getMediaList().forEachIndexed { index, mediaWrapper ->
                list.add(PlayQueueItem(mediaWrapper.id, mediaWrapper.title, mediaWrapper.artist
                        ?: "", mediaWrapper.length, mediaWrapper.artworkMrl
                        ?: "", service.playlistManager.currentIndex == index))
            }
            val gson = Gson()
            return gson.toJson(PlayQueue(list))
        }
        return null
    }

    /**
     * Get the volume to be sent to the client
     *
     * @return an [Int] describing the volume
     */
    private fun getVolume(): Int = when {
        service?.isVideoPlaying == true && service!!.volume > 100 -> service!!.volume
        else -> {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let {
                val vol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                (vol.toFloat() / max * 100).toInt()
            }
                    ?: 0
        }
    }

    /**
     * Get the volume message to be sent to the client
     *
     * @return an [String] describing the volume message
     */
    private fun getVolumeMessage(): String {
        val gson = Gson()
        val volume = when {
            service?.isVideoPlaying == true && service!!.volume > 100 -> service!!.volume
            else -> {
                (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let {
                    val vol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    (vol.toFloat() / max * 100).toInt()
                }
                        ?: 0
            }
        }
        return gson.toJson(Volume(volume))
    }

    /**
     * Returns the server address
     *
     * @return the server address
     */
    fun serverInfo(): String = buildString {
        getIPAddresses(true).forEach {
            append("http://")
            append(it)
            append(":")
            append(engine.environment.connectors[0].port)
        }
    }

    /**
     * Get IP address from first non-localhost interface
     * @param useIPv4   true=return ipv4, false=return ipv6
     * @return  address or empty string
     */
    private fun getIPAddresses(useIPv4: Boolean): List<String> {
        val results = arrayListOf<String>()
        try {
            val interfaces: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val inetAddresses: List<InetAddress> =
                        Collections.list(networkInterface.inetAddresses)
                for (inetAddress in inetAddresses) {
                    if (!inetAddress.isLoopbackAddress) {
                        val hostAddress = inetAddress.hostAddress ?: continue
                        val isIPv4 = hostAddress.indexOf(':') < 0
                        if (useIPv4) {
                            if (isIPv4) results.add(hostAddress)
                        } else {
                            if (!isIPv4) {
                                val delim = hostAddress.indexOf('%')
                                if (delim < 0)
                                    results.add(hostAddress.uppercase(Locale.getDefault()))
                                else
                                    results.add(
                                            hostAddress.substring(0, delim)
                                                    .uppercase(Locale.getDefault())
                                    )
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return results
    }

    abstract class WSMessage(val type: String)
    data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val progress: Long, val duration: Long, val id: Long, val artworkURL: String, val uri: String, val volume: Int, val shouldShow: Boolean = PlaylistManager.showAudioPlayer.value
            ?: false) : WSMessage("now-playing")

    data class PlayQueue(val medias: List<PlayQueueItem>) : WSMessage("play-queue")
    data class PlayQueueItem(val id: Long, val title: String, val artist: String, val length: Long, val artworkURL: String, val playing: Boolean, val resolution: String = "")
    data class Volume(val volume: Int) : WSMessage("volume")

    companion object : SingletonHolder<HttpSharingServer, Context>({ HttpSharingServer(it.applicationContext) })

    data class WebServerConnection(val ip: String)
}