/*
 * *************************************************************************
 *  FileBrowserFragment.java
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

package org.videolan.vlc.gui.browser

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.hf.OtgAccess
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
open class FileBrowserFragment : BaseBrowserFragment() {

    private var needsRefresh: Boolean = false

    override val categoryTitle: String
        get() = getString(R.string.directories)

    override fun createFragment(): Fragment {
        return FileBrowserFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBrowser()
    }

    override fun onStart() {
        super.onStart()
        if (needsRefresh) viewModel.browseRoot()
    }

    override fun onStop() {
        super.onStop()
        if (isRootDirectory && adapter.isEmpty()) needsRefresh = true
    }

    override fun registerSwiperRefreshlayout() {
        if (!isRootDirectory)
            super.registerSwiperRefreshlayout()
        else
            swipeRefreshLayout.isEnabled = false
    }

    protected open fun setupBrowser() {
        viewModel = if (isRootDirectory)
            ViewModelProviders.of(requireActivity(), BrowserModel.Factory(requireContext(), null, TYPE_FILE, showHiddenFiles)).get(BrowserModel::class.java)
        else
            ViewModelProviders.of(this, BrowserModel.Factory(requireContext(), mrl, TYPE_FILE, showHiddenFiles)).get(BrowserModel::class.java)
    }

    override fun getTitle(): String = if (isRootDirectory)
        categoryTitle
    else {
        when {
            currentMedia != null -> when {
                TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mrl?.removeFileProtocole()) -> getString(R.string.internal_memory)
                this is FilePickerFragment -> currentMedia!!.uri.toString()
                else -> currentMedia!!.title
            }
            this is FilePickerFragment -> mrl ?: ""
            else -> FileUtils.getFileNameFromPath(mrl)
        }
    }


    public override fun browseRoot() {
        viewModel.browseRoot()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
            val mw = item as AbstractMediaWrapper
            if ("otg://" == mw.location) {
                val title = getString(R.string.otg_device_title)
                val otgRoot = OtgAccess.otgRoot
                val rootUri = otgRoot.value
                if (rootUri != null && ExternalMonitor.devices.value.size == 1) {
                    browseOtgDevice(rootUri, title)
                } else {
                    otgRoot.observeForever(object : Observer<Uri> {
                        override fun onChanged(uri: Uri?) {
                            OtgAccess.otgRoot.removeObserver(this)
                            if (uri != null) browseOtgDevice(uri, title)
                        }
                    })
                    OtgAccess.requestOtgRoot(requireActivity())
                }
                return
            }
        }
        super.onClick(v, position, item)
    }

    override fun onCtxAction(position: Int, option: Int) {
        val mw = this.adapter.getItem(position) as AbstractMediaWrapper?
        when (option) {
            CTX_FAV_ADD -> browserFavRepository.addLocalFavItem(mw!!.uri, mw.title, mw.artworkURL)
            else -> super.onCtxAction(position, option)
        }
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (!(this is FilePickerFragment || this is StorageBrowserFragment))
            inflater!!.inflate(R.menu.fragment_option_network, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.ml_menu_save) ?: return
        item.isVisible = !isRootDirectory && mrl!!.startsWith("file")
        runIO(Runnable {
            val isFavorite = mrl != null && browserFavRepository.browserFavExists(Uri.parse(mrl))
            launch {
                item.setIcon(if (isFavorite)
                    R.drawable.ic_menu_bookmark_w
                else
                    R.drawable.ic_menu_bookmark_outline_w)
                item.setTitle(if (isFavorite) R.string.favorites_remove else R.string.favorites_add)
            }
        })
    }

    private fun browseOtgDevice(uri: Uri, title: String) {
        val mw = MLServiceLocator.getAbstractMediaWrapper(uri)
        mw.type = AbstractMediaWrapper.TYPE_DIR
        mw.title = title
        handler.post { browse(mw, true) }
    }
}
