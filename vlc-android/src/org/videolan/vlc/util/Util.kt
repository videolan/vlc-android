/*****************************************************************************
 * UiTools.java
 *
 * Copyright © 2011-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import android.app.Activity
import android.app.Service
import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.helpers.hf.WriteExternalDelegate
import org.videolan.vlc.gui.video.VideoPlayerActivity
import java.io.*
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object Util {
    val TAG = "VLC/Util"

    fun readAsset(assetName: String, defaultS: String): String {
        var inputStream: InputStream? = null
        var r: BufferedReader? = null
        try {
            inputStream = VLCApplication.appResources.assets.open(assetName)
            r = BufferedReader(InputStreamReader(inputStream, "UTF8"))
            val sb = StringBuilder()
            var line: String? = r.readLine()
            if (line != null) {
                sb.append(line)
                line = r.readLine()
                while (line != null) {
                    sb.append('\n')
                    sb.append(line)
                    line = r.readLine()
                }
            }
            return sb.toString()
        } catch (e: IOException) {
            return defaultS
        } finally {
            close(inputStream)
            close(r)
        }
    }

    fun close(closeable: Closeable?): Boolean {
        if (closeable != null)
            try {
                closeable.close()
                return true
            } catch (e: IOException) {
            }

        return false
    }

    fun <T> isArrayEmpty(array: Array<T>?): Boolean {
        return array == null || array.isEmpty()
    }

    fun isListEmpty(collection: Collection<*>?): Boolean {
        return collection == null || collection.isEmpty()
    }


    fun <T> arrayToArrayList(array: Array<T>): ArrayList<T> {
        val list = ArrayList<T>(array.size)
        Collections.addAll(list, *array)
        return list
    }


    fun getMediaDescription(artist: String, album: String): String {
        val hasArtist = !TextUtils.isEmpty(artist)
        val hasAlbum = !TextUtils.isEmpty(album)
        if (!hasAlbum && !hasArtist) return ""
        val contentBuilder = StringBuilder(if (hasArtist) artist else "")
        if (hasArtist && hasAlbum) contentBuilder.append(" - ")
        if (hasAlbum) contentBuilder.append(album)
        return contentBuilder.toString()
    }

    fun byPassChromecastDialog(dialog: Dialog.QuestionDialog): Boolean {
        if ("Insecure site" == dialog.title) {
            if ("View certificate" == dialog.action1Text)
                dialog.postAction(1)
            else if ("Accept permanently" == dialog.action2Text) dialog.postAction(2)
            dialog.dismiss()
            return true
        } else if ("Performance warning" == dialog.title) {
            Toast.makeText(VLCApplication.appContext, R.string.cast_performance_warning, Toast.LENGTH_LONG).show()
            dialog.postAction(1)
            dialog.dismiss()
            return true
        }
        return false
    }

    fun checkCpuCompatibility(ctx: Context) {
        runBackground(Runnable {
            if (!VLCInstance.testCompatibleCPU(ctx))
                runOnMainThread(Runnable {
                    when (ctx) {
                        is Service -> ctx.stopSelf()
                        is VideoPlayerActivity -> ctx.exit(Activity.RESULT_CANCELED)
                        is Activity -> ctx.finish()
                    }
                })
        })
    }

    fun checkWritePermission(activity: FragmentActivity, media: AbstractMediaWrapper, callback: Runnable): Boolean {
        val uri = media.uri
        if ("file" != uri.scheme) return false
        if (uri.path!!.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
            //Check write permission starting Oreo
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage()) {
                Permissions.askWriteStoragePermission(activity, false, callback)
                return false
            }
        } else if (AndroidUtil.isLolliPopOrLater && WriteExternalDelegate.needsWritePermission(uri)) {
            WriteExternalDelegate.askForExtWrite(activity, uri, callback)
            return false
        }
        return true
    }
}
