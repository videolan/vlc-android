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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.contentValuesOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.BitmapCache
import org.videolan.tools.CloseableUtils
import org.videolan.tools.HttpImageLoader
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools.snackerConfirm
import org.videolan.vlc.util.Permissions
import java.io.*
import java.lang.Runnable

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object AudioUtil {
    const val TAG = "VLC/AudioUtil"

    fun FragmentActivity.setRingtone(song: MediaWrapper) {
        if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage(this)) {
            Permissions.askWriteStoragePermission(this, false, Runnable { setRingtone(song) })
            return
        }
        if (!Permissions.canWriteSettings(this)) {
            Permissions.checkWriteSettingsPermission(this, Permissions.PERMISSION_SYSTEM_RINGTONE)
            return
        }
        val view = window.decorView.findViewById(R.id.coordinator) ?: window.decorView
        lifecycleScope.snackerConfirm(view, getString(R.string.set_song_question, song.title)) {
            val newRingtone = AndroidUtil.UriToFile(song.uri)
            if (!withContext(Dispatchers.IO) { newRingtone.exists() }) {
                Toast.makeText(applicationContext, getString(R.string.ringtone_error), Toast.LENGTH_SHORT).show()
                return@snackerConfirm
            }

            val values = contentValuesOf(
                    MediaStore.MediaColumns.TITLE to song.title,
                    MediaStore.MediaColumns.MIME_TYPE to "audio/*",
                    MediaStore.Audio.Media.ARTIST to song.artist,
                    MediaStore.Audio.Media.IS_RINGTONE to true,
                    MediaStore.Audio.Media.IS_NOTIFICATION to false,
                    MediaStore.Audio.Media.IS_ALARM to false,
                    MediaStore.Audio.Media.IS_MUSIC to false
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val newUri: Uri = this.contentResolver
                            .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
                    contentResolver.openOutputStream(newUri).use { os ->
                        val size = newRingtone.length().toInt()
                        val bytes = ByteArray(size)
                        val buf = BufferedInputStream(FileInputStream(newRingtone))
                        buf.read(bytes, 0, bytes.size)
                        buf.close()
                        os!!.write(bytes)
                        os.close()
                        os.flush()
                    }
                    RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE,
                            newUri)
                } else {
                    values.put(MediaStore.Audio.Media.DATA, newRingtone.absolutePath)

                    val uri = withContext(Dispatchers.IO) {
                        val tmpUri = MediaStore.Audio.Media.getContentUriForPath(newRingtone.absolutePath)
                        contentResolver.delete(tmpUri, MediaStore.MediaColumns.DATA + "=\"" + newRingtone.absolutePath + "\"", null)
                        contentResolver.insert(tmpUri, values)
                    }
                    RingtoneManager.setActualDefaultRingtoneUri(
                            applicationContext,
                            RingtoneManager.TYPE_RINGTONE,
                            uri
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "error setting ringtone", e)
                Toast.makeText(applicationContext,
                        getString(R.string.ringtone_error),
                        Toast.LENGTH_SHORT).show()
                return@snackerConfirm
            }
            Toast.makeText(
                            applicationContext,
                            getString(R.string.ringtone_set, song.title),
                            Toast.LENGTH_SHORT)
                    .show()
        }
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
        return BitmapCache.getBitmapFromMemCache(path.substringAfter("file://")+"_$width") ?: fetchCoverBitmap(path, width)
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
                while (options.outWidth / (options.inSampleSize + 1) > width)
                    options.inSampleSize = options.inSampleSize * 2
            }

            // Decode the file (with memory allocation this time)
            cover = BitmapFactory.decodeFile(path, options)
            BitmapCache.addBitmapToMemCache(path, cover)
        }
        return cover
    }
}
