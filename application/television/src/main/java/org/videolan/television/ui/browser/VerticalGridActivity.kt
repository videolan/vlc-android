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
package org.videolan.television.ui.browser

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.CATEGORY
import org.videolan.resources.CATEGORY_ALBUMS
import org.videolan.resources.CATEGORY_ARTISTS
import org.videolan.resources.CATEGORY_GENRES
import org.videolan.resources.CATEGORY_PLAYLISTS
import org.videolan.resources.CATEGORY_SONGS
import org.videolan.resources.CATEGORY_VIDEOS
import org.videolan.resources.FAVORITE_TITLE
import org.videolan.resources.HEADER_CATEGORIES
import org.videolan.resources.HEADER_DIRECTORIES
import org.videolan.resources.HEADER_MOVIES
import org.videolan.resources.HEADER_NETWORK
import org.videolan.resources.HEADER_PLAYLISTS
import org.videolan.resources.HEADER_TV_SHOW
import org.videolan.resources.HEADER_VIDEO
import org.videolan.resources.ITEM
import org.videolan.resources.KEY_CURRENT_MEDIA
import org.videolan.resources.KEY_URI
import org.videolan.resources.util.parcelable
import org.videolan.television.R
import org.videolan.television.databinding.TvVerticalGridBinding
import org.videolan.television.ui.MainTvActivity
import org.videolan.television.ui.browser.interfaces.BrowserActivityInterface
import org.videolan.television.ui.browser.interfaces.DetailsFragment
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.interfaces.BrowserFragmentInterface
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class VerticalGridActivity : BaseTvActivity(), BrowserActivityInterface {

    private lateinit var fragment: BrowserFragmentInterface
    private lateinit var binding: TvVerticalGridBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TvVerticalGridBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        if (savedInstanceState == null) {
            val type = intent.getLongExtra(MainTvActivity.BROWSER_TYPE, -1)
            when (type) {
                HEADER_VIDEO -> {
                    fragment = MediaBrowserTvFragment.newInstance(CATEGORY_VIDEOS, null)
                }

                HEADER_CATEGORIES -> {
                    val audioCategory = intent.getLongExtra(CATEGORY, CATEGORY_SONGS)
                    val item = intent.parcelable<MediaLibraryItem>(ITEM)
                    when (audioCategory) {
                        CATEGORY_SONGS -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_SONGS, item)
                        CATEGORY_ALBUMS -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_ALBUMS, item)
                        CATEGORY_ARTISTS -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_ARTISTS, item)
                        CATEGORY_GENRES -> fragment = MediaBrowserTvFragment.newInstance(CATEGORY_GENRES, item)
                    }
                }

                HEADER_NETWORK -> {
                    var uri = intent.data
                    if (uri == null) uri = intent.parcelable(KEY_URI)

                    val item = if (uri == null) null else MLServiceLocator.getAbstractMediaWrapper(uri)
                    if (item != null && intent.hasExtra(FAVORITE_TITLE)) item.title = intent.getStringExtra(FAVORITE_TITLE)

                    fragment = FileBrowserTvFragment.newInstance(
                        type = TYPE_NETWORK,
                        item = item,
                        selectedItem = null,
                        root = item === null
                    )
                }

                HEADER_MOVIES, HEADER_TV_SHOW -> {
                    fragment = MediaScrapingBrowserTvFragment.newInstance(type)
                }

                HEADER_DIRECTORIES -> {
                    fragment = FileBrowserTvFragment.newInstance(
                        type = TYPE_FILE,
                        item = intent.data?.let { MLServiceLocator.getAbstractMediaWrapper(it) },
                        selectedItem = intent.getStringExtra(KEY_CURRENT_MEDIA),
                        root = true
                    )
                }

                HEADER_PLAYLISTS -> {
                    fragment = MediaBrowserTvFragment.newInstance(CATEGORY_PLAYLISTS, null)
                }

                else -> {
                    finish()
                    return
                }
            }
            if (!::fragment.isInitialized && BuildConfig.BETA) Log.i("VerticalGridActivity", "Fragment not initialized: $type")
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
            try {
                if ((supportFragmentManager.fragments[0] as? OnKeyPressedListener)?.onKeyPressed(keyCode) == true) {
                    return true
                }
            } catch (e: IndexOutOfBoundsException) {
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun showProgress(show: Boolean) {
        lifecycleScope.launch {
            binding.tvFragmentEmpty.visibility = View.GONE
            binding.tvFragmentProgress.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun updateEmptyView(empty: Boolean) {
        lifecycleScope.launch { binding.tvFragmentEmpty.visibility = if (empty) View.VISIBLE else View.GONE }
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
