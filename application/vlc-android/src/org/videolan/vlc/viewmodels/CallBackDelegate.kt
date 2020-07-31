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
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.tools.conflatedActor
import org.videolan.tools.safeOffer

interface ICallBackHandler {
    val medialibrary : Medialibrary

    fun CoroutineScope.registerCallBacks(refresh: () -> Unit)
    fun releaseCallbacks()
    fun watchMedia()
    fun watchArtists()
    fun watchAlbums()
    fun watchGenres()
    fun watchPlaylists()
    fun watchHistory()
    fun watchMediaGroups()
}

class CallBackDelegate : ICallBackHandler,
        Medialibrary.OnMedialibraryReadyListener,
        Medialibrary.OnDeviceChangeListener,
        Medialibrary.MediaCb,
        Medialibrary.ArtistsCb,
        Medialibrary.AlbumsCb,
        Medialibrary.GenresCb,
        Medialibrary.PlaylistsCb,
        Medialibrary.HistoryCb,
        Medialibrary.MediaGroupCb
{

    override val medialibrary = Medialibrary.getInstance()
    private lateinit var refreshActor: SendChannel<Unit>

    private var mediaCb = false
    private var artistsCb = false
    private var albumsCb = false
    private var genresCb = false
    private var playlistsCb = false
    private var historyCb = false
    private var mediaGroupsCb = false

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

    override fun watchHistory() {
        medialibrary.addHistoryCb(this)
        historyCb = true
    }

    override fun watchMediaGroups() {
        medialibrary.addMediaGroupCb(this)
        mediaGroupsCb = true
    }

    override fun releaseCallbacks() {
        medialibrary.removeOnMedialibraryReadyListener(this)
        medialibrary.removeOnDeviceChangeListener(this)
        if (mediaCb) medialibrary.removeMediaCb(this)
        if (artistsCb) medialibrary.removeArtistsCb(this)
        if (albumsCb) medialibrary.removeAlbumsCb(this)
        if (genresCb) medialibrary.removeGenreCb(this)
        if (playlistsCb) medialibrary.removePlaylistCb(this)
        if (historyCb) medialibrary.removeHistoryCb(this)
        if (mediaGroupsCb) medialibrary.removeMediaGroupCb(this)
        refreshActor.close()
    }

    override fun onMedialibraryReady() { refreshActor.safeOffer(Unit) }

    override fun onMedialibraryIdle() { refreshActor.safeOffer(Unit) }

    override fun onDeviceChange() { refreshActor.safeOffer(Unit) }

    override fun onMediaAdded() { refreshActor.safeOffer(Unit) }

    override fun onMediaModified() { refreshActor.safeOffer(Unit) }

    override fun onMediaDeleted() { refreshActor.safeOffer(Unit) }

    override fun onArtistsAdded() { refreshActor.safeOffer(Unit) }

    override fun onArtistsModified() { refreshActor.safeOffer(Unit) }

    override fun onArtistsDeleted() { refreshActor.safeOffer(Unit) }

    override fun onAlbumsAdded() { refreshActor.safeOffer(Unit) }

    override fun onAlbumsModified() { refreshActor.safeOffer(Unit) }

    override fun onAlbumsDeleted() { refreshActor.safeOffer(Unit) }

    override fun onGenresAdded() { refreshActor.safeOffer(Unit) }

    override fun onGenresModified() { refreshActor.safeOffer(Unit) }

    override fun onGenresDeleted() { refreshActor.safeOffer(Unit) }

    override fun onPlaylistsAdded() { refreshActor.safeOffer(Unit) }

    override fun onPlaylistsModified() { refreshActor.safeOffer(Unit) }

    override fun onPlaylistsDeleted() { refreshActor.safeOffer(Unit) }

    override fun onHistoryModified() { refreshActor.safeOffer(Unit) }

    override fun onMediaGroupsAdded() { refreshActor.safeOffer(Unit) }

    override fun onMediaGroupsModified() { refreshActor.safeOffer(Unit) }

    override fun onMediaGroupsDeleted() { refreshActor.safeOffer(Unit) }
}
