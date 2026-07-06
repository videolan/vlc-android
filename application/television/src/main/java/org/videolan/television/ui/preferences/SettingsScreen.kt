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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.videolan.television.ui.compose.theme.VlcTVSettingsTheme
import org.videolan.vlc.R

@Composable
fun SettingsScreen(
    categories: List<SettingCategory>,
    selectedCategory: SettingCategory?,
    onCategorySelected: (SettingCategory) -> Unit,
    getBooleanValue: (SettingItem.Toggle) -> Boolean = { it.defaultValue },
    getIntValue: (SettingItem.Slider) -> Int = { it.defaultValue },
    getStringValue: (SettingItem) -> String? = { null },
    getColorValue: (SettingItem.Color) -> Int = { it.defaultColor },
    getSummary: (SettingItem) -> String? = { null },
    onBooleanChanged: (SettingItem.Toggle, Boolean) -> Unit = { _, _ -> },
    onActionClicked: (SettingItem.Action) -> Unit = {},
    onStringChanged: (SettingItem, String) -> Unit = { _, _ -> },
    onIntChanged: (SettingItem.Slider, Int) -> Unit = { _, _ -> },
    onColorClicked: (SettingItem.Color) -> Unit = {},
    isEnabled: (SettingItem) -> Boolean = { true },
    onDetailFocused: () -> Unit = {}
) {
    val sidebarFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    var isDetailFocused by remember { mutableStateOf(false) }

    // Intercept Back button when focus is in the detail pane
    BackHandler(enabled = isDetailFocused) {
        sidebarFocusRequester.requestFocus()
    }
    
    // Trigger onDetailFocused when focus shifts to the detail pane
    LaunchedEffect(isDetailFocused) {
        if (isDetailFocused) {
            onDetailFocused()
        }
    }

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
            onCategoryAction = { detailFocusRequester.requestFocus() },
            focusRequester = sidebarFocusRequester,
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
            getIntValue = getIntValue,
            getStringValue = getStringValue,
            getColorValue = getColorValue,
            getSummary = getSummary,
            onBooleanChanged = onBooleanChanged,
            onActionClicked = onActionClicked,
            onStringChanged = onStringChanged,
            onIntChanged = onIntChanged,
            onColorClicked = onColorClicked,
            isEnabled = isEnabled,
            onFocusChanged = { isDetailFocused = it },
            focusRequester = detailFocusRequester,
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
        getBooleanValue = { viewModel.getBooleanValue(it) },
        getIntValue = { viewModel.getIntValue(it) },
        getStringValue = { viewModel.getStringValue(it) },
        getColorValue = { viewModel.getColorValue(it) },
        getSummary = { viewModel.getSummary(it) },
        onBooleanChanged = { item, v -> viewModel.updateBooleanSetting(context, item, v) },
        onActionClicked = { viewModel.executeAction(context, it) },
        onStringChanged = { item, v -> viewModel.updateStringSetting(context, item, v) },
        onIntChanged = { item, v -> viewModel.updateIntSetting(item, v) },
        onColorClicked = { viewModel.pickColor(context, it) },
        isEnabled = { viewModel.isEnabled(it) },
        onDetailFocused = { viewModel.onDetailFocused(context) }
    )
}

