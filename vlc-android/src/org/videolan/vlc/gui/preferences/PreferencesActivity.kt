/*****************************************************************************
 * PreferencesActivity.java
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

package org.videolan.vlc.gui.preferences

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.util.Settings

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PreferencesActivity : AppCompatActivity() {

    private var mAppBarLayout: AppBarLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        /* Theme must be applied before super.onCreate */
        applyTheme()

        super.onCreate(savedInstanceState)

        setContentView(R.layout.preferences_activity)
        setSupportActionBar(findViewById<View>(R.id.main_toolbar) as Toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_placeholder, PreferencesFragment())
                    .commit()
        }
        mAppBarLayout = findViewById(R.id.appbar)
        mAppBarLayout!!.post { ViewCompat.setElevation(mAppBarLayout!!, resources.getDimensionPixelSize(R.dimen.default_appbar_elevation).toFloat()) }
    }

    internal fun expandBar() {
        mAppBarLayout!!.setExpanded(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (!supportFragmentManager.popBackStackImmediate())
                finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun applyTheme() {
        val pref = Settings.getInstance(this)
        if (pref.contains("app_theme")) {
            AppCompatDelegate.setDefaultNightMode(Integer.valueOf(pref.getString("app_theme", "-1")!!))
        } else if (pref.contains("daynight") || pref.contains("enable_black_theme")) { // legacy support
            val daynight = pref.getBoolean("daynight", false)
            val dark = pref.getBoolean("enable_black_theme", false)
            val mode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else if (daynight) AppCompatDelegate.MODE_NIGHT_AUTO else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun restartMediaPlayer() {
        val le = PlaybackService.restartPlayer
        if (le.hasObservers()) le.value = true
    }

    fun exitAndRescan() {
        setRestart()
        val intent = intent
        finish()
        startActivity(intent)
    }

    fun setRestart() {
        setResult(RESULT_RESTART)
    }

    fun setRestartApp() {
        setResult(RESULT_RESTART_APP)
    }

    fun updateArtists() {
        setResult(RESULT_UPDATE_ARTISTS)
    }

    fun detectHeadset(detect: Boolean) {
        val le = PlaybackService.headSetDetection
        if (le.hasObservers()) le.value = detect
    }

    companion object {

        const val TAG = "VLC/PreferencesActivity"

        val NAME = "VlcSharedPreferences"
        const val VIDEO_RESUME_TIME = "VideoResumeTime"
        const val VIDEO_PAUSED = "VideoPaused"
        const val VIDEO_SPEED = "VideoSpeed"
        const val VIDEO_RESTORE = "video_restore"
        const val VIDEO_RATE = "video_rate"
        const val VIDEO_RATIO = "video_ratio"
        const val AUTO_RESCAN = "auto_rescan"
        const val LOGIN_STORE = "store_login"
        const val KEY_PLAYBACK_RATE = "playback_rate"
        const val KEY_PLAYBACK_SPEED_PERSIST = "playback_speed"
        const val KEY_VIDEO_APP_SWITCH = "video_action_switch"
        const val RESULT_RESCAN = Activity.RESULT_FIRST_USER + 1
        const val RESULT_RESTART = Activity.RESULT_FIRST_USER + 2
        const val RESULT_RESTART_APP = Activity.RESULT_FIRST_USER + 3
        const val RESULT_UPDATE_SEEN_MEDIA = Activity.RESULT_FIRST_USER + 4
        const val RESULT_UPDATE_ARTISTS = Activity.RESULT_FIRST_USER + 5
    }
}
