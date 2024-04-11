/*****************************************************************************
 * ArtworkProvider.kt
 * Copyright © 2011-2021 VLC authors and VideoLAN
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
 */

package org.videolan.vlc

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.VLCInstance
import org.videolan.resources.util.getFromMl
import org.videolan.tools.removeFileScheme
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.media.MediaSessionBrowser
import org.videolan.vlc.util.AccessControl
import org.videolan.vlc.util.ThumbnailsProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.max

private const val TAG = "VLC/ArtworkProvider"
private const val MIME_TYPE_IMAGE_WEBP = "image/webp"
private const val ENABLE_TRACING = false

/**
 * This content provider enables callers to retrieve cover artwork cataloged by the VLC Medialibrary.
 *
 * The URI structure was designed interface with Android Auto, which utilizes Glide to cache artwork.
 * Passing a URI is the preferred approach instead of returning a Bitmap embedded within the menu,
 * as the URI is used as the key for the Glide cache. Bitmaps passed directly lack metadata to
 * properly re-retrieve from the cache, often resulting in the display of incorrect imagery.
 *
 * Glide debugging can be enabled via:
 *   adb shell setprop log.tag.Engine VERBOSE
 *   adb shell setprop log.tag.DecodeJob VERBOSE
 *
 * URI Structure
 *
 * The <id> value at the *end* of the URI is used to lookup data within the Medialibrary. Additional
 * information, such as track count and checksum, have been added within the path to trigger
 * expiration of images when the Medialibrary is updated. This was designed avoid using time-based
 * triggers for *all* imagery which results in noticeable flicker and load delays.
 *
 * Medialibrary content:
 * content://org.videolan.vlc.artwork/album/<track count>/<id>
 * content://org.videolan.vlc.artwork/artist/<track count>/<id>
 * content://org.videolan.vlc.artwork/media/<last modified>/<id>
 * content://org.videolan.vlc.artwork/play_all/artist/<track count>/<id>
 * content://org.videolan.vlc.artwork/play_all/genre/<track count>/<id>
 * content://org.videolan.vlc.artwork/play_all/playlist/<CRC32 checksum>/<track count>/<id>
 * content://org.videolan.vlc.artwork/shuffle_all/<half day expiration time>/<track count>
 * content://org.videolan.vlc.artwork/history/<XOR checksum>/<track count>
 * content://org.videolan.vlc.artwork/last_added/<XOR checksum>/<track count>
 *
 * Non-Medialibrary remote cache:
 * content://org.videolan.vlc.artwork/remote?path=<encoded uri>
 */
class ArtworkProvider : ContentProvider() {

