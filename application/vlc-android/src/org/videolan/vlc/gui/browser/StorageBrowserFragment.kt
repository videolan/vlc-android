/*
 * *************************************************************************
 *  StorageBrowserFragment.java
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

import android.annotation.TargetApi
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.databinding.BrowserItemBinding
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.viewmodels.browser.TYPE_STORAGE
import org.videolan.vlc.viewmodels.browser.getBrowserModel
import java.io.File

const val KEY_IN_MEDIALIB = "key_in_medialib"

class StorageBrowserFragment : FileBrowserFragment(), BrowserContainer<MediaLibraryItem>, IStorageFragmentDelegate by StorageFragmentDelegate() {

    override var scannedDirectory = false
    private var snack: com.google.android.material.snackbar.Snackbar? = null
    private var alertDialog: AlertDialog? = null
    override var inCards = false

    override val categoryTitle: String
        get() = getString(R.string.directories_summary)

    override fun createFragment(): Fragment {
        return StorageBrowserFragment()
    }

    override fun hasFAB(): Boolean {
        if (requireActivity() is SecondaryActivity) return false
        return super.hasFAB()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = StorageBrowserAdapter(this)
        (adapter as StorageBrowserAdapter).bannedFolders = Medialibrary.getInstance().bannedFolders().toList()
        val bundle = savedInstanceState ?: arguments
        if (bundle != null) scannedDirectory = bundle.getBoolean(KEY_IN_MEDIALIB)
        withContext(requireActivity())
        withAdapters(arrayOf(adapter as StorageBrowserAdapter))
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isRootDirectory && Settings.showTvUi) {
            snack = com.google.android.material.snackbar.Snackbar.make(view, R.string.tv_settings_hint, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
            if (AndroidUtil.isLolliPopOrLater) snack?.view?.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        }
    }

    override fun setupBrowser() {
        viewModel = getBrowserModel(TYPE_STORAGE, mrl)
    }

    override fun onStart() {
        super.onStart()
        addRootsCallback()
        snack?.show()
        lifecycleScope.launchWhenStarted { if (isAdded) (adapter as StorageBrowserAdapter).updateListState(requireContext()) }
        addBannedFoldersCallback { folder, _ ->
            (adapter as StorageBrowserAdapter).bannedFolders = Medialibrary.getInstance().bannedFolders().toList()
            adapter.dataset.forEachIndexed{ index, mediaLibraryItem ->
                if ("${Tools.mlEncodeMrl(((mediaLibraryItem as Storage).uri.toString()))}/" == folder) adapter.notifyItemChanged(index)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        removeRootsCallback()
        snack?.dismiss()
        alertDialog?.let { if (it.isShowing) it.dismiss() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IN_MEDIALIB, scannedDirectory)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_custom_dir)?.isVisible = true
        menu.findItem(R.id.ml_menu_refresh)?.isVisible = false
        menu.findItem(R.id.ml_menu_add_playlist)?.isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ml_menu_custom_dir) {
            showAddDirectoryDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mw = (item as? Storage)?.let { MLServiceLocator.getAbstractMediaWrapper(it.uri) } ?: return
        mw.type = MediaWrapper.TYPE_DIR
        browse(mw, scannedDirectory || (DataBindingUtil.findBinding<BrowserItemBinding>(v))?.browserCheckbox?.state == ThreeStatesCheckbox.STATE_CHECKED, createFragment(),if (isRootDirectory) "root" else if (currentMedia != null) currentMedia?.uri.toString() else mrl!!)
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        (item as Storage).uri.path?.let { path ->
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.toggleBanState(path)
            }
        }
        return true
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

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
                viewModel.addCustomDirectory(f.canonicalPath).join()
                viewModel.browseRoot()
            }
        })
        alertDialog = builder.show()
    }

    override fun containerActivity() = requireActivity()
    override fun getStorageDelegate(): IStorageFragmentDelegate? = this

    override val isNetwork = false
    override val isFile = true
}
