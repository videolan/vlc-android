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

import android.util.Log
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.videolan.television.ui.compose.theme.VlcTVSettingsTheme
import org.videolan.vlc.R

/**
 * CompositionLocal to provide a [SettingsProvider] down the UI tree.
 */
val LocalSettingsProvider = staticCompositionLocalOf<SettingsProvider> {
    error("No SettingsProvider provided")
}

/**
 * Main screen for TV settings, implementing a two-pane layout (Sidebar + Detail).
 *
 * @param viewModel The [SettingsViewModel] managing the settings state.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val provider = viewModel as SettingsProvider
    val context = LocalContext.current

    // Local state to track the focus target for the detail pane
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }

    // Collect one-shot navigation events
    LaunchedEffect(Unit) {
        provider.navEvents.collect { event ->
            Log.d("SettingsFocus", "Received nav event: $event")
            when (event) {
                is SettingsEvent.ScrollToAndFocus -> {
                    // 1. Find and select the category
                    val categories = provider.categories.value
                    val cat = categories.find { it.title == event.categoryTitle }
                    Log.d("SettingsFocus", "Navigating to category: ${cat?.title} for key: ${event.itemKey}")
                    cat?.let { provider.selectCategory(it) }
                    
                    // 2. Schedule focus redirection for the detail pane
                    pendingFocusKey = event.itemKey
                }
            }
        }
    }

    CompositionLocalProvider(LocalSettingsProvider provides provider) {
        SettingsScreenContent(
            onDetailFocused = { provider.onDetailFocused(context) },
            pendingFocusKey = pendingFocusKey,
            onFocusConsumed = { pendingFocusKey = null }
        )
    }
}

/**
 * Internal content for the settings screen.
 */
