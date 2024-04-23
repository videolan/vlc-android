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

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.tools.removeFileScheme
import org.videolan.vlc.R
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.*
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.getBrowserModel

open class FileBrowserFragment : BaseBrowserFragment() {

    private var needsRefresh: Boolean = false

    override val categoryTitle: String
        get() = getString(R.string.directories)

    override fun createFragment(): Fragment {
        return FileBrowserFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBrowser()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as? SecondaryActivity)?.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_up)
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
        viewModel = getBrowserModel(category = TYPE_FILE, url = if (!isRootDirectory) mrl else null)
    }

    override fun getTitle(): String = if (isRootDirectory)
        categoryTitle
    else {
        when {
            currentMedia != null -> when {
                AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY == mrl?.removeFileScheme() -> getString(R.string.internal_memory)
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

    override fun onCtxAction(position: Int, option: ContextOption) {
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
    override fun getStorageDelegate(): IStorageFragmentDelegate? = null

    override val isNetwork = false
    override val isFile = true

    override fun onResume() {
        super.onResume()
        viewModel.resetSort()
        if (viewModel.dataset.value.isNotEmpty())
            viewModel.reSort()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val item = menu.findItem(R.id.ml_menu_save) ?: return
        item.isVisible = !isRootDirectory && mrl!!.startsWith("file")
        lifecycleScope.launchWhenStarted {
            mrl?.let {
                val isScanned = withContext(Dispatchers.IO) { MedialibraryUtils.isScanned(it) }
                menu.findItem(R.id.ml_menu_scan)?.isVisible = !isRootDirectory && it.startsWith("file") && !isScanned
            }
            val isFavorite = mrl != null && browserFavRepository.browserFavExists(mrl!!.toUri())

            item.setIcon(if (isFavorite)
                R.drawable.ic_fav_remove
            else
                R.drawable.ic_fav_add)
            item.setTitle(if (isFavorite) R.string.favorites_remove else R.string.favorites_add)
        }
    }

}
