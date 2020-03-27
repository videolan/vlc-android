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
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.tools.removeFileProtocole
import org.videolan.vlc.R
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.repository.DirectoryRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_PICKER

const val EXTRA_MRL = "sub_mrl"

class FilePickerFragment : FileBrowserFragment(), BrowserContainer<MediaLibraryItem> {

    override fun createFragment(): Fragment {
        return FilePickerFragment()
    }

    override fun onCreate(bundle: Bundle?) {
        val uri = activity?.intent?.data
        if (uri == null || uri.scheme == "http" || uri.scheme == "content" || uri.scheme == "fd") {
            activity?.intent = null
        }
        super.onCreate(bundle)
        adapter = FilePickerAdapter(this)
    }

    override fun setupBrowser() {
        viewModel = ViewModelProviders.of(this, BrowserModel.Factory(requireContext(), mrl, TYPE_PICKER, false)).get(BrowserModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.emptyLoading.emptyText = R.string.no_subs_found
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

    private fun pickFile(mw: MediaWrapper) {
        val i = Intent(Intent.ACTION_PICK)
        i.putExtra(EXTRA_MRL, mw.location)
        requireActivity().setResult(Activity.RESULT_OK, i)
        requireActivity().finish()
    }

    fun browseUp() {
        when {
            isRootDirectory -> requireActivity().finish()
            TextUtils.equals(mrl?.removeFileProtocole(), AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY) -> {
                mrl = null
                isRootDirectory = true
                viewModel.refresh()
            }
            mrl != null -> {
                val mw = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(FileUtils.getParent(mrl)))
                browse(mw, false)
            }
        }
    }

    override fun defineIsRoot() = mrl?.run {
        if (startsWith("file")) {
            val path = removeFileProtocole()
            val rootDirectories = runBlocking(Dispatchers.IO) { DirectoryRepository.getInstance(requireContext()).getMediaDirectories() }
            for (directory in rootDirectories) if (path.startsWith(directory)) return false
            return true
        } else length < 7
    } ?: true

    override fun containerActivity() = requireActivity()

    override val isNetwork = false
    override val isFile = true
}
