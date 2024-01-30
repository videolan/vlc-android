/*
 * ************************************************************************
 *  WidgetMigrationDialog.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import org.videolan.tools.NOTIFICATION_PERMISSION_ASKED
import org.videolan.tools.Settings
import org.videolan.vlc.databinding.DialogNorificationPermissionBinding
import org.videolan.vlc.gui.helpers.hf.NotificationDelegate.Companion.getNotificationPermission
import org.videolan.vlc.util.Permissions

class NotificationPermissionDialog : VLCBottomSheetDialogFragment() {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private lateinit var binding: DialogNorificationPermissionBinding


    override fun initialFocusedView(): View = binding.title


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogNorificationPermissionBinding.inflate(layoutInflater, container, false)
        binding.okButton.setOnClickListener {
            dismiss()
        }
        return binding.root
    }

    override fun onDismiss(dialog: DialogInterface) {
        Settings.getInstance(requireActivity()).edit {
            putBoolean(NOTIFICATION_PERMISSION_ASKED, true)
        }
        lifecycleScope.launch { requireActivity().getNotificationPermission() }
        super.onDismiss(dialog)
    }
}

object NotificationPermissionManager {
    fun launchIfNeeded(activity: FragmentActivity):Boolean {
        if(!Permissions.canSendNotifications(activity) && !Settings.getInstance(activity).getBoolean(NOTIFICATION_PERMISSION_ASKED, false)) {
            val notificationPermissionDialog = NotificationPermissionDialog()
            notificationPermissionDialog.show(activity.supportFragmentManager, "fragment_notification_permission")
            return true
        }
        return false
    }
}

