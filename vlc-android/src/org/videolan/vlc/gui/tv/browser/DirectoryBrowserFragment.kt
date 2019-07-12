/*
 * *************************************************************************
 *  NetworkBrowseFragment.java
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
import android.os.Build
import android.os.Bundle
import androidx.leanback.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.util.UPDATE_DESCRIPTION
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class DirectoryBrowserFragment : MediaSortedFragment<BrowserModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this, BrowserModel.Factory(requireContext(), uri.toString(), TYPE_FILE, showHiddenFiles)).get(BrowserModel::class.java)
        viewModel.categories.observe(this, Observer<Map<String, List<MediaLibraryItem>>> { stringListMap -> if (stringListMap != null) update(stringListMap) })
        ExternalMonitor.storageUnplugged.observe(this, Observer { storageUri ->
            if (uri != null && "file" == uri!!.scheme) {
                val currentPath = uri!!.path
                val unpluggedPath = storageUri.path
                if (currentPath != null && unpluggedPath != null && currentPath.startsWith(unpluggedPath)) {
                    val activity = activity
                    activity?.finish()
                }
            }
        })
        viewModel.getDescriptionUpdate().observe(this, Observer { pair ->
            val position = pair.component1()
            val adapter = adapter as ArrayObjectAdapter
            var index = -1
            for (i in 0 until adapter.size()) {
                val objectAdapter = (adapter.get(i) as ListRow).adapter
                if (position > index + objectAdapter.size())
                    index += objectAdapter.size()
                else
                    for (j in 0 until objectAdapter.size()) {
                        if (++index == position) objectAdapter.notifyItemRangeChanged(j, 1, UPDATE_DESCRIPTION)
                    }
            }
        })
    }

    override fun onItemClicked(viewHolder: Presenter.ViewHolder, item: Any, viewHolder1: RowPresenter.ViewHolder, row: Row) {
        if (item is AbstractMediaWrapper && item.type == AbstractMediaWrapper.TYPE_DIR) viewModel.saveList(item)
        super.onItemClicked(viewHolder, item, viewHolder1, row)
    }

    companion object {

        const val TAG = "VLC/DirectoryBrowserFragment"
    }
}
