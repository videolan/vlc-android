/*
 * ************************************************************************
 *  VLCTabRow.kt
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

package org.videolan.television.ui.compose.composable.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.TabInfo
import org.videolan.tools.px

private const val TAG = "VLC/VLCTabRow"

/**
 * Custom implementation of a Tab row
 * It workarounds the material 3 TabRow limitations
 *
 * @param selectedTabIndex The index of the currently selected tab
 * @param modifier Modifier to be applied to the layout
 * @param forceFocus Force the focus on the currently selected tab
 * @param onSelected Callback called when a tab is selected
 * @param indicator The indicator composable to be used
 * @param tabNumber The number of tabs to display
 * @param getTab The composable to be used for each tab
 * @param key The key of the tab row, used to store the offset of the indicator
 */
@Composable
fun VLCTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    forceFocus: Boolean = false,
    onSelected: (Int) -> Unit,
    indicator: @Composable (Boolean) -> Unit,
    tabNumber: Int,
    getTab: @Composable (Int, Boolean) -> Unit,
    key: String,
    mainActivityViewModel: MainActivityViewModel? = if (LocalInspectionMode.current) null else viewModel()
) {
    val tabInfo = mainActivityViewModel?.getTabInfo(key)
    var hasFocus by remember { mutableStateOf(false) }

    var targetX by remember(key) { mutableIntStateOf(tabInfo?.x ?: 0) }
    var targetWidth by remember(key) { mutableIntStateOf(tabInfo?.width ?: 0) }
    var targetHeight by remember(key) { mutableIntStateOf(tabInfo?.height ?: 0) }
    var isInitialized by remember(key) { mutableStateOf(false) }

    val focusRequesters = remember(key, tabNumber) {
        List(tabNumber) { FocusRequester() }
    }

    val offsetAnim = remember(key) { Animatable(IntOffset(targetX, 0), IntOffset.VectorConverter) }
    val widthAnim = remember(key) { Animatable(targetWidth.px.dp, Dp.VectorConverter) }
    val heightAnim = remember(key) { Animatable(targetHeight.px.dp, Dp.VectorConverter) }

    LaunchedEffect(key, targetX, targetWidth, targetHeight) {
        if (isInitialized) {
            offsetAnim.animateTo(IntOffset(targetX, 0), tween())
            widthAnim.animateTo(targetWidth.px.dp, tween())
            heightAnim.animateTo(targetHeight.px.dp, tween())
        } else {
            offsetAnim.snapTo(IntOffset(targetX, 0))
            widthAnim.snapTo(targetWidth.px.dp)
            heightAnim.snapTo(targetHeight.px.dp)
            if (targetWidth > 0) isInitialized = true
        }
    }

    if (forceFocus) {
        LaunchedEffect(key) {
            if (selectedTabIndex < focusRequesters.size)
                focusRequesters[selectedTabIndex].requestFocus()
        }
    }
    Box(
        modifier = modifier
            .onFocusChanged {
                hasFocus = it.hasFocus
            }
    ) {
        Box(
            modifier = Modifier
                .width(widthAnim.value)
                .height(heightAnim.value)
                .offset { offsetAnim.value }
        ) {
            indicator(hasFocus)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .focusProperties {
                    onEnter = {
                        if (selectedTabIndex < focusRequesters.size)
                            focusRequesters[selectedTabIndex].requestFocus()
                        hasFocus = true
                    }
                    onExit = {
                        hasFocus = false
                    }
                }
                .focusGroup()
        ) {
            for (index in 0 until tabNumber) {
                VLCTab(
                    selectedTabIndex == index,
                    modifier = Modifier
                        .focusRequester(focusRequester = focusRequesters[index]),
                    enabled = true,
                    onFocused = {
                        onSelected(index)
                    },
                    onBoundChanged = { coords ->
                        val newX = coords.positionInParent().x.toInt()
                        val newW = coords.size.width
                        val newH = coords.size.height
                        if (newX != targetX || newW != targetWidth || newH != targetHeight) {
                            targetX = newX
                            targetWidth = newW
                            targetHeight = newH
                            mainActivityViewModel?.setTabInfo(key, TabInfo(targetX, targetWidth, targetHeight))
                        }
                    },
                ) {
                    getTab(index, selectedTabIndex == index)
                }
            }
        }
    }
}

@Composable
fun VLCTab(
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBoundChanged: (LayoutCoordinates) -> Unit,
    onFocused: () -> Unit,
    content: @Composable () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (selected || hasFocus) onBoundChanged(coords)
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                if (hasFocus) onFocused()
            }
            .padding(4.dp)
            .zIndex(2f)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }) {
                if (enabled) onFocused()
            }
            .focusable(enabled)
    ) {
        content()
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun VLCTabPreview() {
    var selected by remember { mutableStateOf(false) }
    VlcPreview {
        VLCTab(
            selected = selected,
            onBoundChanged = {},
            onFocused = { selected = true }
        ) {
            Text(text = "Tab Item", color = White, modifier = Modifier.padding(8.dp))
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun VLCTabRowPreview() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Video", "Audio", "Browse", "Playlists")
    VlcPreview {
        VLCTabRow(
            selectedTabIndex = selectedTabIndex,
            onSelected = { selectedTabIndex = it },
            indicator = { hasFocus ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))

                )
            },
            tabNumber = tabs.size,
            getTab = { index, isSelected ->
                Text(
                    text = tabs[index],
                    color = if (isSelected) MaterialTheme.colorScheme.background else White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            },
            key = "preview_tabs"
        )
    }
}
