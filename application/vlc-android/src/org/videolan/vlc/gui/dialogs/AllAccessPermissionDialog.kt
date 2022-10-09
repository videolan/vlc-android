/**
 * **************************************************************************
 * AllAccessPermissionDialog.kt
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
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import org.videolan.tools.PERMISSION_NEVER_ASK
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission

class AllAccessPermissionDialog : VLCBottomSheetDialogFragment() {

    private lateinit var titleView:TextView
    private lateinit var grantAllAccessButton:Button
    private lateinit var neverAskAgain:CheckBox

    companion object {

        /**
         * Create a new AllAccessPermissionDialog
         */
        fun newInstance(): AllAccessPermissionDialog {
            return AllAccessPermissionDialog()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_all_access, container)
        titleView = view.findViewById(R.id.title)
        view.findViewById<TextView>(R.id.description).text = getString(R.string.partial_content_description, getString(R.string.allow_storage_manager_explanation))
        grantAllAccessButton = view.findViewById(R.id.grant_all_access_button)
        neverAskAgain = view.findViewById(R.id.never_ask_again)
        val settings = Settings.getInstance(requireActivity())
        grantAllAccessButton.setOnClickListener {
            lifecycleScope.launch { requireActivity().getStoragePermission(withDialog = false) }
            dismiss()
        }
        neverAskAgain.setOnCheckedChangeListener { _, isChecked ->
            settings.putSingle(PERMISSION_NEVER_ASK, isChecked)
        }
        return view
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun initialFocusedView(): View = titleView

    override fun needToManageOrientation(): Boolean {
        return true
    }
}