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

package org.videolan.resources

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.interfaces.ILibVLC
import org.videolan.libvlc.interfaces.ILibVLCFactory
import org.videolan.libvlc.util.VLCUtil
import org.videolan.resources.VLCInstance.init
import org.videolan.resources.util.VLCCrashHandler
import org.videolan.tools.SingletonHolder

@ObsoleteCoroutinesApi
object VLCInstance : SingletonHolder<ILibVLC, Context>({ init(it.applicationContext) }) {
    const val TAG = "VLC/UiTools/VLCInstance"

    @SuppressLint("StaticFieldLeak")
    private lateinit var sLibVLC: ILibVLC

    private val libVLCFactory= FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory

    @Throws(IllegalStateException::class)
    fun init(ctx: Context) : ILibVLC {
        Thread.setDefaultUncaughtExceptionHandler(VLCCrashHandler())

        if (!VLCUtil.hasCompatibleCPU(ctx)) {
            Log.e(TAG, VLCUtil.getErrorMsg())
            throw IllegalStateException("LibVLC initialisation failed: " + VLCUtil.getErrorMsg())
        }

        // TODO change LibVLC signature to accept a List instead of an ArrayList
        sLibVLC = libVLCFactory.getFromOptions(ctx, VLCOptions.libOptions)
        return sLibVLC
    }

    @Throws(IllegalStateException::class)
    fun restart() {
        sLibVLC.release()
        sLibVLC = libVLCFactory.getFromOptions(AppContextProvider.appContext, VLCOptions.libOptions)
    }

    fun testCompatibleCPU(context: Context): Boolean {
        return if (!VLCUtil.hasCompatibleCPU(context)) {
            if (context is Activity) {
                val i = Intent(Intent.ACTION_VIEW).setClassName(context.applicationContext, COMPATERROR_ACTIVITY)
                context.startActivity(i)
            }
            false
        } else true
    }
}
