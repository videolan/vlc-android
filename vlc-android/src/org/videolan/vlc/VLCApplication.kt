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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.SimpleArrayMap
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.SendCrashActivity
import org.videolan.vlc.gui.dialogs.VlcProgressDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapCache
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.util.*
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VLCApplication : MultiDexApplication() {

    private var dialogCallbacks: Dialog.Callbacks = object : Dialog.Callbacks {
        override fun onDisplay(dialog: Dialog.ErrorMessage) {
            Log.w(TAG, "ErrorMessage " + dialog.text)
        }

        override fun onDisplay(dialog: Dialog.LoginDialog) {
            val key = DialogActivity.KEY_LOGIN + dialogCounter++
            fireDialog(dialog, key)
        }

        override fun onDisplay(dialog: Dialog.QuestionDialog) {
            if (!Util.byPassChromecastDialog(dialog)) {
                val key = DialogActivity.KEY_QUESTION + dialogCounter++
                fireDialog(dialog, key)
            }
        }

        override fun onDisplay(dialog: Dialog.ProgressDialog) {
            val key = DialogActivity.KEY_PROGRESS + dialogCounter++
            fireDialog(dialog, key)
        }

        override fun onCanceled(dialog: Dialog?) {
            (dialog?.context as? DialogFragment)?.dismiss()
        }

        override fun onProgressUpdate(dialog: Dialog.ProgressDialog) {
            val vlcProgressDialog = dialog.context as VlcProgressDialog
            if (vlcProgressDialog.isVisible) vlcProgressDialog.updateProgress()
        }
    }

    init {
        instance = this
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        //Initiate Kotlinx Dispatchers in a thread to prevent ANR
        Thread(Runnable {
            locale = Settings.getInstance(instance).getString("set_locale", "")
            locale.takeIf { !it.isNullOrEmpty() }?.let {
                instance = ContextWrapper(this).wrap(locale!!)
            }

            runIO(Runnable {
                if (AndroidUtil.isOOrLater)
                    NotificationHelper.createNotificationChannels(this@VLCApplication)
                // Prepare cache folder constants
                AudioUtil.prepareCacheFolder(appContext)

                if (!VLCInstance.testCompatibleCPU(appContext)) return@Runnable
                Dialog.setCallbacks(VLCInstance[instance], dialogCallbacks)
            })
            packageManager.setComponentEnabledSetting(ComponentName(this, SendCrashActivity::class.java),
                    if (BuildConfig.BETA) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }).start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        locale.takeIf { !it.isNullOrEmpty() }?.let {
            instance = ContextWrapper(this).wrap(locale!!)
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

    private fun fireDialog(dialog: Dialog, key: String) {
        storeData(key, dialog)
        startActivity(Intent(appContext, DialogActivity::class.java).setAction(key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        private const val TAG = "VLC/VLCApplication"

        const val ACTION_MEDIALIBRARY_READY = "VLC/VLCApplication"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private lateinit var instance: Context

        private val dataMap = SimpleArrayMap<String, WeakReference<Any>>()

        private var dialogCounter = 0

        // Property to get the new locale only on restart to prevent change the locale partially on runtime
        var locale: String? = ""
            private set

        /**
         * @return the main context of the Application
         */
        val appContext: Context
            @SuppressLint("PrivateApi")
            get() {
                return if (::instance.isInitialized)
                    instance
                else {
                    try {
                        instance = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null) as Application
                    } catch (ignored: IllegalAccessException) {
                    } catch (ignored: InvocationTargetException) {
                    } catch (ignored: NoSuchMethodException) {
                    } catch (ignored: ClassNotFoundException) {
                    } catch (ignored: ClassCastException) {
                    }
                    instance
                }
            }

        /**
         * @return the main resources from the Application
         */
        val appResources: Resources
            get() = appContext.resources

        fun storeData(key: String, data: Any) {
            dataMap.put(key, WeakReference(data))
        }

        fun getData(key: String) = dataMap.remove(key)?.get()

        fun hasData(key: String) = dataMap.containsKey(key)

        fun clearData() {
            dataMap.clear()
        }

        /**
         * Check if application is currently displayed
         * @return true if an activity is displayed, false if app is in background.
         */
        val isForeground: Boolean
            get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}