@Composable
private fun SettingsScreenContent(
    onDetailFocused: () -> Unit = {},
    pendingFocusKey: String? = null,
    onFocusConsumed: () -> Unit = {}
) {
    val sidebarFocusRequester = remember { FocusRequester() }
    val detailFocusRequester = remember { FocusRequester() }
    var isDetailFocused by remember { mutableStateOf(false) }

    // Request initial focus on the sidebar
    LaunchedEffect(Unit) {
        sidebarFocusRequester.requestFocus()
    }

    // Intercept Back button when focus is in the detail pane
    BackHandler(enabled = isDetailFocused) {
        sidebarFocusRequester.requestFocus()
    }
    
    // Trigger onDetailFocused when focus shifts to the detail pane
    LaunchedEffect(isDetailFocused, pendingFocusKey) {
        if (isDetailFocused || pendingFocusKey != null) {
            onDetailFocused()
            // If we have a pending focus target, ensure the detail pane itself gets focus first
            if (!isDetailFocused) {
                detailFocusRequester.requestFocus()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sidebar (Left Pane)
        SettingsSidebar(
            onCategoryAction = { detailFocusRequester.requestFocus() },
            focusRequester = sidebarFocusRequester,
            detailFocusRequester = detailFocusRequester,
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
            onFocusChanged = { isDetailFocused = it },
            focusRequester = detailFocusRequester,
            pendingFocusKey = pendingFocusKey,
            onFocusConsumed = onFocusConsumed,
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

@Composable
fun SettingsSidebar(
    onCategoryAction: () -> Unit = {},
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    detailFocusRequester: FocusRequester? = null
) {
    val provider = LocalSettingsProvider.current
    val categories by provider.categories.collectAsState()
    val selectedCategory by provider.selectedCategory.collectAsState()
    
    var sidebarPaneHasFocus by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Sync scroll when the selected category changes externally (e.g., via search)
    LaunchedEffect(selectedCategory) {
        selectedCategory?.let { sel ->
            val index = categories.indexOfFirst { it.title == sel.title }
            if (index != -1) {
                listState.scrollToItem(index)
            }
        }
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                sidebarPaneHasFocus = state.hasFocus
            }
    ) {
        Text(
            text = stringResource(id = org.videolan.television.R.string.preferences),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 24.dp, top = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical))
        )
        LazyColumn(state = listState) {
            items(categories, key = { it.title }) { category ->
                val isSelected = category.title == selectedCategory?.title
                val itemFocusRequester = remember { FocusRequester() }
                
                CategoryItem(
                    category = category,
                    isSelected = isSelected,
                    onSelected = { provider.selectCategory(category) },
                    onAction = onCategoryAction,
                    focusRequester = itemFocusRequester,
                    modifier = Modifier.focusProperties { 
                        detailFocusRequester?.let { right = it }
                    },
                    isNavigating = provider.isNavigating.collectAsState().value
                )

                // When the sidebar itself receives focus (e.g. via DPAD LEFT from detail pane),
                // redirect that focus to the currently selected item.
                LaunchedEffect(sidebarPaneHasFocus, isSelected) {
                    if (sidebarPaneHasFocus && isSelected) {
                        itemFocusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDetail(
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    focusRequester: FocusRequester,
    pendingFocusKey: String? = null,
    onFocusConsumed: () -> Unit = {}
) {
    val provider = LocalSettingsProvider.current
    val category by provider.selectedCategory.collectAsState()
    val searchQuery by provider.searchQuery.collectAsState()
    val searchResults by provider.searchResults.collectAsState()

    var detailPaneHasFocus by remember { mutableStateOf(false) }

    // Track the last focused item key for each category
    val lastFocusedItemPerCategory = remember { mutableMapOf<Int, String?>() }
    val listState = rememberLazyListState()

    // Handle scrolling to the target item when navigation happens
    LaunchedEffect(category?.title, pendingFocusKey) {
        if (category != null) {
            val target = pendingFocusKey
            if (target != null) {
                val index = category!!.items.indexOfFirst { it.key == target }
                if (index != -1) {
                    listState.scrollToItem(index)
                    return@LaunchedEffect
                }
            }
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                detailPaneHasFocus = state.hasFocus
                onFocusChanged(detailPaneHasFocus)
            }
            .focusable()
    ) {
        if (category != null) {
            val categoryId = category!!.title
            val firstFocusableKey = remember(category) { 
                category!!.items.firstOrNull { it !is SettingItem.Header }?.key 
            }

            Text(
                text = stringResource(id = category!!.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                modifier = Modifier.padding(top = dimensionResource(id = org.videolan.resources.R.dimen.tv_overscan_vertical))
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (category!!.title == R.string.search) {
                val searchFocusRequester = remember { FocusRequester() }
                SearchPane(
                    query = searchQuery,
                    results = searchResults,
                    onQueryChanged = { provider.setSearchQuery(it) },
                    onResultClick = { provider.init(it) },
                    focusRequester = searchFocusRequester
                )

                // Redirect focus to search input when detail pane gains focus
                LaunchedEffect(detailPaneHasFocus) {
                    if (detailPaneHasFocus) {
                        searchFocusRequester.requestFocus()
                    }
                }
            } else {
                LazyColumn(state = listState) {
                    items(category!!.items, key = { it.key }) { item ->
                        val isEnabled = provider.isEnabled(item)
                        val isHeader = item is SettingItem.Header
                        val itemFocusRequester = remember { FocusRequester() }
                        
                        Box(modifier = Modifier
                            .onFocusChanged { state ->
                                if (state.hasFocus) {
                                    Log.d("SettingsFocus", "Item gained focus: ${item.key}")
                                    lastFocusedItemPerCategory[categoryId] = item.key
                                    // Consume the pending focus key once landed
                                    if (item.key == pendingFocusKey) {
                                        Log.d("SettingsFocus", "Consuming pendingFocusKey: ${item.key}")
                                        onFocusConsumed()
                                    }
                                }
                            }
                        ) {
                            when (item) {
                                is SettingItem.Header -> {
                                    SettingHeader(title = stringResource(id = item.title))
                                }
                                is SettingItem.Toggle -> {
                                    val context = LocalContext.current
                                    ToggleSettingItem(
                                        item = item,
                                        checked = provider.getBooleanValue(item),
                                        summary = provider.getSummary(item),
                                        onCheckedChange = { provider.updateBooleanSetting(context, item, it) },
                                        enabled = isEnabled,
                                        modifier = Modifier.focusRequester(itemFocusRequester)
                                    )
                                }
                                is SettingItem.Action -> {
                                    val context = LocalContext.current
                                    ActionSettingItem(
                                        item = item,
                                        summary = provider.getSummary(item),
                                        onClick = { provider.executeAction(context, item) },
                                        enabled = isEnabled,
                                        modifier = Modifier.focusRequester(itemFocusRequester)
                                    )
                                }
                                is SettingItem.Options -> {
                                    val context = LocalContext.current
                                    var showDialog by remember { mutableStateOf(false) }
                                    val currentValue = provider.getStringValue(item)
                                    OptionsSettingItem(
                                        item = item,
                                        currentValue = currentValue,
                                        summary = provider.getSummary(item),
                                        onClick = { showDialog = true },
                                        enabled = isEnabled,
                                        modifier = Modifier.focusRequester(itemFocusRequester)
                                    )
                                    if (showDialog) {
                                        SelectionDialog(
                                            item = item,
                                            currentValue = currentValue,
                                            onDismiss = { showDialog = false },
                                            onValueSelected = { provider.updateStringSetting(context, item, it) }
                                        )
                                    }
                                }
                                is SettingItem.Color -> {
                                    val context = LocalContext.current
                                    ColorSettingItem(
                                        item = item,
                                        currentValue = provider.getColorValue(item),
                                        onClick = { provider.pickColor(context, item) },
                                        enabled = isEnabled,
                                        modifier = Modifier.focusRequester(itemFocusRequester)
                                    )
                                }
                                is SettingItem.Input -> {
                                    val context = LocalContext.current
                                    var showDialog by remember { mutableStateOf(false) }
                                    val currentValue = provider.getStringValue(item)
                                    InputSettingItem(
                                        item = item,
                                        currentValue = currentValue,
                                        summary = provider.getSummary(item),
                                        onClick = { showDialog = true },
                                        enabled = isEnabled,
                                        modifier = Modifier.focusRequester(itemFocusRequester)
                                    )
                                    if (showDialog) {
                                        InputDialog(
                                            item = item,
                                            currentValue = currentValue,
                                            onDismiss = { showDialog = false },
                                            onValueConfirmed = { provider.updateStringSetting(context, item, it) }
                                        )
                                    }
                                }
                                is SettingItem.Slider -> {
                                    var showDialog by remember { mutableStateOf(false) }
                                    val currentValue = provider.getIntValue(item)
                                    SliderSettingItem(
                                        item = item,
                                        currentValue = currentValue,
                                        summary = provider.getSummary(item),
                                        onClick = { showDialog = true },
                                        enabled = isEnabled,
                                        modifier = Modifier.focusRequester(itemFocusRequester)
                                    )
                                    if (showDialog) {
                                        SliderDialog(
                                            item = item,
                                            currentValue = currentValue,
                                            onDismiss = { showDialog = false },
                                            onValueConfirmed = { provider.updateIntSetting(item, it) }
                                        )
                                    }
                                }
                            }
                        }

                        // When the detail pane receives focus (e.g. via DPAD RIGHT from sidebar),
                        // redirect it to the last focused item in this category.
                        LaunchedEffect(detailPaneHasFocus, category, pendingFocusKey) {
                            if (detailPaneHasFocus && !isHeader && isEnabled) {
                                val targetKey = pendingFocusKey
                                val lastKey = lastFocusedItemPerCategory[categoryId]
                                
                                if (targetKey != null) {
                                    if (item.key == targetKey) {
                                        Log.d("SettingsFocus", "Requesting focus for target item: ${item.key}")
                                        itemFocusRequester.requestFocus()
                                    }
                                } else if (lastKey == item.key || (lastKey == null && item.key == firstFocusableKey)) {
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
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SettingsScreenPreview() {
    val categories = listOf(
        SettingCategory(org.videolan.vlc.R.string.video_prefs_category, listOf(
            SettingItem.Toggle("video_toggle", org.videolan.vlc.R.string.auto_rescan, org.videolan.vlc.R.string.auto_rescan_summary),
            SettingItem.Action("video_action", org.videolan.vlc.R.string.medialibrary_directories, org.videolan.vlc.R.string.directories_summary)
        ), org.videolan.vlc.R.drawable.ic_pref_video),
        SettingCategory(org.videolan.vlc.R.string.audio_prefs_category, emptyList(), org.videolan.vlc.R.drawable.ic_pref_audio)
    )
    
    VlcTVSettingsTheme {
        CompositionLocalProvider(LocalSettingsProvider provides MockSettingsProvider(categories)) {
            SettingsScreenContent()
        }
    }
}
