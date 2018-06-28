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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.vlc.startMedialibrary
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Permissions.canReadStorage

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class StoragePermissionsDelegate : BaseHeadlessFragment() {

    private var mFirstRun: Boolean = false
    private var mUpgrade: Boolean = false
    private var mWrite: Boolean = false

    interface CustomActionController {
        fun onStorageAccessGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = if (mActivity == null) null else mActivity!!.intent
        if (intent !== null && intent.getBooleanExtra(Constants.EXTRA_UPGRADE, false)) {
            mUpgrade = true
            mFirstRun = intent.getBooleanExtra(Constants.EXTRA_FIRST_RUN, false)
            intent.removeExtra(Constants.EXTRA_UPGRADE)
            intent.removeExtra(Constants.EXTRA_FIRST_RUN)
        }
        mWrite = arguments?.getBoolean("write") ?: false
        if (AndroidUtil.isMarshMallowOrLater && !canReadStorage(activity!!)) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(mActivity, false)
            else
                requestStorageAccess(false)
        } else if (mWrite) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                Permissions.showStoragePermissionDialog(mActivity, false)
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
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ctx is CustomActionController) ctx.onStorageAccessGranted()
                    else ctx.startMedialibrary(mFirstRun, mUpgrade, true)
                    exit()
                } else if (mActivity != null) {
                    Permissions.showStoragePermissionDialog(mActivity, false)
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                        exit()
                }
            }
            Permissions.PERMISSION_WRITE_STORAGE_TAG -> executeCallback()
        }
    }

    companion object {

        const val TAG = "VLC/StorageHF"

        fun askStoragePermission(activity: FragmentActivity, write: Boolean, cb: Runnable?) {
            if (activity.isFinishing) return
            val fm = activity.supportFragmentManager
            var fragment: Fragment? = fm.findFragmentByTag(TAG)
            callback = cb
            if (fragment == null) {
                val args = Bundle()
                args.putBoolean("write", write)
                fragment = StoragePermissionsDelegate()
                fragment.arguments = args
                fm.beginTransaction().add(fragment, TAG).commitAllowingStateLoss()
            } else
                (fragment as StoragePermissionsDelegate).requestStorageAccess(write)
        }
    }
}
