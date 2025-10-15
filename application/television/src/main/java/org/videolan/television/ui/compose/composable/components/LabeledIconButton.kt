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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.television.R
import org.videolan.television.viewmodel.MainActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledIconButton(label: String, modifier: Modifier = Modifier, vectorImage: ImageVector, onClick: () -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip(
                shape = RoundedCornerShape(50),
                containerColor = MaterialTheme.colorScheme.surfaceDim,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50))
            ) {
                Text(label)
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .onFocusChanged{
                    hasFocus = it.hasFocus
                }
        ) {
            Icon(
                imageVector = vectorImage,
                tint = if (hasFocus) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4F),
                contentDescription = label,
            )
        }

    }
}