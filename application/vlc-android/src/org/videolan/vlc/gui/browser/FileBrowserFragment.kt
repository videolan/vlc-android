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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.CTX_FAV_ADD
import org.videolan.tools.removeFileProtocole
import org.videolan.vlc.ExternalMonitor
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.hf.OtgAccess
import org.videolan.vlc.gui.helpers.hf.requestOtgRoot
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.getBrowserModel

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
        viewModel = getBrowserModel(category = TYPE_FILE, url = if (!isRootDirectory) mrl else null, showHiddenFiles = showHiddenFiles)
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
            val mw = item as MediaWrapper
            if ("otg://" == mw.location) {
                val title = getString(R.string.otg_device_title)
                val rootUri = OtgAccess.otgRoot.value
                if (rootUri != null && ExternalMonitor.devices.size == 1) {
                    browseOtgDevice(rootUri, title)
                } else {
                    lifecycleScope.launchWhenStarted {
                        val otgRoot = OtgAccess.otgRoot.asFlow()
                        val uri = otgRoot.filterNotNull().first()
                        browseOtgDevice(uri, title)
                    }
                    requireActivity().requestOtgRoot()
                }
                return
            }
        }
        super.onClick(v, position, item)
    }

    override fun onCtxAction(position: Int, option: Long) {
        val mw = this.adapter.getItem(position) as MediaWrapper?
        when (option) {
            CTX_FAV_ADD -> lifecycleScope.launch { browserFavRepository.addLocalFavItem(mw!!.uri, mw.title, mw.artworkURL) }
            else -> super.onCtxAction(position, option)
        }
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (!(this is FilePickerFragment || this is StorageBrowserFragment))
            inflater.inflate(R.menu.fragment_option_network, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun containerActivity() = requireActivity()

    override val isNetwork = false
    override val isFile = true

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.ml_menu_save) ?: return
        item.isVisible = !isRootDirectory && mrl!!.startsWith("file")
        lifecycleScope.launchWhenStarted {
            mrl?.let {
                val isScanned = withContext(Dispatchers.IO) { MedialibraryUtils.isScanned(it) }
                menu.findItem(R.id.ml_menu_scan)?.isVisible = !isRootDirectory && it.startsWith("file") && !isScanned
            }
            val isFavorite = mrl != null && browserFavRepository.browserFavExists(Uri.parse(mrl))
            item.setIcon(if (isFavorite)
                R.drawable.ic_menu_bookmark_w
            else
                R.drawable.ic_menu_bookmark_outline_w)
            item.setTitle(if (isFavorite) R.string.favorites_remove else R.string.favorites_add)
        }
    }

    private fun browseOtgDevice(uri: Uri, title: String) {
        val mw = MLServiceLocator.getAbstractMediaWrapper(uri)
        mw.type = MediaWrapper.TYPE_DIR
        mw.title = title
        handler.post { browse(mw, true) }
    }
}
