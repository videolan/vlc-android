/*****************************************************************************
 * AppSetupDelegate.ki
 *
 * Copyright © 2020 VLC authors and VideoLAN
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
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.LibVLCFactory
import org.videolan.libvlc.MediaFactory
import org.videolan.libvlc.interfaces.ILibVLCFactory
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.moviepedia.MediaScraper
import org.videolan.moviepedia.provider.MediaScrapingTvshowProvider
import org.videolan.resources.AppContextProvider
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.SendCrashActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.SettingsMigration
import org.videolan.resources.VLCInstance

interface AppDelegate {
    val appContextProvider : AppContextProvider
    fun Context.setupApplication()
}

class AppSetupDelegate : AppDelegate {

    // Store AppContextProvider to prevent GC
    override val appContextProvider = AppContextProvider

    @TargetApi(Build.VERSION_CODES.O)
    override fun Context.setupApplication() {
        appContextProvider.init(this)
        NotificationHelper.createNotificationChannels(this)

        // Service loaders
        FactoryManager.registerFactory(IMediaFactory.factoryId, MediaFactory())
        FactoryManager.registerFactory(ILibVLCFactory.factoryId, LibVLCFactory())

        // Register movipedia to resume tv shows/movies
        MediaScrapingTvshowProvider.getProviders().forEach {
            appContextProvider.mediaContentResolvers.put(it.first, it.second)
        }

        // Setup Moviepedia indexing after Medialibrary scan
        (AppContextProvider.indexingListeners as MutableList).add(MediaScraper.indexListener)

        //Initiate Kotlinx Dispatchers in a thread to prevent ANR
        backgroundInit()
    }

    // init operations executed in background threads
    private fun Context.backgroundInit() {
        Thread(Runnable {
            AppContextProvider.setLocale(Settings.getInstance(this).getString("set_locale", ""))

            AppScope.launch(Dispatchers.IO) {
                // Prepare cache folder constants
                AudioUtil.prepareCacheFolder(this@backgroundInit)

                if (!VLCInstance.testCompatibleCPU(AppContextProvider.appContext)) return@launch
                Dialog.setCallbacks(VLCInstance[this@backgroundInit], DialogDelegate)
            }
            packageManager.setComponentEnabledSetting(ComponentName(this, SendCrashActivity::class.java),
                    if (BuildConfig.BETA) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            SettingsMigration.migrateSettings(this)
        }).start()
    }
}