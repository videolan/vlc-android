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

package org.videolan.vlc.gui.tv.browser

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.tools.KeyHelper
import org.videolan.vlc.*
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.tv.SearchActivity
import org.videolan.vlc.gui.tv.registerTimeView
import org.videolan.vlc.util.Settings
import org.videolan.vlc.util.getContextWithLocale

private const val TAG = "VLC/BaseTvActivity"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class BaseTvActivity : FragmentActivity(), CoroutineScope by MainScope() {

    private lateinit var mediaLibrary: AbstractMedialibrary
    private lateinit var settings: SharedPreferences
    @Volatile
    private var currentlyVisible = false

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale())
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //Init Medialibrary if KO
        if (savedInstanceState != null) startMedialibrary(firstRun = false, upgrade = false, parse = true)
        super.onCreate(savedInstanceState)
        mediaLibrary = AbstractMedialibrary.getInstance()
        settings = Settings.getInstance(this)
        registerLiveData()
        launch { findViewById<View>(R.id.tv_time)?.let { registerTimeView(it as TextView) } }
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

    override fun onDestroy() {
        super.onDestroy()
        cancel()
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
        MediaParsingService.progress.observe(this, Observer { scanProgress -> if (scanProgress != null) onParsingServiceProgress(scanProgress) })
        AbstractMedialibrary.getState().observe(this, Observer { started ->
            if (started == null) return@Observer
            if (started)
                onParsingServiceStarted()
            else
                onParsingServiceFinished()
        })
        MediaParsingService.newStorages.observe(this, Observer<List<String>> { devices ->
            if (devices == null) return@Observer
            for (device in devices) UiTools.newStorageDetected(this@BaseTvActivity, device)
            MediaParsingService.newStorages.value = null
        })
    }
}
