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
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.widget.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.RefreshModel

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class MediaLibBrowserFragment<T : RefreshModel> : GridFragment(), OnItemViewSelectedListener, OnItemViewClickedListener {
    private var mBackgroundManager: BackgroundManager? = null
    private var mSelectedItem: Any? = null
    lateinit var provider: T
    protected var currentItem: MediaLibraryItem? = null
    protected val preferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentItem = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(Constants.AUDIO_ITEM) as? MediaLibraryItem
        else requireActivity().intent.getParcelableExtra<Parcelable>(Constants.AUDIO_ITEM) as? MediaLibraryItem
        mBackgroundManager = BackgroundManager.getInstance(requireActivity())
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setOnItemViewSelectedListener(this)
        onItemViewClickedListener = this
        mBackgroundManager?.attachToView(view)
    }

    override fun onStart() {
        super.onStart()
        TvUtil.updateBackground(mBackgroundManager, mSelectedItem)
    }

    override fun refresh() {
        provider.refresh()
    }

    override fun updateList() {}

    protected fun update(list: List<MediaLibraryItem>) {
        mAdapter.setItems(list, TvUtil.diffCallback)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?,
                                rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        mSelectedItem = item
        item?.run { TvUtil.updateBackground(mBackgroundManager, this) }
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?,
                               rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
//        if (mediaLibraryItem.itemType == MediaLibraryItem.TYPE_MEDIA) {
//            var position = 0
//            for (i in mDataList.indices) {
//                if (mediaLibraryItem.equals(mDataList[i])) {
//                    position = i
//                    break
//                }
//            }
//            TvUtil.playAudioList(mContext, mDataList as Array<MediaWrapper>, position)
//        } else
        TvUtil.openMedia(mContext, item, row)
    }
}
