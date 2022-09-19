/*
 * ************************************************************************
 *  NotificationDelegate.kt
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

package org.videolan.vlc.gui.helpers.hf

import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import videolan.org.commontools.LiveEvent

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class NotificationDelegate : BaseHeadlessFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            requestPermission()
        }
    }

    fun requestPermission() {
        val requestPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    model.deferredGrant.complete(isGranted)
                    exit()
                }
        when {
            ContextCompat.checkSelfPermission(requireActivity(), "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS") -> {
                requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }
            else -> {
                requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    companion object {
        const val TAG = "VLC/NotificationDelegate"
        private val notificationAccessGranted = LiveEvent<Boolean>()



        @OptIn(ExperimentalCoroutinesApi::class)
        suspend fun FragmentActivity.getNotificationPermission() : Boolean {
            if (isFinishing) return false
            val model : PermissionViewmodel by viewModels()
            if (model.isCompleted && notificationAccessGranted.value == true) return model.deferredGrant.getCompleted()
            if (model.permissionPending) {
                val fragment = supportFragmentManager.findFragmentByTag(TAG) as? NotificationDelegate
                fragment?.requestPermission() ?: return false
            } else {
                model.setupDeferred()
                val fragment = NotificationDelegate()
                supportFragmentManager.beginTransaction().add(fragment, TAG).commitAllowingStateLoss()
            }
            return model.deferredGrant.await()
        }
    }
}

