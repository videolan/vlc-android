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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.media.utils.MediaConstants
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.*
import org.videolan.vlc.ArtworkProvider
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.MediaComparators
import org.videolan.vlc.gui.helpers.MediaComparators.formatArticles
import org.videolan.vlc.isPathValid
import org.videolan.vlc.media.MediaUtils.getMediaAlbum
import org.videolan.vlc.media.MediaUtils.getMediaArtist
import org.videolan.vlc.media.MediaUtils.getMediaSubtitle
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.isSchemeStreaming

/**
 * The mediaId used in the media session browser is defined as an opaque string token which is left
 * up to the application developer to define. In practicality, mediaIds from multiple applications
 * may be combined into a single data structure, so we use a valid uri, and have have intentionally
 * prefixed it with a namespace. The value is stored as a string to avoid repeated type conversion;
 * however, it may be parsed by the uri class as needed. The uri starts with two forward slashes to
 * disambiguate the authority from the path, per RFC 3986, section 3.
 *
 * The mediaId structure is documented below for reference. The first (or second) letter of each
 * section is used in lieu of the entire word in order to shorten the id throughout the library.
 * The reduction of space consumed by the mediaId enables an increased number of records per page.
 *
 * Root node
 * //org.videolan.vlc/{r}oot[?{f}latten=1]
 * Root menu
 * //org.videolan.vlc/{r}oot/home
 * //org.videolan.vlc/{r}oot/playlist/<id>
 * //org.videolan.vlc/{r}oot/{l}ib
 * //org.videolan.vlc/{r}oot/stream
 * Home menu
 * //org.videolan.vlc/{r}oot/home/shuffle_all
 * //org.videolan.vlc/{r}oot/home/last_added[?{i}ndex=<track num>]
 * //org.videolan.vlc/{r}oot/home/history[?{i}ndex=<track num>]
 * Library menu
 * //org.videolan.vlc/{r}oot/{l}ib/a{r}tist[?{p}age=<page num>]
 * //org.videolan.vlc/{r}oot/{l}ib/a{r}tist/<id>
 * //org.videolan.vlc/{r}oot/{l}ib/a{l}bum[?{p}age=<page num>]
 * //org.videolan.vlc/{r}oot/{l}ib/a{l}bum/<id>
 * //org.videolan.vlc/{r}oot/{l}ib/{t}rack[?{p}age=<page num>]
 * //org.videolan.vlc/{r}oot/{l}ib/{t}rack[?{p}age=<page num>][&{i}ndex=<track num>]
 * //org.videolan.vlc/{r}oot/{l}ib/{g}enre[?{p}age=<page num>]
 * //org.videolan.vlc/{r}oot/{l}ib/{g}enre/<id>
 * Media
 * //org.videolan.vlc/media/<id>
 * Errors
 * //org.videolan.vlc/error/media
 * //org.videolan.vlc/error/playlist
 * Search
 * //org.videolan.vlc/search?query=<query>
 */
class MediaSessionBrowser {

