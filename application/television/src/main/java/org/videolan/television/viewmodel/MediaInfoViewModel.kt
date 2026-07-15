/*
 * ************************************************************************
 *  MediaInfoViewModel.kt
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

package org.videolan.television.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.VLCInstance
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<MediaInfoUiState>(MediaInfoUiState.Loading)
    val uiState: StateFlow<MediaInfoUiState> = _uiState.asStateFlow()

    private val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory

    /**
     * Set up the view model with the given item ID and type.
     * Fetches the item from the medialibrary and parses its tracks if it's a media wrapper.
     *
     * @param id the ID of the item
     * @param type the type of the item (e.g., MediaLibraryItem.TYPE_MEDIA)
     */
    fun setup(id: Long, type: Int) {
        viewModelScope.launch {
            _uiState.value = MediaInfoUiState.Loading
            try {
                val medialibrary = Medialibrary.getInstance()
                val item: MediaLibraryItem? = withContext(Dispatchers.IO) {
                    when (type) {
                        MediaLibraryItem.TYPE_MEDIA -> medialibrary.getMedia(id)
                        MediaLibraryItem.TYPE_ARTIST -> medialibrary.getArtist(id)
                        MediaLibraryItem.TYPE_ALBUM -> medialibrary.getAlbum(id)
                        MediaLibraryItem.TYPE_GENRE -> medialibrary.getGenre(id)
                        MediaLibraryItem.TYPE_PLAYLIST -> medialibrary.getPlaylist(id, true, false)
                        else -> null
                    }
                }

                if (item == null) {
                    _uiState.value = MediaInfoUiState.Error
                    return@launch
                }

                val tracks = mutableListOf<TrackData>()
                var fileSize = 0L
                if (item is MediaWrapper) {
                    fileSize = withContext(Dispatchers.IO) {
                        if (item.uri?.scheme == "file") {
                            File(item.uri?.path ?: "").length()
                        } else 0L
                    }
                    tracks.addAll(parseTracks(item))
                }

                _uiState.value = MediaInfoUiState.Success(item, tracks, fileSize)
            } catch (e: Exception) {
                _uiState.value = MediaInfoUiState.Error
            }
        }
    }

    /**
     * Parse the tracks of the given media wrapper using libVLC.
     *
     * @param mediaWrapper the media wrapper to parse
     * @return the list of parsed track data
     */
    private suspend fun parseTracks(mediaWrapper: MediaWrapper): List<TrackData> = withContext(Dispatchers.IO) {
        val libVlc = VLCInstance.getInstance(context)
        val media = mediaFactory.getFromUri(libVlc, mediaWrapper.uri)
        media.parse()
        val trackCount = media.trackCount
        val trackDataList = mutableListOf<TrackData>()
        for (i in 0 until trackCount) {
            val track = media.getTrack(i)
            trackDataList.add(
                TrackData(
                    type = track.type,
                    codec = track.codec,
                    bitrate = track.bitrate,
                    language = track.language,
                    description = track.description,
                    width = (track as? IMedia.VideoTrack)?.width ?: 0,
                    height = (track as? IMedia.VideoTrack)?.height ?: 0,
                    channels = (track as? IMedia.AudioTrack)?.channels ?: 0,
                    rate = (track as? IMedia.AudioTrack)?.rate ?: 0
                )
            )
        }
        media.release()
        trackDataList
    }
}

/**
 * UI state for the media info screen.
 */
sealed class MediaInfoUiState {
    object Loading : MediaInfoUiState()
    data class Success(
        val item: MediaLibraryItem,
        val tracks: List<TrackData>,
        val fileSize: Long
    ) : MediaInfoUiState()
    object Error : MediaInfoUiState()
}

/**
 * Data class representing track information.
 */
data class TrackData(
    val type: Int,
    val codec: String,
    val bitrate: Int,
    val language: String?,
    val description: String?,
    val width: Int = 0,
    val height: Int = 0,
    val channels: Int = 0,
    val rate: Int = 0
)
