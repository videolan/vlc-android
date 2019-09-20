/*
 * *************************************************************************
 *  SecondaryActivity.java
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

package org.videolan.vlc.gui

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.vlc.R
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.StorageBrowserFragment
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.KEY_FOLDER
import org.videolan.vlc.util.KEY_GROUP
import org.videolan.vlc.util.RESULT_RESCAN

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class SecondaryActivity : ContentActivity() {

    private var fragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.secondary)
        initAudioPlayerContainerActivity()

        val fph = findViewById<View>(R.id.fragment_placeholder)
        val params = fph.layoutParams as CoordinatorLayout.LayoutParams

        if (AndroidDevices.isTv) {
            TvUtil.applyOverscanMargin(this)
            params.topMargin = resources.getDimensionPixelSize(UiTools.getResourceFromAttribute(this, R.attr.actionBarSize))
        } else
            params.behavior = AppBarLayout.ScrollingViewBehavior()
        fph.requestLayout()

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if (supportFragmentManager.findFragmentById(R.id.fragment_placeholder) == null) {
            val fragmentId = intent.getStringExtra(KEY_FRAGMENT)
            fetchSecondaryFragment(fragmentId)
            if (fragment == null) {
                finish()
                return
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_placeholder, fragment!!)
                    .commit()
        }
    }

    override fun onResume() {
        overridePendingTransition(0, 0)
        super.onResume()
    }

    override fun onPause() {
        if (isFinishing)
            overridePendingTransition(0, 0)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == RESULT_RESCAN) this.reloadLibrary()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.ml_menu_refresh -> {
                val ml = AbstractMedialibrary.getInstance()
                if (!ml.isWorking) reloadLibrary()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun fetchSecondaryFragment(id: String) {
        when (id) {
            ALBUMS_SONGS -> {
                fragment = AudioAlbumsSongsFragment()
                val args = Bundle()
                args.putParcelable(AudioBrowserFragment.TAG_ITEM, intent.getParcelableExtra<Parcelable>(AudioBrowserFragment.TAG_ITEM))
                fragment!!.arguments = args
            }
            ABOUT -> fragment = AboutFragment()
            VIDEO_GROUP_LIST -> {
                fragment = VideoGridFragment().apply {
                    arguments = Bundle(1).apply {
                        putParcelable(KEY_FOLDER, intent.getParcelableExtra<Parcelable>(KEY_FOLDER))
                        putParcelable(KEY_GROUP, intent.getParcelableExtra<Parcelable>(KEY_GROUP))
                    }
                }
            }
            STORAGE_BROWSER -> fragment = StorageBrowserFragment()
            else -> throw IllegalArgumentException("Wrong fragment id.")
        }
    }

    companion object {
        const val TAG = "VLC/SecondaryActivity"

        const val ACTIVITY_RESULT_SECONDARY = 3

        const val KEY_FRAGMENT = "fragment"

        const val ALBUMS_SONGS = "albumsSongs"
        const val ABOUT = "about"
        const val VIDEO_GROUP_LIST = "videoGroupList"
        const val STORAGE_BROWSER = "storage_browser"
    }
}
