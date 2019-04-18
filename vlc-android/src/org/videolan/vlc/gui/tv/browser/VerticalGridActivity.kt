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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.MainTvActivity
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class VerticalGridActivity : BaseTvActivity(), BrowserActivityInterface {

    private lateinit var mFragment: BrowserFragmentInterface
    private lateinit var mContentLoadingProgressBar: ProgressBar
    private lateinit var mEmptyView: TextView

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_vertical_grid)
        mContentLoadingProgressBar = findViewById(R.id.tv_fragment_progress)
        mEmptyView = findViewById(R.id.tv_fragment_empty)
        if (savedInstanceState == null) {
            val type = intent.getLongExtra(MainTvActivity.BROWSER_TYPE, -1)
            if (type == HEADER_VIDEO) {
                mFragment = AudioBrowserTvFragment.newInstance(CATEGORY_VIDEOS, null)
            } else if (type == HEADER_CATEGORIES) {
                val audioCategory = intent.getLongExtra(AUDIO_CATEGORY, CATEGORY_SONGS)
                val item = intent.getParcelableExtra<MediaLibraryItem>(AUDIO_ITEM)
                if (audioCategory == CATEGORY_SONGS) {
                    mFragment = AudioBrowserTvFragment.newInstance(CATEGORY_SONGS, item)
                } else if (audioCategory == CATEGORY_ALBUMS) {
                    mFragment = AudioBrowserTvFragment.newInstance(CATEGORY_ALBUMS, item)
                } else if (audioCategory == CATEGORY_ARTISTS) {
                    mFragment = AudioBrowserTvFragment.newInstance(CATEGORY_ARTISTS, item)
                } else if (audioCategory == CATEGORY_GENRES) {
                    mFragment = AudioBrowserTvFragment.newInstance(CATEGORY_GENRES, item)
                }
            } else if (type == HEADER_NETWORK) {
                var uri = intent.data
                if (uri == null) uri = intent.getParcelableExtra(KEY_URI)
                if (uri == null)
                    mFragment = BrowserGridFragment()
                else
                    mFragment = NetworkBrowserFragment()
            } else if (type == HEADER_DIRECTORIES) {
                mFragment = DirectoryBrowserFragment()
            } else {
                finish()
                return
            }
            supportFragmentManager.beginTransaction()
                    .add(R.id.tv_fragment_placeholder, mFragment as Fragment)
                    .commit()
        }
    }

    override fun refresh() {
        mFragment.refresh()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (mFragment is DetailsFragment && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_Y)) {
            (mFragment as DetailsFragment).showDetails()
            return true
        }
        if (mFragment is OnKeyPressedListener) {
            if ((mFragment as OnKeyPressedListener).onKeyPressed(keyCode)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }


    override fun showProgress(show: Boolean) {
        runOnUiThread {
            mEmptyView.visibility = View.GONE
            mContentLoadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun updateEmptyView(empty: Boolean) {
        runOnUiThread { mEmptyView.visibility = if (empty) View.VISIBLE else View.GONE }
    }

    fun sort(v: View) {
        (mFragment as Sortable).sort(v)
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
