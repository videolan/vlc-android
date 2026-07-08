/*
 * *************************************************************************
 *  PreferencesActivity.java
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

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.resources.util.parcelable
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.television.ui.compose.theme.VlcTVSettingsTheme
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.preferences.EXTRA_PREF_END_POINT
import org.videolan.vlc.gui.preferences.search.PreferenceParser
import org.videolan.vlc.media.MediaUtils

@AndroidEntryPoint
class PreferencesActivity : BaseTvActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VlcTVSettingsTheme {
                SettingsScreen(viewModel)
            }
        }
        if (Settings.getInstance(this).getBoolean(KEY_RESTRICT_SETTINGS, false)) {
            val intent = PinCodeActivity.getIntent(this, PinCodeReason.CHECK)
            startActivityForResult(intent, 0)
        }
        if (savedInstanceState == null) {
            val extraEndPoint = if (intent.hasExtra(EXTRA_PREF_END_POINT)) {
                intent.parcelable<android.os.Parcelable>(EXTRA_PREF_END_POINT) ?: intent.extras?.get(EXTRA_PREF_END_POINT)
            } else null
            viewModel.init(extraEndPoint)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_RESTART_APP) {
            if (resultCode == 1) setRestartAppFinal()
            return
        }
        if (resultCode != RESULT_OK) {
            if (requestCode == 0) finish() // Pin code check failed
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        
        when (requestCode) {
            REQUEST_CODE_COLOR_SUBTITLES -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            REQUEST_CODE_COLOR_BACKGROUND -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_BACKGROUND_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            REQUEST_CODE_COLOR_SHADOW -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_SHADOW_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            REQUEST_CODE_COLOR_OUTLINE -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_OUTLINE_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            REQUEST_CODE_SETTINGS_RESTORE -> {
                data?.getStringExtra(EXTRA_MRL)?.let { mrl ->
                    lifecycleScope.launch {
                        try {
                            PreferenceParser.restoreSettings(this@PreferencesActivity, mrl.toUri())
                            VLCInstance.restart()
                            UiTools.restartDialog(this@PreferencesActivity, true, RESTART_CODE, null)
                        } catch (e: Exception) {
                            UiTools.snacker(this@PreferencesActivity, getString(R.string.invalid_settings_file))
                        }
                    }
                }
            }
            REQUEST_CODE_SOUNDFONT_PICKER -> {
                data?.getStringExtra(EXTRA_MRL)?.let { mrl ->
                    lifecycleScope.launch {
                        MediaUtils.useAsSoundFont(this@PreferencesActivity, mrl.toUri())
                        VLCInstance.restart()
                        UiTools.restartDialog(this@PreferencesActivity, true, RESTART_CODE, null)
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun refresh() {}

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun setRestart() {
        setResult(RESULT_RESTART)
    }

    fun setRestartApp() {
        UiTools.restartDialog(this, fromLeanback = true, leanbackResultCode = REQUEST_CODE_RESTART_APP, leanbackCaller = this)
    }

    fun setRestartAppFinal() {
        setResult(RESULT_RESTART_APP)
        finish()
    }
}
