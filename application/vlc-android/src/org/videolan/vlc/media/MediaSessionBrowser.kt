/*
 * ************************************************************************
 *  MediaSessionBrowser.kt
 * *************************************************************************
 *  Copyright © 2016-2020 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */
package org.videolan.vlc.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.resources.AppContextProvider.appContext
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionManagerService.ExtensionManagerActivity
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.getFileUri
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools.getDefaultAudioDrawable
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.isPathValid
import org.videolan.vlc.media.MediaUtils.getMediaAlbum
import org.videolan.vlc.media.MediaUtils.getMediaArtist
import org.videolan.vlc.media.MediaUtils.getMediaDescription
import org.videolan.vlc.media.MediaUtils.getMediaSubtitle
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.isSchemeStreaming
import java.util.*
import java.util.concurrent.Semaphore

class MediaSessionBrowser : ExtensionManagerActivity {
    override fun displayExtensionItems(extensionId: Int, title: String, items: List<VLCExtensionItem>, showParams: Boolean, isRefresh: Boolean) {
        if (showParams && items.size == 1 && items[0].getType() == VLCExtensionItem.TYPE_DIRECTORY) {
            extensionManagerService?.browse(items[0].stringId)
            return
        }
        var mediaItem: MediaDescriptionCompat.Builder
        var extensionItem: VLCExtensionItem
        for ((i, extensionItem) in items.withIndex()) {
            if (extensionItem.getType() != VLCExtensionItem.TYPE_AUDIO && extensionItem.getType() != VLCExtensionItem.TYPE_DIRECTORY) continue
            mediaItem = MediaDescriptionCompat.Builder()
            val coverUri = extensionItem.getImageUri()
            if (coverUri == null) mediaItem.setIconBitmap(getDefaultAudioDrawable(appContext).bitmap)
            else mediaItem.setIconUri(coverUri)
            mediaItem.setTitle(extensionItem.getTitle())
            mediaItem.setSubtitle(extensionItem.getSubTitle())
            val playable = extensionItem.getType() == VLCExtensionItem.TYPE_AUDIO
            if (playable) {
                mediaItem.setMediaId("${ExtensionsManager.EXTENSION_PREFIX}_${extensionId}_${extensionItem.getLink()}")
                extensionItems.add(MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            } else {
                mediaItem.setMediaId("${ExtensionsManager.EXTENSION_PREFIX}_${extensionId}_${extensionItem.stringId}")
                extensionItems.add(MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
            }
            if (i == MAX_EXTENSION_SIZE - 1) break
        }
        extensionLock.release()
    }

    companion object {
        private const val TAG = "VLC/MediaSessionBrowser"
        private const val BASE_DRAWABLE_URI = "android.resource://${BuildConfig.APP_ID}/drawable"
        private val DEFAULT_ALBUM_ICON = "${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_album_unknown}".toUri()
        private val DEFAULT_ARTIST_ICON = "${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_artist_unknown}".toUri()
        private val DEFAULT_STREAM_ICON = "${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_stream_unknown}".toUri()
        private val DEFAULT_PLAYLIST_ICON = "${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_playlist_unknown}".toUri()
        val DEFAULT_TRACK_ICON = "${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_nothumb}".toUri()
        private val instance = MediaSessionBrowser()
        const val ID_ROOT = "ID_ROOT"
        private const val ID_ARTISTS = "ID_ARTISTS"
        private const val ID_ALBUMS = "ID_ALBUMS"
        private const val ID_TRACKS = "ID_TRACKS"
        private const val ID_GENRES = "ID_GENRES"
        private const val ID_PLAYLISTS = "ID_PLAYLISTS"
        private const val ID_HOME = "ID_HOME"
        const val ID_HISTORY = "ID_HISTORY"
        const val ID_LAST_ADDED = "ID_RECENT"
        private const val ID_STREAMS = "ID_STREAMS"
        private const val ID_LIBRARY = "ID_LIBRARY"
        const val ID_SHUFFLE_ALL = "ID_SHUFFLE_ALL"
        const val ID_NO_MEDIA = "ID_NO_MEDIA"
        const val ID_NO_PLAYLIST = "ID_NO_PLAYLIST"
        const val ALBUM_PREFIX = "album"
        const val ARTIST_PREFIX = "artist"
        const val GENRE_PREFIX = "genre"
        const val PLAYLIST_PREFIX = "playlist"
        const val MAX_HISTORY_SIZE = 100
        private const val MAX_COVER_ART_ITEMS = 50
        private const val MAX_EXTENSION_SIZE = 100
        private const val MAX_RESULT_SIZE = 800

        // Extensions management
        private var extensionServiceConnection: ServiceConnection? = null
        private var extensionManagerService: ExtensionManagerService? = null
        private val extensionItems: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
        private val extensionLock = Semaphore(0)

        @WorkerThread
        fun browse(context: Context, parentId: String): List<MediaBrowserCompat.MediaItem>? {
            var results: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
            var list: Array<out MediaLibraryItem>? = null
            var limitSize = false
            var displayArtistOnTracks = true
            val res = context.resources

            //Extensions
            if (parentId.startsWith(ExtensionsManager.EXTENSION_PREFIX)) {
                if (extensionServiceConnection == null) {
                    createExtensionServiceConnection(context)
                    try {
                        extensionLock.acquire()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                if (extensionServiceConnection == null) return null
                val data = parentId.split("_").toTypedArray()
                val index = Integer.valueOf(data[1])
                extensionItems.clear()
                if (data.size == 2) {
                    //case extension root
                    extensionManagerService?.connectService(index)
                } else {
                    //case sub-directory
                    val stringId = parentId.replace("${ExtensionsManager.EXTENSION_PREFIX}_${index}_", "")
                    extensionManagerService?.browse(stringId)
                }
                try {
                    extensionLock.acquire()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                results = extensionItems
            } else {
                val ml by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
                when (parentId) {
                    ID_ROOT -> {
                        //List of Extensions
                        val extensions = ExtensionsManager.getInstance().getExtensions(context, true)
                        for ((i, extension) in extensions.withIndex()) {
                            var item = MediaDescriptionCompat.Builder()
                            if (extension.androidAutoEnabled()
                                    && Settings.getInstance(context).getBoolean(ExtensionsManager.EXTENSION_PREFIX + "_" + extension.componentName().packageName + "_" + ExtensionsManager.ANDROID_AUTO_SUFFIX, false)) {
                                item.setMediaId(ExtensionsManager.EXTENSION_PREFIX + "_" + i)
                                        .setTitle(extension.title())
                                val iconRes = extension.menuIcon()
                                var b: Bitmap? = null
                                var extensionRes: Resources?
                                if (iconRes != 0) {
                                    try {
                                        extensionRes = context.packageManager
                                                .getResourcesForApplication(extension.componentName().packageName)
                                        b = BitmapFactory.decodeResource(extensionRes, iconRes)
                                    } catch (ignored: PackageManager.NameNotFoundException) {
                                    }
                                }
                                if (b != null) item.setIconBitmap(b) else try {
                                    b = (context.packageManager.getApplicationIcon(extension.componentName().packageName) as BitmapDrawable).bitmap
                                    item.setIconBitmap(b)
                                } catch (e: PackageManager.NameNotFoundException) {
                                    b = context.getBitmapFromDrawable(R.drawable.icon)
                                    item.setIconBitmap(b)
                                }
                                results.add(MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                            }
                        }
                        //Home
                        val homeMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_HOME)
                                .setTitle(res.getString(R.string.auto_home))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_home}".toUri())
                                .setExtras(getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(homeMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Playlists
                        val playlistMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_PLAYLISTS)
                                .setTitle(res.getString(R.string.playlists))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_playlist}".toUri())
                                .setExtras(getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(playlistMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //My library
                        val libraryMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_LIBRARY)
                                .setTitle(res.getString(R.string.auto_my_library))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_audio}".toUri())
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(libraryMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Streams
                        val streamsMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_STREAMS)
                                .setTitle(res.getString(R.string.streams))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_menu_stream}".toUri())
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(streamsMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        return results
                    }
                    ID_HOME -> {
                        /* Shuffle All */
                        val audioCount = ml.audioCount
                        /* Show cover art from the whole library */
                        val offset = Random().nextInt((audioCount - MAX_COVER_ART_ITEMS).coerceAtLeast(0))
                        val allAudio = ml.getPagedAudio(Medialibrary.SORT_ALPHA, false, MAX_COVER_ART_ITEMS, offset)
                        val shuffleAllCover: Bitmap? = getHomeImage(context, "shuffleAll", allAudio)
                        val shuffleAllMediaDesc = getPlayAllBuilder(res, ID_SHUFFLE_ALL, audioCount, shuffleAllCover)
                                .setTitle(res.getString(R.string.shuffle_all_title))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(shuffleAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                        /* Last Added */
                        val recentAudio = ml.recentAudio
                        val lastAddedCover: Bitmap? = getHomeImage(context, "lastAdded", recentAudio)
                        val lastAddedMediaDesc = getPlayAllBuilder(res, ID_LAST_ADDED, recentAudio.size.coerceAtMost(MAX_HISTORY_SIZE), lastAddedCover)
                                .setTitle(res.getString(R.string.auto_last_added_media))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(lastAddedMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        /* History */
                        if (Settings.getInstance(context).getBoolean(PLAYBACK_HISTORY, true)) {
                            val lastMediaPlayed = ml.lastMediaPlayed()?.toList()?.filter { isMediaAudio(it) }
                            if (!lastMediaPlayed.isNullOrEmpty()) {
                                val historyCover: Bitmap? = getHomeImage(context, "history", lastMediaPlayed.toTypedArray())
                                val historyMediaDesc = getPlayAllBuilder(res, ID_HISTORY, lastMediaPlayed.size.coerceAtMost(MAX_HISTORY_SIZE), historyCover)
                                        .setTitle(res.getString(R.string.history))
                                        .build()
                                results.add(MediaBrowserCompat.MediaItem(historyMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                            }
                        }
                    }
                    ID_LIBRARY -> {
                        //Artists
                        val artistsMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_ARTISTS)
                                .setTitle(res.getString(R.string.artists))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_artist}".toUri())
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(artistsMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Albums
                        val albumsMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_ALBUMS)
                                .setTitle(res.getString(R.string.albums))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_album}".toUri())
                                .setExtras(getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_LIST_ITEM_HINT_VALUE))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(albumsMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Tracks
                        val tracksMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_TRACKS)
                                .setTitle(res.getString(R.string.tracks))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_audio}".toUri())
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(tracksMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        //Genres
                        val genresMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_GENRES)
                                .setTitle(res.getString(R.string.genres))
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_genre}".toUri())
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(genresMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        return results
                    }
                    ID_ARTISTS -> list = ml.getArtists(Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false))
                    ID_ALBUMS -> list = ml.albums
                    ID_GENRES -> list = ml.genres
                    ID_TRACKS -> list = ml.audio
                    ID_PLAYLISTS -> list = ml.playlists
                    ID_STREAMS -> list = ml.lastStreamsPlayed()
                    ID_LAST_ADDED -> {
                        limitSize = true
                        list = ml.recentAudio
                        if (list != null && list.size > 1) {
                            val playAllMediaDesc = getPlayAllBuilder(res, parentId, list.size.coerceAtMost(MAX_HISTORY_SIZE)).build()
                            results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                        }
                    }
                    ID_HISTORY -> {
                        limitSize = true
                        list = ml.lastMediaPlayed()?.toList()?.filter { isMediaAudio(it) }?.toTypedArray()
                        if (list != null && list.size > 1) {
                            val playAllMediaDesc = getPlayAllBuilder(res, parentId, list.size.coerceAtMost(MAX_HISTORY_SIZE)).build()
                            results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                        }
                    }
                    else -> {
                        val idSections = parentId.split("_").toTypedArray()
                        val id = idSections[1].toLong()
                        when (idSections[0]) {
                            ALBUM_PREFIX -> {
                                list = ml.getAlbum(id).tracks
                                if (list != null && list.size > 1) {
                                    val playAllMediaDesc = getPlayAllBuilder(res, parentId, list.size).build()
                                    results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                                }
                            }
                            ARTIST_PREFIX -> {
                                list = ml.getArtist(id).albums
                                if (list != null && list.size > 1) {
                                    var cover: Bitmap? = getPlayAllImage(context,"artist:${id}", ml.getArtist(id))
                                    val playAllMediaDesc = getPlayAllBuilder(res, parentId, ml.getArtist(id).tracksCount, cover).build()
                                    results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                                }
                            }
                            GENRE_PREFIX -> {
                                list = ml.getGenre(id).albums
                                if (list != null && list.size > 1) {
                                    var cover: Bitmap? = getPlayAllImage(context, "genre:${id}", ml.getGenre(id))
                                    val playAllMediaDesc = getPlayAllBuilder(res, parentId, ml.getGenre(id).tracksCount, cover).build()
                                    results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                                }
                            }
                        }
                    }
                }
                list?.let { list ->
                    val artworkToUriCache = HashMap<String, Uri>()
                    results.ensureCapacity(list.size.coerceAtMost(MAX_RESULT_SIZE))
                    for (libraryItem in list) {
                        if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA
                                && ((libraryItem as MediaWrapper).type == MediaWrapper.TYPE_STREAM || isSchemeStreaming(libraryItem.uri.scheme))) {
                            libraryItem.type = MediaWrapper.TYPE_STREAM
                        } else if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type != MediaWrapper.TYPE_AUDIO)
                            continue

                        val item = MediaDescriptionCompat.Builder()
                                .setTitle(libraryItem.title)
                                .setMediaId(generateMediaId(libraryItem))

                        /* Set Subtitles */
                        when (libraryItem.itemType) {
                            MediaLibraryItem.TYPE_MEDIA -> {
                                val media = libraryItem as MediaWrapper
                                item.setMediaUri((libraryItem as MediaWrapper).uri)
                                item.setSubtitle(when {
                                    media.type == MediaWrapper.TYPE_STREAM -> media.uri.toString()
                                    parentId.startsWith(ALBUM_PREFIX) -> getMediaSubtitle(media)
                                    else -> getMediaDescription(getMediaArtist(context, media), getMediaAlbum(context, media))
                                })
                            }
                            MediaLibraryItem.TYPE_PLAYLIST -> {
                                item.setSubtitle(res.getString(R.string.track_number, libraryItem.tracksCount))
                            }
                            MediaLibraryItem.TYPE_ARTIST -> {
                                item.setSubtitle(res.getQuantityString(R.plurals.albums_quantity, ml.getArtist(libraryItem.id).albumsCount, ml.getArtist(libraryItem.id).albumsCount))
                            }
                            MediaLibraryItem.TYPE_GENRE -> {
                                item.setSubtitle(res.getQuantityString(R.plurals.albums_quantity, ml.getGenre(libraryItem.id).albumsCount, ml.getGenre(libraryItem.id).albumsCount))
                            }
                            MediaLibraryItem.TYPE_ALBUM -> {
                                if (parentId.startsWith(ARTIST_PREFIX))
                                    item.setSubtitle(res.getString(R.string.track_number, libraryItem.tracksCount))
                                else
                                    item.setSubtitle(libraryItem.description)
                            }
                            else -> item.setSubtitle(libraryItem.description)
                        }

                        /* Set Icons */
                        if (libraryItem.itemType != MediaLibraryItem.TYPE_PLAYLIST && !libraryItem.artworkMrl.isNullOrEmpty() && isPathValid(libraryItem.artworkMrl))
                            item.setIconUri(artworkToUriCache.getOrPut(libraryItem.artworkMrl, { getFileUri(libraryItem.artworkMrl) }))
                        else if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type == MediaWrapper.TYPE_STREAM)
                            item.setIconUri(DEFAULT_STREAM_ICON)
                        else {
                            when (libraryItem.itemType) {
                                MediaLibraryItem.TYPE_ARTIST -> item.setIconUri(DEFAULT_ARTIST_ICON)
                                MediaLibraryItem.TYPE_ALBUM -> item.setIconUri(DEFAULT_ALBUM_ICON)
                                MediaLibraryItem.TYPE_GENRE -> item.setIconUri(null)
                                MediaLibraryItem.TYPE_PLAYLIST -> {
                                    val cover = runBlocking(Dispatchers.IO) {
                                        if (!libraryItem.artworkMrl.isNullOrEmpty()) {
                                            AudioUtil.fetchCoverBitmap(Uri.decode(libraryItem.artworkMrl), 256)
                                        } else {
                                            ThumbnailsProvider.getPlaylistImage("playlist:${libraryItem.id}", libraryItem.tracks.toList(), 256)
                                        }
                                    }
                                    if (cover != null) item.setIconBitmap(cover) else item.setIconUri(DEFAULT_PLAYLIST_ICON)
                                }
                                else -> item.setIconUri(DEFAULT_TRACK_ICON)
                            }
                        }
                        /* Set Extras */
                        when (libraryItem.itemType) {
                            MediaLibraryItem.TYPE_ARTIST, MediaLibraryItem.TYPE_GENRE ->
                                item.setExtras(getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                        }
                        /* Set Flags */
                        val flags = when (libraryItem.itemType) {
                            MediaLibraryItem.TYPE_MEDIA, MediaLibraryItem.TYPE_PLAYLIST -> MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                            else -> MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        }
                        results.add(MediaBrowserCompat.MediaItem(item.build(), flags))
                        if ((limitSize && results.size == MAX_HISTORY_SIZE) || results.size == MAX_RESULT_SIZE) break
                    }
                    artworkToUriCache.clear()
                }
            }
            if (results.isEmpty()) {
                val emptyMediaDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(ID_NO_MEDIA)
                        .setIconUri(DEFAULT_TRACK_ICON)
                        .setTitle(context.getString(R.string.search_no_result))
                when (parentId) {
                    ID_ARTISTS -> emptyMediaDesc.setIconUri(DEFAULT_ARTIST_ICON)
                    ID_ALBUMS -> emptyMediaDesc.setIconUri(DEFAULT_ALBUM_ICON)
                    ID_GENRES -> emptyMediaDesc.setIconUri(null)
                    ID_PLAYLISTS -> {
                        emptyMediaDesc.setMediaId(ID_NO_PLAYLIST)
                        emptyMediaDesc.setTitle(context.getString(R.string.noplaylist))
                    }
                    ID_STREAMS -> emptyMediaDesc.setIconUri(DEFAULT_STREAM_ICON)
                }
                results.add(MediaBrowserCompat.MediaItem(emptyMediaDesc.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            return results
        }

        fun getContentStyle(browsableHint: Int, playableHint: Int): Bundle {
            return Bundle().apply {
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, playableHint)
            }
        }

        fun generateMediaId(libraryItem: MediaLibraryItem): String {
            val prefix = when (libraryItem.itemType) {
                MediaLibraryItem.TYPE_ALBUM -> ALBUM_PREFIX
                MediaLibraryItem.TYPE_ARTIST -> ARTIST_PREFIX
                MediaLibraryItem.TYPE_GENRE -> GENRE_PREFIX
                MediaLibraryItem.TYPE_PLAYLIST -> PLAYLIST_PREFIX
                else -> return libraryItem.id.toString()
            }
            return "${prefix}_${libraryItem.id}"
        }

        fun isMediaAudio(libraryItem: MediaLibraryItem): Boolean {
            return libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type == MediaWrapper.TYPE_AUDIO
        }

        private fun getPlayAllBuilder(res: Resources, mediaId: String, trackCount: Int, cover: Bitmap? = null): MediaDescriptionCompat.Builder {
            return MediaDescriptionCompat.Builder()
                    .setMediaId(mediaId)
                    .setTitle(res.getString(R.string.play_all))
                    .setSubtitle(res.getString(R.string.track_number, trackCount))
                    .setIconBitmap(cover)
                    .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_playall}".toUri())
        }

        private fun getPlayAllImage(context: Context, key: String, mediaItem: MediaLibraryItem): Bitmap? {
            val tracks = mediaItem.tracks.toList()
            val albums = when (mediaItem.itemType) {
                MediaLibraryItem.TYPE_ARTIST -> (mediaItem as Artist).albums
                MediaLibraryItem.TYPE_GENRE -> (mediaItem as Genre).albums
                else -> emptyArray()
            }
            var cover: Bitmap? = null
            if (albums.any { it.artworkMrl != null && it.artworkMrl.isNotEmpty() }) {
                cover = runBlocking(Dispatchers.IO) {
                    ThumbnailsProvider.getPlaylistImage(key, tracks, 256, getBitmapFromDrawable(context, R.drawable.ic_auto_playall_circle))
                }
            }
            return cover
        }

        private fun getHomeImage(context: Context, key: String, list: Array<MediaWrapper>?): Bitmap? {
            var cover: Bitmap? = null
            val tracks: ArrayList<MediaWrapper> = ArrayList<MediaWrapper>()
            list?.let { list ->
                tracks.ensureCapacity(list.size.coerceAtMost(MAX_COVER_ART_ITEMS))
                for (libraryItem in list) {
                    if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && libraryItem.type != MediaWrapper.TYPE_AUDIO)
                        continue
                    tracks.add(libraryItem)
                    if (tracks.size == MAX_COVER_ART_ITEMS) break
                }
                if (tracks.any { it.artworkMrl != null && it.artworkMrl.isNotEmpty() }) {
                    cover = runBlocking(Dispatchers.IO) {
                        val iconAddition = when (key) {
                            "shuffleAll"-> getBitmapFromDrawable(context, R.drawable.ic_auto_shuffle_circle)
                            "lastAdded"-> getBitmapFromDrawable(context, R.drawable.ic_auto_new_circle)
                            "history"-> getBitmapFromDrawable(context, R.drawable.ic_auto_history_circle)
                            else -> null
                        }
                        ThumbnailsProvider.getPlaylistImage(key, tracks, 256, iconAddition)
                    }
                }
            }
            return cover
        }

        private fun createExtensionServiceConnection(context: Context) {
            extensionServiceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    extensionManagerService = (service as ExtensionManagerService.LocalBinder).service.apply {
                        setExtensionManagerActivity(instance)
                    }
                    extensionLock.release()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    context.unbindService(extensionServiceConnection!!)
                    extensionServiceConnection = null
                    extensionManagerService!!.stopSelf()
                }
            }
            extensionServiceConnection?.let {
                val intent = Intent(context, ExtensionManagerService::class.java)
                if (!context.bindService(intent, it, Context.BIND_AUTO_CREATE)) extensionServiceConnection = null
            }
        }

        fun unbindExtensionConnection() {
            extensionManagerService?.disconnect()
        }
    }
}
