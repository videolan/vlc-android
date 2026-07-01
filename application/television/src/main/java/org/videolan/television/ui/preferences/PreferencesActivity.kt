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
import kotlinx.coroutines.launch
import org.videolan.resources.VLCInstance
import org.videolan.television.ui.COLOR_PICKER_SELECTED_COLOR
import org.videolan.television.ui.browser.BaseTvActivity
import org.videolan.television.ui.compose.theme.VlcTVSettingsTheme
import org.videolan.tools.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.PinCodeActivity
import org.videolan.vlc.gui.PinCodeReason
import org.videolan.vlc.gui.browser.EXTRA_MRL
import org.videolan.vlc.gui.preferences.EXTRA_PREF_END_POINT
import org.videolan.vlc.media.MediaUtils

class PreferencesActivity : BaseTvActivity() {
    var extraEndPoint: String? = null
    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(application)
    }

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
            if (intent.hasExtra(EXTRA_PREF_END_POINT)) {
                extraEndPoint = intent.getStringExtra(EXTRA_PREF_END_POINT)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) {
            if (requestCode == 0) finish() // Pin code check failed
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        
        when (requestCode) {
            1 -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            2 -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_BACKGROUND_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            3 -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_SHADOW_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            4 -> data?.let { viewModel.updateColorSetting(KEY_SUBTITLES_OUTLINE_COLOR, it.getIntExtra(COLOR_PICKER_SELECTED_COLOR, 0)) }
            10000 -> { // Soundfont picker
                data?.getStringExtra(EXTRA_MRL)?.let { mrl ->
                    lifecycleScope.launch {
                        MediaUtils.useAsSoundFont(this@PreferencesActivity, mrl.toUri())
                        VLCInstance.restart()
                        org.videolan.vlc.gui.helpers.UiTools.restartDialog(this@PreferencesActivity, true, RESTART_CODE, null)
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
        setResult(RESULT_RESTART_APP)
    }

    fun exitAndRescan() {
        setRestart()
        val intent = intent
        finish()
        startActivity(intent)
    }

    fun detectHeadset(detect: Boolean) {
        val le = PlaybackService.headSetDetection
        if (le.hasObservers()) le.value = detect
    }
}
