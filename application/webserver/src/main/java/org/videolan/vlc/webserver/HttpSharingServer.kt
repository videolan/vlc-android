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
import android.media.AudioManager
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
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
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.tools.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.FileUtils
import java.io.File
import java.text.DateFormat
import java.time.Duration
import java.util.*
import java.util.regex.Pattern

class HttpSharingServer(private val context: Context): PlaybackService.Callback {
    private var engine: NettyApplicationEngine
    private var websocketSession: ArrayList<DefaultWebSocketServerSession> = arrayListOf()
    private var service: PlaybackService? = null
    private val format by lazy {
        object : ThreadLocal<DateFormat>() {
            override fun initialValue() = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
        }
    }

    private var auth = false
    private var user = ""
    private var password = ""

    init {
        copyWebServer(context)
        auth = Settings.getInstance(context).getBoolean(KEY_WEB_SERVER_AUTH, false)
        user = Settings.getInstance(context).getString(KEY_WEB_SERVER_USER, "") ?: ""
        password = Settings.getInstance(context).getString(KEY_WEB_SERVER_PASSWORD, "")
                ?: ""
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }
                .onCompletion { service?.removeCallback(this@HttpSharingServer) }
                .launchIn(AppScope)
        engine  =  generateServer(context)
    }


    fun start() {
        engine.start()
    }
    fun stop() {
        engine.stop()
    }

    private fun getServerFiles(context: Context): String {
        return "${context.filesDir.path}/server/"
    }

    private fun onServiceChanged(service: PlaybackService?) {
        if (service !== null) {
            this.service = service
            service.addCallback(this)
        } else this.service?.let {
            it.removeCallback(this)
            this.service = null
        }
    }

    private fun copyWebServer(context: Context) {
        File(getServerFiles(context)).mkdirs()
        FileUtils.copyAssetFolder(context.assets, "dist", "${context.filesDir.path}/server", true)
    }


    private fun generateServer(context: Context) = embeddedServer(Netty, 8080) {
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
        PlaylistManager.showAudioPlayer.observeForever {
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text("Stopped")) } }
        }
        routing {
            if (auth) authenticate("auth-basic") {
                setupRouting(context)
            } else setupRouting(context)
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
                            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(getVolumeMessage(context))) } }
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
                    }
                }
                websocketSession.remove(this)
            }
        }
    }

    private fun Route.setupRouting(context: Context) {
        static("") {
            files(getServerFiles(context))
        }
        get("/") {
            call.respondRedirect("index.html", permanent = true)
        }
        get("/index.html") {
            try {
                val html = FileUtils.getStringFromFile("${getServerFiles(context)}index.html")
                call.respondText(html, ContentType.Text.Html)
            } catch (e: Exception) {
                call.respondText("Failed to load index.html")
            }
        }
        post("/upload.json") {
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
        get("/artwork") {
            try {
                val artworkMrl = call.request.queryParameters["artwork"] ?:  service?.coverArt
                artworkMrl?.let { coverArt ->
                    AudioUtil.readCoverBitmap(Uri.decode(coverArt), 512)?.let { bitmap ->
                        BitmapUtil.convertBitmapToByteArray(bitmap)?.let {

                            call.respondBytes(ContentType.Image.JPEG) { it }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("networkShareReplace", e.message, e)
            }
            call.respond(HttpStatusCode.NotFound, "")
        }
    }

    private suspend fun getLogsFiles(): List<String> = withContext(Dispatchers.IO) {
        val result = ArrayList<String>()
        val folder = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
        val files = folder.listFiles()
        files.forEach {
            if (it.isFile && it.name.startsWith("vlc_logcat_")) result.add(it.path)
        }

        return@withContext result

    }

    fun String.networkShareReplace(context: Context): String {
        var newString = this
        try {
            val logEntry = Pattern.compile("\\{%(.*?)%\\}")
            newString = newString.replace(logEntry.toRegex()) {
                val str = context.getString(context.resIdByName(it.value.trim().drop(2).dropLast(2), "string"))
                str
            }
        } catch (e: Exception) {
            Log.e("networkShareReplace", e.message, e)
        }

        return newString
    }

    fun String.contentReplace(context: Context, logsHtml: String = ""): String {
        var newString = this
        try {
            val logEntry = Pattern.compile("\\{*(.*?)%*\\}")
            newString = newString.replace(logEntry.toRegex()) {
                when (it.value.trim().drop(2).dropLast(2)) {
                    "logs" -> logsHtml
                    else -> ""
                }
            }
        } catch (e: Exception) {
            Log.e("networkShareReplace", e.message, e)
        }

        return newString
    }

    override fun update() {
        generateNowPlaying(AppContextProvider.appContext)?.let { nowPlaying ->
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(nowPlaying)) } }
        }
        generatePlayQueue()?.let { playQueue ->
            AppScope.launch {
                coroutineContext
                websocketSession.forEach { it.send(Frame.Text(playQueue)) } }
        }
    }

    override fun onMediaEvent(event: IMedia.Event) {
        generateNowPlaying(AppContextProvider.appContext)?.let { nowPlaying ->
            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(nowPlaying)) } }
        }
        generatePlayQueue()?.let { playQueue ->
            AppScope.launch {
                coroutineContext
                websocketSession.forEach { it.send(Frame.Text(playQueue)) } }
        }
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        generateNowPlaying(AppContextProvider.appContext)?.let { nowPlaying ->
            AppScope.launch {
                coroutineContext
                websocketSession.forEach { it.send(Frame.Text(nowPlaying)) } }
        }
        generatePlayQueue()?.let { playQueue ->
            AppScope.launch {
                coroutineContext
                websocketSession.forEach { it.send(Frame.Text(playQueue)) } }
        }
    }

    private fun generateNowPlaying(context: Context): String? {
        service?.let { service ->
            service.currentMediaWrapper?.let { media ->
                val gson = Gson()
                val nowPlaying = NowPlaying(media.title ?: "", media.artist
                        ?: "", service.isPlaying, service.getTime(), service.length, media.id, media.artworkURL
                        ?: "", media.uri.toString(), getVolume(context))
                return gson.toJson(nowPlaying)

            }
        }
        return null
    }

    private fun generatePlayQueue(): String? {
        service?.let { service ->
            val list = ArrayList<PlayQueueItem>()
            service.playlistManager.getMediaList().forEachIndexed { index, mediaWrapper ->
                list.add(PlayQueueItem(mediaWrapper.id, mediaWrapper.title, mediaWrapper.artist ?:"", mediaWrapper.length, mediaWrapper.artworkMrl ?: "", service.playlistManager.currentIndex == index))
            }
            val gson = Gson()
            return gson.toJson(PlayQueue(list))
        }
        return null
    }

    private fun getVolume(context: Context):Int  = when {
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
    private fun getVolumeMessage(context: Context): String {
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

    abstract class WSMessage(val type: String)
    data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val progress: Long, val duration: Long, val id: Long, val artworkURL: String, val uri: String, val volume:Int, val shouldShow:Boolean = PlaylistManager.showAudioPlayer.value ?: false) : WSMessage("now-playing")
    data class PlayQueue(val medias: List<PlayQueueItem>) : WSMessage("play-queue")
    data class PlayQueueItem(val id: Long, val title:String, val artist:String, val length:Long, val artworkURL:String , val playing: Boolean)
    data class Volume(val volume: Int) : WSMessage("volume")

    companion object : SingletonHolder<HttpSharingServer, Context>({ HttpSharingServer(it.applicationContext) })
}