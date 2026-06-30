/*
 * ************************************************************************
 *  SettingsScreen.kt
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

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.R
import org.videolan.television.ui.compose.theme.VlcTVTheme

/**
 * Main screen for TV settings, implementing a two-pane layout (Sidebar + Detail).
 *
 * @param viewModel The [SettingsViewModel] managing the settings state.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sidebar (Left Pane)
        SettingsSidebar(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) },
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .padding(top = 48.dp)
        )

        // Detail (Right Pane)
        SettingsDetail(
            category = selectedCategory,
            viewModel = viewModel,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 48.dp, vertical = 48.dp)
        )
    }
}

/**
 * Sidebar displaying settings categories.
 */
@Composable
fun SettingsSidebar(
    categories: List<SettingCategory>,
    selectedCategory: SettingCategory?,
    onCategorySelected: (SettingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(id = R.string.preferences),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 24.dp)
        )
        LazyColumn {
            items(categories) { category ->
                CategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    onSelected = { onCategorySelected(category) }
                )
            }
        }
    }
}

/**
 * Detail view displaying items for the selected category.
 */
@Composable
fun SettingsDetail(
    category: SettingCategory?,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (category != null) {
            Text(
                text = stringResource(id = category.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn {
                items(category.items) { item ->
                    when (item) {
                        is SettingItem.Toggle -> {
                            ToggleSettingItem(
                                item = item,
                                checked = viewModel.getBooleanValue(item.key, item.defaultValue),
                                summary = viewModel.getSummary(item),
                                onCheckedChange = { viewModel.updateBooleanSetting(item.key, it) }
                            )
                        }
                        is SettingItem.Action -> {
                            val context = LocalContext.current
                            ActionSettingItem(
                                item = item,
                                summary = viewModel.getSummary(item),
                                onClick = { viewModel.executeAction(context, item) }
                            )
                        }
                        is SettingItem.Options -> {
                            OptionsSettingItem(
                                item = item,
                                currentValue = viewModel.getStringValue(item.key, item.defaultValue),
                                summary = viewModel.getSummary(item),
                                onClick = { /* TODO: Show selection dialog */ }
                            )
                        }
                        else -> {
                            // TODO: Implement other item types (Input)
                            SettingItemRow(
                                title = stringResource(id = item.title),
                                summary = "Not yet implemented"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenPreview() {
    val context = LocalContext.current
    val viewModel = remember {
        SettingsViewModel(context.applicationContext as Application).apply {
            setCategories(listOf(
                SettingCategory(
                    title = R.string.video_prefs_category,
                    icon = R.drawable.ic_pref_video,
                    items = listOf(
                        SettingItem.Toggle("video_toggle", R.string.auto_rescan, R.string.auto_rescan_summary),
                        SettingItem.Action("video_action", R.string.directories, R.string.directories_summary),
                        SettingItem.Options(
                            "hardware_acceleration",
                            R.string.hardware_acceleration,
                            entries = listOf("Automatic", "Disabled", "Decoding only"),
                            entryValues = listOf("-1", "0", "1"),
                            defaultValue = "-1"
                        )
                    )
                ),
                SettingCategory(
                    title = R.string.audio_prefs_category,
                    icon = R.drawable.ic_pref_audio,
                    items = listOf(
                        SettingItem.Toggle("audio_toggle", R.string.audio_prefs_category)
                    )
                )
            ))
        }
    }
    VlcTVTheme {
        SettingsScreen(viewModel)
    }
}
