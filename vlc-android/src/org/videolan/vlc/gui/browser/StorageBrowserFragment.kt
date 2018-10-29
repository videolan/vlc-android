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
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.medialibrary.media.Storage
import org.videolan.tools.coroutineScope
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.databinding.BrowserItemBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.CTX_CUSTOM_REMOVE
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_STORAGE
import java.io.File

const val KEY_IN_MEDIALIB = "key_in_medialib"

class StorageBrowserFragment : FileBrowserFragment(), EntryPointsEventsCb {

    internal var mScannedDirectory = false
    private val mProcessingFolders = androidx.collection.SimpleArrayMap<String, CheckBox>()
    private var mSnack: com.google.android.material.snackbar.Snackbar? = null
    private var mAlertDialog: AlertDialog? = null

    override val categoryTitle: String
        get() = getString(R.string.directories_summary)

    override fun createFragment(): androidx.fragment.app.Fragment {
        return StorageBrowserFragment()
    }

    override fun onCreate(bundle: Bundle?) {
        var bundle = bundle
        super.onCreate(bundle)
        adapter = StorageBrowserAdapter(this)
        if (bundle == null) bundle = arguments
        if (bundle != null) mScannedDirectory = bundle.getBoolean(KEY_IN_MEDIALIB)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isRootDirectory && AndroidDevices.showTvUi(view.context)) {
            mSnack = com.google.android.material.snackbar.Snackbar.make(view, R.string.tv_settings_hint, com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
            if (AndroidUtil.isLolliPopOrLater) mSnack?.view?.elevation = view.resources.getDimensionPixelSize(R.dimen.audio_player_elevation).toFloat()
        }
    }

    override fun setupBrowser() {
        viewModel = ViewModelProviders.of(this, BrowserModel.Factory(requireContext(), mrl, TYPE_STORAGE, showHiddenFiles)).get(BrowserModel::class.java)
    }

    override fun onStart() {
        super.onStart()
        VLCApplication.getMLInstance().addEntryPointsEventsCb(this)
        mSnack?.let { it.show() }
    }

    override fun onStop() {
        super.onStop()
        VLCApplication.getMLInstance().removeEntryPointsEventsCb(this)
        mSnack?.let { it.dismiss() }
        mAlertDialog?.let { if (it.isShowing) it.dismiss() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IN_MEDIALIB, mScannedDirectory)
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        super.onPrepareOptionsMenu(menu)
        menu?.findItem(R.id.ml_menu_custom_dir)?.isVisible = true
        menu?.findItem(R.id.ml_menu_equalizer)?.isVisible = false
        menu?.findItem(R.id.ml_menu_refresh)?.isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item?.itemId == R.id.ml_menu_custom_dir) {
            showAddDirectoryDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun browse(media: MediaWrapper, position: Int, scanned: Boolean) {
        val ft = activity?.supportFragmentManager?.beginTransaction()
        val next = createFragment()
        val args = Bundle()
        args.putParcelable(KEY_MEDIA, media)
        args.putBoolean(KEY_IN_MEDIALIB, mScannedDirectory || scanned)
        next.arguments = args
        ft?.replace(R.id.fragment_placeholder, next, media.location)
        ft?.addToBackStack(mrl)
        ft?.commit()
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        if (isRootDirectory) {
            val storage = adapter.getItem(position) as Storage
            coroutineScope.launch {
                val isCustom = viewModel.customDirectoryExists(storage.uri.path)
                if (isCustom) showContext(requireActivity(), this@StorageBrowserFragment, position, item.title, CTX_CUSTOM_REMOVE)
            }
        }
    }

    override fun onCtxAction(position: Int, option: Int) {
        val storage = adapter.getItem(position) as Storage
        viewModel.deleteCustomDirectory(storage.uri.path)
        viewModel.remove(storage)
        (activity as AudioPlayerContainerActivity).updateLib()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mw = MediaWrapper((item as Storage).uri)
        mw.type = MediaWrapper.TYPE_DIR
        browse(mw, position, (DataBindingUtil.findBinding<BrowserItemBinding>(v))?.browserCheckbox?.state == ThreeStatesCheckbox.STATE_CHECKED)
    }

    internal fun processEvent(cbp: CheckBox, mrl: String) {
        cbp.isEnabled = false
        mProcessingFolders.put(mrl, cbp)
    }

    override fun onEntryPointBanned(entryPoint: String, success: Boolean) {}

    override fun onEntryPointUnbanned(entryPoint: String, success: Boolean) {}

    override fun onEntryPointRemoved(entryPoint: String, success: Boolean) {
        var entryPoint = entryPoint
        if (entryPoint.endsWith("/"))
            entryPoint = entryPoint.substring(0, entryPoint.length - 1)
        if (mProcessingFolders.containsKey(entryPoint)) {
            mProcessingFolders.remove(entryPoint)?.let {
                handler.post {
                    it.isEnabled = true
                    if (success) {
                        (adapter as StorageBrowserAdapter).updateMediaDirs(requireContext())
                        adapter.notifyDataSetChanged()
                    } else
                        it.isChecked = true
                }
            }
        }
    }

    override fun onDiscoveryStarted(entryPoint: String) {}

    override fun onDiscoveryProgress(entryPoint: String) {}

    override fun onDiscoveryCompleted(entryPoint: String) {
        var path = entryPoint
        if (path.endsWith("/")) path = path.dropLast(1)
        if (mProcessingFolders.containsKey(path)) {
            val finalPath = path
            handler.post { mProcessingFolders.get(finalPath)?.isEnabled = true }
            (adapter as StorageBrowserAdapter).updateMediaDirs(requireContext())
        }
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
        builder.setPositiveButton(R.string.ok, DialogInterface.OnClickListener { dialog, which ->
            val path = input.text.toString().trim { it <= ' ' }
            val f = File(path)
            if (!f.exists() || !f.isDirectory) {
                UiTools.snacker(view!!, getString(R.string.directorynotfound, path))
                return@OnClickListener
            }

            coroutineScope.launch(CoroutineExceptionHandler{ _, _ ->}) {
                viewModel.addCustomDirectory(f.canonicalPath).join()
                viewModel.browserRoot()
            }
        })
        mAlertDialog = builder.show()
    }
}
