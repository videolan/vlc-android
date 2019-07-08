/*
 *****************************************************************************
 * CallBackDelegate.kt
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.tools.conflatedActor

interface ICallBackHandler {
    val medialibrary : AbstractMedialibrary

    fun CoroutineScope.registerCallBacks(refresh: () -> Unit)
    fun releaseCallbacks()
    fun watchMedia()
    fun watchArtists()
    fun watchAlbums()
    fun watchGenres()
    fun watchPlaylists()
}

class CallBackDelegate : ICallBackHandler,
        AbstractMedialibrary.OnMedialibraryReadyListener,
        AbstractMedialibrary.OnDeviceChangeListener,
        AbstractMedialibrary.MediaCb,
        AbstractMedialibrary.ArtistsCb,
        AbstractMedialibrary.AlbumsCb,
        AbstractMedialibrary.GenresCb,
        AbstractMedialibrary.PlaylistsCb{

    override val medialibrary = AbstractMedialibrary.getInstance()
    private lateinit var refreshActor: SendChannel<Unit>

    private var mediaCb = false
    private var artistsCb = false
    private var albumsCb = false
    private var genresCb = false
    private var playlistsCb = false

    override fun CoroutineScope.registerCallBacks(refresh: () -> Unit) {
        refreshActor = conflatedActor { refresh() }
        medialibrary.addOnMedialibraryReadyListener(this@CallBackDelegate)
        medialibrary.addOnDeviceChangeListener(this@CallBackDelegate)
    }

    override fun watchMedia() {
        medialibrary.addMediaCb(this)
        mediaCb = true
    }

    override fun watchArtists() {
        medialibrary.addArtistsCb(this)
        artistsCb = true
    }

    override fun watchAlbums() {
        medialibrary.addAlbumsCb(this)
        albumsCb = true
    }

    override fun watchGenres() {
        medialibrary.addGenreCb(this)
        genresCb = true
    }

    override fun watchPlaylists() {
        medialibrary.addPlaylistCb(this)
        playlistsCb = true
    }

    override fun releaseCallbacks() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        if (mediaCb) medialibrary.removeMediaCb(this)
        if (artistsCb) medialibrary.removeArtistsCb(this)
        if (albumsCb) medialibrary.removeAlbumsCb(this)
        if (genresCb) medialibrary.removeGenreCb(this)
        if (playlistsCb) medialibrary.removePlaylistCb(this)
        refreshActor.close()

    }

    override fun onMedialibraryReady() { refreshActor.offer(Unit) }

    override fun onMedialibraryIdle() { refreshActor.offer(Unit) }

    override fun onDeviceChange() { refreshActor.offer(Unit) }

    override fun onMediaAdded() { refreshActor.offer(Unit) }

    override fun onMediaModified() { refreshActor.offer(Unit) }

    override fun onMediaDeleted() { refreshActor.offer(Unit) }

    override fun onArtistsAdded() { refreshActor.offer(Unit) }

    override fun onArtistsModified() { refreshActor.offer(Unit) }

    override fun onArtistsDeleted() { refreshActor.offer(Unit) }

    override fun onAlbumsAdded() { refreshActor.offer(Unit) }

    override fun onAlbumsModified() { refreshActor.offer(Unit) }

    override fun onAlbumsDeleted() { refreshActor.offer(Unit) }

    override fun onGenresAdded() { refreshActor.offer(Unit) }

    override fun onGenresModified() { refreshActor.offer(Unit) }

    override fun onGenresDeleted() { refreshActor.offer(Unit) }

    override fun onPlaylistsAdded() { refreshActor.offer(Unit) }

    override fun onPlaylistsModified() { refreshActor.offer(Unit) }

    override fun onPlaylistsDeleted() { refreshActor.offer(Unit) }
}
