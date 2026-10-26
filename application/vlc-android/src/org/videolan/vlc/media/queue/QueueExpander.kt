/*
 * ************************************************************************
 *  QueueExpander.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.vlc.media.queue

import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.AppScope
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.media.MediaWrapperList
import org.videolan.vlc.media.PlaybackServiceCallback
import org.videolan.vlc.media.PlayerController
import org.videolan.vlc.media.PlaylistOrchestrator
import org.videolan.vlc.util.isSchemeFD
import org.videolan.vlc.util.isSchemeHttpOrHttps

/**
 * Encapsulates MRL/subitem expansion for media lists.
 */
class QueueExpander(
    private val playlistManager: PlaylistOrchestrator,
    private val player: PlayerController,
    private val playQueue: PlayQueue,
    private val medialibrary: Medialibrary,
    private val service: PlaybackServiceCallback
) {

    @MainThread
    suspend fun expand(updateHistory: Boolean, skipNextParsing: Boolean = false): Int {
        playlistManager.entryUrl = null
        if (BuildConfig.BETA) Log.d("VLC/QueueExpander", "expand with values: ", Exception("Call stack"))
        val index = playlistManager.currentIndex
        val expandedMedia: MediaWrapper? = playlistManager.getCurrentMedia()
        val stream = expandedMedia?.type == MediaWrapper.TYPE_STREAM
        val ml = player.expand()
        var ret = -1

        if (ml != null && ml.count > 0) {
            val mrl = if (updateHistory) expandedMedia?.location else null
            playQueue.removeEventListener(playlistManager as MediaWrapperList.EventListener)
            try {
                playQueue.remove(index)
                for (i in 0 until ml.count) {
                    val child = ml.getMediaAt(i)
                    if (isSchemeHttpOrHttps(child.uri.scheme) && child.uri.authority?.endsWith(".youtube.com") == true) {
                        playlistManager.shouldDisableCookieForwarding = true
                    }
                    if (!skipNextParsing) {
                        withContext(Dispatchers.IO) { child.parse() }
                    }
                    if (BuildConfig.BETA) Log.d("VLC/QueueExpander", "inserting: ${child.uri}")
                    playQueue.insertItem(
                        index + i,
                        MLServiceLocator.getAbstractMediaWrapper(child).apply {
                            if (skipNextParsing) addFlags(MediaWrapper.MEDIA_NO_PARSE)
                        }
                    )
                    child.release()
                }
            } finally {
                playQueue.addEventListener(playlistManager as MediaWrapperList.EventListener)
            }
            playlistManager.notifyUpdate()
            service.onMediaListChanged()
            val currentMedia: MediaWrapper? = playlistManager.getCurrentMedia()
            if (mrl != null && ml.count == 1 && currentMedia != null) {
                AppScope.launch(Dispatchers.IO) {
                    if (stream) {
                        currentMedia.type = MediaWrapper.TYPE_STREAM
                        playlistManager.entryUrl = mrl
                        medialibrary.getMedia(mrl)?.run { if (id > 0) medialibrary.removeExternalMedia(id) }
                    } else if (!currentMedia.uri.scheme.isSchemeFD()) {
                        medialibrary.addToHistory(mrl, currentMedia.title)
                    }
                }
            }
            ret = index
        }
        ml?.release()
        return ret
    }
}
