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
import androidx.collection.SimpleArrayMap
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.CTX_CUSTOM_REMOVE
import org.videolan.tools.*
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.BrowserItemBinding
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.ThreeStatesCheckbox
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.onboarding.OnboardingActivity
import org.videolan.vlc.viewmodels.browser.TYPE_STORAGE
import org.videolan.vlc.viewmodels.browser.getBrowserModel
import java.io.File

const val KEY_IN_MEDIALIB = "key_in_medialib"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class StorageBrowserFragment : FileBrowserFragment(), EntryPointsEventsCb, BrowserContainer<MediaLibraryItem> {

    override var scannedDirectory = false
    private val processingFolders = SimpleArrayMap<String, CheckBox>()
    private var snack: com.google.android.material.snackbar.Snackbar? = null
    private var alertDialog: AlertDialog? = null
    override val inCards = false

    override val categoryTitle: String
        get() = getString(R.string.directories_summary)

    override fun createFragment(): Fragment {
        return StorageBrowserFragment()
    }

    override fun onCreate(bundle: Bundle?) {
        var bundle = bundle
        super.onCreate(bundle)
        adapter = StorageBrowserAdapter(this)
        if (bundle == null) bundle = arguments
        if (bundle != null) scannedDirectory = bundle.getBoolean(KEY_IN_MEDIALIB)
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
        viewModel = getBrowserModel(TYPE_STORAGE, mrl, showHiddenFiles)
    }

    override fun onStart() {
        super.onStart()
        Medialibrary.getInstance().addEntryPointsEventsCb(this)
        snack?.show()
        lifecycleScope.launchWhenStarted { if (isAdded) (adapter as StorageBrowserAdapter).updateListState(requireContext()) }
    }

    override fun onStop() {
        super.onStop()
        Medialibrary.getInstance().removeEntryPointsEventsCb(this)
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
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ml_menu_custom_dir) {
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
        args.putBoolean(KEY_IN_MEDIALIB, scannedDirectory || scanned)
        next.arguments = args
        ft?.replace(R.id.fragment_placeholder, next, media.location)
        ft?.addToBackStack(if (isRootDirectory) "root" else if (currentMedia != null) currentMedia?.uri.toString() else mrl!!)
        ft?.commit()
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {
        if (isRootDirectory) {
            val storage = adapter.getItem(position) as Storage
            val path = storage.uri.path ?: return
            lifecycleScope.launchWhenStarted {
                val isCustom = viewModel.customDirectoryExists(path)
                if (isCustom && isAdded) showContext(requireActivity(), this@StorageBrowserFragment, position, item.title, CTX_CUSTOM_REMOVE)
            }
        }
    }

    override fun onCtxAction(position: Int, option: Long) {
        val storage = adapter.getItem(position) as Storage
        val path = storage.uri.path ?: return
        viewModel.deleteCustomDirectory(path)
        viewModel.remove(storage)
        (activity as AudioPlayerContainerActivity).updateLib()
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val mw = (item as? Storage)?.let { MLServiceLocator.getAbstractMediaWrapper(it.uri) } ?: return
        mw.type = MediaWrapper.TYPE_DIR
        browse(mw, position, (DataBindingUtil.findBinding<BrowserItemBinding>(v))?.browserCheckbox?.state == ThreeStatesCheckbox.STATE_CHECKED)
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    fun checkBoxAction(v: View, mrl: String) {
        val tscb = v as ThreeStatesCheckbox
        val checked = tscb.state == ThreeStatesCheckbox.STATE_CHECKED
        val activity = requireActivity()
        if (activity is OnboardingActivity) {
            val path = mrl.sanitizePath()
            if (checked) {
                MediaParsingService.preselectedStorages.removeAll { it.startsWith(path) }
                MediaParsingService.preselectedStorages.add(path)
            } else {
                MediaParsingService.preselectedStorages.removeAll { it.startsWith(path) }
            }
        } else {
            if (checked) {
                MedialibraryUtils.addDir(mrl, v.context.applicationContext)
                val prefs = Settings.getInstance(v.getContext())
                if (prefs.getInt(KEY_MEDIALIBRARY_SCAN, -1) != ML_SCAN_ON) prefs.putSingle(KEY_MEDIALIBRARY_SCAN, ML_SCAN_ON)
            } else
                MedialibraryUtils.removeDir(mrl)
            processEvent(v as CheckBox, mrl)
        }
    }


    internal fun processEvent(cbp: CheckBox, mrl: String) {
        cbp.isEnabled = false
        processingFolders.put(mrl, cbp)
    }

    override fun onEntryPointBanned(entryPoint: String, success: Boolean) {}

    override fun onEntryPointUnbanned(entryPoint: String, success: Boolean) {}

    override fun onEntryPointAdded(entryPoint: String, success: Boolean) {}

    override fun onEntryPointRemoved(entryPoint: String, success: Boolean) {
        var entryPoint = entryPoint
        if (entryPoint.endsWith("/"))
            entryPoint = entryPoint.substring(0, entryPoint.length - 1)
        if (processingFolders.containsKey(entryPoint)) {
            processingFolders.remove(entryPoint)?.let {
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
        if (processingFolders.containsKey(path)) {
            val finalPath = path
            handler.post { processingFolders.get(finalPath)?.isEnabled = true }
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

            lifecycleScope.launch(CoroutineExceptionHandler { _, _ -> }) {
                viewModel.addCustomDirectory(f.canonicalPath).join()
                viewModel.browseRoot()
            }
        })
        alertDialog = builder.show()
    }

    override fun containerActivity() = requireActivity()

    override val isNetwork = false
    override val isFile = true
}
