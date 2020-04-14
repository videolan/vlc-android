/*
 * *************************************************************************
 *  NetworkBrowserFragment.kt
 * **************************************************************************
 *  Copyright © 2015-2019 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.browser

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.CTX_FAV_ADD
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.util.DialogDelegate
import org.videolan.vlc.util.IDialogManager
import org.videolan.vlc.util.showVlcDialog
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK
import org.videolan.vlc.viewmodels.browser.getBrowserModel

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class NetworkBrowserFragment : BaseBrowserFragment(), IDialogManager {

    private val dialogsDelegate = DialogDelegate()
    private lateinit var networkMonitor : NetworkMonitor

    override fun createFragment() = NetworkBrowserFragment()

    override val categoryTitle: String
        get() = getString(R.string.network_browsing)

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        dialogsDelegate.observeDialogs(this, this)
        networkMonitor = NetworkMonitor.getInstance(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = getBrowserModel(TYPE_NETWORK, mrl, showHiddenFiles)
        if (isRootDirectory) swipeRefreshLayout.isEnabled = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fragment_option_network, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun containerActivity() = requireActivity()

    override val isNetwork = true
    override val isFile = false

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.ml_menu_save)
        item.isVisible = !isRootDirectory
        lifecycleScope.launchWhenStarted {
            val isFavorite = mrl != null && browserFavRepository.browserFavExists(Uri.parse(mrl))
            item.setIcon(if (isFavorite) R.drawable.ic_menu_bookmark_w else R.drawable.ic_menu_bookmark_outline_w)
            item.setTitle(if (isFavorite) R.string.favorites_remove else R.string.favorites_add)
        }
    }

    override fun onStart() {
        super.onStart()
        fabPlay?.setImageResource(if (isRootDirectory) R.drawable.ic_fab_add else R.drawable.ic_fab_play)
        fabPlay?.setOnClickListener(this)
    }

    override fun refresh() {
        if (networkMonitor.connected)
            super.refresh()
        else {
            updateEmptyView()
            adapter.clear()
        }
    }

    override fun fireDialog(dialog: Dialog) {
        showVlcDialog(dialog)
    }

    override fun dialogCanceled(dialog: Dialog?) {
        when(dialog) {
            is Dialog.LoginDialog -> goBack()
            is Dialog.ErrorMessage -> {
               view?.let { Snackbar.make(it, "${dialog.title}: ${dialog.text}", Snackbar.LENGTH_LONG).show() }
               goBack()
            }
        }
    }

    override fun onCtxAction(position: Int, option: Long) {
        val mw = this.adapter.getItem(position) as MediaWrapper
        when (option) {
            CTX_FAV_ADD -> lifecycleScope.launch { browserFavRepository.addNetworkFavItem(mw.uri, mw.title, mw.artworkURL) }
            else -> super.onCtxAction(position, option)
        }
    }

    override fun browseRoot() {}

    /**
     * Update views visibility and emptiness info
     */
    override fun updateEmptyView() {
        if (networkMonitor.connected) {
            if (viewModel.isEmpty()) {
                if (swipeRefreshLayout.isRefreshing) {
                    binding.emptyLoading.state = EmptyLoadingState.LOADING
                    binding.networkList.visibility = View.GONE
                } else {
                    if (isRootDirectory) {
                        if (networkMonitor.lanAllowed) {
                            binding.emptyLoading.state = EmptyLoadingState.LOADING
                            binding.emptyLoading.loadingText = R.string.network_shares_discovery
                        } else {
                            binding.emptyLoading.state = EmptyLoadingState.EMPTY
                            binding.emptyLoading.emptyText = R.string.network_connection_needed
                        }
                    } else {
                        binding.emptyLoading.state = EmptyLoadingState.EMPTY
                        binding.emptyLoading.emptyText = R.string.network_empty
                    }
                    binding.networkList.visibility = View.GONE
                    handler.sendEmptyMessage(MSG_HIDE_LOADING)
                }
            } else {
                binding.emptyLoading.state = EmptyLoadingState.NONE
                binding.networkList.visibility = View.VISIBLE
            }
        } else {
            binding.emptyLoading.state = EmptyLoadingState.EMPTY
            binding.emptyLoading.emptyText = R.string.network_connection_needed
            binding.networkList.visibility = View.GONE
            binding.showFavorites = false
        }
    }
}
