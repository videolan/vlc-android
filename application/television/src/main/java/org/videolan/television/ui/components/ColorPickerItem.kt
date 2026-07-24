/*
 * ************************************************************************
 *  ColorPickerItem.kt
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

package org.videolan.television.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.videolan.television.ui.compose.theme.VlcTVTheme

@Composable
fun ColorPickerItem(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(if (isFocused) 1.2f else 1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(4.dp)
            .border(1.dp, if (isFocused) Color.White else Color(0xFF9E9E9E), CircleShape)
            .background(Color(color), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(id = org.videolan.vlc.R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(2.dp)
            )
        }
    }
}

@Preview
@Composable
private fun ColorPickerItemPreview() {
    VlcTVTheme {
        ColorPickerItem(
            color = android.graphics.Color.RED,
            isSelected = true,
            onClick = {},
            modifier = Modifier.size(48.dp)
        )
    }
}

@Preview
@Composable
private fun ColorPickerItemNotSelectedPreview() {
    VlcTVTheme {
        ColorPickerItem(
            color = android.graphics.Color.BLUE,
            isSelected = false,
            onClick = {},
            modifier = Modifier.size(48.dp)
        )
    }
}
