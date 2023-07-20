/*****************************************************************************
 * MainTvActivity.java
 *
 * Copyright © 2014-2018 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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
package org.videolan.television.ui

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.television.R
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.tools.RESULT_RESCAN
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.RESULT_RESTART_APP
import org.videolan.vlc.ScanProgress
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.SchedulerCallback
import org.videolan.vlc.util.Util

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MainTvActivity : BaseTvActivity(), StoragePermissionsDelegate.CustomActionController, SchedulerCallback {

    private lateinit var browseFragment: MainTvFragment
    private lateinit var progressBar: ProgressBar
    lateinit var scheduler: LifecycleAwareScheduler


    override fun onTaskTriggered(id: String, data: Bundle) {
        when (id) {
            SHOW_LOADING -> progressBar.visibility = View.VISIBLE
            HIDE_LOADING -> {
                scheduler.cancelAction(SHOW_LOADING)
                progressBar.visibility = View.GONE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduler =  LifecycleAwareScheduler(this)

        Util.checkCpuCompatibility(this)

        setContentView(R.layout.tv_main)

        val fragmentManager = supportFragmentManager
        browseFragment = fragmentManager.findFragmentById(R.id.browse_fragment) as MainTvFragment
        progressBar = findViewById(R.id.tv_main_progress)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            when (resultCode) {
                RESULT_RESCAN -> this.reloadLibrary()
                RESULT_RESTART, RESULT_RESTART_APP -> {
                    val intent = Intent(this, if (resultCode == RESULT_RESTART_APP) StartActivity::class.java else MainTvActivity::class.java)
                    finish()
                    startActivity(intent)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            browseFragment.showDetails()
        } else super.onKeyDown(keyCode, event)
    }

    override fun onParsingServiceStarted() {
        scheduler.startAction(SHOW_LOADING)
    }

    override fun onParsingServiceProgress(scanProgress: ScanProgress?) {
        if (progressBar.visibility == View.GONE && Medialibrary.getInstance().isWorking)
            scheduler.startAction(SHOW_LOADING)
    }

    override fun onParsingServiceFinished() {
        if (!Medialibrary.getInstance().isWorking)
            scheduler.scheduleAction(HIDE_LOADING, 500)
    }

    fun hideLoading() {
        scheduler.scheduleAction(HIDE_LOADING, 500)
    }

    override fun onStorageAccessGranted() {
        refresh()
    }

    override fun refresh() {
        this.reloadLibrary()
    }

    companion object {

        const val ACTIVITY_RESULT_PREFERENCES = 1

        const val BROWSER_TYPE = "browser_type"

        const val TAG = "VLC/MainTvActivity"
        private const val SHOW_LOADING = "show_loading"
        private const val HIDE_LOADING = "hide_loading"
    }
}
