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

package org.videolan.vlc.gui.helpers.hf

import android.Manifest
import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.vlc.startMedialibrary
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.util.EXTRA_FIRST_RUN
import org.videolan.vlc.util.EXTRA_UPGRADE
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
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(requireActivity(), false)
            else
                requestStorageAccess(true)
        }
    }

    private fun requestStorageAccess(write: Boolean) {
        requestPermissions(arrayOf(if (write) Manifest.permission.WRITE_EXTERNAL_STORAGE else Manifest.permission.READ_EXTERNAL_STORAGE),
                if (write) Permissions.PERMISSION_WRITE_STORAGE_TAG else Permissions.PERMISSION_STORAGE_TAG)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            Permissions.PERMISSION_STORAGE_TAG -> {
                // If request is cancelled, the result arrays are empty.
                val ctx = activity ?: return
                if (grantResults.isGranted()) {
                    storageAccessGranted.value = true
                    deferredGrant.complete(true)
                    exit()
                } else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        Permissions.showStoragePermissionDialog(ctx, false)
                        return
                    } else {
                        storageAccessGranted.value = false
                        deferredGrant.complete(false)
                        exit()
                    }
                }
                storageAccessGranted.value = false
                deferredGrant.complete(false)
            }
            Permissions.PERMISSION_WRITE_STORAGE_TAG -> deferredGrant.complete(grantResults.isGranted())
        }
    }

    companion object {

        const val TAG = "VLC/StorageHF"
        val storageAccessGranted = LiveEvent<Boolean>()

        fun askStoragePermission(activity: FragmentActivity, write: Boolean, cb: Runnable?) {
            val intent = activity.intent
            val upgrade = intent?.getBooleanExtra(EXTRA_UPGRADE, false) ?: false
            val firstRun = upgrade && intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
            AppScope.launch {
                if (getStoragePermission(activity, write)) (cb ?: getAction(activity, firstRun, upgrade)).run()
            }
        }

        suspend fun getStoragePermission(activity: FragmentActivity, write: Boolean) : Boolean{
            if (activity.isFinishing) return false
            val fm = activity.supportFragmentManager
            var fragment: Fragment? = fm.findFragmentByTag(TAG)
            if (fragment == null) {
                val args = Bundle()
                args.putBoolean("write", write)
                fragment = StoragePermissionsDelegate()
                fragment.arguments = args
                fm.beginTransaction().add(fragment, TAG).commitAllowingStateLoss()
            } else {
                (fragment as StoragePermissionsDelegate).requestStorageAccess(write)
                return false //Fragment is already waiting for answear
            }
            return fragment.awaitGrant()
        }

        private fun getAction(activity: FragmentActivity, firstRun: Boolean, upgrade: Boolean) = Runnable {
            if (activity is CustomActionController) activity.onStorageAccessGranted()
            else {
                activity.startMedialibrary(firstRun, upgrade, true)
            }
        }
    }
}
