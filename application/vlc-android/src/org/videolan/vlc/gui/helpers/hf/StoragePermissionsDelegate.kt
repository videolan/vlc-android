/*
 * *************************************************************************
 *  StoragePermissionsDelegate.kt
 * **************************************************************************
 *  Copyright © 2017-2018 VLC authors and VideoLAN
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

@file:Suppress("EXPERIMENTAL_API_USAGE")

package org.videolan.vlc.gui.helpers.hf

import android.Manifest
import android.annotation.TargetApi
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXTRA_FIRST_RUN
import org.videolan.resources.EXTRA_UPGRADE
import org.videolan.resources.util.startMedialibrary
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Permissions.canReadStorage
import videolan.org.commontools.LiveEvent

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class StoragePermissionsDelegate : BaseHeadlessFragment() {

    private var firstRun: Boolean = false
    private var upgrade: Boolean = false
    private var write: Boolean = false

    interface CustomActionController {
        fun onStorageAccessGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = activity?.intent
        if (intent !== null && intent.getBooleanExtra(EXTRA_UPGRADE, false)) {
            upgrade = true
            firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        }
        write = arguments?.getBoolean("write") ?: false
        if (AndroidUtil.isMarshMallowOrLater && !canReadStorage(requireContext())) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(requireActivity(), false)
            else
                requestStorageAccess(false)
        } else if (write) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(requireActivity(), false)
            else
                requestStorageAccess(true)
        }
    }

    private fun requestStorageAccess(write: Boolean) {
        val code = if (write) Manifest.permission.WRITE_EXTERNAL_STORAGE else Manifest.permission.READ_EXTERNAL_STORAGE
        val tag = if (write) Permissions.PERMISSION_WRITE_STORAGE_TAG else Permissions.PERMISSION_STORAGE_TAG
        requestPermissions(arrayOf(code), tag)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: PermissionResults) {
        when (requestCode) {
            Permissions.PERMISSION_STORAGE_TAG -> {
                // If request is cancelled, the result arrays are empty.
                val ctx = activity ?: return
                if (grantResults.granted()) {
                    storageAccessGranted.value = true
                    model.deferredGrant.complete(true)
                    exit()
                    return
                } else if (!model.permissionRationale && shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    model.permissionRationale = true
                    Permissions.showStoragePermissionDialog(ctx, false)
                    return
                }
                storageAccessGranted.value = false
                if (model.permissionPending) model.deferredGrant.complete(false)
                exit()
            }
            Permissions.PERMISSION_WRITE_STORAGE_TAG -> {
                model.deferredGrant.complete(grantResults.granted())
                exit()
            }
        }
    }

    companion object {

        const val TAG = "VLC/StorageHF"
        val storageAccessGranted = LiveEvent<Boolean>()

        fun FragmentActivity.askStoragePermission( write: Boolean, cb: Runnable?) {
            val intent = intent
            val upgrade = intent?.getBooleanExtra(EXTRA_UPGRADE, false) ?: false
            val firstRun = upgrade && intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
            lifecycleScope.launch {
                val granted = getStoragePermission(write)
                val model : PermissionViewmodel by viewModels()
                if (model.permissionPending) model.deferredGrant.complete(granted)
                if (granted) (cb ?: getAction(this@askStoragePermission, firstRun, upgrade)).run()
            }
        }

        suspend fun FragmentActivity.getStoragePermission(write: Boolean = false) : Boolean {
            if (isFinishing) return false
            val model : PermissionViewmodel by viewModels()
            if (model.isCompleted && storageAccessGranted.value == true) return model.deferredGrant.getCompleted()
            if (!model.permissionPending) {
                model.setupDeferred()
                val fragment = StoragePermissionsDelegate().apply {
                    arguments = bundleOf("write" to write)
                }
                supportFragmentManager.beginTransaction().add(fragment, TAG).commitAllowingStateLoss()
            } else if (model.permissionRationale) {
                val fragment = supportFragmentManager.findFragmentByTag(TAG) as? StoragePermissionsDelegate
                fragment?.requestStorageAccess(write) ?: return false
            }
            return model.deferredGrant.await()
        }

        suspend fun FragmentActivity.resumePermissionRequest() : Boolean {
            if (isFinishing) return false
            val model : PermissionViewmodel by viewModels()
            if (!model.permissionPending && !model.permissionRationale) return false
            if (model.isCompleted) return model.deferredGrant.getCompleted()
            if (model.permissionRationale) Permissions.showStoragePermissionDialog(this, false)
            return model.deferredGrant.await()
        }

        private fun getAction(activity: FragmentActivity, firstRun: Boolean, upgrade: Boolean) = Runnable {
            if (activity is CustomActionController) activity.onStorageAccessGranted()
            else activity.startMedialibrary(firstRun, upgrade, true)
        }

        suspend fun FragmentActivity.getWritePermission(uri: Uri) = if (uri.path?.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY) == true) {
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage()) getStoragePermission(true)
            else withContext(Dispatchers.IO) { FileUtils.canWrite(uri) }
        } else getExtWritePermission(uri)

        suspend fun Fragment.getWritePermission(uri: Uri) = activity?.getWritePermission(uri) ?: false
    }
}