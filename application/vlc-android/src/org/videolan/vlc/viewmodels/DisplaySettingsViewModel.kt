/*
 * ************************************************************************
 *  DisplaySettingsViewModel.kt
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

package org.videolan.vlc.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * View model allowing to emit / collect display setting changes between
 * a calling fragment and the [DisplaySettingsDialog]
 *
 */
class DisplaySettingsViewModel: ViewModel() {
    /**
     * Display setting object
     * Initial values should always be discarded
     *
     * @property key the setting key
     * @property value the setting value
     */
    data class SettingChange(
            val key: String = "init",
            val value: Any = 1,
    )

    private val _settingChangeFlow = MutableStateFlow(SettingChange())
    val settingChangeFlow = _settingChangeFlow.asStateFlow()

    /**
     * Send a new event when a setting is changed
     *
     * @param key the setting key
     * @param value the setting value
     */
    suspend fun send(key: String, value: Any) {
        _settingChangeFlow.emit(SettingChange(key, value))
    }

    /**
     * When the flow value is consumed, revert to "init" state
     *
     */
    suspend fun consume() {
        _settingChangeFlow.emit(SettingChange())
    }

}