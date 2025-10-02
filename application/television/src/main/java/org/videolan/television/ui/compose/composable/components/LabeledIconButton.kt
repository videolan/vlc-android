/*
 * ************************************************************************
 *  LabeledIconButton.kt
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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.viewmodel.MainActivityViewModel

@Composable
fun LabeledIconButton(label: String, modifier: Modifier = Modifier, vectorImage: ImageVector, viewModel: MainActivityViewModel = viewModel(), onClick: () -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(modifier = Modifier
        .onFocusChanged{
            hasFocus = it.isFocused
            if (!it.hasFocus) {
                viewModel.tooltip.value = null
            }
        }
        .onGloballyPositioned {
            if (hasFocus)
               with(it.positionInRoot()) {
                   viewModel.tooltip.value = TooltipInfo(label, x.toInt() + it.size.width / 2, y.toInt() + it.size.height)
               }
        }
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Icon(
                imageVector = vectorImage,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = label,
            )
        }

    }
}

data class TooltipInfo(val label: String, val x: Int, val y: Int)