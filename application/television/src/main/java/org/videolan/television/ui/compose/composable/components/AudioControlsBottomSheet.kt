/*
 * ************************************************************************
 *  AudioControlsBottomSheet.kt
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

package org.videolan.television.ui.compose.composable.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.preferences.MockSettingsProvider
import org.videolan.television.ui.preferences.SettingItemContent
import org.videolan.television.ui.preferences.SettingsFactory
import org.videolan.television.ui.preferences.SettingsProvider
import org.videolan.television.ui.preferences.SettingsViewModel
import org.videolan.vlc.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlsBottomSheet(
    onDismissRequest: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    AudioControlsBottomSheet(
        onDismissRequest = onDismissRequest,
        provider = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlsBottomSheet(
    onDismissRequest: () -> Unit,
    provider: SettingsProvider
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null
    ) {
        AudioControlsBottomSheetContent(provider)
    }
}

@Composable
private fun AudioControlsBottomSheetContent(provider: SettingsProvider) {
    val context = LocalContext.current
    val audioControlsCategory = remember(context) { SettingsFactory.createAudioControlsCategory(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = stringResource(id = R.string.controls_setting),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(audioControlsCategory.items, key = { it.key }) { item ->
                SettingItemContent(
                    item = item,
                    provider = provider
                )
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioControlsBottomSheetPreview() {
    VlcTVTheme {
        val context = LocalContext.current
        val category = SettingsFactory.createAudioControlsCategory(context)
        AudioControlsBottomSheetContent(
            provider = MockSettingsProvider(listOf(category))
        )
    }
}
