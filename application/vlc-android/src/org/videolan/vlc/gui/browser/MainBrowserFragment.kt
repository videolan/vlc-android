/*
 * ************************************************************************
 *  MainBrowserFragment.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.browser

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.resources.EXTRA_FOR_ESPRESSO
import org.videolan.resources.util.parcelableList
import org.videolan.tools.KEY_BROWSE_NETWORK
import org.videolan.tools.KEY_NAVIGATOR_SCREEN_UNSTABLE
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.Settings
import org.videolan.tools.isStarted
import org.videolan.tools.putSingle
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.dialogs.CONFIRM_PERMISSION_CHANGED
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.KEY_PERMISSION_CHANGED
import org.videolan.vlc.gui.dialogs.NetworkServerDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylistAsync
import org.videolan.vlc.gui.helpers.UiTools.showMediaInfo
import org.videolan.vlc.gui.helpers.hf.OTG_SCHEME
import org.videolan.vlc.gui.helpers.hf.OtgAccess
import org.videolan.vlc.gui.helpers.hf.requestOtgRoot
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.EmptyLoadingStateView
import org.videolan.vlc.gui.view.TitleListView
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_ADD_FOLDER_AND_SUB_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_ADD_FOLDER_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_EDIT
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.isSchemeFavoriteEditable
import org.videolan.vlc.viewmodels.browser.BrowserFavoritesModel
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK
import org.videolan.vlc.viewmodels.browser.getBrowserModel

class MainBrowserFragment : BaseFragment(), View.OnClickListener, CtxActionReceiver {

    private lateinit var networkMonitor: NetworkMonitor
    private var currentCtx: MainBrowserContainer? = null
    private lateinit var browserFavRepository: BrowserFavRepository
    private lateinit var localEntry: TitleListView
    private lateinit var localViewModel: BrowserModel

    private lateinit var favoritesEntry: TitleListView
    private lateinit var favoritesViewModel: BrowserFavoritesModel

    private lateinit var networkEntry: TitleListView
    private lateinit var networkViewModel: BrowserModel

    private var currentAdapterActionMode: BaseBrowserAdapter? = null

    private val containerAdapterAssociation = HashMap<MainBrowserContainer, Pair<BaseBrowserAdapter, ViewModel>>()

    private var requiringOtg = false

    private var displayInList = false
    private val displayInListKey = "main_browser_fragment_display_mode"

    override fun hasFAB() = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.main_browser_fragment, container, false)
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        if (!isStarted()) return false
        @Suppress("UNCHECKED_CAST") val list = currentAdapterActionMode?.multiSelectHelper?.getSelection() as? List<MediaWrapper>
                ?: return false
        if (list.isNotEmpty()) {
            when (item?.itemId) {
                R.id.action_mode_file_play -> MediaUtils.openList(activity, list, 0)
                R.id.action_mode_file_append -> MediaUtils.appendMedia(activity, list)
                R.id.action_mode_file_add_playlist -> requireActivity().addToPlaylist(list)
                R.id.action_mode_file_info -> requireActivity().showMediaInfo(list[0])
                else -> {
                    stopActionMode()
                    return false
                }
            }
        }
        stopActionMode()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        menu.findItem(R.id.ml_menu_display_grid).isVisible = displayInList
        menu.findItem(R.id.ml_menu_display_list).isVisible = !displayInList
        menu.findItem(R.id.add_server_favorite).isVisible = true
        menu.findItem(R.id.browse_network)?.isVisible = true
        menu.findItem(R.id.browse_network)?.isChecked = Settings.getInstance(requireActivity()).getBoolean(KEY_BROWSE_NETWORK, true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_display_list, R.id.ml_menu_display_grid -> {
                displayInList = item.itemId == R.id.ml_menu_display_list
                containerAdapterAssociation.keys.forEach {
                    it.inCards = !displayInList
                }
                localEntry.displayInCards = !displayInList
                favoritesEntry.displayInCards = !displayInList
                networkEntry.displayInCards = !displayInList
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).putSingle(displayInListKey, displayInList)
                true
            }
            R.id.browse_network -> {
                lifecycleScope.launch {
                    item.isChecked = !item.isChecked
                    Settings.getInstance(requireActivity()).putSingle(KEY_BROWSE_NETWORK, item.isChecked)
                    if (!item.isChecked) {
                        networkViewModel.provider.stop()
                        networkViewModel.provider.dataset.clear()
                    }
                    networkViewModel.refresh()
                }
                true
            }
            R.id.add_server_favorite -> {
                showAddServerDialog(null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        currentAdapterActionMode?.itemCount?.let { currentAdapterActionMode?.multiSelectHelper?.toggleActionMode(true, it) }
        mode?.menuInflater?.inflate(R.menu.action_mode_browser_file, menu)
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        currentAdapterActionMode?.itemCount?.let { currentAdapterActionMode?.multiSelectHelper?.toggleActionMode(false, it) }
        actionMode = null
        currentAdapterActionMode?.multiSelectHelper?.clearSelection()
        currentAdapterActionMode = null
    }

    override fun getTitle() = getString(R.string.browse)

    override fun onCreate(savedInstanceState: Bundle?) {
        Settings.getInstance(requireActivity()).edit {
            putBoolean(KEY_NAVIGATOR_SCREEN_UNSTABLE, true)
        }
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
        networkMonitor = NetworkMonitor.getInstance(requireContext())
        super.onCreate(savedInstanceState)
        localViewModel = getBrowserModel(category = TYPE_FILE, url = null)
        favoritesViewModel = BrowserFavoritesModel(requireContext())
        networkViewModel = getBrowserModel(category = TYPE_NETWORK, url = null, mocked = arguments?.parcelableList(EXTRA_FOR_ESPRESSO))
    }

    override fun onPause() {
        super.onPause()
        Settings.getInstance(requireActivity()).edit {
            putBoolean(KEY_NAVIGATOR_SCREEN_UNSTABLE, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        displayInList = Settings.getInstance(requireActivity()).getBoolean(displayInListKey, false)

        //local
        localEntry = view.findViewById(R.id.local_browser_entry)
        val storageBrowserContainer = MainBrowserContainer(isNetwork = false, isFile = true, inCards = !displayInList)
        val storageBrowserAdapter = BaseBrowserAdapter(storageBrowserContainer)
        localEntry.list.adapter = storageBrowserAdapter
        containerAdapterAssociation[storageBrowserContainer] = Pair(storageBrowserAdapter, localViewModel)
        localViewModel.dataset.observe(viewLifecycleOwner) { list ->
            list?.let {
                if (Permissions.canReadStorage(requireActivity())) storageBrowserAdapter.update(it)
                localEntry.loading.state = when {
                    !Permissions.canReadStorage(requireActivity()) -> EmptyLoadingState.MISSING_PERMISSION
                    list.isNotEmpty() -> EmptyLoadingState.NONE
                    localViewModel.loading.value == true -> EmptyLoadingState.LOADING
                    else -> EmptyLoadingState.EMPTY
                }
            }
        }
        localViewModel.loading.observe(viewLifecycleOwner) {
            if (it) localEntry.loading.state = EmptyLoadingState.LOADING else if (!Permissions.canReadStorage(requireActivity())) localEntry.loading.state = EmptyLoadingState.MISSING_PERMISSION
        }
        localViewModel.browseRoot()
        localViewModel.getDescriptionUpdate().observe(viewLifecycleOwner) { pair ->
            if (pair != null) storageBrowserAdapter.notifyItemChanged(pair.first, pair.second)
        }

        favoritesEntry = view.findViewById(R.id.fav_browser_entry)
        favoritesEntry.loading.showNoMedia = false
        favoritesEntry.loading.emptyText = getString(R.string.no_favorite)
        val favoritesBrowserContainer = MainBrowserContainer(isNetwork = false, isFile = true, inCards = !displayInList)
        val favoritesAdapter = BaseBrowserAdapter(favoritesBrowserContainer)
        favoritesEntry.list.adapter = favoritesAdapter
        containerAdapterAssociation[favoritesBrowserContainer] = Pair(favoritesAdapter, favoritesViewModel)
        favoritesViewModel.favorites.observe(viewLifecycleOwner) { list ->
            list.let {
                if (list.isEmpty()) favoritesEntry.setGone() else favoritesEntry.setVisible()
                favoritesAdapter.update(it)
                favoritesEntry.loading.state = when {
                    list.isNotEmpty() -> EmptyLoadingState.NONE
                    localViewModel.loading.value == true -> EmptyLoadingState.LOADING
                    else -> EmptyLoadingState.EMPTY
                }
            }
        }
        favoritesViewModel.provider.loading.observe(viewLifecycleOwner) {
            if (it) localEntry.loading.state = EmptyLoadingState.LOADING
        }
        favoritesViewModel.provider.descriptionUpdate.observe(viewLifecycleOwner) { pair ->
            if (pair != null) favoritesAdapter.notifyItemChanged(pair.first, pair.second)
        }

        networkEntry = view.findViewById(R.id.network_browser_entry)
        networkEntry.loading.showNoMedia = false
        networkEntry.loading.emptyText = getString(R.string.nomedia)
        val networkBrowserContainer = MainBrowserContainer(isNetwork = true, isFile = false, inCards = !displayInList)
        val networkAdapter = BaseBrowserAdapter(networkBrowserContainer)
        networkEntry.list.adapter = networkAdapter
        containerAdapterAssociation[networkBrowserContainer] = Pair(networkAdapter, networkViewModel)
        networkViewModel.dataset.observe(viewLifecycleOwner) { list ->
            list?.let {
                networkAdapter.update(it)
                updateNetworkEmptyView(networkEntry.loading)
                if (networkViewModel.loading.value == false) networkEntry.loading.state = if (list.isEmpty()) EmptyLoadingState.EMPTY else EmptyLoadingState.NONE
            }
        }
        networkViewModel.loading.observe(viewLifecycleOwner) {
            if (it) networkEntry.loading.state = EmptyLoadingState.LOADING
            updateNetworkEmptyView(networkEntry.loading)
        }
        networkViewModel.browseRoot()

        localEntry.displayInCards = !displayInList
        favoritesEntry.displayInCards = !displayInList
        networkEntry.displayInCards = !displayInList

        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_PERMISSION_CHANGED, viewLifecycleOwner) { requestKey, bundle ->
            val changed = bundle.getBoolean(KEY_PERMISSION_CHANGED)
            if (changed) {
                localViewModel.provider.refresh()
                favoritesViewModel.provider.refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (requiringOtg && OtgAccess.otgRoot.value != null) {
            val intent = Intent(requireActivity().applicationContext, SecondaryActivity::class.java)
            val otgMedia = MediaWrapperImpl("otg://".toUri())
            otgMedia.title = getString(R.string.otg_device_title)
            intent.putExtra(KEY_MEDIA, otgMedia)
            intent.putExtra("fragment", SecondaryActivity.FILE_BROWSER)
            startActivity(intent)
        }
        requiringOtg = false
    }

    private fun updateNetworkEmptyView(emptyLoading: EmptyLoadingStateView) {
        if (!Settings.getInstance(requireActivity()).getBoolean(KEY_BROWSE_NETWORK, true)) {
            emptyLoading.state = EmptyLoadingState.EMPTY
            emptyLoading.emptyText = getString(R.string.network_disabled)
            return
        }
        if (networkMonitor.connected) {
            if (networkViewModel.isEmpty()) {
                if (networkViewModel.loading.value == true) {
                    emptyLoading.state = EmptyLoadingState.LOADING
                } else {
                    if (networkMonitor.lanAllowed) {
                        emptyLoading.state = EmptyLoadingState.LOADING
                        emptyLoading.loadingText = getString(R.string.network_shares_discovery)
                    } else {
                        emptyLoading.state = EmptyLoadingState.EMPTY
                        emptyLoading.emptyText = getString(R.string.network_connection_needed)
                    }
                    networkEntry.list.visibility = View.GONE
//                    handler.sendEmptyMessage(MSG_HIDE_LOADING)
                }
            } else {
                emptyLoading.state = EmptyLoadingState.NONE
                networkEntry.list.visibility = View.VISIBLE
            }
        } else {
            emptyLoading.state = EmptyLoadingState.EMPTY
            emptyLoading.emptyText = getString(R.string.network_connection_needed)
            networkEntry.list.visibility = View.GONE
        }
    }

    override fun onClick(v: View) { }

    private fun showAddServerDialog(mw: MediaWrapper?) {
        val fm = try {
            parentFragmentManager
        } catch (e: IllegalStateException) {
            return
        }
        val dialog = NetworkServerDialog()
        mw?.let { dialog.setServer(it) }
        dialog.show(fm, "fragment_add_server")
    }

    inner class MainBrowserContainer(
            override val scannedDirectory: Boolean = false,
            override val mrl: String? = null,
            override val isRootDirectory: Boolean = true,
            override val isNetwork: Boolean,
            override val isFile: Boolean,
            override var inCards: Boolean = true
    ) : BrowserContainer<MediaLibraryItem> by BrowserContainerImpl(scannedDirectory, mrl, isRootDirectory, isNetwork, isFile, inCards) {
        override fun containerActivity() = requireActivity()

        fun requireAdapter() = containerAdapterAssociation[this]?.first
                ?: throw IllegalStateException("Adapter not stored on the association map")

        private fun requireViewModel() = containerAdapterAssociation[this]?.second
                ?: throw IllegalStateException("ViewModel not stored on the association map")

        private fun checkAdapterForActionMode(): Boolean {
            val adapter = requireAdapter()
            if (currentAdapterActionMode == null) {
                currentAdapterActionMode = adapter
            } else if (currentAdapterActionMode != adapter) return false
            return true
        }

        override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
            val mediaWrapper = item as MediaWrapper
            if (actionMode != null) {
                if (!checkAdapterForActionMode()) return
                val adapter = requireAdapter()
                if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                        mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                        mediaWrapper.type == MediaWrapper.TYPE_DIR) {
                    adapter.multiSelectHelper.toggleSelection(position)
                    if (adapter.multiSelectHelper.getSelection().isEmpty()) stopActionMode()
                    invalidateActionMode()
                }
            } else {
                if (item.itemType == MediaLibraryItem.TYPE_MEDIA) {
                    if ("otg://" == item.location) {
                        val rootUri = OtgAccess.otgRoot.value
                        if (rootUri == null) {
                            requiringOtg = true
                            requireActivity().requestOtgRoot()
                            return
                        }
                    }
                }
                val intent = Intent(requireActivity().applicationContext, SecondaryActivity::class.java)
                intent.putExtra(KEY_MEDIA, item)
                intent.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.FILE_BROWSER)
                startActivity(intent)
            }
        }

        override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
            if (item.itemType != MediaLibraryItem.TYPE_MEDIA) return false
            val mediaWrapper = item as MediaWrapper
            if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                    mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                    mediaWrapper.type == MediaWrapper.TYPE_DIR) {
                if (!checkAdapterForActionMode()) return false
                val adapter = requireAdapter()
                adapter.multiSelectHelper.toggleSelection(position)
                if (actionMode == null) startActionMode() else invalidateActionMode()
            } else onCtxClick(v, position, item)
            return true
        }

        override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {
            onClick(v, position, item)
        }

        override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {

            if (actionMode == null && item.itemType == MediaLibraryItem.TYPE_MEDIA) lifecycleScope.launch {
                val viewModel = requireViewModel()

                val mw = item as MediaWrapper
                if (mw.uri.scheme == "content" || mw.uri.scheme == OTG_SCHEME) return@launch
                val flags = FlagSet(ContextOption::class.java).apply {
                    val isEmpty = (viewModel as? BrowserModel)?.isFolderEmpty(mw) != false
                    if (!isEmpty) add(CTX_PLAY)
                    val isFileBrowser = isFile && item.uri.scheme == "file"
                    val favExists = withContext(Dispatchers.IO) { browserFavRepository.browserFavExists(mw.uri) }
                    if (favExists) {
                        if (mw.uri.scheme.isSchemeFavoriteEditable() && withContext(Dispatchers.IO) { browserFavRepository.isFavNetwork(mw.uri) })
                            addAll(CTX_FAV_EDIT, CTX_FAV_REMOVE)
                        else add(CTX_FAV_REMOVE)
                    } else add(CTX_FAV_ADD)
                    if (isFileBrowser) {
                        if (localViewModel.provider.hasMedias(mw)) add(CTX_ADD_FOLDER_PLAYLIST)
                        if (localViewModel.provider.hasSubfolders(mw)) add(CTX_ADD_FOLDER_AND_SUB_PLAYLIST)
                    }
                }
                if (flags.isNotEmpty()) {
                    showContext(requireActivity(), this@MainBrowserFragment, position, item, flags)
                    currentCtx = this@MainBrowserContainer
                }
            }
        }
    }

    override fun onCtxAction(position: Int, option: ContextOption) {
        val adapter = currentCtx?.requireAdapter() ?: return
        val mw = adapter.getItemByPosition(position) as? MediaWrapper
                ?: return
        when (option) {
            CTX_PLAY -> MediaUtils.openMedia(activity, mw)
            CTX_FAV_REMOVE -> lifecycleScope.launch(Dispatchers.IO) { browserFavRepository.deleteBrowserFav(mw.uri) }
            CTX_ADD_FOLDER_PLAYLIST -> requireActivity().addToPlaylistAsync(mw.uri.toString(), false, mw.title)
            CTX_ADD_FOLDER_AND_SUB_PLAYLIST -> requireActivity().addToPlaylistAsync(mw.uri.toString(), true, mw.title)
            CTX_FAV_EDIT -> showAddServerDialog(mw)
            else -> {}
        }
    }
}
