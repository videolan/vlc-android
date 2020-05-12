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
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.television.R
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.tools.RESULT_RESCAN
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.RESULT_RESTART_APP
import org.videolan.tools.WeakHandler
import org.videolan.vlc.ScanProgress
import org.videolan.vlc.StartActivity
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Util

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MainTvActivity : BaseTvActivity() {

    private lateinit var browseFragment: MainTvFragment
    private lateinit var progressBar: ProgressBar
    private val handler = MainTvActivityHandler(this)


    class MainTvActivityHandler(owner: MainTvActivity) : WeakHandler<MainTvActivity>(owner) {

        override fun handleMessage(msg: Message) {
            val activity = owner ?: return
            when (msg.what) {
                SHOW_LOADING -> activity.progressBar.visibility = View.VISIBLE
                HIDE_LOADING -> {
                    removeMessages(SHOW_LOADING)
                    activity.progressBar.visibility = View.GONE
                    super.handleMessage(msg)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Util.checkCpuCompatibility(this)

        // Delay access permission dialog prompt to avoid background corruption
        if (!Permissions.canReadStorage(this))
            handler.postDelayed({ Permissions.checkReadStoragePermission(this@MainTvActivity) }, 1000)

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
        handler.sendEmptyMessage(SHOW_LOADING)
    }

    override fun onParsingServiceProgress(scanProgress: ScanProgress?) {
        if (progressBar.visibility == View.GONE && Medialibrary.getInstance().isWorking)
            handler.sendEmptyMessage(SHOW_LOADING)
    }

    override fun onParsingServiceFinished() {
        if (!Medialibrary.getInstance().isWorking)
            handler.sendEmptyMessageDelayed(HIDE_LOADING, 500)
    }

    fun hideLoading() {
        handler.sendEmptyMessageDelayed(HIDE_LOADING, 500)
    }

    override fun refresh() {
        this.reloadLibrary()
    }

    companion object {

        const val ACTIVITY_RESULT_PREFERENCES = 1

        const val BROWSER_TYPE = "browser_type"

        const val TAG = "VLC/MainTvActivity"
        private const val SHOW_LOADING = 0
        private const val HIDE_LOADING = 1
    }
}
