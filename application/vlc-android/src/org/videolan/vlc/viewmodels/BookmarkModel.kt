/*
 * ************************************************************************
 *  BookmarkModel.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R

class BookmarkModel : ViewModel(), PlaybackService.Callback {

    val dataset = LiveDataset<Bookmark>()
    var service: PlaybackService? = null

    init {
        PlaybackService.serviceFlow.onEach { onServiceChanged(it) }
            .onCompletion { onServiceChanged(null) }
            .launchIn(viewModelScope)
    }

    private fun setup(service: PlaybackService) {
        service.addCallback(this)
        update()
    }

    fun refresh() {
        service?.currentMediaWrapper?.let {
            viewModelScope.launch {
                dataset.value = withContext(Dispatchers.IO) {
                    it.bookmarks ?: arrayOf<Bookmark>()
                }.toMutableList()
            }
        }
    }

    private fun onServiceChanged(service: PlaybackService?) {
        if (this.service == service) return
        if (service != null) {
            this.service = service
            setup(service)
        } else {
            this.service?.apply {
                removeCallback(this@BookmarkModel)
            }
            this.service = null
        }
    }

    companion object {
        fun get(activity: FragmentActivity) =
            ViewModelProvider(activity).get(BookmarkModel::class.java)
    }

    override fun update() {
    }

    override fun onMediaEvent(event: IMedia.Event) {
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        if (event.type == MediaPlayer.Event.Opening) refresh()
    }

    fun delete(bookmark: Bookmark) {
        service?.currentMediaWrapper?.let { media ->
            viewModelScope.launch {

                withContext(Dispatchers.IO) {
                    media.removeBookmark(bookmark.time)
                }
                refresh()
            }
        }
    }

    fun addBookmark(context: Context) {
        if (service == null) return
        service?.currentMediaWrapper?.let {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    val bookmark = it.addBookmark(service!!.getTime())
                    bookmark?.setName(context.getString(R.string.bookmark_default_name, Tools.millisToString(service!!.getTime())))
                }
                refresh()
            }
        }
    }

    suspend fun rename(bookmark: Bookmark, name: String) : List<Bookmark> {
        var bookmarks: List<Bookmark> = listOf()
        service?.currentMediaWrapper?.let {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    bookmark.setName(name)
                    bookmarks = it.bookmarks.toList()
                    bookmarks[bookmarks.indexOf(bookmark)].setName(name)
                }
            }
        }
        return bookmarks
    }

}
