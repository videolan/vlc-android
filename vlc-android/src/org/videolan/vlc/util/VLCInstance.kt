/*****************************************************************************
 * VLCInstance.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log

import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.util.VLCUtil
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.VLCCrashHandler
import org.videolan.vlc.gui.CompatErrorActivity

object VLCInstance {
    val TAG = "VLC/UiTools/VLCInstance"

    @SuppressLint("StaticFieldLeak")
    private var sLibVLC: LibVLC? = null

    /** A set of utility functions for the VLC application  */
    @Synchronized
    @Throws(IllegalStateException::class)
    operator fun get(ctx: Context): LibVLC {
        if (sLibVLC == null) {
            Thread.setDefaultUncaughtExceptionHandler(VLCCrashHandler())

            val context = ctx.applicationContext
            if (!VLCUtil.hasCompatibleCPU(context)) {
                Log.e(TAG, VLCUtil.getErrorMsg())
                throw IllegalStateException("LibVLC initialisation failed: " + VLCUtil.getErrorMsg())
            }

            // TODO change LibVLC signature to accept a List instead of an ArrayList
            sLibVLC = LibVLC(context, VLCOptions.libOptions)
        }
        return sLibVLC!!
    }

    @Synchronized
    @Throws(IllegalStateException::class)
    fun restart() {
        if (sLibVLC != null) {
            sLibVLC!!.release()
            sLibVLC = LibVLC(VLCApplication.appContext, VLCOptions.libOptions)
        }
    }

    @Synchronized
    fun testCompatibleCPU(context: Context): Boolean {
        return if (sLibVLC == null && !VLCUtil.hasCompatibleCPU(context)) {
            if (context is Activity) {
                val i = Intent(context, CompatErrorActivity::class.java)
                context.startActivity(i)
            }
            false
        } else
            true
    }
}
