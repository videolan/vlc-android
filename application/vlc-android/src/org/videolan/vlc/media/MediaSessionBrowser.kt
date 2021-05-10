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
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.resources.AppContextProvider.appContext
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.Settings
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionManagerService.ExtensionManagerActivity
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.extensions.api.VLCExtensionItem
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
        private val DEFAULT_PLAYALL_ICON = "${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_playall}".toUri()
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
        const val SEARCH_PREFIX = "search"
        const val MAX_HISTORY_SIZE = 100
        const val MAX_COVER_ART_ITEMS = 50
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
                val ml = Medialibrary.getInstance()
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
                                .setIconUri("${BASE_DRAWABLE_URI}/${R.drawable.ic_auto_stream}".toUri())
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(streamsMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        return results
                    }
                    ID_HOME -> {
                        /* Shuffle All */
                        val audioCount = ml.audioCount
                        val shuffleAllPath = if (audioCount > 0) {
                            Uri.Builder()
                                    .appendPath(ArtworkProvider.SHUFFLE_ALL)
                                    .appendPath(ArtworkProvider.computeExpiration())
                                    .appendPath("$audioCount")
                                    .build()
                        } else null
                        val shuffleAllMediaDesc = getPlayAllBuilder(res, ID_SHUFFLE_ALL, audioCount, shuffleAllPath)
                                .setTitle(res.getString(R.string.shuffle_all_title))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(shuffleAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                        /* Last Added */
                        val recentAudio = ml.recentAudio
                        val recentAudioSize = recentAudio.size.coerceAtMost(MAX_HISTORY_SIZE)
                        val lastAddedPath = if (recentAudioSize > 0) {
                            Uri.Builder()
                                    .appendPath(ArtworkProvider.LAST_ADDED)
                                    .appendPath("${ArtworkProvider.computeChecksum(recentAudio.toList())}")
                                    .appendPath("$recentAudioSize")
                                    .build()
                        } else null
                        val lastAddedMediaDesc = getPlayAllBuilder(res, ID_LAST_ADDED, recentAudioSize, lastAddedPath)
                                .setTitle(res.getString(R.string.auto_last_added_media))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(lastAddedMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        /* History */
                        if (Settings.getInstance(context).getBoolean(PLAYBACK_HISTORY, true)) {
                            val lastMediaPlayed = ml.lastMediaPlayed()?.toList()?.filter { isMediaAudio(it) }
                            if (!lastMediaPlayed.isNullOrEmpty()) {
                                val lastMediaSize = lastMediaPlayed.size.coerceAtMost(MAX_HISTORY_SIZE)
                                val historyPath = Uri.Builder()
                                        .appendPath(ArtworkProvider.HISTORY)
                                        .appendPath("${ArtworkProvider.computeChecksum(lastMediaPlayed)}")
                                        .appendPath("$lastMediaSize")
                                        .build()
                                val historyMediaDesc = getPlayAllBuilder(res, ID_HISTORY, lastMediaSize, historyPath)
                                        .setTitle(res.getString(R.string.history))
                                        .build()
                                results.add(MediaBrowserCompat.MediaItem(historyMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                            }
                        }
                        return results
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
                                val artist = ml.getArtist(id)
                                list = artist.albums
                                if (list != null && list.size > 1) {
                                    val hasArtwork = list.any { !it.artworkMrl.isNullOrEmpty() && isPathValid(it.artworkMrl) }
                                    val playAllPath = if (hasArtwork) {
                                        Uri.Builder()
                                                .appendPath(ArtworkProvider.PLAY_ALL)
                                                .appendPath(ArtworkProvider.ARTIST)
                                                .appendPath("${artist.tracksCount}")
                                                .appendPath("$id")
                                                .build()
                                    } else null
                                    val playAllMediaDesc = getPlayAllBuilder(res, parentId, artist.tracksCount, playAllPath).build()
                                    results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                                }
                            }
                            GENRE_PREFIX -> {
                                val genre = ml.getGenre(id)
                                list = genre.albums
                                if (list != null && list.size > 1) {
                                    val playAllPath = Uri.Builder()
                                            .appendPath(ArtworkProvider.PLAY_ALL)
                                            .appendPath(ArtworkProvider.GENRE)
                                            .appendPath("${genre.tracksCount}")
                                            .appendPath("$id")
                                            .build()
                                    val playAllMediaDesc = getPlayAllBuilder(res, parentId, genre.tracksCount, playAllPath).build()
                                    results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                                }
                            }
                        }
                    }
                }
            }
            results.addAll(buildMediaItems(context, parentId, list, null, limitSize))
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

        /**
         * The search method is passed a simple query string absent metadata indicating
         * the user's intent to load a playlist, album, artist, or song. This is slightly different
         * than PlaybackService.onPlayFromSearch (which is also invoked by voice search) and allows
         * the user to navigate to other content via on-screen menus.
         */
        @WorkerThread
        fun search(context: Context, query: String): List<MediaBrowserCompat.MediaItem>? {
            val res = context.resources
            val results: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
            val searchAggregate = Medialibrary.getInstance().search(query)
            results.addAll(buildMediaItems(context, ID_PLAYLISTS, searchAggregate.playlists, res.getString(R.string.playlists)))
            results.addAll(buildMediaItems(context, ARTIST_PREFIX, searchAggregate.artists, res.getString(R.string.artists)))
            results.addAll(buildMediaItems(context, ALBUM_PREFIX, searchAggregate.albums, res.getString(R.string.albums)))
            val trackLst = buildMediaItems(context, ID_TRACKS, searchAggregate.tracks, res.getString(R.string.tracks))
            if (trackLst.size > 1) {
                val extras = Bundle().apply {
                    putString(EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT, res.getString(R.string.tracks))
                }
                val playAllMediaDesc = getPlayAllBuilder(res, SEARCH_PREFIX + "_$query", trackLst.size)
                        .setExtras(extras)
                        .build()
                results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            if (trackLst.isNotEmpty()) results.addAll(trackLst)
            if (results.isEmpty()) {
                val emptyMediaDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(ID_NO_MEDIA)
                        .setIconUri(DEFAULT_TRACK_ICON)
                        .setTitle(context.getString(R.string.search_no_result))
                        .build()
                results.add(MediaBrowserCompat.MediaItem(emptyMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            return results
        }

        /**
         * This function constructs a collection of MediaBrowserCompat.MediaItems for each applicable
         * array element in the MediaLibraryItems list passed from either the browse or search methods.
         *
         * @param context Application context to resolve string resources
         * @param parentId Identifies the position in the menu hierarchy. The browse function
         * will pass the argument from the calling application. The search function will use a
         * placeholder value to act as if the user navigated to the location.
         * @param list MediaLibraryItems to process into MediaBrowserCompat.MediaItems
         * @param groupTitle Common heading to group items (unused if null)
         * @param limitSize Limit the number of items returned (default is false)
         * @return List containing fully constructed MediaBrowser MediaItem
         */
        private fun buildMediaItems(context: Context, parentId: String, list: Array<out MediaLibraryItem>?, groupTitle: String?, limitSize: Boolean = false): List<MediaBrowserCompat.MediaItem> {
            if (list.isNullOrEmpty()) return emptyList()
            val res = context.resources
            val artworkToUriCache = HashMap<String, Uri>()
            var results: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
            results.ensureCapacity(list.size.coerceAtMost(MAX_RESULT_SIZE))
            /* Iterate over list */
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
                        val albumsCount = Medialibrary.getInstance().getArtist(libraryItem.id).albumsCount
                        item.setSubtitle(res.getQuantityString(R.plurals.albums_quantity, albumsCount, albumsCount))
                    }
                    MediaLibraryItem.TYPE_GENRE -> {
                        val albumsCount = Medialibrary.getInstance().getGenre(libraryItem.id).albumsCount
                        item.setSubtitle(res.getQuantityString(R.plurals.albums_quantity, albumsCount, albumsCount))
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
                if (libraryItem.itemType != MediaLibraryItem.TYPE_PLAYLIST && !libraryItem.artworkMrl.isNullOrEmpty() && isPathValid(libraryItem.artworkMrl)) {
                    val iconUri = Uri.Builder()
                    when (libraryItem.itemType) {
                        MediaLibraryItem.TYPE_ARTIST ->{
                            iconUri.appendPath(ArtworkProvider.ARTIST)
                            iconUri.appendPath("${libraryItem.tracksCount}")
                        }
                        MediaLibraryItem.TYPE_ALBUM -> {
                            iconUri.appendPath(ArtworkProvider.ALBUM)
                            iconUri.appendPath("${libraryItem.tracksCount}")
                        }
                        else -> {
                            iconUri.appendPath(ArtworkProvider.MEDIA)
                            (libraryItem as? MediaWrapper)?.let { iconUri.appendPath("${it.lastModified}") }
                        }
                    }
                    iconUri.appendPath("${libraryItem.id}")
                    item.setIconUri(artworkToUriCache.getOrPut(libraryItem.artworkMrl, { ArtworkProvider.buildUri(iconUri.build()) }))
                } else if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type == MediaWrapper.TYPE_STREAM)
                    item.setIconUri(DEFAULT_STREAM_ICON)
                else {
                    when (libraryItem.itemType) {
                        MediaLibraryItem.TYPE_ARTIST -> item.setIconUri(DEFAULT_ARTIST_ICON)
                        MediaLibraryItem.TYPE_ALBUM -> item.setIconUri(DEFAULT_ALBUM_ICON)
                        MediaLibraryItem.TYPE_GENRE -> item.setIconUri(null)
                        MediaLibraryItem.TYPE_PLAYLIST -> {
                            val trackList = libraryItem.tracks.toList()
                            val hasArtwork = trackList.any { (ThumbnailsProvider.isMediaVideo(it) || (!it.artworkMrl.isNullOrEmpty() && isPathValid(it.artworkMrl))) }
                            if (hasArtwork) {
                                val playAllPlaylist = Uri.Builder()
                                        .appendPath(ArtworkProvider.PLAY_ALL)
                                        .appendPath(ArtworkProvider.PLAYLIST)
                                        .appendPath("${ArtworkProvider.computeChecksum(trackList, true)}")
                                        .appendPath("${libraryItem.tracksCount}")
                                        .appendPath("${libraryItem.id}")
                                        .build()
                                item.setIconUri(ArtworkProvider.buildUri(playAllPlaylist))
                            } else {
                                item.setIconUri(DEFAULT_PLAYLIST_ICON)
                            }
                        }
                        else -> item.setIconUri(DEFAULT_TRACK_ICON)
                    }
                }
                /* Set Extras */
                val extras = when (libraryItem.itemType) {
                    MediaLibraryItem.TYPE_ARTIST, MediaLibraryItem.TYPE_GENRE -> getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    else -> Bundle()
                }
                if (groupTitle != null) extras.putString(EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT, groupTitle)
                item.setExtras(extras)
                /* Set Flags */
                val flags = when (libraryItem.itemType) {
                    MediaLibraryItem.TYPE_MEDIA, MediaLibraryItem.TYPE_PLAYLIST -> MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    else -> MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                }
                results.add(MediaBrowserCompat.MediaItem(item.build(), flags))
                if ((limitSize && results.size == MAX_HISTORY_SIZE) || results.size == MAX_RESULT_SIZE) break
            }
            artworkToUriCache.clear()
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

        private fun getPlayAllBuilder(res: Resources, mediaId: String, trackCount: Int, uri: Uri? = null): MediaDescriptionCompat.Builder {
            return MediaDescriptionCompat.Builder()
                    .setMediaId(mediaId)
                    .setTitle(res.getString(R.string.play_all))
                    .setSubtitle(res.getString(R.string.track_number, trackCount))
                    .setIconUri(if (uri != null) ArtworkProvider.buildUri(uri) else DEFAULT_PLAYALL_ICON)
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
