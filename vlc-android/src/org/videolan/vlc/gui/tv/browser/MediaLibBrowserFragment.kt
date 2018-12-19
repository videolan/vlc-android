/*
 * *************************************************************************
 *  MediaLibBrowserFragment.java
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
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.widget.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.util.AUDIO_ITEM
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.BaseModel

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class MediaLibBrowserFragment<T : BaseModel<out MediaLibraryItem>> : GridFragment(), OnItemViewSelectedListener, OnItemViewClickedListener, Sortable {
    private var mBackgroundManager: BackgroundManager? = null
    private var mSelectedItem: Any? = null
    lateinit var model: T
    protected var currentItem: MediaLibraryItem? = null
    protected val preferences: SharedPreferences by lazy { Settings.getInstance(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentItem = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(AUDIO_ITEM) as? MediaLibraryItem
        else requireActivity().intent.getParcelableExtra<Parcelable>(AUDIO_ITEM) as? MediaLibraryItem
        mBackgroundManager = BackgroundManager.getInstance(requireActivity())
        setOnSearchClickedListener { sort(requireActivity().findViewById(R.id.title_orb)) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setOnItemViewSelectedListener(this)
        onItemViewClickedListener = this
        mBackgroundManager?.attachToView(view)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.orange500)
        requireActivity().findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_menu_sort)
    }

    override fun onStart() {
        super.onStart()
        TvUtil.updateBackground(mBackgroundManager, mSelectedItem)
    }

    override fun refresh() {
        model.refresh()
    }

    protected fun update(list: List<MediaLibraryItem>) {
        mAdapter.setItems(list, TvUtil.diffCallback)
    }

    private var currentArt : String? = null
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?,
                                rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        mSelectedItem = item
        (item as? MediaLibraryItem)?.run {
            if (currentArt == artworkMrl) return@run
            currentArt = artworkMrl
            TvUtil.updateBackground(mBackgroundManager, this)
        }
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?,
                               rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        TvUtil.openMedia(mContext, item, row)
    }

    override fun getVM(): BaseModel<out MediaLibraryItem> = this.model
}
