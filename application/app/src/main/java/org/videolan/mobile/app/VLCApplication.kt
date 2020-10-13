/*****************************************************************************
 * VLCApplication.ki
 *
 * Copyright © 2010-2020 VLC authors and VideoLAN
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
package org.videolan.mobile.app

import android.annotation.TargetApi
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.*
import org.videolan.libvlc.interfaces.ILibVLCFactory
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.tools.BitmapCache
import org.videolan.vlc.util.DialogDelegate

private const val TAG = "VLC/VLCApplication"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VLCApplication : MultiDexApplication(), Dialog.Callbacks by DialogDelegate, AppDelegate by AppSetupDelegate(), AppFactoryProvider {

    init {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        setupApplication()
        super.onCreate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        appContextProvider.updateContext()
    }

    /**
     * Called when the overall system is running low on memory
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "System is running low on memory")
        BitmapCache.clear()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "onTrimMemory, level: $level")
        BitmapCache.clear()
    }

    override fun getMediaFactory(): IMediaFactory {
        try {
            return FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
        } catch (e: Exception) {
            FactoryManager.registerFactory(IMediaFactory.factoryId, MediaFactory())
        }
        return FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    }

    override fun getLibVLCFactory(): ILibVLCFactory {
        try {
            return FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory
        } catch (e: Exception) {
            FactoryManager.registerFactory(ILibVLCFactory.factoryId, LibVLCFactory())
        }
        return FactoryManager.getFactory(ILibVLCFactory.factoryId) as ILibVLCFactory
    }
}
