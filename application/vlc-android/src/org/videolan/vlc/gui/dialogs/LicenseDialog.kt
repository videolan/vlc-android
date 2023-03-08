/**
 * **************************************************************************
 * LicenseDialog.kt
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
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.resources.util.parcelable
import org.videolan.vlc.databinding.DialogLicenseBinding
import org.videolan.vlc.gui.LibraryWithLicense
import org.videolan.vlc.util.openLinkIfPossible

const val LICENSE_ITEM = "LICENSE_ITEM"

/**
 * Dialog showing a license text
 */
class LicenseDialog : VLCBottomSheetDialogFragment() {

    private lateinit var licenseItem: LibraryWithLicense
    private lateinit var binding: DialogLicenseBinding

    companion object {

        fun newInstance(libraryWithLicense: LibraryWithLicense): LicenseDialog {
            return LicenseDialog().apply {
                arguments = bundleOf(LICENSE_ITEM to libraryWithLicense)
            }
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return false
    }

    override fun initialFocusedView(): View = binding.title

    override fun onCreate(savedInstanceState: Bundle?) {
        licenseItem = arguments?.parcelable(LICENSE_ITEM) ?: return
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogLicenseBinding.inflate(layoutInflater, container, false)
        binding.library = licenseItem
        binding.licenseButton.setOnClickListener {
            if (licenseItem.licenseLink.isNotEmpty()) requireActivity().openLinkIfPossible(licenseItem.licenseLink)
        }
        return binding.root
    }
}





