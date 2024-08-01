/*
 * *************************************************************************
 *  PreferencesAdvanced.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.television.ui.preferences

import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.text.isDigitsOnly
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.AndroidDevices
import org.videolan.resources.EXPORT_SETTINGS_FILE
import org.videolan.resources.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.resources.KEY_CURRENT_AUDIO
import org.videolan.resources.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.resources.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.resources.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.resources.KEY_CURRENT_MEDIA
import org.videolan.resources.KEY_CURRENT_MEDIA_RESUME
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST_RESUME
import org.videolan.resources.ROOM_DATABASE
import org.videolan.resources.SCHEME_PACKAGE
import org.videolan.resources.VLCInstance
import org.videolan.television.ui.dialogs.ConfirmationTvActivity
import org.videolan.tools.BitmapCache
import org.videolan.tools.DAV1D_THREAD_NUMBER
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.MediaParsingService
import org.videolan.vlc.R
import org.videolan.vlc.gui.DebugLogActivity
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.browser.FilePickerActivity
import org.videolan.vlc.gui.browser.KEY_PICKER_TYPE
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.NEW_INSTALL
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.UPDATE_DATE
import org.videolan.vlc.gui.dialogs.UPDATE_URL
import org.videolan.vlc.gui.dialogs.UpdateDialog
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getWritePermission
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.providers.PickerType
import org.videolan.vlc.util.AutoUpdate
import org.videolan.vlc.util.FeatureFlag
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.deleteAllWatchNext
import java.io.File
import java.io.IOException

private const val FILE_PICKER_RESULT_CODE = 10000
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class PreferencesAdvanced : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {
    override fun getXml(): Int {
        return R.xml.preferences_adv
    }


    override fun getTitleId(): Int {
        return R.string.advanced_prefs_category
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (FeatureFlag.values().isNotEmpty()) findPreference<Preference>("optional_features")?.isVisible = true
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        val f = super.buildPreferenceDialogFragment(preference)
        if (f is CustomEditTextPreferenceDialogFragment) {
            when (preference.key) {
                "network_caching" -> {
                    f.setInputType(InputType.TYPE_CLASS_NUMBER)
                    f.setFilters(arrayOf(InputFilter.LengthFilter(5)))
                }
            }
            return
        }
        if (!BuildConfig.DEBUG) findPreference<Preference>("show_update")?.isVisible  = false
        super.onDisplayPreferenceDialog(preference)
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val ctx = activity
        if (preference.key == null || ctx == null) return false
        when (preference.key) {
            "debug_logs" -> {
                val intent = Intent(ctx, DebugLogActivity::class.java)
                startActivity(intent)
                return true
            }
            "nightly_install" -> {

                val appCompatActivity = activity as PreferencesActivity
                android.app.AlertDialog.Builder(appCompatActivity)
                        .setTitle(resources.getString(R.string.install_nightly))
                        .setMessage(resources.getString(R.string.install_nightly_alert))
                        .setPositiveButton(R.string.ok){ _, _ ->
                            appCompatActivity.lifecycleScope.launch {
                                AutoUpdate.checkUpdate(appCompatActivity.application, true) {url, date ->
                                    val updateDialog = UpdateDialog().apply {
                                        arguments = bundleOf(UPDATE_URL to url, UPDATE_DATE to date.time, NEW_INSTALL to true)
                                    }
                                    updateDialog.show(appCompatActivity.supportFragmentManager, "fragment_update")
                                }
                            }
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                return true
            }

            "clear_history" -> {
                val dialog = ConfirmDeleteDialog.newInstance(title = getString(R.string.clear_playback_history), description = getString(R.string.clear_history_message), buttonText = getString(R.string.clear_history))
                dialog.show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
                dialog.setListener {
                    Medialibrary.getInstance().clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL)
                    Settings.getInstance(activity).edit()
                            .remove(KEY_AUDIO_LAST_PLAYLIST)
                            .remove(KEY_MEDIA_LAST_PLAYLIST)
                            .remove(KEY_MEDIA_LAST_PLAYLIST_RESUME)
                            .remove(KEY_CURRENT_AUDIO)
                            .remove(KEY_CURRENT_MEDIA)
                            .remove(KEY_CURRENT_MEDIA_RESUME)
                            .remove(KEY_CURRENT_AUDIO_RESUME_TITLE)
                            .remove(KEY_CURRENT_AUDIO_RESUME_ARTIST)
                            .remove(KEY_CURRENT_AUDIO_RESUME_THUMB)
                            .apply()
                }
                return true
            }

            "clear_app_data" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    val dialog = ConfirmDeleteDialog.newInstance(title = getString(R.string.clear_app_data), description = getString(R.string.clear_app_data_message), buttonText = getString(R.string.clear))
                    dialog.show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
                    dialog.setListener { (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData() }
                } else {
                    val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    i.addCategory(Intent.CATEGORY_DEFAULT)
                    i.data = Uri.fromParts(SCHEME_PACKAGE, activity.applicationContext.packageName, null)
                    startActivity(i)
                }
                return true
            }

            "clear_media_db" -> {
                val medialibrary = Medialibrary.getInstance()
                if (medialibrary.isWorking) {
                    activity?.let {
                        Toast.makeText(
                                it,
                                R.string.settings_ml_block_scan,
                                Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val dialog = ConfirmDeleteDialog.newInstance(
                            title = getString(R.string.clear_media_db),
                            description = getString(R.string.clear_media_db_message),
                            buttonText = getString(R.string.clear)
                    )
                    dialog.show(
                            (activity as FragmentActivity).supportFragmentManager,
                            RenameDialog::class.simpleName
                    )
                    dialog.setListener {
                        launch {
                            activity.stopService(Intent(activity, MediaParsingService::class.java))
                            withContext((Dispatchers.IO)) {
                                medialibrary.clearDatabase(false)
                                deleteAllWatchNext(activity)
                                //delete thumbnails
                                try {
                                    activity.getExternalFilesDir(null)?.let {
                                        val files =
                                                File(it.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME).listFiles()
                                        files?.forEach { file ->
                                            if (file.isFile) FileUtils.deleteFile(file)
                                        }
                                    }
                                    BitmapCache.clear()
                                } catch (e: IOException) {
                                    Log.e(this::class.java.simpleName, e.message, e)
                                }
                            }
                            medialibrary.discover(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
                        }
                    }
                }
                return true
            }

            "quit_app" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
                return true
            }

            "dump_media_db" -> {
                if (Medialibrary.getInstance().isWorking)
                    activity?.let { Toast.makeText(it, R.string.settings_ml_block_scan, Toast.LENGTH_LONG).show() }
                else {
                    val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + Medialibrary.VLC_MEDIA_DB_NAME)
                    launch {
                        if ((activity as FragmentActivity).getWritePermission(Uri.fromFile(dst))) {
                            val copied = withContext(Dispatchers.IO) {
                                val db = File(activity.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)

                                FileUtils.copyFile(db, dst)
                            }
                            Toast.makeText(activity, getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                        }
                    }
                }
                return true
            }

            "dump_app_db" -> {
                val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + ROOM_DATABASE)
                launch {
                    if ((activity as FragmentActivity).getWritePermission(Uri.fromFile(dst))) {
                        val copied = withContext(Dispatchers.IO) {
                            val db = File((activity as FragmentActivity).getDir("db", Context.MODE_PRIVATE).parent!! + "/databases")

                            val files = db.listFiles()?.map { it.path }?.toTypedArray()

                            if (files == null) false else
                                FileUtils.zip(files, dst.path)

                        }
                        Toast.makeText(activity, getString(if (copied) R.string.dump_db_succes else R.string.dump_db_failure), Toast.LENGTH_LONG).show()
                    }
                }
                return true
            }
            "export_settings" -> {
                val dst = File(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + EXPORT_SETTINGS_FILE)
                launch(Dispatchers.IO) {
                    if ((activity as FragmentActivity).getWritePermission(Uri.fromFile(dst))) {
                        PreferenceParser.exportPreferences(activity!!, dst)
                    }
                }
                return true
            }
            "restore_settings" -> {
                val filePickerIntent = Intent(activity, FilePickerActivity::class.java)
                filePickerIntent.putExtra(KEY_PICKER_TYPE, PickerType.SETTINGS.ordinal)
                startActivityForResult(filePickerIntent, FILE_PICKER_RESULT_CODE)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        if (requestCode == FILE_PICKER_RESULT_CODE) {
            if (data.hasExtra(EXTRA_MRL)) {
                launch {
                    PreferenceParser.restoreSettings(
                        activity, Uri.parse(
                            data.getStringExtra(
                                EXTRA_MRL
                            )
                        )
                    )
                }
                UiTools.restartDialog(activity!!, true, RESTART_CODE, this)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        when (key) {
            "network_caching" -> {
                sharedPreferences.edit {
                    try {
                        val origValue = Integer.parseInt(sharedPreferences.getString(key, "0"))
                        val newValue = origValue.coerceIn(0, 60000)
                        putInt("network_caching_value", newValue)
                        findPreference<EditTextPreference>(key)?.let { it.text = newValue.toString() }
                        if (origValue != newValue) activity?.let { Toast.makeText(it, R.string.network_caching_popup, Toast.LENGTH_SHORT).show() }
                    } catch (e: NumberFormatException) {
                        putInt("network_caching_value", 0)
                        findPreference<EditTextPreference>(key)?.let { it.text = "0" }
                        activity?.let { Toast.makeText(it, R.string.network_caching_popup, Toast.LENGTH_SHORT).show() }
                    }
                }
                launch { restartLibVLC() }
            }

            "custom_libvlc_options" -> {
                launch {
                    try {
                        VLCInstance.restart()
                    } catch (e: IllegalStateException) {
                        activity?.let { Toast.makeText(it, R.string.custom_libvlc_options_invalid, Toast.LENGTH_LONG).show() }
                        sharedPreferences.putSingle("custom_libvlc_options", "")
                    } finally {
                        restartMediaPlayer()
                    }
                    restartLibVLC()
                }
            }

            DAV1D_THREAD_NUMBER -> {
                val threadNumber = sharedPreferences.getString(key, "") ?: ""
                if (threadNumber != "" ) {
                    if ((threadNumber.isDigitsOnly() && threadNumber.toInt() < 1) || !threadNumber.isDigitsOnly()) {
                        UiTools.snacker(activity, R.string.dav1d_thread_number_invalid)
                        sharedPreferences.putSingle(DAV1D_THREAD_NUMBER, "")
                    }
                } else {
                    // In case of failure, after resetting the value to "" the SimpleSummaryProvider
                    // doesn't re-update it's summary to the default, has to be forced
                    val pref = findPreference<EditTextPreference>(key)
                    if (pref?.callChangeListener("") == true) {
                        pref.setText("");
                    }
                }
            }

            "opengl", "deblocking", "enable_frame_skip", "enable_time_stretching_audio", "enable_verbose_mode", "prefer_smbv1" -> {
                launch { restartLibVLC() }
            }
        }
    }

    private suspend fun restartLibVLC() {
        VLCInstance.restart()
        restartMediaPlayer()
    }
}