@Composable
fun SettingsSidebar(
    categories: List<SettingCategory>,
    selectedCategory: SettingCategory?,
    onCategorySelected: (SettingCategory) -> Unit,
    onCategoryAction: () -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isSidebarFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isSidebarFocused = state.hasFocus || state.isFocused
            }
            .focusable()
    ) {
        Text(
            text = stringResource(id = org.videolan.television.R.string.preferences),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 24.dp, top = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical))
        )
        LazyColumn {
            items(categories, key = { it.title }) { category ->
                val isSelected = category == selectedCategory
                val itemFocusRequester = remember { FocusRequester() }
                
                CategoryItem(
                    category = category,
                    isSelected = isSelected,
                    onSelected = { onCategorySelected(category) },
                    onAction = onCategoryAction,
                    focusRequester = itemFocusRequester
                )

                // When the sidebar itself receives focus (e.g. via DPAD LEFT),
                // redirect that focus to the currently selected item.
                LaunchedEffect(isSidebarFocused, isSelected) {
                    if (isSidebarFocused && isSelected) {
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
    getBooleanValue: (SettingItem.Toggle) -> Boolean,
    getIntValue: (SettingItem.Slider) -> Int,
    getStringValue: (SettingItem) -> String?,
    getColorValue: (SettingItem.Color) -> Int,
    getSummary: (SettingItem) -> String?,
    onBooleanChanged: (SettingItem.Toggle, Boolean) -> Unit,
    onActionClicked: (SettingItem.Action) -> Unit,
    onStringChanged: (SettingItem, String) -> Unit,
    onIntChanged: (SettingItem.Slider, Int) -> Unit,
    onColorClicked: (SettingItem.Color) -> Unit,
    isEnabled: (SettingItem) -> Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    var detailPaneHasFocus by remember { mutableStateOf(false) }

    // Track the last focused item key for each category
    val lastFocusedItemPerCategory = remember { mutableMapOf<Int, String?>() }
    val listState = rememberLazyListState()

    // Reset scroll ONLY when switching to a DIFFERENT category
    LaunchedEffect(category?.title) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                detailPaneHasFocus = state.hasFocus || state.isFocused
                onFocusChanged(detailPaneHasFocus)
            }
            .focusable()
    ) {
        if (category != null) {
            val categoryId = category.title
            val firstFocusableKey = remember(category) { 
                category.items.firstOrNull { it !is SettingItem.Header }?.key 
            }

            Text(
                text = stringResource(id = category.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.padding(top = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical))
            )
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn(state = listState) {
                items(category.items, key = { it.key }) { item ->
                    val isEnabled = isEnabled(item)
                    val isHeader = item is SettingItem.Header
                    val itemFocusRequester = remember { FocusRequester() }
                    
                    Box(modifier = Modifier
                        .onFocusChanged { state ->
                            if (state.hasFocus) {
                                lastFocusedItemPerCategory[categoryId] = item.key
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
                                    checked = getBooleanValue(item),
                                    summary = getSummary(item),
                                    onCheckedChange = { onBooleanChanged(item, it) },
                                    enabled = isEnabled,
                                    modifier = Modifier.focusRequester(itemFocusRequester)
                                )
                            }
                            is SettingItem.Action -> {
                                ActionSettingItem(
                                    item = item,
                                    summary = getSummary(item),
                                    onClick = { onActionClicked(item) },
                                    enabled = isEnabled,
                                    modifier = Modifier.focusRequester(itemFocusRequester)
                                )
                            }
                            is SettingItem.Options -> {
                                var showDialog by remember { mutableStateOf(false) }
                                val currentValue = getStringValue(item)
                                OptionsSettingItem(
                                    item = item,
                                    currentValue = currentValue,
                                    summary = getSummary(item),
                                    onClick = { showDialog = true },
                                    enabled = isEnabled,
                                    modifier = Modifier.focusRequester(itemFocusRequester)
                                )
                                if (showDialog) {
                                    SelectionDialog(
                                        item = item,
                                        currentValue = currentValue,
                                        onDismiss = { showDialog = false },
                                        onValueSelected = { onStringChanged(item, it) }
                                    )
                                }
                            }
                            is SettingItem.Color -> {
                                ColorSettingItem(
                                    item = item,
                                    currentValue = getColorValue(item),
                                    onClick = { onColorClicked(item) },
                                    enabled = isEnabled,
                                    modifier = Modifier.focusRequester(itemFocusRequester)
                                )
                            }
                            is SettingItem.Input -> {
                                var showDialog by remember { mutableStateOf(false) }
                                val currentValue = getStringValue(item)
                                InputSettingItem(
                                    item = item,
                                    currentValue = currentValue,
                                    summary = getSummary(item),
                                    onClick = { showDialog = true },
                                    enabled = isEnabled,
                                    modifier = Modifier.focusRequester(itemFocusRequester)
                                )
                                if (showDialog) {
                                    InputDialog(
                                        item = item,
                                        currentValue = currentValue,
                                        onDismiss = { showDialog = false },
                                        onValueConfirmed = { onStringChanged(item, it) }
                                    )
                                }
                            }
                            is SettingItem.Slider -> {
                                var showDialog by remember { mutableStateOf(false) }
                                val currentValue = getIntValue(item)
                                SliderSettingItem(
                                    item = item,
                                    currentValue = currentValue,
                                    summary = getSummary(item),
                                    onClick = { showDialog = true },
                                    enabled = isEnabled,
                                    modifier = Modifier.focusRequester(itemFocusRequester)
                                )
                                if (showDialog) {
                                    SliderDialog(
                                        item = item,
                                        currentValue = currentValue,
                                        onDismiss = { showDialog = false },
                                        onValueConfirmed = { onIntChanged(item, it) }
                                    )
                                }
                            }
                        }
                    }

                    // When the detail pane receives focus (e.g. via DPAD RIGHT from sidebar),
                    // redirect it to the last focused item in this category.
                    LaunchedEffect(detailPaneHasFocus, category) {
                        if (detailPaneHasFocus && !isHeader && isEnabled) {
                            val lastKey = lastFocusedItemPerCategory[categoryId]
                            if (lastKey == item.key || (lastKey == null && item.key == firstFocusableKey)) {
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
            SettingCategory(org.videolan.vlc.R.string.video_prefs_category, listOf(
                SettingItem.Toggle("video_toggle", org.videolan.vlc.R.string.auto_rescan, org.videolan.vlc.R.string.auto_rescan_summary),
                SettingItem.Action("video_action", org.videolan.vlc.R.string.medialibrary_directories, org.videolan.vlc.R.string.directories_summary)
            ), org.videolan.vlc.R.drawable.ic_pref_video),
            SettingCategory(org.videolan.vlc.R.string.audio_prefs_category, emptyList(), org.videolan.vlc.R.drawable.ic_pref_audio)
        )
    }
    VlcTVSettingsTheme {
        SettingsScreen(
            categories = categories,
            selectedCategory = categories[0],
            onCategorySelected = {},
            getBooleanValue = { it.defaultValue },
            getStringValue = { (it as? SettingItem.Options)?.defaultValue ?: (it as? SettingItem.Input)?.defaultValue },
            getColorValue = { it.defaultColor },
            getSummary = { item ->
                // Simple mock summary logic for preview
                item.summary?.let { "Summary for setting" }
            }
        )
    }
}