    private lateinit var ctx: Context

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val callingUid = Binder.getCallingUid()
        AccessControl.logCaller(callingUid)
        val uriSegments = uri.pathSegments
        if (uriSegments.isEmpty()) throw FileNotFoundException("Path is empty")
        return try {
            if (ENABLE_TRACING) {
                val callingPackage = AccessControl.getCallingPackage(ctx, callingUid)
                Log.d(TAG, "openFile() Time: ${getTimestamp()} URI: $uri " +
                        "Thread: ${Thread.currentThread().name} Caller: $callingPackage")
            }
            val bigVariant = uri.getQueryParameter(BIG_VARIANT)  == "1"
            val remoteAccess = uri.getQueryParameter(REMOTE_ACCESS)  == "1"
            //retrieve thumbnails.
            when (uriSegments[0]) {
                HISTORY -> getPFDFromByteArray(getHistory(ctx))
                LAST_ADDED -> getPFDFromByteArray(getLastAdded(ctx))
                SHUFFLE_ALL -> getPFDFromByteArray(getShuffleAll(ctx))
                VIDEO -> if (remoteAccess)
                    getMediaImage(ctx, ContentUris.parseId(uri), false, fallbackIcon =  if (bigVariant) R.drawable.ic_remote_video_unknown_big else R.drawable.ic_remote_video_unknown, isLarge = true)
                else
                    getMediaImage(ctx, ContentUris.parseId(uri), false)
                MEDIA -> getMediaImage(ctx, ContentUris.parseId(uri), fallbackIcon = if (remoteAccess) if (bigVariant) R.drawable.ic_remote_song_unknown_big else R.drawable.ic_remote_song_unknown else null)
                ALBUM -> getCategoryImage(ctx, ALBUM, ContentUris.parseId(uri), remoteAccess, bigVariant)
                ARTIST -> getCategoryImage(ctx, ARTIST, ContentUris.parseId(uri), remoteAccess, bigVariant)
                REMOTE -> getRemoteImage(ctx, uri.getQueryParameter(PATH))
                GENRE -> if (remoteAccess) getGenreImage(ctx, ContentUris.parseId(uri), fallbackIcon = if (bigVariant) R.drawable.ic_remote_genre_unknown_big else R.drawable.ic_remote_genre_unknown) else getGenreImage(ctx, ContentUris.parseId(uri))
                PLAYLIST -> if (remoteAccess) getPlaylistImage(ctx, ContentUris.parseId(uri), fallbackIcon = if (bigVariant) R.drawable.ic_remote_playlist_unknown_big else R.drawable.ic_remote_playlist_unknown) else getPlaylistImage(ctx, ContentUris.parseId(uri))
                PLAY_ALL -> getPlayAllImage(ctx, uriSegments[1], ContentUris.parseId(uri),
                        uri.getBooleanQueryParameter(SHUFFLE, false))
                else -> throw FileNotFoundException("Uri is not supported: $uri")
            }
        } catch (e: Exception) {
            throw FileNotFoundException(e.message)
        }
    }

    /**
     * Load an image from a remote path and cache it in memory. When a URI is passed to the OS for
     * cover art it is requested from several different places, such as Android Auto, bluetooth, etc.
     * Rather than going over the network repeatedly we request it once and return it internally.
     */
    private fun getRemoteImage(ctx: Context, path: String?): ParcelFileDescriptor? {
        val width = 512
        if (path == null) return null
        val image = getOrPutImage(path) {
            runBlocking(Dispatchers.IO) {
                var bitmap = AudioUtil.readCoverBitmap(path, width)
                if (bitmap != null) bitmap = padSquare(bitmap)
                if (bitmap == null) bitmap = ctx.getBitmapFromDrawable(R.drawable.ic_no_media, width, width)
                return@runBlocking BitmapUtil.encodeImage(bitmap, ENABLE_TRACING){
                    getTimestamp()
                }
            }
        }
        return getPFDFromByteArray(image)
    }

    /**
     * Return square artwork for use within the Android Auto album and artist listings.
     *
     * Non-square images are padded for uniformity with getMediaImage, although not technically
     * required for display solely within AA menus. This function does slightly differ from
     * getMediaImage in that results are not cached since AA is the only consumer and Glide within
     * AA performs caching.
     */
    private fun getCategoryImage(context: Context, category: String, id: Long, forRemote:Boolean = false, bigVariant:Boolean = true): ParcelFileDescriptor {
        val mw: MediaLibraryItem? = runBlocking(Dispatchers.IO) {
            when (category) {
                ALBUM -> context.getFromMl { getAlbum(id) }
                ARTIST -> context.getFromMl { getArtist(id) }
                else -> null
            }
        }
        mw?.let {
            if (!mw.artworkMrl.isNullOrEmpty()) {
                val filePath = Uri.decode(mw.artworkMrl).removeFileScheme()
                val file = File(filePath)
                if (file.exists()) return@getCategoryImage ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            runBlocking(Dispatchers.IO) {
                var bitmap = ThumbnailsProvider.obtainBitmap(mw, 256)
                if (bitmap != null) bitmap = padSquare(bitmap)
                return@runBlocking BitmapUtil.encodeImage(bitmap, ENABLE_TRACING) {
                    getTimestamp()
                }
            }?.let { return@getCategoryImage getPFDFromByteArray(it) }
        }
        val unknownIcon = when (category) {
            ALBUM -> if (forRemote) if (bigVariant) R.drawable.ic_remote_album_unknown_big else R.drawable.ic_remote_album_unknown else R.drawable.ic_auto_album_unknown
            ARTIST -> if (forRemote) if (bigVariant) R.drawable.ic_remote_artist_unknown_big else R.drawable.ic_remote_artist_unknown else R.drawable.ic_auto_artist_unknown
            else -> R.drawable.ic_auto_nothumb
        }
        return getPFDFromBitmap(context.getBitmapFromDrawable(unknownIcon))
    }

    /**
     * This function caches and returns square artwork for display within the Android Auto queue and
     * for the currently playing song. It may be called by half-a-dozen consumers concurrently.
     *
     * For the currently playing song, a URI to this function is always specified which either returns
     * actual cover art or a high resolution orange cone. When a track does *not* have cover art
     * available, the ic_auto_nothumb drawable is passed in the MediaSessionBrowser for the *queue*.
     * By sharing a common URI between the queue and currently playing song, we vastly cut down on
     * the number of calls to the content provider, as the cache sees a higher hit ratio. Users also
     * tend to browse the queue, which effectively pre-loads the artwork.
     *
     * If the artwork is already square on disk, we simply return the file (png, jpg)
     * If the artwork is not square, pad it square based on the max size of the largest dimension (webp)
     * If the artwork is null, or mediaId is 0, return a 512x512 orange cone (webp)
     *
     * @param ctx the context used to retrieve drawables
     * @param mediaId the media ID
     * @param padSquare if true, make the image sqaure
     * @param fallbackIcon if set, the fallback icon will use this resource
     * @param isLarge if true, a 16:9 ratio will be used (540x960)
     * @return
     */
    private fun getMediaImage(ctx: Context, mediaId: Long, padSquare:Boolean = true, @DrawableRes fallbackIcon: Int? = null, isLarge:Boolean = false): ParcelFileDescriptor {
        val width = 512
        val height169 = 540
        val width169 = 960
        val mw: MediaLibraryItem? = runBlocking(Dispatchers.IO) { ctx.getFromMl { getMedia(mediaId) } }
        mw?.let {
            if (!mw.artworkMrl.isNullOrEmpty()) {
                val filePath = Uri.decode(mw.artworkMrl).removeFileScheme()
                val file = File(filePath)
                if (file.canRead() && isImageWithinBounds(filePath)) return@getMediaImage ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        // Non-square cover art will have an artworkMrl, which will be padded, re-encoded, and cached.
        // Videos, tracks with no cover art, etc. use mediaId and will be processed per library item.
        var key = mw?.artworkMrl ?: "$mediaId"
        val nonTransparent = (Build.VERSION.SDK_INT == 33) && ("com.android.systemui" == callingPackage)
        if (nonTransparent) key += "_nonTransparent"
        if (fallbackIcon != null) key += fallbackIcon.toString()
        val image = getOrPutImage(key) {
            runBlocking(Dispatchers.IO) {
                var bitmap = if (mw != null) ThumbnailsProvider.obtainBitmap(mw, width) else null
                if (bitmap == null) bitmap = readEmbeddedArtwork(mw, width)
                if (padSquare && bitmap != null) bitmap = padSquare(bitmap)
                if (bitmap == null) {
                    bitmap = ctx.getBitmapFromDrawable(fallbackIcon
                            ?: R.drawable.ic_no_media, width, width)
                    if (isLarge && bitmap != null) {
                        val paint = Paint()
                        val bmp = Bitmap.createBitmap(width169, height169, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawBitmap(bitmap, 224F, 0F, paint)
                        bitmap = bmp
                    }
                }
                if (nonTransparent) bitmap = removeTransparency(bitmap)
                return@runBlocking BitmapUtil.encodeImage(bitmap, ENABLE_TRACING) {
                    getTimestamp()
                }
            }
        }
        return getPFDFromByteArray(image)
    }

    /**
     * Get the genre image and cache it. Without overlay
     *
     * @param ctx the context
     * @param id the genre id
     * @param fallbackIcon if set, the fallback icon will use this resource
     * @return a ParcelFileDescriptor containing the genre image
     */
    private fun getGenreImage(ctx: Context, id: Long, @DrawableRes fallbackIcon: Int? = null): ParcelFileDescriptor? {
        val bitmap = runBlocking(Dispatchers.IO) {
            val tracks = ctx.getFromMl { getGenre(id)?.albums?.flatMap { it.tracks.toList() } }
            val cover = tracks?.let {

                ThumbnailsProvider.getPlaylistOrGenreImage("genre:${id}_256", tracks, 256)
            }
            return@runBlocking when {
                cover != null -> cover
                else -> ctx.getBitmapFromDrawable(fallbackIcon ?: R.drawable.ic_auto_genre)
            }
        }
        return getPFDFromBitmap(bitmap)
    }

    /**
     * Get the playlist image and cache it. Without overlay
     *
     * @param ctx the context
     * @param id the playlist id
     * @param fallbackIcon if set, the fallback icon will use this resource
     * @return a ParcelFileDescriptor containing the playlist image
     */
    private fun getPlaylistImage(ctx: Context, id: Long, @DrawableRes fallbackIcon: Int? = null): ParcelFileDescriptor? {
        val bitmap = runBlocking(Dispatchers.IO) {
            val tracks = ctx.getFromMl { getPlaylist(id, true, false)?.tracks?.toList() }
            val cover = tracks?.let {

                ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${id}_256", tracks, 256)
            }
            return@runBlocking when {
                cover != null -> cover
                else -> ctx.getBitmapFromDrawable(fallbackIcon ?: R.drawable.ic_auto_playlist)
            }
        }
        return getPFDFromBitmap(bitmap)
    }

    private fun getPlayAllImage(ctx: Context, type: String, id: Long, shuffle: Boolean): ParcelFileDescriptor {
        val bitmap = runBlocking(Dispatchers.IO) {
            val tracks = when (type) {
                GENRE -> ctx.getFromMl { getGenre(id)?.albums?.flatMap { it.tracks.toList() } }
                ARTIST -> ctx.getFromMl { getArtist(id)?.tracks?.toList() }
                PLAYLIST -> ctx.getFromMl { getPlaylist(id, true, false)?.tracks?.toList() }
                else -> null
            }
            val cover = tracks?.let {
                val iconAddition = when {
                    type == PLAYLIST -> null
                    shuffle -> ctx.getBitmapFromDrawable(R.drawable.ic_auto_shuffle_circle)
                    else -> ctx.getBitmapFromDrawable(R.drawable.ic_auto_playall_circle)
                }
                val key = if (shuffle) "${type}_shuffle" else type
                ThumbnailsProvider.getPlaylistOrGenreImage("${key}:${id}_256", tracks, 256, iconAddition)
            }
            return@runBlocking when {
                cover != null -> cover
                type == PLAYLIST -> ctx.getBitmapFromDrawable(R.drawable.ic_auto_playlist_unknown)
                else -> ctx.getBitmapFromDrawable(R.drawable.ic_auto_playall)
            }
        }
        return getPFDFromBitmap(bitmap)
    }

    private fun getHistory(ctx: Context): ByteArray? {
        return runBlocking(Dispatchers.IO) {
            /* Last Played */
            val lastMediaPlayed = ctx.getFromMl { history(Medialibrary.HISTORY_TYPE_LOCAL)?.toList()?.filter { MediaSessionBrowser.isMediaAudio(it) } }
            if (!lastMediaPlayed.isNullOrEmpty()) {
                return@runBlocking getHomeImage(ctx, HISTORY, lastMediaPlayed.toTypedArray())
            }
            null
        }
    }

    private fun getShuffleAll(ctx: Context): ByteArray? {
        return runBlocking(Dispatchers.IO) {
            /* Shuffle All */
            val audioCount = ctx.getFromMl { audioCount }
            /* Show cover art from the whole library */
            val offset = SecureRandom().nextInt((audioCount - MediaSessionBrowser.MAX_COVER_ART_ITEMS).coerceAtLeast(1))
            val list = ctx.getFromMl { getPagedAudio(Medialibrary.SORT_ALPHA, false, false, false, MediaSessionBrowser.MAX_COVER_ART_ITEMS, offset) }
            return@runBlocking getHomeImage(ctx, SHUFFLE_ALL, list)
        }
    }

    private fun getLastAdded(ctx: Context): ByteArray? {
        return runBlocking(Dispatchers.IO) {
            /* Last Added */
            val recentAudio = ctx.getFromMl { getPagedAudio(Medialibrary.SORT_INSERTIONDATE, true, false, false, MediaSessionBrowser.MAX_HISTORY_SIZE, 0) }
            return@runBlocking getHomeImage(ctx, LAST_ADDED, recentAudio)
        }
    }

    /**
     *  Generate shuffle all, last added, and history images for the home screen.
     */
    private suspend fun getHomeImage(context: Context, key: String, list: Array<MediaWrapper>?): ByteArray? {
        var cover: Bitmap? = null
        val tracks: ArrayList<MediaWrapper> = ArrayList()
        list?.let {
            tracks.ensureCapacity(list.size.coerceAtMost(MediaSessionBrowser.MAX_COVER_ART_ITEMS))
            for (libraryItem in list) {
                if (libraryItem.itemType == MediaLibraryItem.TYPE_MEDIA && libraryItem.type != MediaWrapper.TYPE_AUDIO)
                    continue
                tracks.add(libraryItem)
                if (tracks.size == MediaSessionBrowser.MAX_COVER_ART_ITEMS) break
            }
            if (tracks.any { mw -> mw.artworkMrl != null && mw.artworkMrl.isNotEmpty() }) {
                val iconAddition = when (key) {
                    SHUFFLE_ALL -> getBitmapFromDrawable(context, R.drawable.ic_auto_shuffle_circle)
                    LAST_ADDED -> getBitmapFromDrawable(context, R.drawable.ic_auto_new_circle)
                    HISTORY -> getBitmapFromDrawable(context, R.drawable.ic_auto_history_circle)
                    else -> null
                }
                cover = ThumbnailsProvider.getPlaylistOrGenreImage("${key}_256", tracks, 256, iconAddition)
            }
        }
        return BitmapUtil.encodeImage(cover ?: context.getBitmapFromDrawable(R.drawable.ic_auto_playall), ENABLE_TRACING){
            getTimestamp()
        }
    }

    /**
     * Test if the cover art image is square to determine if padding is required. Also check if the
     * image is larger than 2000x2000px. Images 3000x3000px crash Android Auto.
     */
    private fun isImageWithinBounds(path: String): Boolean {
        val options = BitmapFactory.Options()
        /* Get the resolution of the bitmap without allocating the memory */
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        val outWidth = options.outWidth
        val outHeight = options.outHeight
        return when {
            outWidth == -1 || outHeight == -1 -> false
            outWidth > 2_000 || outHeight > 2_000 -> false
            else -> outWidth == outHeight
        }
    }

    /**
     * Android Auto Head Units, in particular Hyundai and perhaps others, will only display cover
     * art on the head-unit's home screen if the image is square. If a *bitmap* is passed, the head-unit
     * stretches it square, typically distorting it. If a *URI* is passed with a non-square image, it does
     * not display at all. We check to see if the image is square, and if not, we pad it by
     * placing it centered on a transparent background.
     */
    private fun padSquare(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        if (width == height) return src
        val maxSize = max(width, height)
        val x = ((height - width) / 2f).coerceAtLeast(0f)
        val y = ((width - height) / 2f).coerceAtLeast(0f)
        val dst = Bitmap.createBitmap(maxSize, maxSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(dst)
        c.drawBitmap(src, x, y, null)
        return dst
    }

    /**
     * Workaround for Android 13 notification bar media controls. Crossfade animation between old
     * cover art and new cover art does not work correctly with transparency.
     */
    private fun removeTransparency(src: Bitmap?): Bitmap? {
        if (src == null) return null
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        dst.eraseColor(Color.BLACK)
        val c = Canvas(dst)
        c.drawBitmap(src, 0f, 0f, null)
        return dst
    }

    /**
     * Attempt to directly load embedded artwork.
     */
    private fun readEmbeddedArtwork(mw: MediaLibraryItem?, width: Int): Bitmap? {
        if (mw is MediaWrapper && mw.artworkMrl == null && mw.uri != null) {
            var media: IMedia? = null
            return try {
                val libVlc = VLCInstance.getInstance(ctx)
                val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
                media = mediaFactory.getFromUri(libVlc, mw.uri).apply { parse() }
                AudioUtil.readCoverBitmap(Uri.decode(MLServiceLocator.getAbstractMediaWrapper(media).artworkMrl), width)
            } finally {
                media?.release()
            }
        }
        return null
    }

    /**
     * Return a ParcelFileDescriptor from a Bitmap encoded in WEBP format. This function writes the
     * compressed data stream directly to the file descriptor with no intermediate byte array.
     */
    @Suppress("DEPRECATION")
    private fun getPFDFromBitmap(bitmap: Bitmap?): ParcelFileDescriptor {
        return super.openPipeHelper(Uri.EMPTY, MIME_TYPE_IMAGE_WEBP, null, bitmap
        ) { pfd: ParcelFileDescriptor, _: Uri, _: String, _: Bundle?, bmp: Bitmap? ->
            /* Compression is performed on an AsyncTask thread within openPipeHelper() */
            try {
                bmp?.let { FileOutputStream(pfd.fileDescriptor).use { bmp.compress(CompressFormat.WEBP, 100, it) } }
            } catch (e: IOException) {
                logError(e)
            }
        }
    }

    /**
     * Return a ParcelFileDescriptor from an existing image in a byte array.
     */
    private fun getPFDFromByteArray(byteArray: ByteArray?): ParcelFileDescriptor {
        return super.openPipeHelper(Uri.EMPTY, MIME_TYPE_IMAGE_WEBP, null, byteArray
        ) { pfd: ParcelFileDescriptor, _: Uri, _: String, _: Bundle?, bArray: ByteArray? ->
            try {
                bArray?.let { FileOutputStream(pfd.fileDescriptor).use { it.write(bArray) } }
            } catch (e: IOException) {
                logError(e)
            }
        }
    }

    private fun logError(e: Exception) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
            Log.e(TAG, "Could not transfer cover art", e)
        else
            Log.e(TAG, "Could not transfer cover art to caller: $callingPackage", e)
    }

    private val dateFormatter by lazy {
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("hhmmss.SSS", Locale.getDefault())
        }
    }

    private fun getTimestamp() = dateFormatter.get()?.format(System.currentTimeMillis())

    override fun onCreate(): Boolean {
        this.ctx = context!!
        return true
    }

    override fun getType(uri: Uri): String = MIME_TYPE_IMAGE_WEBP

    override fun insert(uri: Uri, values: ContentValues?) = Uri.EMPTY!!

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {

        const val PATH = "path"
        const val BIG_VARIANT = "big_variant"
        const val REMOTE_ACCESS = "remote_access"
        const val ALBUM = "album"
        const val GENRE = "genre"
        const val VIDEO = "video"
        const val MEDIA = "media"
        const val ARTIST = "artist"
        const val REMOTE = "remote"
        const val HISTORY = "history"
        const val PLAYLIST = "playlist"
        const val PLAY_ALL = "play_all"
        const val LAST_ADDED = "last_added"
        const val SHUFFLE = "shuffle"
        const val SHUFFLE_ALL = "shuffle_all"

        //Used to store webp encoded bitmap of the currently playing artwork
        private val memCache: LruCache<String, ByteArray> = LruCache<String, ByteArray>(if (Build.VERSION.SDK_INT == 33) 2 else 1)

        @Synchronized
        fun clear() {
            memCache.evictAll()
        }

        @Synchronized
        fun getOrPutImage(key: String, defaultValue: () -> ByteArray?): ByteArray? {
            val value = memCache.get(key)
            return if (value == null) {
                val answer = defaultValue()
                if (answer != null) {
                    memCache.put(key, answer)
                    answer
                } else null
            } else {
                value
            }
        }

        /**
         * Compute an expiration time for thumbs we would like to periodically rotate.
         * @param halfDayExpiration If true (default), return the time rounded to midnight,
         * if between midnight and noon, or time rounded to noon, if between noon and midnight.
         * If false, return the current time (will always refresh thumbnail on page load).
         */
        fun computeExpiration(halfDayExpiration: Boolean = true): String {
            val cal = Calendar.getInstance()
            if (halfDayExpiration) {
                cal.set(Calendar.HOUR_OF_DAY, if (cal.get(Calendar.HOUR_OF_DAY) < 12) 0 else 12)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis.toString()
        }

        /**
         * Construct the URI used to access this content provider
         */
        fun buildUri(ctx: Context, path: Uri?): Uri {
            val uriBuilder = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority("${ctx.packageName}.artwork")
            path?.pathSegments?.forEach { it?.let { uriBuilder.appendPath(it) } }
            path?.queryParameterNames?.forEach { key -> key?.let { uriBuilder.appendQueryParameter(key, path.getQueryParameter(key)) } }
            val uri = uriBuilder.build()
            if (ENABLE_TRACING) Log.d(TAG, "buildUri() Path: ${uri.path} Thread: ${Thread.currentThread().name}")
            return uri
        }

        /**
         * Construct the URI for MediaWrappers
         */
        fun buildMediaUri(ctx: Context, media: MediaWrapper): Uri {
            val audioNoArtwork = media.type == MediaWrapper.TYPE_AUDIO && media.artworkMrl.isNullOrEmpty()
            return buildUri(ctx, Uri.Builder()
                    .appendPath(MEDIA)
                    .appendPath("${if (audioNoArtwork) 0L else media.lastModified}")
                    .appendPath("${if (audioNoArtwork) 0L else media.id}")
                    .build())
        }

        /**
         * Compute either a CRC32 or a simple XOR checksum. For playlists, the CRC32 enables
         * detection if the contents of the playlist are re-ordered. This is important for capturing
         * the cover art change for playlists with videos. For last added and history on the home
         * screen we actually want to ignore re-ordering. If the user likes to play the
         * same songs from their history over and over, it will simply re-sort the history. As XOR
         * is commutative, the value computed will be the same and the cached image will be shown.
         */
        fun computeChecksum(list: List<MediaWrapper>, detectReordering: Boolean = false): Long {
            if (detectReordering) {
                val checksum = CRC32()
                val buffer = ByteBuffer.allocate(Long.SIZE_BYTES)
                list.forEach {
                    buffer.putLong(it.lastModified)
                    checksum.update(buffer.array())
                    buffer.clear()
                }
                return checksum.value
            } else {
                var checksum = 0L
                list.forEach {
                    checksum = checksum xor it.lastModified
                }
                return checksum
            }
        }
    }
}