    companion object {
        private const val TAG = "VLC/MediaSessionBrowser"
        private val instance = MediaSessionBrowser()

        // Root item
        // MediaIds are all strings. Maintain in uri parsable format.
        const val ID_ROOT = "//${BuildConfig.APP_ID}/r"
        const val ID_ROOT_NO_TABS = "$ID_ROOT?f=1"
        const val ID_MEDIA = "$ID_ROOT/media"
        const val ID_SEARCH = "$ID_ROOT/search"
        const val ID_SUGGESTED = "$ID_ROOT/suggested"
        const val ID_NO_MEDIA = "$ID_ROOT/error/media"
        const val ID_NO_PLAYLIST = "$ID_ROOT/error/playlist"

        // Top-level menu
        const val ID_HOME = "$ID_ROOT/home"
        const val ID_PLAYLIST = "$ID_ROOT/playlist"
        private const val ID_LIBRARY = "$ID_ROOT/l"
        const val ID_STREAM = "$ID_ROOT/stream"

        // Home menu
        const val ID_SHUFFLE_ALL = "$ID_HOME/shuffle_all"
        const val ID_LAST_ADDED = "$ID_HOME/last_added"
        const val ID_HISTORY = "$ID_HOME/history"

        // Library menu
        const val ID_ARTIST = "$ID_LIBRARY/r"
        const val ID_ALBUM = "$ID_LIBRARY/l"
        const val ID_TRACK = "$ID_LIBRARY/t"
        const val ID_GENRE = "$ID_LIBRARY/g"
        const val MAX_HISTORY_SIZE = 100
        const val MAX_COVER_ART_ITEMS = 50
        private const val MAX_SUGGESTED_SIZE = 15
        const val MAX_RESULT_SIZE = 800

        // Extensions management

        @WorkerThread
        fun browse(context: Context, parentId: String, isShuffling: Boolean, rootHints: Bundle? = null): List<MediaBrowserCompat.MediaItem> {
            val results: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
            var list: Array<out MediaLibraryItem>? = null
            var limitSize = false
            val res = context.resources
            //Extensions
            val ml = Medialibrary.getInstance()
            val parentIdUri = parentId.toUri()
            val page = parentIdUri.getQueryParameter("p")
            val pageOffset = page?.toInt()?.times(MAX_RESULT_SIZE) ?: 0
            val isAndroidAuto = rootHints?.containsKey(EXTRA_BROWSER_ICON_SIZE) ?: false
            val flatten = parentIdUri.getBooleanQueryParameter("f", false)
            when (parentIdUri.removeQuery().toString()) {
                ID_ROOT -> {
                    //Home
                    if (flatten) browse(context, ID_HOME, isShuffling)?.let { results.addAll(it) }
                    else {
                        val homeMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_HOME)
                                .setTitle(res.getString(R.string.auto_home))
                                .setIconUri(res.getResourceUri(R.drawable.ic_auto_home))
                                .setExtras(getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(homeMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    }
                    //Playlists
                    val playlistMediaDesc = MediaDescriptionCompat.Builder()
                            .setMediaId(ID_PLAYLIST)
                            .setTitle(res.getString(R.string.playlists))
                            .setIconUri(res.getResourceUri(R.drawable.ic_auto_playlist))
                            .setExtras(getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                            .build()
                    results.add(MediaBrowserCompat.MediaItem(playlistMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    //My library
                    if (flatten) browse(context, ID_LIBRARY, isShuffling)?.let { results.addAll(it) }
                    else {
                        val libraryMediaDesc = MediaDescriptionCompat.Builder()
                                .setMediaId(ID_LIBRARY)
                                .setTitle(res.getString(R.string.auto_my_library))
                                .setIconUri(res.getResourceUri(R.drawable.ic_auto_my_library))
                                .setExtras(getContentStyle(CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE))
                                .build()
                        results.add(MediaBrowserCompat.MediaItem(libraryMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    }
                    //Streams
                    val streamsMediaDesc = MediaDescriptionCompat.Builder()
                            .setMediaId(ID_STREAM)
                            .setTitle(res.getString(R.string.streams))
                            .setIconUri(res.getResourceUri(R.drawable.ic_auto_stream))
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
                    val shuffleAllMediaDesc = getPlayAllBuilder(context, ID_SHUFFLE_ALL, R.string.shuffle_all_title, audioCount, shuffleAllPath).build()
                    results.add(MediaBrowserCompat.MediaItem(shuffleAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                    /* Last Added */
                    val recentAudio = ml.getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, false, false, MAX_HISTORY_SIZE, 0)
                    val recentAudioSize = recentAudio.size
                    val lastAddedPath = if (recentAudioSize > 0) {
                        Uri.Builder()
                                .appendPath(ArtworkProvider.LAST_ADDED)
                                .appendPath("${ArtworkProvider.computeChecksum(recentAudio.toList())}")
                                .appendPath("$recentAudioSize")
                                .build()
                    } else null
                    val lastAddedMediaDesc = getPlayAllBuilder(context, ID_LAST_ADDED, R.string.auto_last_added_media, recentAudioSize, lastAddedPath).build()
                    results.add(MediaBrowserCompat.MediaItem(lastAddedMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    /* History */
                    if (Settings.getInstance(context).getBoolean(PLAYBACK_HISTORY, true)) {
                        val lastMediaPlayed = ml.history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList()?.filter { isMediaAudio(it) }
                        if (!lastMediaPlayed.isNullOrEmpty()) {
                            val lastMediaSize = lastMediaPlayed.size.coerceAtMost(MAX_HISTORY_SIZE)
                            val historyPath = Uri.Builder()
                                    .appendPath(ArtworkProvider.HISTORY)
                                    .appendPath("${ArtworkProvider.computeChecksum(lastMediaPlayed)}")
                                    .appendPath("$lastMediaSize")
                                    .build()
                            val historyMediaDesc = getPlayAllBuilder(context, ID_HISTORY, R.string.history, lastMediaSize, historyPath).build()
                            results.add(MediaBrowserCompat.MediaItem(historyMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                        }
                    }
                    return results
                }
                ID_LIBRARY -> {
                    //Artists
                    val artistsShowAll = Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false)
                    val artistsCount = ml.getArtistsCount(artistsShowAll)
                    val artistsMediaDesc = MediaDescriptionCompat.Builder()
                            .setMediaId(ID_ARTIST)
                            .setTitle(res.getString(R.string.artists))
                            .setIconUri(res.getResourceUri(R.drawable.ic_auto_artist))
                            .setExtras(if (artistsCount > MAX_RESULT_SIZE) getContentStyle(CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE) else null)
                            .build()
                    results.add(MediaBrowserCompat.MediaItem(artistsMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    //Albums
                    val albumsMediaDesc = MediaDescriptionCompat.Builder()
                            .setMediaId(ID_ALBUM)
                            .setTitle(res.getString(R.string.albums))
                            .setIconUri(res.getResourceUri(R.drawable.ic_auto_album))
                            .setExtras(getContentStyle(if (ml.albumsCount > MAX_RESULT_SIZE) CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE else CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                            .build()
                    results.add(MediaBrowserCompat.MediaItem(albumsMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    //Tracks
                    val tracksMediaDesc = MediaDescriptionCompat.Builder()
                            .setMediaId(ID_TRACK)
                            .setTitle(res.getString(R.string.tracks))
                            .setIconUri(res.getResourceUri(R.drawable.ic_auto_audio))
                            .setExtras(getContentStyle(if (ml.audioCount > MAX_RESULT_SIZE) CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE else CONTENT_STYLE_LIST_ITEM_HINT_VALUE))
                            .build()
                    results.add(MediaBrowserCompat.MediaItem(tracksMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    //Genres
                    val genresMediaDesc = MediaDescriptionCompat.Builder()
                            .setMediaId(ID_GENRE)
                            .setTitle(res.getString(R.string.genres))
                            .setIconUri(res.getResourceUri(R.drawable.ic_auto_genre))
                            .setExtras(getContentStyle(if (ml.genresCount > MAX_RESULT_SIZE) CONTENT_STYLE_CATEGORY_ITEM_HINT_VALUE else CONTENT_STYLE_LIST_ITEM_HINT_VALUE))
                            .build()
                    results.add(MediaBrowserCompat.MediaItem(genresMediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                    return results
                }
                ID_ARTIST -> {
                    val artistsShowAll = Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false)
                    val artists = ml.getArtists(artistsShowAll, Medialibrary.SORT_ALPHA, false, false, false)
                    artists.sortWith(MediaComparators.ANDROID_AUTO)
                    if (page == null && artists.size > MAX_RESULT_SIZE)
                        return paginateLibrary(artists, parentIdUri, res.getResourceUri(R.drawable.ic_auto_artist))
                    list = artists.copyOfRange(pageOffset.coerceAtMost(artists.size), (pageOffset + MAX_RESULT_SIZE).coerceAtMost(artists.size))
                }
                ID_ALBUM -> {
                    val albums = ml.getAlbums(Medialibrary.SORT_ALPHA, false, false, false)
                    albums.sortWith(MediaComparators.ANDROID_AUTO)
                    if (page == null && albums.size > MAX_RESULT_SIZE)
                        return paginateLibrary(albums, parentIdUri, res.getResourceUri(R.drawable.ic_auto_album), getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE))
                    list = albums.copyOfRange(pageOffset.coerceAtMost(albums.size), (pageOffset + MAX_RESULT_SIZE).coerceAtMost(albums.size))
                }
                ID_TRACK -> {
                    val tracks = ml.getAudio(Medialibrary.SORT_ALPHA, false, false, false)
                    tracks.sortWith(MediaComparators.ANDROID_AUTO)
                    if (page == null && tracks.size > MAX_RESULT_SIZE)
                        return paginateLibrary(tracks, parentIdUri, res.getResourceUri(R.drawable.ic_auto_audio))
                    list = tracks.copyOfRange(pageOffset.coerceAtMost(tracks.size), (pageOffset + MAX_RESULT_SIZE).coerceAtMost(tracks.size))
                }
                ID_GENRE -> {
                    val genres = ml.getGenres(Medialibrary.SORT_ALPHA, false, false, false)
                    genres.sortWith(MediaComparators.ANDROID_AUTO)
                    if (page == null && genres.size > MAX_RESULT_SIZE)
                        return paginateLibrary(genres, parentIdUri, res.getResourceUri(R.drawable.ic_auto_genre))
                    list = genres.copyOfRange(pageOffset.coerceAtMost(genres.size), (pageOffset + MAX_RESULT_SIZE).coerceAtMost(genres.size))
                }
                ID_PLAYLIST -> {
                        list = ml.getPlaylists(Playlist.Type.Audio, false)
                    list.sortWith(MediaComparators.ANDROID_AUTO)
                }
                ID_STREAM -> {
                    list = ml.history(Medialibrary.HISTORY_TYPE_NETWORK)
                    list.sortWith(MediaComparators.ANDROID_AUTO)
                }
                ID_LAST_ADDED -> {
                    limitSize = true
                    list = ml.getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, false, false, MAX_HISTORY_SIZE, 0)
                }
                ID_HISTORY -> {
                    limitSize = true
                    list = ml.history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList()?.filter { isMediaAudio(it) }?.toTypedArray()
                }
                ID_SUGGESTED -> return buildSuggestions(context, parentId, ml)
                else -> {
                    val id = ContentUris.parseId(parentIdUri)
                    when (parentIdUri.retrieveParent().toString()) {
                        ID_ALBUM -> list = ml.getAlbum(id).tracks
                        ID_ARTIST -> {
                            val artist = ml.getArtist(id)
                            list = artist.albums
                            if (list != null && list.size > 1) {
                                val hasArtwork = list.any { !it.artworkMrl.isNullOrEmpty() && isPathValid(it.artworkMrl) }
                                val shuffleMode = isShuffling && artist.tracksCount > 2
                                val playAllPath = if (hasArtwork) {
                                    Uri.Builder()
                                            .appendPath(ArtworkProvider.PLAY_ALL)
                                            .appendPath(ArtworkProvider.ARTIST)
                                            .appendPath("${artist.tracksCount}")
                                            .appendPath("$id")
                                            .appendQueryParameter(ArtworkProvider.SHUFFLE, "${shuffleMode.toInt()}")
                                            .build()
                                } else null
                                val title = if (shuffleMode) R.string.shuffle_all_title else R.string.play_all
                                val playAllMediaDesc = getPlayAllBuilder(context, parentId, title, artist.tracksCount, playAllPath).build()
                                results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                            }
                        }
                        ID_GENRE -> {
                            val genre = ml.getGenre(id)
                            list = genre.albums
                            val tracksCount = list.sumOf { it.tracksCount }
                            if (list != null && list.size > 1) {
                                val shuffleMode = isShuffling && tracksCount > 2
                                val playAllPath = Uri.Builder()
                                        .appendPath(ArtworkProvider.PLAY_ALL)
                                        .appendPath(ArtworkProvider.GENRE)
                                        .appendPath("$tracksCount")
                                        .appendPath("$id")
                                        .appendQueryParameter(ArtworkProvider.SHUFFLE, "${shuffleMode.toInt()}")
                                        .build()
                                val title = if (shuffleMode) R.string.shuffle_all_title else R.string.play_all
                                val playAllMediaDesc = getPlayAllBuilder(context, parentId, title, tracksCount, playAllPath).build()
                                results.add(MediaBrowserCompat.MediaItem(playAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
                            }
                        }
                    }
                }
            }
            results.addAll(buildMediaItems(context, parentId, list, null, limitSize, androidAuto = isAndroidAuto))
            if (results.isEmpty()) {
                val emptyMediaDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(ID_NO_MEDIA)
                        .setIconUri(res.getResourceUri(R.drawable.ic_auto_nothumb))
                        .setTitle(res.getString(R.string.search_no_result))
                        .setExtras(Bundle().apply {
                            putInt(EXTRA_CONTENT_STYLE_SINGLE_ITEM, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
                        })
                when (parentId) {
                    ID_ARTIST -> emptyMediaDesc.setIconUri(res.getResourceUri(R.drawable.ic_auto_artist_unknown))
                    ID_ALBUM -> emptyMediaDesc.setIconUri(res.getResourceUri(R.drawable.ic_auto_album_unknown))
                    ID_GENRE -> emptyMediaDesc.setIconUri(null)
                    ID_PLAYLIST -> {
                        emptyMediaDesc.setMediaId(ID_NO_PLAYLIST)
                        emptyMediaDesc.setTitle(res.getString(R.string.noplaylist))
                    }
                    ID_STREAM -> emptyMediaDesc.setIconUri(res.getResourceUri(R.drawable.ic_auto_stream_unknown))
                }
                results.add(MediaBrowserCompat.MediaItem(emptyMediaDesc.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            return results
        }

        /**
         * The search method is passed a simple query string absent metadata indicating
         * the user's intent to load a playlist, genre, artist, album, or song. This is slightly different
         * than PlaybackService.onPlayFromSearch (which is also invoked by voice search) and allows
         * the user to navigate to other content via on-screen menus.
         */
        @WorkerThread
        fun search(context: Context, query: String, rootHints: Bundle?): List<MediaBrowserCompat.MediaItem> {
            val res = context.resources
            val results: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
            val isAndroidAuto = rootHints?.containsKey(EXTRA_BROWSER_ICON_SIZE) ?: false
            val searchAggregate = Medialibrary.getInstance().search(query, false, false)
            val searchMediaId = ID_SEARCH.toUri().buildUpon().appendQueryParameter("query", query).toString()
            results.addAll(buildMediaItems(context, ID_PLAYLIST, searchAggregate.playlists, res.getString(R.string.playlists)))
            results.addAll(buildMediaItems(context, ID_GENRE, searchAggregate.genres, res.getString(R.string.genres)))
            results.addAll(buildMediaItems(context, ID_ARTIST, searchAggregate.artists, res.getString(R.string.artists)))
            results.addAll(buildMediaItems(context, ID_ALBUM, searchAggregate.albums, res.getString(R.string.albums)))
            results.addAll(buildMediaItems(context, searchMediaId, searchAggregate.tracks, res.getString(R.string.tracks), androidAuto = isAndroidAuto))
            if (results.isEmpty()) {
                val emptyMediaDesc = MediaDescriptionCompat.Builder()
                        .setMediaId(ID_NO_MEDIA)
                        .setIconUri(res.getResourceUri(R.drawable.ic_auto_nothumb))
                        .setTitle(res.getString(R.string.search_no_result))
                        .build()
                results.add(MediaBrowserCompat.MediaItem(emptyMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
            return results
        }

        /**
         * This function constructs a list of suggestions to display in driving mode. A max of fifteen
         * items are returned to the caller. The first item is always shuffle all.
         */
        private fun buildSuggestions(context: Context, parentId: String, ml: Medialibrary): List<MediaBrowserCompat.MediaItem> {
            val audioCount = ml.audioCount
            if (audioCount == 0) return emptyList()
            /* Obtain the most recently played albums from history */
            val albumNames = mutableSetOf<String>()
            if (Settings.getInstance(context).getBoolean(PLAYBACK_HISTORY, true)) {
                val lastMediaPlayed = ml.history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList()?.filter { isMediaAudio(it) }
                if (!lastMediaPlayed.isNullOrEmpty()) for (mw in lastMediaPlayed) mw.album?.let { albumNames.add(it) }
            }
            /* Pad the end with recently added albums. We may end up dropping a few due to absent artwork. */
            val recentAudio = ml.getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, false, false, MAX_HISTORY_SIZE, 0)
            if (!recentAudio.isNullOrEmpty()) for (mw in recentAudio) mw.album?.let { albumNames.add(it) }
            /* Build the list of media items */
            val results: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
            val shuffleAllPath = Uri.Builder()
                    .appendPath(ArtworkProvider.SHUFFLE_ALL)
                    .appendPath(ArtworkProvider.computeExpiration())
                    .appendPath("$audioCount")
                    .build()
            val shuffleAllMediaDesc = getPlayAllBuilder(context, ID_SHUFFLE_ALL, R.string.shuffle_all_title, audioCount, shuffleAllPath).build()
            results.add(MediaBrowserCompat.MediaItem(shuffleAllMediaDesc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            /* Query albums by name */
            val albums = mutableSetOf<Album>()
            for (albumName in albumNames) ml.searchAlbum(albumName)?.let { albums.addAll(it.toList()) }
            results.addAll(buildMediaItems(context, parentId, albums.toTypedArray(), null, limitSize = false, suggestionMode = true)
                    .take((MAX_SUGGESTED_SIZE - results.size).coerceAtLeast(0)))
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
        private fun buildMediaItems(context: Context, parentId: String, list: Array<out MediaLibraryItem>?, groupTitle: String?,
                                    limitSize: Boolean = false, suggestionMode: Boolean = false, androidAuto: Boolean = false): List<MediaBrowserCompat.MediaItem> {
            if (list.isNullOrEmpty()) return emptyList()
            val res = context.resources
            val artworkToUriCache = HashMap<String, Uri>()
            val results: ArrayList<MediaBrowserCompat.MediaItem> = ArrayList()
            results.ensureCapacity(list.size.coerceAtMost(MAX_RESULT_SIZE))
            /* Iterate over list */
            val parentIdUri = parentId.toUri()
            for ((index, libraryItem) in list.withIndex()) {
                if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA
                        && ((libraryItem as MediaWrapper).type == MediaWrapper.TYPE_STREAM || isSchemeStreaming(libraryItem.uri.scheme))) {
                    libraryItem.type = MediaWrapper.TYPE_STREAM
                } else if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type != MediaWrapper.TYPE_AUDIO)
                    continue

                /* Media ID */
                var mediaId = when (libraryItem.itemType) {
                    MediaLibraryItem.TYPE_MEDIA -> parentIdUri.buildUpon().appendQueryParameter("i", "$index").toString()
                    else -> generateMediaId(libraryItem)
                }

                /* Subtitle */
                val subtitle = when (libraryItem.itemType) {
                    MediaLibraryItem.TYPE_MEDIA -> {
                        val media = libraryItem as MediaWrapper
                        when {
                            media.type == MediaWrapper.TYPE_STREAM -> media.uri.toString()
                            parentId.startsWith(ID_ALBUM) -> getMediaSubtitle(media)
                            else -> TextUtils.separatedString('-', getMediaArtist(context, media), getMediaAlbum(context, media))
                        }
                    }
                    MediaLibraryItem.TYPE_PLAYLIST -> res.getString(R.string.track_number, libraryItem.tracksCount)
                    MediaLibraryItem.TYPE_ARTIST -> {
                        val albumsCount = Medialibrary.getInstance().getArtist(libraryItem.id).albumsCount
                        res.getQuantityString(R.plurals.albums_quantity, albumsCount, albumsCount)
                    }
                    MediaLibraryItem.TYPE_GENRE -> {
                        val albumsCount = Medialibrary.getInstance().getGenre(libraryItem.id).albumsCount
                        res.getQuantityString(R.plurals.albums_quantity, albumsCount, albumsCount)
                    }
                    MediaLibraryItem.TYPE_ALBUM -> {
                        if (parentId.startsWith(ID_ARTIST))
                            res.getString(R.string.track_number, libraryItem.tracksCount)
                        else
                            libraryItem.description
                    }
                    else -> libraryItem.description
                }

                /* Extras */
                val extras = when (libraryItem.itemType) {
                    MediaLibraryItem.TYPE_ARTIST, MediaLibraryItem.TYPE_GENRE -> getContentStyle(CONTENT_STYLE_GRID_ITEM_HINT_VALUE, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
                    else -> Bundle()
                }
                if (groupTitle != null) extras.putString(EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT, groupTitle)

                if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).isPodcast) {
                    var pct = libraryItem.position.toDouble()

                    pct = when {
                        pct >= 0.95 -> 1.0
                        pct <= 0.00 && libraryItem.playCount > 0 -> 1.0
                        pct <= 0.00 -> 0.0
                        else -> pct
                    }.also { extras.putDouble(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE, it) }

                    when (pct) {
                        1.0 -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                        0.0 -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                        else -> MediaConstants.DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    }.also { extras.putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS, it) }

                    // Only Android Auto passes extras to onPlayFromMediaId
                    if (androidAuto) {
                        // Relative id stored in the extras bundle to support play from here
                        extras.putString(EXTRA_RELATIVE_MEDIA_ID, mediaId)
                        // Set mediaId to the library item id to enable completion bar updates
                        mediaId = generateMediaId(libraryItem)
                    }
                }

                /* Icon */
                val iconUri = if (libraryItem.itemType != MediaLibraryItem.TYPE_PLAYLIST && !libraryItem.artworkMrl.isNullOrEmpty() && isPathValid(libraryItem.artworkMrl)) {
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
                    artworkToUriCache.getOrPut(libraryItem.artworkMrl) { ArtworkProvider.buildUri(context, iconUri.build()) }
                } else if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type == MediaWrapper.TYPE_STREAM)
                    res.getResourceUri(R.drawable.ic_auto_stream_unknown)
                else {
                    when (libraryItem.itemType) {
                        MediaLibraryItem.TYPE_ARTIST -> res.getResourceUri(R.drawable.ic_auto_artist_unknown)
                        MediaLibraryItem.TYPE_ALBUM -> res.getResourceUri(R.drawable.ic_auto_album_unknown)
                        MediaLibraryItem.TYPE_GENRE -> null
                        MediaLibraryItem.TYPE_PLAYLIST -> {
                            val trackList = libraryItem.tracks.toList()
                            val hasArtwork = trackList.any { (ThumbnailsProvider.isMediaVideo(it) || (!it.artworkMrl.isNullOrEmpty() && isPathValid(it.artworkMrl))) }
                            if (!hasArtwork) res.getResourceUri(R.drawable.ic_auto_playlist_unknown) else {
                                val playAllPlaylist = Uri.Builder()
                                        .appendPath(ArtworkProvider.PLAY_ALL)
                                        .appendPath(ArtworkProvider.PLAYLIST)
                                        .appendPath("${ArtworkProvider.computeChecksum(trackList, true)}")
                                        .appendPath("${libraryItem.tracksCount}")
                                        .appendPath("${libraryItem.id}")
                                        .build()
                                ArtworkProvider.buildUri(context, playAllPlaylist)
                            }
                        }
                        else -> res.getResourceUri(R.drawable.ic_auto_nothumb)
                    }
                }

                /**
                 * Media Description
                 * The media URI not used in the browser and takes up a significant number of bytes.
                 */
                val description = MediaDescriptionCompat.Builder()
                        .setTitle(libraryItem.title)
                        .setSubtitle(subtitle)
                        .setIconUri(iconUri)
                        .setMediaId(mediaId)
                        .setExtras(extras)
                        .build()

                /* Set Flags */
                var flags = when (libraryItem.itemType) {
                    MediaLibraryItem.TYPE_MEDIA, MediaLibraryItem.TYPE_PLAYLIST -> MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    else -> MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                }
                /* Suggestions must be playable. Skip entries without artwork. */
                if (suggestionMode) {
                    flags = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    if (iconUri == null || iconUri.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) continue
                }
                results.add(MediaBrowserCompat.MediaItem(description, flags))
                if ((limitSize && results.size == MAX_HISTORY_SIZE) || results.size == MAX_RESULT_SIZE) break
            }
            artworkToUriCache.clear()
            return results
        }

        fun getContentStyle(browsableHint: Int = CONTENT_STYLE_LIST_ITEM_HINT_VALUE, playableHint: Int = CONTENT_STYLE_LIST_ITEM_HINT_VALUE): Bundle {
            return Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_HINT, browsableHint)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, playableHint)
            }
        }

        fun generateMediaId(libraryItem: MediaLibraryItem): String {
            val prefix = when (libraryItem.itemType) {
                MediaLibraryItem.TYPE_ALBUM -> ID_ALBUM
                MediaLibraryItem.TYPE_ARTIST -> ID_ARTIST
                MediaLibraryItem.TYPE_GENRE -> ID_GENRE
                MediaLibraryItem.TYPE_PLAYLIST -> ID_PLAYLIST
                else -> ID_MEDIA
            }
            return "${prefix}/${libraryItem.id}"
        }

        fun isMediaAudio(libraryItem: MediaLibraryItem): Boolean {
            return libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && (libraryItem as MediaWrapper).type == MediaWrapper.TYPE_AUDIO
        }

        /**
         * At present Android Auto has no ability to directly handle paging so we must limit the size of the result
         * to avoid returning a parcel which exceeds the size limitations. We break the results into another
         * layer of browsable drill-downs labeled "start - finish" for each entry type.
         */
        private fun paginateLibrary(mediaList: Array<out MediaLibraryItem>, parentIdUri: Uri, iconUri: Uri, extras: Bundle? = null): List<MediaBrowserCompat.MediaItem> {
            val results: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
            /* Build menu items per group */
            for (page in 0..(mediaList.size / MAX_RESULT_SIZE)) {
                val offset = (page * MAX_RESULT_SIZE)
                val lastOffset = (offset + MAX_RESULT_SIZE - 1).coerceAtMost(mediaList.size - 1)
                if (offset >= lastOffset) break
                val mediaDesc = MediaDescriptionCompat.Builder()
                        .setTitle(buildRangeLabel(mediaList[offset].title, mediaList[lastOffset].title))
                        .setMediaId(parentIdUri.buildUpon().appendQueryParameter("p", "$page").toString())
                        .setIconUri(iconUri)
                        .setExtras(extras)
                        .build()
                results.add(MediaBrowserCompat.MediaItem(mediaDesc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE))
                if (results.size == MAX_RESULT_SIZE) break
            }
            return results
        }

        private fun buildRangeLabel(firstTitle: String, lastTitle: String): String {
            val beginTitle = formatArticles(firstTitle, true)
            val endTitle = formatArticles(lastTitle, true)
            var beginTitleSize = beginTitle.length
            var endTitleSize = endTitle.length
            val halfLabelSize = 10
            val maxLabelSize = 20
            if (beginTitleSize > halfLabelSize && endTitleSize > halfLabelSize) {
                beginTitleSize = halfLabelSize
                endTitleSize = halfLabelSize
            } else if (beginTitleSize > halfLabelSize) {
                beginTitleSize = (maxLabelSize - endTitleSize).coerceAtMost(beginTitleSize)
            } else if (endTitleSize > halfLabelSize) {
                endTitleSize = (maxLabelSize - beginTitleSize).coerceAtMost(endTitleSize)
            }
            return TextUtils.separatedString(beginTitle.abbreviate(beginTitleSize).markBidi(), endTitle.abbreviate(endTitleSize).markBidi())
        }

        private fun getPlayAllBuilder(ctx: Context, mediaId: String, @StringRes title: Int, trackCount: Int, uri: Uri? = null): MediaDescriptionCompat.Builder {
            val res = ctx.resources
            return MediaDescriptionCompat.Builder()
                    .setMediaId(mediaId)
                    .setTitle(res.getString(title))
                    .setSubtitle(res.getString(R.string.track_number, trackCount))
                    .setIconUri(if (uri != null) ArtworkProvider.buildUri(ctx, uri) else res.getResourceUri(R.drawable.ic_auto_playall))
        }
    }
}
