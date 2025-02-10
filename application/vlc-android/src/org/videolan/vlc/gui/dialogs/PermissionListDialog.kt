/*
 * ************************************************************************
 *  PermissionListDialog.kt
 * *************************************************************************
 * Copyright © 2024 VLC authors and VideoLAN
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

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.launch
import org.videolan.resources.AndroidDevices
import org.videolan.resources.SCHEME_PACKAGE
import org.videolan.resources.util.isExternalStorageManager
import org.videolan.tools.dp
import org.videolan.tools.setGone
import org.videolan.tools.setInvisible
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogPermissionsBinding
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission
import org.videolan.vlc.util.Permissions

const val CONFIRM_PERMISSION_CHANGED = "CONFIRM_PERMISSION_CHANGED"
const val KEY_PERMISSION_CHANGED = "KEY_PERMISSION_CHANGED"

/**
 * Dialog showing the info of the current version
 */
class PermissionListDialog : VLCBottomSheetDialogFragment() {

    private lateinit var binding: DialogPermissionsBinding
    private val defaultBackground: Int
        get() {
            val outValue = TypedValue()
            requireActivity().theme.resolveAttribute(
                android.R.attr.selectableItemBackground,
                outValue,
                true
            )
            return outValue.resourceId
        }

    private var initialPermissionLevel = -1
    private var permissionChanged = false

    companion object {
        fun newInstance(): PermissionListDialog {
            return PermissionListDialog()
        }
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun needToManageOrientation(): Boolean {
        return false
    }

    override fun initialFocusedView(): View = binding.permissionTitle

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogPermissionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateStorageState()
    }

    override fun dismiss() {
        setFragmentResult(CONFIRM_PERMISSION_CHANGED, bundleOf(KEY_PERMISSION_CHANGED to permissionChanged))
        super.dismiss()
    }

    override fun onCancel(dialog: DialogInterface) {
        setFragmentResult(CONFIRM_PERMISSION_CHANGED, bundleOf(KEY_PERMISSION_CHANGED to permissionChanged))
        super.onCancel(dialog)
    }

    private fun updateStorageState() {
        binding.notificationPermissionCheck.setImageDrawable(ContextCompat.getDrawable(requireActivity(), if (Permissions.canSendNotifications(requireActivity())) R.drawable.ic_permission_check_checked else R.drawable.ic_permission_check_unchecked))
        binding.notificationPermissionContainer.setOnClickListener {
            if (Permissions.canSendNotifications(requireActivity())) {
                Permissions.showAppSettingsPage(requireActivity())
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(), arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                )
                Permissions.timeAsked = System.currentTimeMillis()
            }
        }
        if (initialPermissionLevel == -1) {
            initialPermissionLevel = getPermissionLevel()
        } else
            permissionChanged = initialPermissionLevel != getPermissionLevel()


        // radio states
        arrayOf(
            binding.noAccessCheck,
            binding.manageAllPermsCheck,
            binding.manageMediaPermsCheck
        ).forEach {
            it.isChecked = false
        }

        when {
            Permissions.hasAllAccess(requireActivity()) -> binding.manageAllPermsCheck.isChecked = true
            Permissions.canReadStorage(requireActivity()) -> binding.manageMediaPermsCheck.isChecked = true
            else -> binding.noAccessCheck.isChecked = true
        }

        // media permission states
        binding.manageMediaVideo.isEnabled = !Permissions.hasAllAccess(requireActivity()) && !Permissions.hasVideoPermission(requireActivity())
        binding.manageMediaAudio.isEnabled = !Permissions.hasAllAccess(requireActivity()) && !Permissions.hasAudioPermission(requireActivity())

        //backgrounds
        binding.manageMediaPermsCheck.setBackgroundResource(defaultBackground)


        // explanation text state
        binding.fileAccessExplanation.text = when {
            Permissions.hasAllAccess(requireActivity()) -> getString(R.string.permission_onboarding_perm_all)
            Permissions.hasAnyFileFineAccess(requireActivity()) -> getString(R.string.permission_onboarding_perm_media)
            else -> getString(R.string.permission_expanation_no_allow)
        }
        binding.fileAccessExplanation.setCompoundDrawablesRelativeWithIntrinsicBounds(when {
            Permissions.hasAllAccess(requireActivity()) -> ContextCompat.getDrawable(requireActivity(), R.drawable.ic_perm_all)
            Permissions.hasAnyFileFineAccess(requireActivity()) -> ContextCompat.getDrawable(requireActivity(), R.drawable.ic_perm_media)
            else -> ContextCompat.getDrawable(requireActivity(), R.drawable.ic_perm_none)
        }, null, null, null)


        //warning visibility
        binding.allAccessWarning.setInvisible()
        binding.allAccessWarning.text = when {
            Permissions.hasAllAccess(requireActivity()) -> getString(R.string.permission_media_warning)
            Permissions.hasAnyFileFineAccess(requireActivity()) -> getString(R.string.permission_all_warning)
            else -> ""
        }


