/*
 * *************************************************************************
 *  Permissions.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.util

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.askStoragePermission
import org.videolan.vlc.gui.helpers.hf.WriteExternalDelegate

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
object Permissions {

    const val PERMISSION_STORAGE_TAG = 255
    const val PERMISSION_SETTINGS_TAG = 254
    const val PERMISSION_WRITE_STORAGE_TAG = 253


    const val PERMISSION_SYSTEM_RINGTONE = 42
    private const val PERMISSION_SYSTEM_BRIGHTNESS = 43
    private const val PERMISSION_SYSTEM_DRAW_OVRLAYS = 44

    var sAlertDialog: Dialog? = null

    /*
     * Marshmallow permission system management
     */

    @TargetApi(Build.VERSION_CODES.M)
    fun canDrawOverlays(context: Context): Boolean {
        return !AndroidUtil.isMarshMallowOrLater || android.provider.Settings.canDrawOverlays(context)
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun canWriteSettings(context: Context): Boolean {
        return !AndroidUtil.isMarshMallowOrLater || android.provider.Settings.System.canWrite(context)
    }

    fun canReadStorage(context: Context): Boolean {
        return !AndroidUtil.isMarshMallowOrLater || ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    @JvmOverloads
    fun canWriteStorage(context: Context = AppContextProvider.appContext): Boolean {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun checkReadStoragePermission(activity: FragmentActivity, exit: Boolean = false): Boolean {
        if (AndroidUtil.isMarshMallowOrLater && !canReadStorage(activity)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                            Manifest.permission.READ_EXTERNAL_STORAGE)) {
                showStoragePermissionDialog(activity, exit)
            } else
                activity.requestStoragePermission(false, null)
            return false
        }
        return true
    }

    fun askWriteStoragePermission(activity: FragmentActivity, exit: Boolean, callback: Runnable) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showStoragePermissionDialog(activity, exit)
        } else
            activity.requestStoragePermission(true, callback)
    }

    fun checkDrawOverlaysPermission(activity: FragmentActivity) {
        if (AndroidUtil.isMarshMallowOrLater && !canDrawOverlays(activity)) {
            showSettingsPermissionDialog(activity, PERMISSION_SYSTEM_DRAW_OVRLAYS)
        }
    }

    fun checkWriteSettingsPermission(activity: FragmentActivity, mode: Int) {
        if (!canWriteSettings(activity)) showSettingsPermissionDialog(activity, mode)
    }

    private fun showSettingsPermissionDialog(activity: FragmentActivity, mode: Int) {
        if (activity.isFinishing || sAlertDialog != null && sAlertDialog!!.isShowing) return
        sAlertDialog = createSettingsDialogCompat(activity, mode)
    }

    fun showStoragePermissionDialog(activity: FragmentActivity, exit: Boolean) {
        if (activity.isFinishing || sAlertDialog != null && sAlertDialog!!.isShowing) return
        sAlertDialog = if (activity is AppCompatActivity)
            createDialogCompat(activity, exit)
        else
            createDialog(activity, exit)
    }

    private fun createDialog(activity: FragmentActivity, exit: Boolean): Dialog {
        val dialogBuilder = android.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_storage_access_title))
                .setMessage(activity.getString(R.string.allow_storage_access_description))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setPositiveButton(activity.getString(R.string.permission_ask_again)) { _, _ ->
                    val settings = Settings.getInstance(activity)
                    if (!settings.getBoolean("user_declined_storage_access", false))
                        activity.requestStoragePermission()
                    else {
                        showAppSettingsPage(activity)
                    }
                    settings.putSingle("user_declined_storage_access", true)
                }
        if (exit) {
            dialogBuilder.setNegativeButton(activity.getString(R.string.exit_app)) { _, _ -> activity.finish() }
                    .setCancelable(false)
        }
        return dialogBuilder.show()
    }

    private fun createDialogCompat(activity: FragmentActivity, exit: Boolean): Dialog {
        val dialogBuilder = AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.allow_storage_access_title))
                .setMessage(activity.getString(R.string.allow_storage_access_description))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setPositiveButton(activity.getString(R.string.permission_ask_again)) { _, _ ->
                    val settings = Settings.getInstance(activity)
                    if (!settings.getBoolean("user_declined_storage_access", false))
                        activity.requestStoragePermission()
                    else {
                        showAppSettingsPage(activity)
                    }
                    settings.putSingle("user_declined_storage_access", true)
                }
        if (exit) {
            dialogBuilder.setNegativeButton(activity.getString(R.string.exit_app)) { _, _ -> activity.finish() }
                    .setCancelable(false)
        }
        return dialogBuilder.show().apply {
            activity.lifecycle.addObserver(object : LifecycleObserver {
                @Suppress("unused")
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun clear() {
                    dismiss()
                }
            })
        }
    }

    private fun showAppSettingsPage(activity: FragmentActivity) {
        val i = Intent()
        i.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
        i.addCategory(Intent.CATEGORY_DEFAULT)
        i.data = Uri.parse("package:" + AppContextProvider.appContext.packageName)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(i)
        } catch (ignored: Exception) {
        }

    }

    private fun createSettingsDialogCompat(activity: Activity, mode: Int): Dialog {
        var titleId = 0
        var textId = 0
        var action = android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
        when (mode) {
            PERMISSION_SYSTEM_RINGTONE -> {
                titleId = R.string.allow_settings_access_ringtone_title
                textId = R.string.allow_settings_access_ringtone_description
            }
            PERMISSION_SYSTEM_BRIGHTNESS -> {
                titleId = R.string.allow_settings_access_brightness_title
                textId = R.string.allow_settings_access_brightness_description
            }
            PERMISSION_SYSTEM_DRAW_OVRLAYS -> {
                titleId = R.string.allow_draw_overlays_title
                textId = R.string.allow_sdraw_overlays_description
                action = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            }
        }
        val finalAction = action
        val dialogBuilder = AlertDialog.Builder(activity)
                .setTitle(activity.getString(titleId))
                .setMessage(activity.getString(textId))
                .setIcon(R.drawable.ic_warning)
                .setPositiveButton(activity.getString(R.string.permission_ask_again)) { _, _ ->
                    val settings = Settings.getInstance(activity)
                    val i = Intent(finalAction)
                    i.data = Uri.parse("package:" + activity.packageName)
                    try {
                        activity.startActivity(i)
                    } catch (ignored: Exception) {
                    }

                    val editor = settings.edit()
                    editor.putBoolean("user_declined_settings_access", true)
                    editor.apply()
                }
        return dialogBuilder.show()
    }

    private fun FragmentActivity.requestStoragePermission(write: Boolean = false, callback: Runnable? = null) {
        askStoragePermission(write, callback)
    }

    fun checkWritePermission(activity: FragmentActivity, media: MediaWrapper, callback: Runnable): Boolean {
        val uri = media.uri
        if ("file" != uri.scheme) return false
        if (uri.path!!.startsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)) {
            //Check write permission starting Oreo
            if (AndroidUtil.isOOrLater && !Permissions.canWriteStorage()) {
                askWriteStoragePermission(activity, false, callback)
                return false
            }
        } else if (AndroidUtil.isLolliPopOrLater && WriteExternalDelegate.needsWritePermission(uri)) {
            WriteExternalDelegate.askForExtWrite(activity, uri, callback)
            return false
        }
        return true
    }
}
