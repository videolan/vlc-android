/*
 * ************************************************************************
 *  WebServerWebSockets.kt
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

package org.videolan.vlc.webserver.websockets

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.google.gson.Gson
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope
import org.videolan.tools.WEB_SERVER_PLAYBACK_CONTROL
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.webserver.HttpSharingServer

object WebServerWebSockets {
    private const val TAG = "HttpSharingServerWS"
    var websocketSession: ArrayList<DefaultWebSocketServerSession> = arrayListOf()
    fun Routing.setupWebSockets(context: Context, settings: SharedPreferences) {
        webSocket("/echo", protocol = "player") {
            websocketSession.add(this)
            // Handle a WebSocket session
            for (frame in incoming) {
                try {
                    frame as? Frame.Text ?: continue
                    val message = frame.readText()
                    val gson = Gson()
                    val incomingMessage = gson.fromJson(message, WSIncomingMessage::class.java)
                    val service = HttpSharingServer.getInstance(context).service
                    when (incomingMessage.message) {
                        "play" -> if (playbackControlAllowedOrSend(settings)) service?.play()
                        "pause" -> if (playbackControlAllowedOrSend(settings)) service?.pause()
                        "previous" -> if (playbackControlAllowedOrSend(settings)) service?.previous(false)
                        "next" -> if (playbackControlAllowedOrSend(settings)) service?.next()
                        "previous10" -> if (playbackControlAllowedOrSend(settings)) service?.let { it.seek((it.getTime() - 10000).coerceAtLeast(0), fromUser = true) }
                        "next10" -> if (playbackControlAllowedOrSend(settings)) service?.let { it.seek((it.getTime() + 10000).coerceAtMost(it.length), fromUser = true) }
                        "shuffle" -> if (playbackControlAllowedOrSend(settings)) service?.shuffle()
                        "repeat" -> if (playbackControlAllowedOrSend(settings)) service?.let {
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

                        "get-volume" -> {
                            AppScope.launch { websocketSession.forEach { it.send(Frame.Text(getVolumeMessage(context, service))) } }
                        }

                        "set-volume" -> {
                            if (playbackControlAllowedOrSend(settings)) (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let {
                                val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                it.setStreamVolume(AudioManager.STREAM_MUSIC, ((incomingMessage.id!!.toFloat() / 100) * max).toInt(), AudioManager.FLAG_SHOW_UI)

                            }

                        }

                        "set-progress" -> {
                            if (playbackControlAllowedOrSend(settings)) incomingMessage.id?.let {
                                service?.setTime(it.toLong())
                            }
                        }

                        "play-media" -> {
                            if (playbackControlAllowedOrSend(settings)) service?.playIndex(incomingMessage.id!!)

                        }

                        "delete-media" -> {
                            if (playbackControlAllowedOrSend(settings)) service?.remove(incomingMessage.id!!)

                        }

                        "move-media-bottom" -> {
                            if (playbackControlAllowedOrSend(settings)) {
                                val index = incomingMessage.id!!
                                if (index < (service?.playlistManager?.getMediaListSize()
                                                ?: 0) - 1)
                                    service?.moveItem(index, index + 2)
                            }

                        }

                        "move-media-top" -> {
                            if (playbackControlAllowedOrSend(settings)) {
                                val index = incomingMessage.id!!
                                if (index > 0)
                                    service?.moveItem(index, index - 1)
                            }

                        }

                        else -> Log.w(TAG, "Unrecognized message", IllegalStateException("Unrecognized message: $message"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.message, e)
                }
            }
            websocketSession.remove(this)
        }
    }

    private fun playbackControlAllowedOrSend(settings: SharedPreferences): Boolean {
        val allowed = settings.getBoolean(WEB_SERVER_PLAYBACK_CONTROL, true)
        val message = Gson().toJson(HttpSharingServer.PlaybackControlForbidden())
        if (!allowed) AppScope.launch { websocketSession.forEach { it.send(Frame.Text(message)) } }
        return allowed
    }

    /**
     * Get the volume message to be sent to the client
     *
     * @return an [String] describing the volume message
     */
    private fun getVolumeMessage(context: Context, service: PlaybackService?): String {
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
        return gson.toJson(HttpSharingServer.Volume(volume))
    }
}