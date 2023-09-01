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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AppContextProvider
import org.videolan.tools.conflatedActor
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.FileUtils
import java.io.File

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
    fun watchFolders()
    fun pause()
    fun resume()
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
        Medialibrary.MediaGroupCb,
        Medialibrary.FoldersCb
{

    override val medialibrary = Medialibrary.getInstance()
    private lateinit var refreshActor: SendChannel<Unit>
    private lateinit var deleteActor: SendChannel<MediaAction>

    private var mediaCb = false
    private var artistsCb = false
    private var albumsCb = false
    private var genresCb = false
    private var playlistsCb = false
    private var historyCb = false
    private var mediaGroupsCb = false
    private var foldersCb = false
    var paused = false
        set(value) {
            field = value
            if (!value && isInvalid) {
                refreshActor.trySend(Unit)
                isInvalid = false
            }
        }
    var isInvalid = false

    /**
     * Pause the callbacks while the caller is paused to avoid unwanted refreshes
     * During this time, instead of refreshing, it's marked as invalid.
     * If invalid, a refresh is launched upon resuming
     */
    override fun pause() {
        paused = true
    }

    /**
     * Resumes the callback and refresh if it has been marked invalid in the meantime
     */
    override fun resume() {
        paused = false
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    override fun CoroutineScope.registerCallBacks(refresh: () -> Unit) {
        refreshActor = conflatedActor {
           if (paused) isInvalid = true else refresh()
        }
        deleteActor = actor(context = Dispatchers.IO, capacity = Channel.UNLIMITED) {
            for (action in channel) when (action) {
                is MediaDeletedAction -> {
                    action.ids.forEach {mediaId ->
                        deleteThumbnail(mediaId)
                    }
                }
                is MediaConvertedExternalAction -> {
                    action.ids.forEach {mediaId ->
                        deleteThumbnail(mediaId)
                    }
                }
            }
        }
        medialibrary.addOnMedialibraryReadyListener(this@CallBackDelegate)
        medialibrary.addOnDeviceChangeListener(this@CallBackDelegate)
    }

    private fun deleteThumbnail(mediaId: Long) {
        AppContextProvider.appContext.getExternalFilesDir(null)?.let {
            val file = File(it.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME + "/$mediaId.jpg")
            if (file.exists()) {
                medialibrary.getMedia(mediaId)?.removeThumbnail()
            }
            FileUtils.deleteFile(file)
        }
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

    override fun watchFolders() {
        medialibrary.addFoldersCb(this)
        foldersCb = true
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
        if (foldersCb) medialibrary.removeFoldersCb(this)
        refreshActor.close()
    }

    override fun onMedialibraryReady() { refreshActor.trySend(Unit) }

    override fun onMedialibraryIdle() { refreshActor.trySend(Unit) }

    override fun onDeviceChange() { refreshActor.trySend(Unit) }

    override fun onMediaAdded() { refreshActor.trySend(Unit) }

    override fun onMediaModified() {
        if (PlaylistManager.skipMediaUpdateRefresh) {
            PlaylistManager.skipMediaUpdateRefresh = false
            return
        }
        refreshActor.trySend(Unit)
    }

    override fun onMediaDeleted(ids: LongArray) {
        refreshActor.trySend(Unit)
        deleteActor.trySend(MediaDeletedAction(ids))
    }

    override fun onMediaConvertedToExternal(ids: LongArray) {
        refreshActor.trySend(Unit)
        deleteActor.trySend(MediaConvertedExternalAction(ids))
    }

    override fun onFoldersAdded() { refreshActor.trySend(Unit) }

    override fun onFoldersModified() { refreshActor.trySend(Unit) }

    override fun onFoldersDeleted() { refreshActor.trySend(Unit) }

    override fun onArtistsAdded() { refreshActor.trySend(Unit) }

    override fun onArtistsModified() { refreshActor.trySend(Unit) }

    override fun onArtistsDeleted() { refreshActor.trySend(Unit) }

    override fun onAlbumsAdded() { refreshActor.trySend(Unit) }

    override fun onAlbumsModified() { refreshActor.trySend(Unit) }

    override fun onAlbumsDeleted() { refreshActor.trySend(Unit) }

    override fun onGenresAdded() { refreshActor.trySend(Unit) }

    override fun onGenresModified() { refreshActor.trySend(Unit) }

    override fun onGenresDeleted() { refreshActor.trySend(Unit) }

    override fun onPlaylistsAdded() { refreshActor.trySend(Unit) }

    override fun onPlaylistsModified() { refreshActor.trySend(Unit) }

    override fun onPlaylistsDeleted() { refreshActor.trySend(Unit) }

    override fun onHistoryModified() { refreshActor.trySend(Unit) }

    override fun onMediaGroupsAdded() { refreshActor.trySend(Unit) }

    override fun onMediaGroupsModified() { refreshActor.trySend(Unit) }

    override fun onMediaGroupsDeleted() { refreshActor.trySend(Unit) }
}

sealed class MediaAction
class MediaDeletedAction(val ids:LongArray): MediaAction()
class MediaConvertedExternalAction(val ids:LongArray): MediaAction()