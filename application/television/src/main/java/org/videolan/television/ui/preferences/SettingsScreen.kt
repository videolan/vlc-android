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
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.R
import org.videolan.television.ui.compose.theme.VlcTVSettingsTheme

@Composable
fun SettingsScreen(
    categories: List<SettingCategory>,
    selectedCategory: SettingCategory?,
    onCategorySelected: (SettingCategory) -> Unit,
    getBooleanValue: (String, Boolean) -> Boolean = { _, d -> d },
    getStringValue: (String, String?) -> String? = { _, d -> d },
    getColorValue: (String, Int) -> Int = { _, d -> d },
    getSummary: (SettingItem) -> String? = { null },
    onBooleanChanged: (String, Boolean) -> Unit = { _, _ -> },
    onActionClicked: (SettingItem.Action) -> Unit = {},
    onStringChanged: (String, String) -> Unit = { _, _ -> },
    onColorClicked: (SettingItem.Color) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sidebar (Left Pane)
        SettingsSidebar(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .padding(
                    start = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_horizontal),
                    bottom = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical)
                )
        )

        // Detail (Right Pane)
        SettingsDetail(
            category = selectedCategory,
            getBooleanValue = getBooleanValue,
            getStringValue = getStringValue,
            getColorValue = getColorValue,
            getSummary = getSummary,
            onBooleanChanged = onBooleanChanged,
            onActionClicked = onActionClicked,
            onStringChanged = onStringChanged,
            onColorClicked = onColorClicked,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(
                    end = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_horizontal),
                    bottom = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical),
                    start = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_horizontal)
                )
        )
    }
}

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
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    SettingsScreen(
        categories = categories,
        selectedCategory = selectedCategory,
        onCategorySelected = { viewModel.selectCategory(it) },
        getBooleanValue = { k, d -> viewModel.getBooleanValue(k, d) },
        getStringValue = { k, d -> viewModel.getStringValue(k, d) },
        getColorValue = { k, d -> viewModel.getColorValue(k, d) },
        getSummary = { viewModel.getSummary(it) },
        onBooleanChanged = { k, v -> viewModel.updateBooleanSetting(context, k, v) },
        onActionClicked = { viewModel.executeAction(context, it) },
        onStringChanged = { k, v -> viewModel.updateStringSetting(context, k, v) },
        onColorClicked = { viewModel.pickColor(context, it) }
    )
}