        //media permission icons
        binding.manageMediaVideo.setImageDrawable(
            ContextCompat.getDrawable(
                requireActivity(),
                if (Permissions.hasVideoPermission(requireActivity())) R.drawable.ic_permission_media_video else R.drawable.ic_permission_media_video_denied
            )
        )
        binding.manageMediaAudio.setImageDrawable(
            ContextCompat.getDrawable(
                requireActivity(),
                if (Permissions.hasAudioPermission(requireActivity())) R.drawable.ic_permission_media_audio else R.drawable.ic_permission_media_audio_denied
            )
        )

        //click listeners
        binding.noAccessCheck.setOnClickListener {
            if (Permissions.hasAllAccess(requireActivity()) || Permissions.hasAnyFileFineAccess(
                    requireActivity()
                )
            ) {
                (it as RadioButton).isChecked = false
                when {
                    Permissions.hasAllAccess(requireActivity()) -> binding.manageAllPermsCheck
                    else -> binding.manageMediaPermsCheck
                }.background  = ContextCompat.getDrawable(requireActivity(), R.drawable.rounded_corners_permissions_warning)
                showWarning()
            }
        }

        binding.manageAllPermsCheck.setOnClickListener {
            if (!Permissions.hasAllAccess(requireActivity()) && Permissions.hasAnyFileFineAccess(requireActivity())) {
                (it as RadioButton).isChecked = false
                binding.manageMediaPermsCheck.background  = ContextCompat.getDrawable(requireActivity(), R.drawable.rounded_corners_permissions_warning)
                showWarning()
            } else
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    if (!Permissions.hasAllAccess(requireActivity())) {
                        Permissions.checkReadStoragePermission(
                            requireActivity(),
                            false,
                            forceAsking = true
                        )
                        binding.manageAllPermsCheck.isChecked = false
                    }
                    else
                        Permissions.showAppSettingsPage(requireActivity())
                } else
                    requireActivity().lifecycleScope.launch {
                        val uri = Uri.fromParts(SCHEME_PACKAGE, requireContext().packageName, null)
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            uri
                        )
                        startActivity(intent)
                    }
        }

        binding.manageMediaPermsCheck.setOnClickListener {
            if (!isExternalStorageManager() && Permissions.hasAnyFileFineAccess(requireActivity())) {
                Permissions.showAppSettingsPage(requireActivity())
                (it as RadioButton).isChecked = false
            } else if (!isExternalStorageManager() && Permissions.canReadStorage(requireActivity())) {
                Permissions.showAppSettingsPage(requireActivity())
                (it as RadioButton).isChecked = false
            } else if (Permissions.hasAllAccess(requireActivity())) {
                (it as RadioButton).isChecked = false
                binding.manageAllPermsCheck.background  = ContextCompat.getDrawable(requireActivity(), R.drawable.rounded_corners_permissions_warning)
                showWarning()
            } else
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        requireActivity(), arrayOf(
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO
                        ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                    )
                    Permissions.timeAsked = System.currentTimeMillis()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ActivityCompat.requestPermissions(
                        requireActivity(), arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                    )
                    Permissions.timeAsked = System.currentTimeMillis()
                } else lifecycleScope.launch {
                    requireActivity().getStoragePermission(withDialog = false, onlyMedia = true)
                }
        }

        binding.manageMediaAudio.setOnClickListener {
            if (!Permissions.hasAllAccess(requireActivity()) && !Permissions.hasAudioPermission(requireActivity())) {
                ActivityCompat.requestPermissions(
                    requireActivity(), arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO
                    ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                )
                Permissions.timeAsked = System.currentTimeMillis()
            }
        }

        binding.manageMediaVideo.setOnClickListener {
            if (!Permissions.hasAllAccess(requireActivity()) && !Permissions.hasVideoPermission(requireActivity())) {
                ActivityCompat.requestPermissions(
                    requireActivity(), arrayOf(
                        Manifest.permission.READ_MEDIA_VIDEO
                    ), Permissions.FINE_STORAGE_PERMISSION_REQUEST_CODE
                )
                Permissions.timeAsked = System.currentTimeMillis()
            }
        }

        //Manage view visibility for older versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || AndroidDevices.isTv) {
            binding.manageMediaPermsCheck.setGone()
            binding.manageMediaVideo.setGone()
            binding.manageMediaAudio.setGone()

        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            binding.manageMediaAudio.setGone()
            binding.manageMediaVideo.setGone()
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            binding.notificationPermissionContainer.setGone()
            binding.notificationPermissionTitle.setGone()
        }


    }

    private fun getPermissionLevel() = when {
        Permissions.hasAllAccess(requireActivity()) -> 2
        Permissions.canReadStorage(requireActivity()) -> 1
        else -> 0
    }

    private fun showWarning() {
        binding.allAccessWarning.translationY = 100.dp.toFloat()
        binding.allAccessWarning.setVisible()
        binding.allAccessWarning.animate().translationY(0F).setDuration(300).start()
    }

}





