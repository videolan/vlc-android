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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.util.Permissions
import java.io.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object AudioUtil {
    const val TAG = "VLC/AudioUtil"

    fun setRingtone(song: MediaWrapper, context: FragmentActivity) {
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

    private fun getCoverFromMediaStore(context: Context, media: MediaWrapper): String? {
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
            CloseableUtils.close(out)
        }
    }

    //TODO Make it a suspend function to get rid of runBlocking {... }
    @WorkerThread
    fun readCoverBitmap(path: String?, width: Int): Bitmap? {
        val path = path ?: return null
        if (path.startsWith("http")) return runBlocking(Dispatchers.Main) {
            HttpImageLoader.downloadBitmap(path)
        }
        return BitmapCache.getBitmapFromMemCache(path.substringAfter("file://")) ?: fetchCoverBitmap(path, width)
    }

    @WorkerThread
    fun fetchCoverBitmap(path: String, width: Int): Bitmap? {
        val path = path.substringAfter("file://")
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
}
