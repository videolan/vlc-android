/*
 * *************************************************************************
 *  FilePickerFragment.java
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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.util.parcelable
import org.videolan.tools.removeFileScheme
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_PICKER

const val EXTRA_MRL = "sub_mrl"

class FilePickerFragment : FileBrowserFragment(), BrowserContainer<MediaLibraryItem> {

    override fun createFragment(): Fragment {
        return FilePickerFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requireActivity().intent?.parcelable<MediaWrapper>(KEY_MEDIA)?.let { media ->
            if (media.uri == null || media.uri.scheme == "http" || media.uri.scheme == "content" || media.uri.scheme == "fd") {
                activity?.intent = null
            }
        }
        requireActivity().intent?.getIntExtra(KEY_PICKER_TYPE, 0)?.let { pickerIndex ->
            pickerType = PickerType.values()[pickerIndex]
        } ?: PickerType.SUBTITLE
        super.onCreate(savedInstanceState)
        adapter = FilePickerAdapter(this)
    }

    override fun setupBrowser() {
        viewModel = ViewModelProvider(this, BrowserModel.Factory(requireContext(), mrl, TYPE_PICKER, false, pickerType = pickerType)).get(BrowserModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.emptyLoading.emptyText = getString(R.string.no_subs_found)
    }

    override fun onStart() {
        super.onStart()
        if (activity !is ContentActivity || (activity as ContentActivity).displayTitle) activity?.title = getTitle()
        swipeRefreshLayout.isEnabled = false
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        val media = item as MediaWrapper
        if (media.type == MediaWrapper.TYPE_DIR)
            browse(media, true)
        else
            pickFile(media)
    }

    override fun backTo(tag: String) {
        if (tag == "root") {
            val supportFragmentManager = requireActivity().supportFragmentManager
            for (i in 0 until supportFragmentManager.backStackEntryCount) {
                supportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            viewModel.setDestination(MLServiceLocator.getAbstractMediaWrapper(tag.toUri()))
            supportFragmentManager.beginTransaction().detach(this).attach(this).commit()
            return
        }
        super.backTo(tag)
    }

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {}

    private fun pickFile(mw: MediaWrapper) {
        val i = Intent(Intent.ACTION_PICK)
        i.putExtra(EXTRA_MRL, mw.location)
        requireActivity().setResult(Activity.RESULT_OK, i)
        requireActivity().finish()
    }

    fun browseUp() {
        when {
            isRootDirectory -> requireActivity().finish()
            mrl?.removeFileScheme() == AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY -> {
                mrl = null
                isRootDirectory = true
                viewModel.refresh()
            }
            mrl != null -> {
                val mw = MLServiceLocator.getAbstractMediaWrapper(FileUtils.getParent(mrl)?.toUri())
                browse(mw, false)
            }
        }
    }

    override fun defineIsRoot() = mrl?.run {
        if (startsWith("file")) {
            val path = removeFileScheme()
            val rootDirectories = runBlocking(Dispatchers.IO) { DirectoryRepository.getInstance(requireContext()).getMediaDirectories() }
            for (directory in rootDirectories) if (path.startsWith(directory)) return false
            return true
        } else length < 7
    } ?: true

    override fun containerActivity() = requireActivity()

    private lateinit var pickerType: PickerType
    override val isNetwork = false
    override val isFile = true
}
