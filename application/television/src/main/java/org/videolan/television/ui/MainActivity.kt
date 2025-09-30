/*
 * ************************************************************************
 *  MainActivity.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
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

package org.videolan.television.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.videolan.television.ui.compose.composable.screens.MainScreen
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.util.EventThrottler
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.tools.KEY_SHOW_UPDATE
import org.videolan.tools.Settings
import org.videolan.vlc.gui.dialogs.UPDATE_DATE
import org.videolan.vlc.gui.dialogs.UPDATE_URL
import org.videolan.vlc.gui.dialogs.UpdateDialog
import org.videolan.vlc.util.AutoUpdate
import kotlin.time.Duration.Companion.milliseconds


class MainActivity : AppCompatActivity() {

    private val myViewModel by viewModels<MainActivityViewModel>()
    private val horizontalThrottler = EventThrottler(75L)
    private val verticalThrottler = EventThrottler(200L)
    private val selectThrottler = EventThrottler(500L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VlcTVTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }

        lifecycleScope.launch {
            AutoUpdate.clean(this@MainActivity.application)
            if (!Settings.getInstance(this@MainActivity).getBoolean(KEY_SHOW_UPDATE, true)) return@launch
            AutoUpdate.checkUpdate(this@MainActivity.application) { url, date ->
                val updateDialog = UpdateDialog().apply {
                    arguments = bundleOf(UPDATE_URL to url, UPDATE_DATE to date.time)
                }
                updateDialog.show(supportFragmentManager, "fragment_update")
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val isThrottled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                horizontalThrottler.throttleEvent()
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                verticalThrottler.throttleEvent()
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                selectThrottler.throttleEvent()
            }

            else -> false
        }

        return isThrottled || super.dispatchKeyEvent(event)
    }
}
