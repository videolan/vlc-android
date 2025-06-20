/*
 *****************************************************************************
 * MediaBrowserCallback.kt
 *****************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
 *
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
 *****************************************************************************/

package org.videolan.vlc

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.SendChannel
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.conflatedActor

/**
 * @see org.videolan.vlc.viewmodels.ICallBackHandler
 */
interface IMediaBrowserCallback {
    fun registerCallback(callback: (BrowserUpdate) -> Unit)
    fun removeCallbacks()
}

/**
 *@see org.videolan.vlc.viewmodels.CallBackDelegate
 */
class MediaBrowserCallback(private val playbackService: PlaybackService) : IMediaBrowserCallback,
        Medialibrary.MediaCb,
        Medialibrary.ArtistsCb,
        Medialibrary.AlbumsCb,
        Medialibrary.GenresCb,
        Medialibrary.PlaylistsCb,
        Medialibrary.HistoryCb {

    private val medialibrary = Medialibrary.getInstance()
    private lateinit var refreshActor: SendChannel<BrowserUpdate>

    override fun registerCallback(callback: (BrowserUpdate) -> Unit) {
        refreshActor = playbackService.lifecycleScope.conflatedActor { callback(it) }
        medialibrary.addMediaCb(this)
        medialibrary.addArtistsCb(this)
        medialibrary.addAlbumsCb(this)
        medialibrary.addGenreCb(this)
        medialibrary.addPlaylistCb(this)
        medialibrary.addHistoryCb(this)
    }

    override fun onMediaAdded() {
        refreshActor.trySend(BrowserUpdate(UpdateType.MEDIA))
    }

    override fun onMediaDeleted(id: LongArray?) {
        refreshActor.trySend(BrowserUpdate(UpdateType.MEDIA))
    }

    override fun onMediaModified() {
        /* Intentionally Ignored */
    }

    override fun onMediaConvertedToExternal(id: LongArray?) {
        refreshActor.trySend(BrowserUpdate(UpdateType.MEDIA))
    }

    override fun onArtistsAdded() {
        refreshActor.trySend(BrowserUpdate(UpdateType.ARTIST))
    }

    override fun onArtistsModified() {
        refreshActor.trySend(BrowserUpdate(UpdateType.ARTIST))
    }

    override fun onArtistsDeleted() {
        refreshActor.trySend(BrowserUpdate(UpdateType.ARTIST))
    }

    override fun onAlbumsAdded() {
        refreshActor.trySend(BrowserUpdate(UpdateType.ALBUM))
    }

    override fun onAlbumsModified() {
        refreshActor.trySend(BrowserUpdate(UpdateType.ALBUM))
    }

    override fun onAlbumsDeleted() {
        refreshActor.trySend(BrowserUpdate(UpdateType.ALBUM))
    }

    override fun onGenresAdded() {
        refreshActor.trySend(BrowserUpdate(UpdateType.GENRE))
    }

    override fun onGenresModified() {
        refreshActor.trySend(BrowserUpdate(UpdateType.GENRE))
    }

    override fun onGenresDeleted() {
        refreshActor.trySend(BrowserUpdate(UpdateType.GENRE))
    }

    override fun onPlaylistsAdded() {
        refreshActor.trySend(BrowserUpdate(UpdateType.PLAYLIST))
    }

    override fun onPlaylistsModified() {
        refreshActor.trySend(BrowserUpdate(UpdateType.PLAYLIST))
    }

    override fun onPlaylistsDeleted() {
        refreshActor.trySend(BrowserUpdate(UpdateType.PLAYLIST))
    }

    override fun onHistoryModified() {
        refreshActor.trySend(BrowserUpdate(UpdateType.HISTORY))
    }

    fun onShuffleChanged() {
        refreshActor.trySend(BrowserUpdate(UpdateType.SHUFFLE))
    }

    override fun removeCallbacks() {
        if (::refreshActor.isInitialized) {
            medialibrary.removeMediaCb(this)
            medialibrary.removeArtistsCb(this)
            medialibrary.removeAlbumsCb(this)
            medialibrary.removeGenreCb(this)
            medialibrary.removePlaylistCb(this)
            medialibrary.removeHistoryCb(this)
            refreshActor.close()
        }
    }
}

enum class UpdateType {
    MEDIA,
    ARTIST,
    ALBUM,
    GENRE,
    PLAYLIST,
    HISTORY,
    SHUFFLE;
}

class BrowserUpdate(val updateType: UpdateType)