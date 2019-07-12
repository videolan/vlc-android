/*****************************************************************************
 * VerticalGridActivity.java
 *
 * Copyright © 2014-2016 VLC authors, VideoLAN and VideoLabs
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
package org.videolan.vlc.gui.tv.browser

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.tv_vertical_grid.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.MainTvActivity
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class VerticalGridActivity : BaseTvActivity(), BrowserActivityInterface {

    private lateinit var fragment: BrowserFragmentInterface

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_vertical_grid)
        if (savedInstanceState == null) {
            val type = intent.getLongExtra(MainTvActivity.BROWSER_TYPE, -1)
            if (type == HEADER_VIDEO) {
                fragment = MediaBrowserTvFragment.newInstance(CATEGORY_VIDEOS, null)
            } else if (type == HEADER_CATEGORIES) {
                val audioCategory = intent.getLongExtra(CATEGORY, CATEGORY_SONGS)
                val item = intent.getParcelableExtra<MediaLibraryItem>(ITEM)
                when (audioCategory) {
                    CATEGORY_SONGS -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_SONGS, item)
                    CATEGORY_ALBUMS -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_ALBUMS, item)
                    CATEGORY_ARTISTS -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_ARTISTS, item)
                    CATEGORY_GENRES -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_GENRES, item)
                }
            } else if (type == HEADER_NETWORK) {
                var uri = intent.data
                if (uri == null) uri = intent.getParcelableExtra(KEY_URI)

                val item = if (uri == null) null else MLServiceLocator.getAbstractMediaWrapper(uri)

                fragment = FileBrowserTvFragment.newInstance(TYPE_NETWORK, item)
            } else if (type == HEADER_DIRECTORIES) {
                fragment = FileBrowserTvFragment.newInstance(TYPE_FILE, intent.data?.let { MLServiceLocator.getAbstractMediaWrapper(it) })
            } else {
                finish()
                return
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.tv_fragment_placeholder, fragment as Fragment)
                    .commit()
        }
    }

    override fun refresh() {
        fragment.refresh()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::fragment.isInitialized) {
            if (fragment is DetailsFragment && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_Y)) {
                (fragment as DetailsFragment).showDetails()
                return true
            }
            if (fragment is OnKeyPressedListener) {
                if ((fragment as OnKeyPressedListener).onKeyPressed(keyCode)) {
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }


    override fun showProgress(show: Boolean) {
        runOnUiThread {
            tv_fragment_empty.visibility = View.GONE
            tv_fragment_progress.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun updateEmptyView(empty: Boolean) {
        runOnUiThread { tv_fragment_empty.visibility = if (empty) View.VISIBLE else View.GONE }
    }

    fun sort(v: View) {
        (fragment as Sortable).sort(v)
    }

    interface OnKeyPressedListener {

        /**
         * a key has been pressed
         * @param keyCode the pressed key
         * @return true if the event has been intercepted
         */
        fun onKeyPressed(keyCode: Int): Boolean


    }
}
