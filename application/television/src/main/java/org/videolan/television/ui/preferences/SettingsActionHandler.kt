/*
 * ************************************************************************
 *  SettingsActionHandler.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.ui.preferences

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.*
import org.videolan.resources.util.startRemoteAccess
import org.videolan.resources.util.stopRemoteAccess
import org.videolan.tools.*
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.PermissionListDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.gui.preferences.EXTRA_PREF_END_POINT
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.util.AutoUpdate
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.deleteAllWatchNext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to handle the execution of side-effects for settings actions.
 *
 * This class offloads the heavy business logic from the [SettingsViewModel].
 */
@Singleton
class SettingsActionHandler @Inject constructor(
    private val settings: SharedPreferences
) {

    /**
     * Executes the side-effect associated with a setting action.
     *
     * @param context The context used for activity starts or dialogs.
     * @param scope The coroutine scope to launch background tasks.
     * @param item The action setting item.
     * @param onRefresh Callback to trigger state and visibility refresh in the ViewModel.
     */
    fun execute(
        context: Context,
        scope: CoroutineScope,
        item: SettingItem.Action,
        onRefresh: () -> Unit
    ) {
        when (item.key) {
            KEY_ACTION_OPTIONAL_FEATURES -> {
                val intent = Intent(context, PreferencesActivity::class.java).apply {
                    putExtra(EXTRA_PREF_END_POINT, R.xml.preferences_optional)
                }
                context.startActivity(intent)
            }
            KEY_ACTION_EXPORT_SETTINGS -> exportSettings(context, scope)
            KEY_ACTION_RESTORE_SETTINGS -> {
                val filePickerIntent = Intent(context, FilePickerActivity::class.java).apply {
                    putExtra(KEY_PICKER_TYPE, PickerType.SETTINGS.ordinal)
                }
                (context as? Activity)?.startActivityForResult(filePickerIntent, 10002)
            }
            KEY_ACTION_NIGHTLY_INSTALL -> {
                android.app.AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.install_nightly))
                    .setMessage(context.getString(R.string.install_nightly_alert))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        scope.launch {
                            AutoUpdate.checkUpdate((context as Activity).application, true) { url, _ ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            KEY_ACTION_DIRECTORIES -> {
                if (Medialibrary.getInstance().isWorking) {
                    Toast.makeText(context, context.getString(R.string.settings_ml_block_scan), Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(context.applicationContext, SecondaryActivity::class.java).apply {
                        putExtra("fragment", SecondaryActivity.STORAGE_BROWSER)
                    }
                    context.startActivity(intent)
                    (context as? Activity)?.setResult(RESULT_RESTART)
                }
            }
            KEY_ACTION_SOUNDFONT -> {
                val filePickerIntent = Intent(context, FilePickerActivity::class.java).apply {
                    putExtra(KEY_PICKER_TYPE, PickerType.SOUNDFONT.ordinal)
                }
                (context as? Activity)?.startActivityForResult(filePickerIntent, 10000)
            }
            KEY_ACTION_SLEEP_TIMER -> {
                (context as? FragmentActivity)?.let {
                    val dialog = SleepTimerDialog.newInstance(true)
                    dialog.onDismissListener = DialogInterface.OnDismissListener { onRefresh() }
                    dialog.show(it.supportFragmentManager, "time")
                }
            }
            KEY_ACTION_DEBUG_LOGS -> context.startActivity(Intent(context, DebugLogActivity::class.java))
            KEY_ACTION_CLEAR_HISTORY -> clearHistory(context)
            KEY_ACTION_CLEAR_MEDIA_DB -> clearMediaDatabase(context, scope)
            KEY_ACTION_DUMP_MEDIA_DB -> dumpMediaDatabase(context, scope)
            KEY_ACTION_DUMP_APP_DB -> dumpAppDatabase(context, scope)
            KEY_ACTION_CLEAR_APP_DATA -> {
                (context as? FragmentActivity)?.let { activity ->
                    val dialog = ConfirmDeleteDialog.newInstance(
                        title = context.getString(R.string.clear_app_data),
                        description = context.getString(R.string.clear_app_data_message),
                        buttonText = context.getString(R.string.clear)
                    )
                    dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                    dialog.setListener {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.clearApplicationUserData()
                    }
                }
            }
            KEY_ACTION_QUIT_APP -> android.os.Process.killProcess(android.os.Process.myPid())
            KEY_ACTION_MODIFY_PIN_CODE -> {
                val intent = org.videolan.vlc.gui.PinCodeActivity.getIntent(context, org.videolan.vlc.gui.PinCodeReason.MODIFY)
                (context as? Activity)?.startActivityForResult(intent, 0)
            }
            KEY_ACTION_REMOTE_ACCESS_STATUS -> {
                context.startActivity(Intent(context, StartActivity::class.java).apply { action = "vlc.remoteaccess.share" })
            }
            KEY_ACTION_REMOTE_ACCESS_INFO -> {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply { setClassName(context, REMOTE_ACCESS_ONBOARDING) })
            }
            KEY_ACTION_PERMISSIONS -> {
                (context as? FragmentActivity)?.let {
                    PermissionListDialog.newInstance().show(it.supportFragmentManager, "PermissionListDialog")
                }
            }
        }
    }

    private fun exportSettings(context: Context, scope: CoroutineScope) {
        (context as? FragmentActivity)?.let { activity ->
            val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + EXPORT_SETTINGS_FILE)
            scope.launch {
                if (activity.getWritePermission(Uri.fromFile(dst))) {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            PreferenceParser.exportPreferences(activity, dst)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                    Toast.makeText(context, context.getString(if (success) R.string.export_settings_success else R.string.export_settings_failure), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearHistory(context: Context) {
        (context as? FragmentActivity)?.let { activity ->
            val dialog = ConfirmDeleteDialog.newInstance(
                title = context.getString(R.string.clear_playback_history),
                description = context.getString(R.string.clear_history_message),
                buttonText = context.getString(R.string.clear_history)
            )
            dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
            dialog.setListener {
                Medialibrary.getInstance().clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
                settings.edit {
                    remove(KEY_AUDIO_LAST_PLAYLIST)
                    remove(KEY_MEDIA_LAST_PLAYLIST)
                    remove(KEY_MEDIA_LAST_PLAYLIST_RESUME)
                    remove(KEY_CURRENT_AUDIO)
                    remove(KEY_CURRENT_MEDIA)
                    remove(KEY_CURRENT_MEDIA_RESUME)
                    remove(KEY_CURRENT_AUDIO_RESUME_TITLE)
                    remove(KEY_CURRENT_AUDIO_RESUME_ARTIST)
                    remove(KEY_CURRENT_AUDIO_RESUME_THUMB)
                }
            }
        }
    }

    private fun clearMediaDatabase(context: Context, scope: CoroutineScope) {
        (context as? FragmentActivity)?.let { activity ->
            val medialibrary = Medialibrary.getInstance()
            if (medialibrary.isWorking) {
                Toast.makeText(context, R.string.settings_ml_block_scan, Toast.LENGTH_LONG).show()
            } else {
                val roots = medialibrary.foldersList
                val dialog = ConfirmDeleteDialog.newInstance(
                    title = context.getString(R.string.clear_media_db),
                    description = context.getString(R.string.clear_media_db_message),
                    buttonText = context.getString(R.string.clear)
                )
                dialog.show(activity.supportFragmentManager, RenameDialog::class.simpleName)
                dialog.setListener {
                    scope.launch {
                        context.stopService(Intent(context, MediaParsingService::class.java))
                        withContext(Dispatchers.IO) {
                            medialibrary.clearDatabase(false)
                            deleteAllWatchNext(context)
                            try {
                                context.getExternalFilesDir(null)?.let { dir ->
                                    File(dir.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME).listFiles()
                                        ?.forEach { file -> if (file.isFile) FileUtils.deleteFile(file) }
                                }
                                BitmapCache.clear()
                            } catch (e: IOException) {
                                Log.e("SettingsActionHandler", e.message, e)
                            }
                        }
                        for (root in roots) {
                            MedialibraryUtils.addDir(root.removePrefix("file://"), context)
                        }
                    }
                }
            }
        }
    }

    private fun dumpMediaDatabase(context: Context, scope: CoroutineScope) {
        (context as? FragmentActivity)?.let { activity ->
            if (Medialibrary.getInstance().isWorking) {
                Toast.makeText(context, R.string.settings_ml_block_scan, Toast.LENGTH_LONG).show()
            } else {
                val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + Medialibrary.VLC_MEDIA_DB_NAME)
                scope.launch {
                    if (activity.getWritePermission(Uri.fromFile(dst))) {
                        val copied = withContext(Dispatchers.IO) {
                            val db = File(context.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)
                            FileUtils.copyFile(db, dst)
                        }
                        Toast.makeText(context, context.getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun dumpAppDatabase(context: Context, scope: CoroutineScope) {
        (context as? FragmentActivity)?.let { activity ->
            val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + ROOM_DATABASE)
            scope.launch {
                if (activity.getWritePermission(Uri.fromFile(dst))) {
                    val copied = withContext(Dispatchers.IO) {
                        val db = File(context.getDir("db", Context.MODE_PRIVATE).parent!! + "/databases")
                        val files = db.listFiles()?.map { it.path }?.toTypedArray()
                        if (files == null) false else FileUtils.zip(files, dst.path)
                    }
                    Toast.makeText(context, context.getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
