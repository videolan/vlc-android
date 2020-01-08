/*****************************************************************************
 * VLCApplication.java
 *
 * Copyright © 2010-2013 VLC authors and VideoLAN
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

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.LibVLCFactory
import org.videolan.libvlc.MediaFactory
import org.videolan.libvlc.interfaces.ILibVLCFactory
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.VLCCommonApplication
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.tools.wrap
import org.videolan.vlc.gui.SendCrashActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapCache
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.tools.AppScope
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.SettingsMigration
import org.videolan.vlc.util.VLCInstance

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VLCApplication : VLCCommonApplication(), Dialog.Callbacks by DialogDelegate {

    init {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        FactoryManager.registerFactory(IMediaFactory.factoryId, MediaFactory())
        FactoryManager.registerFactory(ILibVLCFactory.factoryId, LibVLCFactory())
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        //Initiate Kotlinx Dispatchers in a thread to prevent ANR
        Thread(Runnable {
            locale = Settings.getInstance(appContext).getString("set_locale", "")
            locale.takeIf { !it.isNullOrEmpty() }?.let {
                updateAppContext(ContextWrapper(this).wrap(locale!!))
            }

            AppScope.launch(Dispatchers.IO) {
                // Prepare cache folder constants
                AudioUtil.prepareCacheFolder(appContext)

                if (!VLCInstance.testCompatibleCPU(appContext)) return@launch
                Dialog.setCallbacks(VLCInstance[appContext], DialogDelegate)
            }
            packageManager.setComponentEnabledSetting(ComponentName(this, SendCrashActivity::class.java),
                    if (BuildConfig.BETA) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            SettingsMigration.migrateSettings(this)
        }).start()
        if (AndroidUtil.isOOrLater)
            NotificationHelper.createNotificationChannels(this@VLCApplication)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        locale.takeIf { !it.isNullOrEmpty() }?.let {
            updateAppContext(ContextWrapper(this).wrap(locale!!))
        }
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

    companion object {
        private const val TAG = "VLC/VLCApplication"

        const val ACTION_MEDIALIBRARY_READY = "VLC/VLCApplication"


        // Property to get the new locale only on restart to prevent change the locale partially on runtime
        var locale: String? = ""
            private set


        /**
         * @return the main resources from the Application
         */
        val appResources: Resources
            get() = appContext.resources

        /**
         * Check if application is currently displayed
         * @return true if an activity is displayed, false if app is in background.
         */
        val isForeground: Boolean
            get() = ProcessLifecycleOwner.get().isStarted()
    }
}
