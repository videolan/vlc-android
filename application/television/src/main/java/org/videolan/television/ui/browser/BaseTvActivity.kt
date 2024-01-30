/*
 * *************************************************************************
 *  BaseTvActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.television.ui.browser

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AppContextProvider
import org.videolan.resources.util.startMedialibrary
import org.videolan.television.ui.SearchActivity
import org.videolan.television.ui.dialogs.ConfirmationTvActivity
import org.videolan.tools.KeyHelper
import org.videolan.tools.Settings
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.ScanProgress
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogManager

private const val TAG = "VLC/BaseTvActivity"
const val REQUEST_CODE_NO_CONNECTION = 100
const val REQUEST_CODE_RESTART_APP = 101

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class BaseTvActivity : FragmentActivity(), IDialogManager {

    private lateinit var mediaLibrary: Medialibrary
    private lateinit var settings: SharedPreferences
    @Volatile
    private var currentlyVisible = false
    private val dialogsDelegate = DialogDelegate()

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Init Medialibrary if KO
        if (savedInstanceState != null) startMedialibrary(firstRun = false, upgrade = false, parse = true)
        super.onCreate(savedInstanceState)
        mediaLibrary = Medialibrary.getInstance()
        settings = Settings.getInstance(this)
        registerLiveData()
        dialogsDelegate.observeDialogs(this, this)
    }

    override fun onStart() {
        ExternalMonitor.subscribeStorageCb(this)

        // super.onStart must be called after receiver registration
        super.onStart()
        currentlyVisible = true
    }

    override fun onStop() {
        currentlyVisible = false
        ExternalMonitor.unsubscribeStorageCb(this)
        super.onStop()
    }


    override fun fireDialog(dialog: Dialog) {
        DialogActivity.dialog = dialog
        startActivity(Intent(DialogActivity.KEY_DIALOG, null, this, DialogActivity::class.java))
    }

    override fun dialogCanceled(dialog: Dialog?) {
        when(dialog) {
            is Dialog.ErrorMessage -> {
                Snackbar.make(window.decorView, "${dialog.title}: ${dialog.text}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        KeyHelper.manageModifiers(event)
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            startActivity(Intent(this, SearchActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        KeyHelper.manageModifiers(event)
        return super.onKeyUp(keyCode, event)
    }

    protected abstract fun refresh()

    protected open fun onParsingServiceStarted() {}

    protected open fun onParsingServiceProgress(scanProgress: ScanProgress?) {}
    protected open fun onParsingServiceFinished() {}

    private fun registerLiveData() {
        MediaParsingService.progress.observe(this) { scanProgress -> if (scanProgress != null) onParsingServiceProgress(scanProgress) }
        Medialibrary.getState().observe(this) { started ->
            if (started == null) return@observe
            if (started)
                onParsingServiceStarted()
            else
                onParsingServiceFinished()
        }
        MediaParsingService.newStorages.observe(this) { devices ->
            if (devices == null) return@observe
            for (device in devices) UiTools.newStorageDetected(this@BaseTvActivity, device)
            MediaParsingService.newStorages.value = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_NO_CONNECTION -> {
                if (resultCode == ConfirmationTvActivity.ACTION_ID_NEGATIVE) finish() else {

                    try {
                        val name = ComponentName("com.android.tv.settings",
                                "com.android.tv.settings.connectivity.NetworkActivity")
                        val i = Intent(Intent.ACTION_MAIN)
                        i.addCategory(Intent.CATEGORY_LAUNCHER)
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        i.component = name
                        startActivity(i)
                    } catch (e: Exception) {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                }
            }
            REQUEST_CODE_RESTART_APP -> {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
