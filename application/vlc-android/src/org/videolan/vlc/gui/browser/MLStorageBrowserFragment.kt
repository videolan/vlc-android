/*
 * ************************************************************************
 *  MLStorageBrowserFragment.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.AppCompatEditText
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.setGone
import org.videolan.tools.stripTrailingSlash
import org.videolan.vlc.R
import org.videolan.vlc.databinding.BrowserItemBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.EmptyLoadingStateView
import org.videolan.vlc.gui.view.TitleListView
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_CUSTOM_REMOVE
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK
import org.videolan.vlc.viewmodels.browser.TYPE_STORAGE
import org.videolan.vlc.viewmodels.browser.getBrowserModel
import java.io.File

private const val FROM_ONBOARDING = "from_onboarding"

class MLStorageBrowserFragment : BaseFragment(), IStorageFragmentDelegate by StorageFragmentDelegate(), CtxActionReceiver {

    private lateinit var localEntry: TitleListView
    private lateinit var networkEntry: TitleListView
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var localViewModel: BrowserModel
    private lateinit var networkViewModel: BrowserModel
    private lateinit var storageBrowserAdapter: StorageBrowserAdapter

    private var alertDialog: AlertDialog? = null

    override fun getTitle() = getString(if (arguments?.getBoolean(FROM_ONBOARDING, false) == true) R.string.medialibrary_directories else  R.string.directories_summary)

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

    override fun onDestroyActionMode(mode: ActionMode?) { }

    override fun hasFAB() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        networkMonitor = NetworkMonitor.getInstance(requireContext())
        withContext(requireActivity())
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        addRootsCallback()
    }

    override fun onStop() {
        super.onStop()
        removeRootsCallback()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.main_browser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val favoritesEntry = view.findViewById<View>(R.id.fav_browser_entry)
        favoritesEntry.setGone()

        localEntry = view.findViewById(R.id.local_browser_entry)
        storageBrowserAdapter = StorageBrowserAdapter(getBrowserContainer(false))
        localEntry.list.adapter = storageBrowserAdapter
        localViewModel = getBrowserModel(category = TYPE_STORAGE, url = null)
        localViewModel.dataset.observe(viewLifecycleOwner) { list ->
            list?.let {
                storageBrowserAdapter.update(it)
                localEntry.loading.state = when {
                    list.isNotEmpty() -> EmptyLoadingState.NONE
                    localViewModel.loading.value == true -> EmptyLoadingState.LOADING
                    else -> EmptyLoadingState.EMPTY
                }
            }
        }
        localViewModel.loading.observe(viewLifecycleOwner) {
            if (it) localEntry.loading.state = EmptyLoadingState.LOADING
        }
        localViewModel.browseRoot()
        localViewModel.getDescriptionUpdate().observe(viewLifecycleOwner) { pair ->
            if (pair != null) storageBrowserAdapter.notifyItemChanged(pair.first, pair.second)
        }

        networkEntry = view.findViewById(R.id.network_browser_entry)
        networkEntry.loading.showNoMedia = false
        networkEntry.loading.emptyText = getString(R.string.nomedia)
        val networkAdapter = StorageBrowserAdapter(getBrowserContainer(true))
        networkEntry.list.adapter = networkAdapter
        networkViewModel = getBrowserModel(category = TYPE_NETWORK, url = null)
        networkViewModel.dataset.observe(viewLifecycleOwner) { list ->
            list?.let {
                val filtered = it.filter { item -> item is MediaWrapper && item.uri?.scheme == "smb" }
                networkAdapter.update(filtered)
                updateNetworkEmptyView(networkEntry.loading)
                if (networkViewModel.loading.value == false) networkEntry.loading.state = if (list.isEmpty()) EmptyLoadingState.EMPTY else EmptyLoadingState.NONE
            }
        }
        networkViewModel.loading.observe(viewLifecycleOwner) {
            if (it) networkEntry.loading.state = EmptyLoadingState.LOADING
            updateNetworkEmptyView(networkEntry.loading)
        }
        networkViewModel.browseRoot()

        localEntry.displayInCards = false
        networkEntry.displayInCards = false
        withAdapters(arrayOf(storageBrowserAdapter, networkAdapter))
    }

    private fun updateNetworkEmptyView(emptyLoading: EmptyLoadingStateView) {
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

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val onboarding = arguments?.getBoolean(FROM_ONBOARDING, false) == true
        menu.findItem(R.id.ml_menu_custom_dir)?.isVisible = !onboarding
        menu.findItem(R.id.ml_menu_refresh)?.isVisible = false
        menu.findItem(R.id.ml_menu_add_playlist)?.isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ml_menu_custom_dir) {
            showAddDirectoryDialog()
            return true
        }
        return false
    }

    private fun showAddDirectoryDialog() {
        val context = activity
        val builder = AlertDialog.Builder(context!!)
        val input = AppCompatEditText(context)
        input.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        builder.setTitle(R.string.add_custom_path)
        builder.setMessage(R.string.add_custom_path_description)
        builder.setView(input)
        builder.setNegativeButton(R.string.cancel) { _, _ -> }
        builder.setPositiveButton(R.string.ok, DialogInterface.OnClickListener { _, _ ->
            val path = input.text.toString().trim { it <= ' ' }
            val f = File(path)
            if (!f.exists() || !f.isDirectory) {
                UiTools.snacker(requireActivity(), getString(R.string.directorynotfound, path))
                return@OnClickListener
            }

            lifecycleScope.launch(CoroutineExceptionHandler { _, _ -> }) {
                localViewModel.addCustomDirectory(f.canonicalPath).join()
                localViewModel.browseRoot()
            }
        })
        alertDialog = builder.show()
    }

    private fun getBrowserContainer(isNetwork: Boolean) = object : BrowserContainer<MediaLibraryItem> {
        override fun containerActivity() = requireActivity()
        override fun getStorageDelegate(): IStorageFragmentDelegate? = this@MLStorageBrowserFragment
        override val scannedDirectory = false
        override val mrl: String? = null
        override val isRootDirectory = true
        override val isNetwork = isNetwork
        override val isFile = !isNetwork
        override var inCards = false

        override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
            val mw = (item as? MediaWrapper)?.let { MLServiceLocator.getAbstractMediaWrapper(it.uri) } ?:(item as? Storage)?.let { MLServiceLocator.getAbstractMediaWrapper(it.uri) }
                    ?: return
            mw.type = MediaWrapper.TYPE_DIR
            browse(mw, (DataBindingUtil.findBinding<BrowserItemBinding>(v))?.browserCheckbox?.state == ThreeStatesCheckbox.STATE_CHECKED, StorageBrowserFragment(), "root")
        }

        override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
            return false
        }

        override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {}

        override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
            if (isRootDirectory) {
                val storage = storageBrowserAdapter.getItemByPosition(position) as Storage
                val path = storage.uri.path ?: return
                lifecycleScope.launchWhenStarted {
                    val isCustom = localViewModel.customDirectoryExists(path.stripTrailingSlash())
                    if (isCustom && isAdded) showContext(requireActivity(), this@MLStorageBrowserFragment, position, item, FlagSet(ContextOption::class.java).apply { add(CTX_CUSTOM_REMOVE) })
                }
            }
        }

        override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) { }

        override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

        override fun onItemFocused(v: View, item: MediaLibraryItem) {}
    }
    override fun onCtxAction(position: Int, option: ContextOption) {
        val storage = storageBrowserAdapter.getItemByPosition(position) as Storage
        val path = storage.uri.path ?: return
        localViewModel.deleteCustomDirectory(path)
        localViewModel.remove(storage)
        (activity as AudioPlayerContainerActivity).updateLib()
    }

    companion object {
        fun newInstance(onboarding:Boolean) = MLStorageBrowserFragment().apply { arguments = Bundle().apply { putBoolean(FROM_ONBOARDING, onboarding) } }
    }
}