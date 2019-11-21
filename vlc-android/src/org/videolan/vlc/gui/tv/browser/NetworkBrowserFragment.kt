/*
 * *************************************************************************
 *  NetworkBrowseFragment.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.leanback.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.dialogs.VlcLoginDialog
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.browser.NetworkModel

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class NetworkBrowserFragment : MediaSortedFragment<NetworkModel>(), IDialogManager {

    private val dialogsDelegate = DialogDelegate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this, NetworkModel.Factory(requireContext(), uri!!.toString(), showHiddenFiles)).get(NetworkModel::class.java)
        viewModel.categories.observe(this, Observer<Map<String, List<MediaLibraryItem>>> { stringListMap -> if (stringListMap != null) update(stringListMap) })
        ExternalMonitor.connected.observe(this, Observer { connected -> refresh(connected!!) })
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
        dialogsDelegate.observeDialogs(this, this)
    }

    fun refresh(connected: Boolean) {
        if (connected) refresh()
        //TODO Disconnected view
    }

    override fun onItemClicked(viewHolder: Presenter.ViewHolder, item: Any, viewHolder1: RowPresenter.ViewHolder, row: Row) {
        if (item is AbstractMediaWrapper && item.type == AbstractMediaWrapper.TYPE_DIR) viewModel.saveList(item)
        super.onItemClicked(viewHolder, item, viewHolder1, row)
    }

    override fun fireDialog(dialog: Dialog) = showVlcDialog(dialog)

    override fun dialogCanceled(dialog: Dialog?) {
        when(dialog) {
            is Dialog.LoginDialog -> activity?.finish()
            is Dialog.ErrorMessage -> {
                view?.let { Snackbar.make(it, "${dialog.title}: ${dialog.text}", Snackbar.LENGTH_LONG).show() }
                activity?.finish()
            }
        }
    }
}
