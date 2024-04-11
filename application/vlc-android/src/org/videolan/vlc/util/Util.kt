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
import android.app.ActivityOptions
import android.app.Service
import android.content.Context
import android.os.Build
import android.os.Bundle
import org.videolan.resources.AppContextProvider
import org.videolan.resources.VLCInstance
import org.videolan.tools.CloseableUtils
import org.videolan.tools.runBackground
import org.videolan.tools.runOnMainThread
import org.videolan.vlc.gui.video.VideoPlayerActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

object Util {
    const val TAG = "VLC/Util"

    fun readAsset(assetName: String, defaultS: String): String {
        var inputStream: InputStream? = null
        var r: BufferedReader? = null
        try {
            inputStream = AppContextProvider.appResources.assets.open(assetName)
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
            CloseableUtils.close(inputStream)
            CloseableUtils.close(r)
        }
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

    fun getFullScreenBundle() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val options = ActivityOptions.makeBasic()
        options.setLaunchBounds(null)
        options.toBundle()
    } else Bundle()

}
