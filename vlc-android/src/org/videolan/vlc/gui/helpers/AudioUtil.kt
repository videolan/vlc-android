/*****************************************************************************
 * AudioUtil.java
 *
 * Copyright © 2011-2012 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.helpers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*
import java.io.*
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object AudioUtil {
    val TAG = "VLC/AudioUtil"

    /**
     * Cache directory (/sdcard/Android/data/...)
     */
    private var CACHE_DIR: String? = null
    /**
     * VLC embedded art storage location
     */
    private val ART_DIR = AtomicReference<String>()
    /**
     * Cover caching directory
     */
    private val COVER_DIR = AtomicReference<String>()
    //    /**
    //     * User-defined playlist storage directory
    //     */
    //    public static AtomicReference<String> PLAYLIST_DIR = new AtomicReference<>();

    fun setRingtone(song: AbstractMediaWrapper, context: FragmentActivity) {
        if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage(context)) {
            Permissions.askWriteStoragePermission(context, false, Runnable { setRingtone(song, context) })
            return
        }
        if (!Permissions.canWriteSettings(context)) {
            Permissions.checkWriteSettingsPermission(context, Permissions.PERMISSION_SYSTEM_RINGTONE)
            return
        }
        UiTools.snackerConfirm(context.window.decorView, context.getString(R.string.set_song_question, song.title), Runnable {
            runIO(Runnable {
                val newRingtone = AndroidUtil.UriToFile(song.uri)
                if (!newRingtone.exists()) {
                    runOnMainThread(Runnable { Toast.makeText(context.applicationContext, context.getString(R.string.ringtone_error), Toast.LENGTH_SHORT).show() })
                    return@Runnable
                }

                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DATA, newRingtone.absolutePath)
                values.put(MediaStore.MediaColumns.TITLE, song.title)
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/*")
                values.put(MediaStore.Audio.Media.ARTIST, song.artist)
                values.put(MediaStore.Audio.Media.IS_RINGTONE, true)
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
                values.put(MediaStore.Audio.Media.IS_ALARM, false)
                values.put(MediaStore.Audio.Media.IS_MUSIC, false)

                val uri = MediaStore.Audio.Media.getContentUriForPath(newRingtone.absolutePath)
                val newUri: Uri?
                try {
                    context.contentResolver.delete(uri, MediaStore.MediaColumns.DATA + "=\"" + newRingtone.absolutePath + "\"", null)
                    newUri = context.contentResolver.insert(uri, values)
                    RingtoneManager.setActualDefaultRingtoneUri(
                            context.applicationContext,
                            RingtoneManager.TYPE_RINGTONE,
                            newUri
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "error setting ringtone", e)
                    runOnMainThread(Runnable {
                        Toast.makeText(context.applicationContext,
                                context.getString(R.string.ringtone_error),
                                Toast.LENGTH_SHORT).show()
                    })
                    return@Runnable
                }

                runOnMainThread(Runnable {
                    Toast.makeText(
                            context.applicationContext,
                            context.getString(R.string.ringtone_set, song.title),
                            Toast.LENGTH_SHORT)
                            .show()
                })
            })
        })
    }

    @SuppressLint("NewApi")
    @WorkerThread
    fun prepareCacheFolder(context: Context) {
        try {
            if (AndroidDevices.hasExternalStorage() && context.externalCacheDir != null)
                CACHE_DIR = context.externalCacheDir!!.path
            else
                CACHE_DIR = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Android/data/" + BuildConfig.APPLICATION_ID + "/cache"
        } catch (e: Exception) { // catch NPE thrown by getExternalCacheDir()
            CACHE_DIR = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Android/data/" + BuildConfig.APPLICATION_ID + "/cache"
        } catch (e: ExceptionInInitializerError) {
            CACHE_DIR = AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Android/data/" + BuildConfig.APPLICATION_ID + "/cache"
        }

        ART_DIR.set(CACHE_DIR!! + "/art/")
        COVER_DIR.set(CACHE_DIR!! + "/covers/")
        for (path in Arrays.asList(ART_DIR.get(), COVER_DIR.get())) {
            val file = File(path)
            if (!file.exists())
                file.mkdirs()
        }
    }

    fun clearCacheFolders() {
        for (path in Arrays.asList(ART_DIR.get(), COVER_DIR.get())) {
            val file = File(path)
            if (file.exists())
                deleteContent(file, false)
        }
    }

    private fun deleteContent(dir: File, deleteDir: Boolean) {
        if (dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null && files.size > 0) {
                for (file in files) {
                    deleteContent(file, true)
                }
            }
        }
        if (deleteDir)
            dir.delete()
    }

    private fun getCoverFromMediaStore(context: Context, media: AbstractMediaWrapper): String? {
        val album = media.album ?: return null
        val contentResolver = context.contentResolver
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ALBUM_ART),
                MediaStore.Audio.Albums.ALBUM + " LIKE ?",
                arrayOf(album), null)
        if (cursor == null) {
            // do nothing
        } else if (!cursor.moveToFirst()) {
            // do nothing
            cursor.close()
        } else {
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
            val albumArt = cursor.getString(titleColumn)
            cursor.close()
            return albumArt
        }
        return null
    }

    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    private fun getCoverFromVlc(context: Context, media: AbstractMediaWrapper): String? {
        var artworkURL: String? = media.artworkURL
        if (artworkURL != null && artworkURL.startsWith("file://")) {
            return Uri.decode(artworkURL).replace("file://", "")
        } else if (artworkURL != null && artworkURL.startsWith("attachment://")) {
            // Decode if the album art is embedded in the file
            val mArtist = MediaUtils.getMediaArtist(context, media)
            val mAlbum = MediaUtils.getMediaAlbum(context, media)

            /* Parse decoded attachment */
            if (mArtist.isEmpty() || mAlbum.isEmpty() ||
                    mArtist == VLCApplication.appContext.getString(R.string.unknown_artist) ||
                    mAlbum == VLCApplication.appContext.getString(R.string.unknown_album)) {
                /* If artist or album are missing, it was cached by title MD5 hash */
                val md = MessageDigest.getInstance("MD5")
                val binHash = md.digest((artworkURL + media.title).toByteArray(charset("UTF-8")))
                /* Convert binary hash to normal hash */
                val hash = BigInteger(1, binHash)
                var titleHash = hash.toString(16)
                while (titleHash.length < 32) {
                    titleHash = "0$titleHash"
                }
                /* Use generated hash to find art */
                artworkURL = ART_DIR.get() + "/arturl/" + titleHash + "/art.png"
            } else {
                /* Otherwise, it was cached by artist and album */
                artworkURL = ART_DIR.get() + "/artistalbum/" + mArtist + "/" + mAlbum + "/art.png"
            }

            return artworkURL
        }
        return null
    }

    private fun getCoverFromFolder(media: AbstractMediaWrapper): String? {
        val f = AndroidUtil.UriToFile(media.uri) ?: return null

        val folder = f.parentFile ?: return null

        val imageExt = arrayOf(".png", ".jpeg", ".jpg")
        val coverImages = arrayOf("Folder.jpg", /* Windows */
                "AlbumArtSmall.jpg", /* Windows */
                "AlbumArt.jpg", /* Windows */
                "Album.jpg", ".folder.png", /* KDE?    */
                "cover.jpg", /* rockbox */
                "thumb.jpg")

        /* Find the path without the extension  */
        val index = f.name.lastIndexOf('.')
        if (index > 0) {
            val name = f.name.substring(0, index)
            val ext = f.name.substring(index)
            val files = folder.listFiles { _, filename -> filename.startsWith(name) && Arrays.asList(*imageExt).contains(ext) }
            if (files != null && files.isNotEmpty())
                return files[0].absolutePath
        }

        /* Find the classic cover Images */
        if (folder.listFiles() != null) {
            for (file in folder.listFiles()) {
                for (str in coverImages) {
                    if (file.absolutePath.endsWith(str))
                        return file.absolutePath
                }
            }
        }
        return null
    }

    private fun getCoverCachePath(context: Context, media: AbstractMediaWrapper, width: Int): String {
        val hash = MurmurHash.hash32(MediaUtils.getMediaArtist(context, media) + MediaUtils.getMediaAlbum(context, media))
        return COVER_DIR.get() + (if (hash >= 0) "" + hash else "m" + -hash) + "_" + width
    }

    private fun getCoverFromMemCache(context: Context, media: AbstractMediaWrapper?, width: Int): Bitmap? {
        var cover: Bitmap? = null
        if (media != null && media.artist != null && media.album != null) {
            cover = BitmapCache.getBitmapFromMemCache(getCoverCachePath(context, media, width))
        }
        if (cover == null && media != null && !TextUtils.isEmpty(media.artworkURL) && media.artworkURL.startsWith("http")) {
            cover = BitmapCache.getBitmapFromMemCache(media.artworkURL)
        }
        return cover
    }

    @SuppressLint("NewApi")
    @Synchronized
    fun getCover(context: Context, media: AbstractMediaWrapper, width: Int): Bitmap? {
        var coverPath: String? = null
        var cover: Bitmap? = null
        var cachePath: String? = null
        var cacheFile: File? = null

        if (width <= 0) {
            Log.e(TAG, "Invalid cover width requested")
            return null
        }

        // if external storage is not available, skip covers to prevent slow audio browsing
        if (!AndroidDevices.hasExternalStorage())
            return null

        try {
            // try to load from cache
            if (media.artist != null && media.album != null) {
                cachePath = getCoverCachePath(context, media, width)

                // try to get the cover from the LRUCache first
                cover = BitmapCache.getBitmapFromMemCache(cachePath)
                if (cover != null)
                    return cover

                // try to get the cover from the storage cache
                cacheFile = File(cachePath)
                if (cacheFile.exists()) {
                    if (cacheFile.length() > 0)
                        coverPath = cachePath
                }
            }
            // try to get it from VLC
            if (coverPath == null || !cacheFile!!.exists())
                coverPath = getCoverFromVlc(context, media)

            // try to get the cover from android MediaStore
            if (coverPath == null || !File(coverPath).exists())
                coverPath = getCoverFromMediaStore(context, media)

            // no found yet, looking in folder
            if (coverPath == null || !File(coverPath).exists())
                coverPath = getCoverFromFolder(media)

            // read (and scale?) the bitmap
            cover = readCoverBitmap(coverPath, width)

            // store cover into both cache
            if (cachePath != null) {
                writeBitmap(cover, cachePath)
                BitmapCache.addBitmapToMemCache(cachePath, cover)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return cover
    }

    @Throws(IOException::class)
    private fun writeBitmap(bitmap: Bitmap?, path: String) {
        var out: OutputStream? = null
        try {
            val file = File(path)
            if (file.exists() && file.length() > 0)
                return
            out = BufferedOutputStream(FileOutputStream(file), 4096)
            bitmap?.compress(CompressFormat.JPEG, 90, out)
        } catch (e: Exception) {
            Log.e(TAG, "writeBitmap failed : " + e.message)
        } finally {
            Util.close(out)
        }
    }

    @WorkerThread
    fun readCoverBitmap(path: String?, width: Int): Bitmap? {
        var path: String? = path ?: return null
        if (path!!.startsWith("http")) return HttpImageLoader.downloadBitmap(path)
        if (path.startsWith("file")) path = path.substring(7)
        var cover: Bitmap? = null
        val options = BitmapFactory.Options()

        /* Get the resolution of the bitmap without allocating the memory */
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)

        if (options.outWidth > 0 && options.outHeight > 0) {
            options.inJustDecodeBounds = false
            options.inSampleSize = 1

            // Find the best decoding scale for the bitmap
            if (width > 0) {
                while (options.outWidth / options.inSampleSize > width)
                    options.inSampleSize = options.inSampleSize * 2
            }

            // Decode the file (with memory allocation this time)
            cover = BitmapFactory.decodeFile(path, options)
            BitmapCache.addBitmapToMemCache(path, cover)
        }
        return cover
    }

    @JvmOverloads
    fun getCover(context: Context, list: List<AbstractMediaWrapper>, width: Int, fromMemCache: Boolean = false): Bitmap? {
        var cover: Bitmap? = null
        val testedAlbums = LinkedList<String>()
        for (media in list) {
            /* No list cover is artist or album are null */
            if (media.album == null || media.artist == null)
                continue
            if (testedAlbums.contains(media.album))
                continue

            cover = if (fromMemCache) getCoverFromMemCache(context, media, width) else getCover(context, media, width)
            if (cover != null)
                break
            else if (media.album != null)
                testedAlbums.add(media.album)
        }
        return cover
    }

    fun getCoverFromMemCache(context: Context, list: List<AbstractMediaWrapper>, width: Int): Bitmap? {
        return getCover(context, list, width, true)
    }
}
