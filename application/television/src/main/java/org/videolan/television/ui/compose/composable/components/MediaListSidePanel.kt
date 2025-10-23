/*
 * ************************************************************************
 *  MediaListSidePanel.kt
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

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.viewmodel.MediaListModelEntry
import org.videolan.television.viewmodel.MediaListsViewModel
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType

@Composable
fun MediaListSidePanel(inCard: Boolean, listState: ScrollableState, grouping: VideoGroupingType? = null, viewModel: MediaListsViewModel = viewModel(), listener: (MediaListSidePanelListenerKey, Any) -> Unit = { _, _ -> }) {
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .padding(bottom = 32.dp, top = 16.dp, start = 16.dp, end = 8.dp)
            .dropShadow(
                shape = RoundedCornerShape(20.dp),
                shadow = Shadow(
                    radius = 8.dp,
                    spread = 3.dp,
                    color = Color(0x40000000),
                    offset = DpOffset(x = 0.dp, 0.dp)
                )
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(8.dp)
            .focusProperties {
                onEnter = {
                    focusRequester.requestFocus()
                }
            }
            .focusGroup()) {
        LabeledIconButton(
            stringResource(R.string.scroll_to_top),
            modifier = Modifier.focusRequester(focusRequester = focusRequester),
            vectorImage = Icons.Outlined.ArrowUpward
        ) {
            coroutineScope.launch {
                when (listState) {
                    is LazyListState -> listState.animateScrollToItem(0)
                    is LazyGridState -> listState.animateScrollToItem(0)
                }
            }
        }
        LabeledIconButton(stringResource(if (inCard) R.string.display_in_list else R.string.display_in_grid), vectorImage = if (inCard) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView) {
            listener(MediaListSidePanelListenerKey.DISPLAY_MODE, !inCard)
        }
        if (grouping != null) {
            var expanded by remember { mutableStateOf(false) }

            LabeledIconButton(
                stringResource(R.string.videos_groups_title),
                vectorImage = Icons.Outlined.Collections
            ) {
                expanded = !expanded
            }
            DropdownMenu(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim),
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {

                DropdownMenuItem(
                    leadingIcon = {
                        if (grouping == VideoGroupingType.NAME)
                            Icon(Icons.Default.Check, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.video_min_group_length_name)) },
                    onClick = {
                        listener(MediaListSidePanelListenerKey.GROUPING, VideoGroupingType.NAME)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        if (grouping == VideoGroupingType.FOLDER)
                            Icon(Icons.Default.Check, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.video_min_group_length_folder)) },
                    onClick = {
                        listener(MediaListSidePanelListenerKey.GROUPING, VideoGroupingType.FOLDER)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        if (grouping == VideoGroupingType.NONE)
                            Icon(Icons.Default.Check, contentDescription = null)
                    },
                    text = { Text(stringResource(R.string.video_min_group_length_disable)) },
                    onClick = {
                        listener(MediaListSidePanelListenerKey.GROUPING, VideoGroupingType.NONE)
                        expanded = false
                    }
                )
            }
        }
    }
}

enum class MediaListSidePanelListenerKey {
    DISPLAY_MODE, GROUPING
}