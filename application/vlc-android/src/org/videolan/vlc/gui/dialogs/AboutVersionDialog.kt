/**
 * **************************************************************************
 * AboutVersionDialog.kt
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 *
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogAboutVersionBinding

/**
 * Dialog showing the info of the current version
 */
class AboutVersionDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogAboutVersionBinding

    companion object {

        fun newInstance(): AboutVersionDialog {
            return AboutVersionDialog()
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return false
    }

    override fun initialFocusedView(): View = binding.medias2

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DialogAboutVersionBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.version.text = BuildConfig.VLC_VERSION_NAME
        binding.medias2.text = getString(R.string.build_time)
        binding.changelog.text = getString(R.string.changelog).replace("*", "•")
        binding.revision.text = getString(R.string.build_revision)
        binding.vlcRevision.text = getString(R.string.build_vlc_revision)
        binding.libvlcRevision.text = getString(R.string.build_libvlc_revision)
        binding.libvlcVersion.text = BuildConfig.LIBVLC_VERSION
        binding.compiledBy.text = getString(R.string.build_host)
        binding.moreButton.setOnClickListener {
            val whatsNewDialog = WhatsNewDialog()
            whatsNewDialog.show(requireActivity().supportFragmentManager, "fragment_whats_new")
            dismiss()
        }
    }


}





