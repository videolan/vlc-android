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

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.viewmodel.MainActivityViewModel
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
    mainActivityViewModel: MainActivityViewModel = viewModel()
) {
    var indicatorCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    val pxToMove = indicatorCoords?.positionInParent()?.x?.toInt() ?: mainActivityViewModel.getOffsetForTab(key)

    val focusRequesters = remember {
        List(tabNumber) { FocusRequester() }
    }

    val offset by animateIntOffsetAsState(
        targetValue =
            IntOffset(pxToMove, 0),
        label = "offset"
    )

    if (forceFocus) {
        LaunchedEffect("") {
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
                .width((indicatorCoords?.size?.width?.px?.dp ?: 0.dp))
                .height(indicatorCoords?.size?.height?.px?.dp ?: 0.dp)
                .offset {
                    if (mainActivityViewModel.getOffsetForTab(key) == -1)
                        IntOffset(pxToMove, 0)
                    else {
                        mainActivityViewModel.setOffsetForTab(key, offset.x)
                        offset
                    }
                }
        ) {
            indicator(hasFocus)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .focusProperties {
                    onEnter = {
                        focusRequesters[selectedTabIndex].requestFocus()
                        hasFocus = true
                    }
                    onExit = {
                        hasFocus = false
                    }
                }
                .focusGroup()
        ) {
            for (index in 0..tabNumber - 1) {
                VLCTab(
                    selectedTabIndex == index,
                    modifier = Modifier
                        .focusRequester(focusRequester = focusRequesters[index]),
                    enabled = true,
                    onFocused = {
                        onSelected(index)
                    },
                    onBoundChanged = { indicatorCoords = it },
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

