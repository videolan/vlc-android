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
import org.videolan.resources.VLCInstance
import org.videolan.tools.runBackground
import org.videolan.tools.runOnMainThread
import org.videolan.vlc.gui.video.VideoPlayerActivity

object Util {
    const val TAG = "VLC/Util"

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

}