@Composable
fun SettingsSidebar(
    categories: List<SettingCategory>,
    selectedCategory: SettingCategory?,
    onCategorySelected: (SettingCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarFocusRequester = remember { FocusRequester() }
    var isSidebarFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .focusRequester(sidebarFocusRequester)
            .onFocusChanged { state ->
                isSidebarFocused = state.hasFocus || state.isFocused
                Log.d("VLC/Settings", "Sidebar focus changed: isSidebarFocused=$isSidebarFocused")
            }
            .focusable()
    ) {
        Text(
            text = stringResource(id = R.string.preferences),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 24.dp, top = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical))
        )
        LazyColumn {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                val itemFocusRequester = remember { FocusRequester() }
                
                CategoryItem(
                    category = category,
                    isSelected = isSelected,
                    onSelected = { onCategorySelected(category) },
                    focusRequester = itemFocusRequester
                )

                // When the sidebar itself receives focus (e.g. via DPAD LEFT),
                // redirect that focus to the currently selected item.
                LaunchedEffect(isSidebarFocused, isSelected) {
                    if (isSidebarFocused && isSelected) {
                        Log.d("VLC/Settings", "Sidebar: Redirecting focus to selected category: ${category.title}")
                        itemFocusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDetail(
    category: SettingCategory?,
    getBooleanValue: (String, Boolean) -> Boolean,
    getStringValue: (String, String?) -> String?,
    getColorValue: (String, Int) -> Int,
    getSummary: (SettingItem) -> String?,
    onBooleanChanged: (String, Boolean) -> Unit,
    onActionClicked: (SettingItem.Action) -> Unit,
    onStringChanged: (String, String) -> Unit,
    onColorClicked: (SettingItem.Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val detailFocusRequester = remember { FocusRequester() }
    var detailPaneHasFocus by remember { mutableStateOf(false) }

    // Track the last focused item key for each category
    val lastFocusedItemPerCategory = remember { mutableMapOf<Int, String?>() }

    Column(
        modifier = modifier
            .focusRequester(detailFocusRequester)
            .onFocusChanged { state ->
                val gainingFocus = state.hasFocus && !detailPaneHasFocus
                detailPaneHasFocus = state.hasFocus
                Log.d("VLC/Settings", "Detail focus changed: hasFocus=${state.hasFocus}, gainingFocus=$gainingFocus")
            }
    ) {
        if (category != null) {
            val categoryId = category.title

            Text(
                text = stringResource(id = category.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.padding(top = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical))
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn {
                items(category.items) { item ->
                    val isHeader = item is SettingItem.Header
                    val itemFocusRequester = remember { FocusRequester() }
                    
                    Box(modifier = Modifier
                        .focusRequester(itemFocusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                lastFocusedItemPerCategory[categoryId] = item.key
                                Log.d("VLC/Settings", "Detail: Item focused: ${item.key} in category $categoryId")
                            }
                        }
                    ) {
                        when (item) {
                            is SettingItem.Header -> {
                                SettingHeader(title = stringResource(id = item.title))
                            }
                            is SettingItem.Toggle -> {
                                ToggleSettingItem(
                                    item = item,
                                    checked = getBooleanValue(item.key, item.defaultValue),
                                    summary = getSummary(item),
                                    onCheckedChange = { onBooleanChanged(item.key, it) }
                                )
                            }
                            is SettingItem.Action -> {
                                ActionSettingItem(
                                    item = item,
                                    summary = getSummary(item),
                                    onClick = { onActionClicked(item) }
                                )
                            }
                            is SettingItem.Options -> {
                                var showDialog by remember { mutableStateOf(false) }
                                val currentValue = getStringValue(item.key, item.defaultValue)
                                OptionsSettingItem(
                                    item = item,
                                    currentValue = currentValue,
                                    summary = getSummary(item),
                                    onClick = { showDialog = true }
                                )
                                if (showDialog) {
                                    SelectionDialog(
                                        item = item,
                                        currentValue = currentValue,
                                        onDismiss = { showDialog = false },
                                        onValueSelected = { onStringChanged(item.key, it) }
                                    )
                                }
                            }
                            is SettingItem.Color -> {
                                ColorSettingItem(
                                    item = item,
                                    currentValue = getColorValue(item.key, item.defaultColor),
                                    onClick = { onColorClicked(item) }
                                )
                            }
                            is SettingItem.Input -> {
                                var showDialog by remember { mutableStateOf(false) }
                                val currentValue = getStringValue(item.key, item.defaultValue)
                                InputSettingItem(
                                    item = item,
                                    currentValue = currentValue,
                                    summary = getSummary(item),
                                    onClick = { showDialog = true }
                                )
                                if (showDialog) {
                                    InputDialog(
                                        item = item,
                                        currentValue = currentValue,
                                        onDismiss = { showDialog = false },
                                        onValueConfirmed = { onStringChanged(item.key, it) }
                                    )
                                }
                            }
                        }
                    }

                    // When the detail pane receives focus (e.g. via DPAD RIGHT from sidebar),
                    // redirect it to the last focused item in this category.
                    LaunchedEffect(detailPaneHasFocus, category) {
                        if (detailPaneHasFocus && !isHeader) {
                            val lastKey = lastFocusedItemPerCategory[categoryId]
                            val firstFocusableKey = category.items.firstOrNull { it !is SettingItem.Header }?.key
                            if (lastKey == item.key || (lastKey == null && item.key == firstFocusableKey)) {
                                Log.d("VLC/Settings", "Detail: Redirecting focus to item: ${item.key} in category: ${category.title}")
                                itemFocusRequester.requestFocus()
                            }
                        }
                    }

                    if (!isHeader) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenPreview() {
    val categories = remember {
        listOf(
            SettingCategory(R.string.video_prefs_category, listOf(
                SettingItem.Toggle("video_toggle", R.string.auto_rescan, R.string.auto_rescan_summary),
                SettingItem.Action("video_action", R.string.medialibrary_directories, R.string.directories_summary)
            ), R.drawable.ic_pref_video),
            SettingCategory(R.string.audio_prefs_category, emptyList(), R.drawable.ic_pref_audio)
        )
    }
    VlcTVSettingsTheme {
        SettingsScreen(
            categories = categories,
            selectedCategory = categories[0],
            onCategorySelected = {},
            getSummary = { item ->
                // Simple mock summary logic for preview
                item.summary?.let { "Summary for setting" }
            }
        )
    }
}
