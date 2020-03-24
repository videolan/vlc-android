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

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.tools.RESULT_RESTART
import org.videolan.tools.RESULT_RESTART_APP
import org.videolan.tools.RESULT_UPDATE_ARTISTS
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseActivity

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class PreferencesActivity : BaseActivity() {

    private var mAppBarLayout: AppBarLayout? = null
    override val displayTitle = true

    override fun onCreate(savedInstanceState: Bundle?) {
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
}
