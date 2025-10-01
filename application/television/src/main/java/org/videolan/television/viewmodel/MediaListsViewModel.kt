/*
 * ************************************************************************
 *  MediaListsViewModel.kt
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

package org.videolan.television.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.BuildConfig
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.resources.util.getFromMl
import org.videolan.tools.Settings
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.viewmodels.CallBackDelegate
import org.videolan.vlc.viewmodels.ICallBackHandler

private const val TAG = "VLC/MediaListsViewModel"

class MediaListsViewModel(app: Application) : TvMediaViewModel(app), ICallBackHandler by CallBackDelegate() {

    val lists = List(MediaListModelEntry.entries.size) { MutableLiveData<List<MediaLibraryItem>>()}
    val loadingStates = List(MediaListModelEntry.entries.size) { MutableLiveData(false)}

    init {
        @Suppress("LeakingThis")
        viewModelScope.registerCallBacks { refresh() }
    }

    fun load(entry: MediaListModelEntry, page: Int) = viewModelScope.launch {
        setLoading(entry.getLoadingState(this@MediaListsViewModel), true)
        if (!Permissions.canReadStorage(getContext())) {
            showPermissionItem(entry.getMediaList(this@MediaListsViewModel))
            return@launch
        }
        getContext().getFromMl {
            entry.getQuery(this, page)
        }.let { newList ->
            entry.getMediaList(this@MediaListsViewModel).value = mutableListOf<MediaLibraryItem>().apply {
                entry.getMediaList(this@MediaListsViewModel).value?.let { oldList ->
                    addAll(oldList)
                }
                addAll(newList)
            }
        }
        setLoading(entry.getLoadingState(this@MediaListsViewModel), false)
    }


    fun refresh() {
        MediaListModelEntry.entries.forEach {
            it.currentPage = 0
            lists[it.ordinal].value = emptyList()
            load(it, 0)
        }
    }

    fun loadMore(entry: MediaListModelEntry) {
        entry.currentPage++
        entry.updateMediaList(this, entry.currentPage)
    }
}

enum class MediaListModelEntry(var currentPage:Int = -1, val inCardsKey: String, val defaultInCard: Boolean) {
    VIDEO(inCardsKey = "video_display_in_cards", defaultInCard = true),
    ARTISTS(inCardsKey = "display_mode_audio_browser_artists", defaultInCard = true),
    ALBUMS(inCardsKey = "display_mode_audio_browser_albums", defaultInCard = true),
    TRACKS(inCardsKey = "display_mode_audio_browser_track", defaultInCard = false),
    GENRES(inCardsKey = "display_mode_audio_browser_genres", defaultInCard = false),
    AUDIO_PLAYLISTS(inCardsKey = "display_mode_playlists_AudioOnly", defaultInCard = true),
    ALL_PLAYLISTS(inCardsKey = "display_mode_playlists_All", defaultInCard = false);

    fun updateMediaList(viewModel: MediaListsViewModel, page: Int) = viewModel.load(this, page)

    fun getMediaList(viewModel: MediaListsViewModel) = viewModel.lists[ordinal]

    fun getLoadingState(viewModel: MediaListsViewModel) = viewModel.loadingStates[ordinal]

    fun getQuery(medialibrary: Medialibrary, page: Int) = when (this) {
        VIDEO -> medialibrary.getPagedVideos(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
        ARTISTS -> medialibrary.getPagedArtists(true, Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
        ALBUMS -> medialibrary.getPagedAlbums(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
        TRACKS -> medialibrary.getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
        GENRES -> medialibrary.getPagedGenres(Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
        AUDIO_PLAYLISTS -> medialibrary.getPagedPlaylists(Playlist.Type.Audio, Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
        ALL_PLAYLISTS -> medialibrary.getPagedPlaylists(Playlist.Type.All, Medialibrary.SORT_INSERTIONDATE, true, true, false, MEDIALIBRARY_PAGE_SIZE, page* MEDIALIBRARY_PAGE_SIZE)
    }

    fun displayInCard(context: Context): Boolean {
        return Settings.getInstance(context).getBoolean(inCardsKey, defaultInCard)
    }
}