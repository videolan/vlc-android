/*
 * ************************************************************************
 *  PlaylistOrchestrator.kt
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

package org.videolan.vlc.media

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.media.persistence.ResumeStatus
import org.videolan.vlc.media.queue.QueueNavigator

/**
 * Interface decoupling child media delegates from the concrete [PlaylistManager] facade.
 */
interface PlaylistOrchestrator : CoroutineScope, MediaWrapperList.EventListener {
    var currentIndex: Int
    val nextIndex: Int
    var entryUrl: String?
    var endReachedFor: String?
    var savedTime: Long
    var stopAfter: Int
    var videoBackground: Boolean
    var shouldDisableCookieForwarding: Boolean
    var isBenchmark: Boolean
    var isHardware: Boolean
    var parsed: Boolean
    var newMedia: Boolean
    var shuffling: Boolean

    val audioResumeStatus: ResumeStatus
    val videoResumeStatus: ResumeStatus
    val navigator: QueueNavigator
    val abRepeat: MutableLiveData<ABRepeat>
    val waitForConfirmation: MutableLiveData<WaitConfirmation?>
    val waitForConfirmationAudio: MutableLiveData<WaitConfirmation?>

    fun getCurrentMedia(): MediaWrapper?
    fun isAudioList(): Boolean
    fun canShuffle(): Boolean
    fun hasNext(): Boolean
    fun savePosition(reset: Boolean = false, video: Boolean = false)
    fun saveCurrentMedia()
    suspend fun saveMediaList()
    fun saveMediaMeta(end: Boolean = false): Job
    fun loadMediaMeta(mw: MediaWrapper)
    fun clearABRepeat()
    fun getStartTime(mw: MediaWrapper): Long
    fun notifyUpdate()
    suspend fun playIndex(index: Int, flags: Int = 0, forceResume: Boolean = false, forceRestart: Boolean = false)
    fun stop(systemExit: Boolean = false, video: Boolean = false)
    fun next(force: Boolean = false)
    fun shuffle()
    suspend fun determinePrevAndNextIndices(expand: Boolean = false)
    fun refreshTrackMeta(mw: MediaWrapper)
    fun restoreSpeed(media: MediaWrapper)
    suspend fun savePlaycount(mw: MediaWrapper)
}
