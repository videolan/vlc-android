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
import org.videolan.tools.safeOffer

/**
 * @see org.videolan.vlc.viewmodels.ICallBackHandler
 */
interface IMediaBrowserCallback {
    fun registerHistoryCallback(function: () -> Unit)
    fun registerMediaCallback(function: () -> Unit)
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
    private lateinit var historyActor: SendChannel<Unit>
    private lateinit var refreshActor: SendChannel<Unit>

    override fun registerHistoryCallback(refresh: () -> Unit) {
        historyActor = playbackService.lifecycleScope.conflatedActor { refresh() }
        medialibrary.addHistoryCb(this)
    }

    override fun onHistoryModified() {
        historyActor.safeOffer(Unit)
    }

    override fun registerMediaCallback(refresh: () -> Unit) {
        refreshActor = playbackService.lifecycleScope.conflatedActor { refresh() }
        medialibrary.addMediaCb(this)
        medialibrary.addArtistsCb(this)
        medialibrary.addAlbumsCb(this)
        medialibrary.addGenreCb(this)
        medialibrary.addPlaylistCb(this)
    }

    override fun onMediaAdded() {
        refreshActor.safeOffer(Unit)
    }

    override fun onMediaDeleted(id: LongArray?) {
        refreshActor.safeOffer(Unit)
    }

    override fun onMediaModified() {
        /* Intentionally Ignored */
    }

    override fun onMediaConvertedToExternal(id: LongArray?) {
        refreshActor.safeOffer(Unit)
    }

    override fun onArtistsAdded() {
        refreshActor.safeOffer(Unit)
    }

    override fun onArtistsModified() {
        refreshActor.safeOffer(Unit)
    }

    override fun onArtistsDeleted() {
        refreshActor.safeOffer(Unit)
    }

    override fun onAlbumsAdded() {
        refreshActor.safeOffer(Unit)
    }

    override fun onAlbumsModified() {
        refreshActor.safeOffer(Unit)
    }

    override fun onAlbumsDeleted() {
        refreshActor.safeOffer(Unit)
    }

    override fun onGenresAdded() {
        refreshActor.safeOffer(Unit)
    }

    override fun onGenresModified() {
        refreshActor.safeOffer(Unit)
    }

    override fun onGenresDeleted() {
        refreshActor.safeOffer(Unit)
    }

    override fun onPlaylistsAdded() {
        refreshActor.safeOffer(Unit)
    }

    override fun onPlaylistsModified() {
        refreshActor.safeOffer(Unit)
    }

    override fun onPlaylistsDeleted() {
        refreshActor.safeOffer(Unit)
    }

    override fun removeCallbacks() {
        if (::refreshActor.isInitialized) {
            medialibrary.removeMediaCb(this)
            medialibrary.removeArtistsCb(this)
            medialibrary.removeAlbumsCb(this)
            medialibrary.removeGenreCb(this)
            medialibrary.removePlaylistCb(this)
            refreshActor.close()
        }
        if (::historyActor.isInitialized) {
            medialibrary.removeHistoryCb(this)
            historyActor.close()
        }
    }
}