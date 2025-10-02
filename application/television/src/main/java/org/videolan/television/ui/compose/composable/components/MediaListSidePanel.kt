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

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.viewmodel.MediaListModelEntry
import org.videolan.television.viewmodel.MediaListsViewModel

@Composable
fun MediaListSidePanel(inCard: Boolean, listState: LazyListState, gridState: LazyGridState, entry: MediaListModelEntry, viewModel: MediaListsViewModel = viewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .padding(8.dp)
            .focusProperties {
                onEnter = {
                    focusRequester.requestFocus()
                }
            }
            .focusGroup()) {
        LabeledIconButton(stringResource(R.string.scroll_to_top), vectorImage = Icons.Outlined.ArrowUpward) {
            coroutineScope.launch {
                if (inCard)
                    gridState.animateScrollToItem(0)
                else
                    listState.animateScrollToItem(0)
            }
        }
        LabeledIconButton(stringResource(if (inCard) R.string.display_in_list else R.string.display_in_grid), vectorImage = if (inCard) Icons.AutoMirrored.Outlined.List else Icons.Outlined.GridView) {
            viewModel.changeDisplayInCard(entry)
        }
    }
}