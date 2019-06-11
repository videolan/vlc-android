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
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.SimpleArrayMap
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.AMedialibrary
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.dialogs.VlcProgressDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapCache
import org.videolan.vlc.gui.helpers.NotificationHelper
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.*
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class VLCApplication : Application() {

    private var mDialogCallbacks: Dialog.Callbacks = object : Dialog.Callbacks {
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
            if (dialog != null && dialog.context != null) (dialog.context as DialogFragment).dismiss()
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
            locale = Settings.getInstance(instance!!).getString("set_locale", "")
            runOnMainThread(Runnable {
                // Set the locale for API < 24 and set application resources and direction for API >=24
                UiTools.setLocale(appContext!!)
            })

            runIO(Runnable {
                if (AndroidUtil.isOOrLater)
                    NotificationHelper.createNotificationChannels(this@VLCApplication)
                // Prepare cache folder constants
                AudioUtil.prepareCacheFolder(appContext!!)

                if (!VLCInstance.testCompatibleCPU(appContext!!)) return@Runnable
                Dialog.setCallbacks(VLCInstance[instance!!], mDialogCallbacks)
            })
        }).start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        UiTools.setLocale(appContext!!)
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
        const val TAG = "VLC/VLCApplication"

        const val ACTION_MEDIALIBRARY_READY = "VLC/VLCApplication"
        @Volatile
        private var instance: Application? = null

        var playerSleepTime: Calendar? = null

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
                return if (instance != null)
                    instance!!
                else {
                    try {
                        instance = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null) as Application
                    } catch (ignored: IllegalAccessException) {
                    } catch (ignored: InvocationTargetException) {
                    } catch (ignored: NoSuchMethodException) {
                    } catch (ignored: ClassNotFoundException) {
                    } catch (ignored: ClassCastException) {
                    }

                    instance!!
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

        fun getData(key: String): Any? {
            val wr = dataMap.remove(key)
            return wr?.get()
        }

        fun hasData(key: String): Boolean {
            return dataMap.containsKey(key)
        }

        fun clearData() {
            dataMap.clear()
        }

        val mlInstance: AMedialibrary
            get() = AMedialibrary.getInstance()

        /**
         * Check if application is currently displayed
         * @return true if an activity is displayed, false if app is in background.
         */
        val isForeground: Boolean
            get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}
