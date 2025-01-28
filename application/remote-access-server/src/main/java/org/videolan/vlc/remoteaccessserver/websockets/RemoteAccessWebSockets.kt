/*
 * ************************************************************************
 *  RemoteAccessWebSockets.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.vlc.remoteaccessserver.websockets

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.annotation.MainThread
import com.squareup.moshi.Moshi
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.tools.AppScope
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.remoteaccessserver.BuildConfig
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.convertToJson
import org.videolan.vlc.remoteaccessserver.ssl.SecretGenerator
import org.videolan.vlc.remoteaccessserver.websockets.IncomingMessageType.*
import java.util.Calendar
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

object RemoteAccessWebSockets {
    private const val TAG = "VLC/HttpSharingServerWS"

    val onPlaybackEventChannel = Channel<RemoteAccessServer.WSMessage>()
    val messageQueue: LinkedBlockingQueue<RemoteAccessServer.WSMessage> = LinkedBlockingQueue()
    private val webSocketSessions: MutableMap<Int, WebSocketServerSession> = ConcurrentHashMap()
    private val tickets: MutableList<WSAuthTicket> = Collections.synchronizedList(mutableListOf())
    private val sessionIds = AtomicInteger(0)

    fun Routing.setupWebSockets(context: Context, settings: SharedPreferences) {
        webSocket("/echo", protocol = "player") {
            val sessionId = sessionIds.incrementAndGet()
            try {
                webSocketSessions[sessionId] = this
                if (BuildConfig.DEBUG) Log.d(TAG, "WebSockets: Started session: $sessionId")
                val moshi = Moshi.Builder().build().adapter(WSIncomingMessage::class.java)
                // Handle a WebSocket session
                for (frame in incoming) {
                    try {
                        frame as? Frame.Text ?: continue
                        val message = frame.readText()
                        val incomingMessage = moshi.fromJson(message) ?: continue
                        if (BuildConfig.DEBUG) Log.i(TAG, "WebSockets: Received message '$message'")
                        if (!verifyWebsocketAuth(incomingMessage)) {
                            send(Frame.Text(convertToJson(RemoteAccessServer.WebSocketAuthorization("forbidden", initialMessage = message))))
                        } else {
                            val service = RemoteAccessServer.getInstance(context).service
                            withContext(Dispatchers.Main) {
                                manageIncomingMessages(incomingMessage, settings, service, context)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.message, e)
                    }
                }
            } finally {
                webSocketSessions.remove(sessionId)?.close()
                if (BuildConfig.DEBUG) Log.d(TAG, "WebSockets: Removed and closed session: $sessionId")
            }
        }
    }

    /**
     * Manage incoming messages from the client, either from websockets or long polling.
     *
     * Run this handler on the main thread to simplify calls to PlaybackService.
     *
     * @param incomingMessage the incoming message
     * @param settings the shared preferences
     * @param service the playback service
     * @param context the context
     * @return true if the message has been handled, false if playback control is not allowed
     */
    @MainThread
    suspend fun manageIncomingMessages(
        incomingMessage: WSIncomingMessage,
        settings: SharedPreferences,
        service: PlaybackService?,
        context: Context,
    ): Boolean {
        val incomingMessageType = IncomingMessageType.fromString(incomingMessage.message)
        if (incomingMessageType == null) {
            Log.w(TAG, "Unrecognized message", IllegalStateException("Unrecognized message: '$incomingMessage'"))
            return false
        }
        // Verify playback control is enabled
        if (incomingMessageType.controlRequired && !playbackControlAllowedOrSend(settings)) return false

        when (incomingMessageType) {
            HELLO -> {}
            PLAY -> service?.play()
            PAUSE -> service?.pause()
            PREVIOUS -> service?.previous(false)
            NEXT -> service?.next()
            PREVIOUS10 -> service?.let { it.seek((it.getTime() - 10000).coerceAtLeast(0), fromUser = true) }
            NEXT10 -> service?.let { it.seek((it.getTime() + 10000).coerceAtMost(it.length), fromUser = true) }
            SHUFFLE -> service?.shuffle()
            REPEAT -> {
                service?.let {
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
            }
            GET_VOLUME -> {
                val message = Frame.Text(getVolumeMessage(context, service))
                webSocketSessions.forEach { (_, session) -> session.send(message) }
            }
            SET_VOLUME -> {
                incomingMessage.id?.let { volume ->
                    (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volume.toFloat() / 100) * max).toInt(), AudioManager.FLAG_SHOW_UI)
                    }
                }
            }
            SET_PROGRESS -> incomingMessage.id?.let { time -> service?.setTime(time.toLong()) }
            PLAY_CHAPTER -> incomingMessage.id?.let { index -> service?.chapterIdx = index }
            SPEED -> incomingMessage.floatValue?.let { speed -> service?.setRate(speed, true) }
            SLEEP_TIMER -> {
                incomingMessage.longValue?.let { sleepTimerEnd ->
                    val sleepTime = Calendar.getInstance()
                    sleepTime.timeInMillis += sleepTimerEnd
                    sleepTime.set(Calendar.SECOND, 0)
                    service?.setSleepTimer(sleepTime)
                    service?.sleepTimerInterval = sleepTimerEnd
                    RemoteAccessServer.getInstance(context).generateNowPlaying()?.let { sendToAll(it) }
                }
            }
            SLEEP_TIMER_WAIT -> {
                incomingMessage.stringValue?.let { waitForMediaEnd ->
                    service?.waitForMediaEnd = (waitForMediaEnd == "true")
                    RemoteAccessServer.getInstance(context).generateNowPlaying()?.let { sendToAll(it) }
                }
            }
            SLEEP_TIMER_RESET -> {
                incomingMessage.stringValue?.let { resetOnInteraction ->
                    service?.resetOnInteraction = (resetOnInteraction == "true")
                    RemoteAccessServer.getInstance(context).generateNowPlaying()?.let { sendToAll(it) }
                }
            }
            ADD_BOOKMARK -> {
                incomingMessage.longValue?.let { bookmarkTime ->
                    service?.let {
                        it.currentMediaWrapper?.let { media ->
                            AppScope.launch(Dispatchers.IO) {
                                val bookmark = media.addBookmark(bookmarkTime)
                                bookmark?.setName(context.getString(R.string.bookmark_default_name, Tools.millisToString(it.getTime())))
                            }
                        }
                    }
                }
            }
            DELETE_BOOKMARK -> {
                incomingMessage.longValue?.let { bookmarkTime ->
                    service?.currentMediaWrapper?.let { media ->
                        AppScope.launch(Dispatchers.IO) {
                            media.removeBookmark(bookmarkTime)
                        }
                    }
                }
            }
            RENAME_BOOKMARK -> {
                incomingMessage.longValue?.let { bookmarkTime ->
                    incomingMessage.stringValue?.let { bookmarkName ->
                        service?.currentMediaWrapper?.let { media ->
                            AppScope.launch(Dispatchers.IO) {
                                media.bookmarks.firstOrNull { it.time == bookmarkTime }?.setName(bookmarkName)
                            }
                        }
                    }
                }
            }
            PLAY_MEDIA -> incomingMessage.id?.let { index -> service?.playIndex(index) }
            DELETE_MEDIA -> incomingMessage.id?.let { index -> service?.remove(index) }
            MOVE_MEDIA_BOTTOM -> {
                incomingMessage.id?.let { index ->
                    service?.let {
                        if (index < (it.playlistManager?.getMediaListSize() ?: 0) - 1)
                            it.moveItem(index, index + 2)
                    }
                }
            }
            MOVE_MEDIA_TOP -> {
                incomingMessage.id?.let { index ->
                    if (index > 0)
                        service?.moveItem(index, index - 1)
                }
            }
            REMOTE -> incomingMessage.stringValue?.let { action -> VideoPlayerActivity.videoRemoteFlow.emit(action) }
        }
        return true
    }

    /**
     * Verify if the websocket auth ticket is here and valid
     *
     * @param incomingMessage the incoming message containing the ticket
     * @return true if the websocket message is allowed
     */
    private fun verifyWebsocketAuth(incomingMessage: WSIncomingMessage?): Boolean {
        synchronized(tickets) {
            tickets.removeIf { it.expiration < System.currentTimeMillis() }
            return incomingMessage?.authTicket != null && tickets.firstOrNull { incomingMessage.authTicket == it.id } != null
        }
    }

    private fun playbackControlAllowedOrSend(settings: SharedPreferences): Boolean {
        val allowed = settings.getBoolean(REMOTE_ACCESS_PLAYBACK_CONTROL, true)
        if (!allowed) {
            val message = convertToJson(RemoteAccessServer.PlaybackControlForbidden())
            AppScope.launch { webSocketSessions.forEach { (_, session) -> session.send(Frame.Text(message)) } }
        }
        return allowed
    }

    /**
     * Get the volume message to be sent to the client
     *
     * @return an [String] describing the volume message
     */
    private fun getVolumeMessage(context: Context, service: PlaybackService?): String {
        val volume = when {
            service?.isVideoPlaying == true && service.volume > 100 -> service.volume
            else -> {
                (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let {
                    val vol = it.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    (vol.toFloat() / max * 100).toInt()
                }
                        ?: 0
            }
        }
        return convertToJson(RemoteAccessServer.Volume(volume))
    }

    fun createTicket(): String {
        val ticket = WSAuthTicket(SecretGenerator.generateRandomAlphanumericString(45), System.currentTimeMillis() + 60_000L)
        tickets.add(ticket)
        return ticket.id
    }

   suspend fun sendToAll(messageObj: RemoteAccessServer.WSMessage) {
       val message = convertToJson(messageObj)
       addToQueue(messageObj)
       onPlaybackEventChannel.trySend(messageObj)
       if (BuildConfig.DEBUG) Log.d(TAG, "WebSockets: sendToAll called on ${webSocketSessions.size} sessions with message '$message'")
       webSocketSessions.forEach { (sessionId, session) ->
           try {
               session.send(Frame.Text(message))
           } catch (e: Exception) {
               webSocketSessions.remove(sessionId)?.close()
               if (BuildConfig.DEBUG) Log.d(TAG, "WebSockets: Exception caught. Session removed and closed: $sessionId", e)
           }
       }
   }

    /**
     * Add a message to the queue and remove duplicates if needed
     *
     * @param wsMessage the message to send
     */
    private fun addToQueue(wsMessage: RemoteAccessServer.WSMessage) {
        when (wsMessage.type) {
            // Duplicate browser description messages are OK
            RemoteAccessServer.WSMessageType.BROWSER_DESCRIPTION -> {}
            else -> messageQueue.removeIf { it.type == wsMessage.type }
        }
        messageQueue.add(wsMessage)
    }

    suspend fun closeAllSessions() {
        if (BuildConfig.DEBUG) Log.d(TAG, "WebSockets: Closing ${webSocketSessions.size} sessions")
        webSocketSessions.forEach { (sessionId, session) ->
            session.close()
            if (BuildConfig.DEBUG) Log.d(TAG, "WebSockets: Closed sessionId: $sessionId")
        }
        webSocketSessions.clear()
    }
}