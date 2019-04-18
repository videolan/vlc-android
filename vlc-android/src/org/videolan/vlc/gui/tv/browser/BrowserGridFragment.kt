/*
 * *************************************************************************
 *  BrowserGridFragment.java
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
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.leanback.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.gui.tv.DetailsActivity
import org.videolan.vlc.gui.tv.MediaItemDetails
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface
import org.videolan.vlc.gui.tv.browser.interfaces.DetailsFragment
import org.videolan.vlc.util.HEADER_NETWORK
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.browser.NetworkModel

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class BrowserGridFragment : GridFragment(), OnItemViewSelectedListener, OnItemViewClickedListener, DetailsFragment {

    private var mItemSelected: MediaWrapper? = null
    private var provider: NetworkModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setOnItemViewSelectedListener(this)
        onItemViewClickedListener = this
        val showHiddenFiles = Settings.getInstance(requireContext()).getBoolean("browser_show_hidden_files", false)
        provider = ViewModelProviders.of(this, NetworkModel.Factory(requireContext(), null, showHiddenFiles)).get(NetworkModel::class.java)
        provider!!.dataset.observe(this, Observer<List<MediaLibraryItem>> { mediaLibraryItems -> adapter.setItems(mediaLibraryItems, TvUtil.diffCallback) })
        ExternalMonitor.connected.observe(this, Observer { connected ->
            if (connected != null && connected) provider!!.refresh()
            //TODO empty/disconnected view
        })
    }

    override fun onPause() {
        super.onPause()
        (context as BrowserActivityInterface).updateEmptyView(false)
    }

    override fun showDetails() {
        if (mItemSelected!!.type == MediaWrapper.TYPE_DIR) {
            val intent = Intent(activity, DetailsActivity::class.java)
            // pass the item information
            intent.putExtra("media", mItemSelected)
            intent.putExtra("item", MediaItemDetails(mItemSelected!!.title,
                    mItemSelected!!.artist, mItemSelected!!.album,
                    mItemSelected!!.location, mItemSelected!!.artworkURL))
            startActivity(intent)
        }
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder, item: Any, rowViewHolder: RowPresenter.ViewHolder, row: Row) {
        mItemSelected = item as MediaWrapper
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any, rowViewHolder: RowPresenter.ViewHolder, row: Row) {
        val media = item as MediaWrapper
        if (media.type == MediaWrapper.TYPE_DIR)
            TvUtil.browseFolder(requireActivity(), HEADER_NETWORK, item.uri)
        else
            TvUtil.openMedia(requireActivity(), item, provider)
    }
}
