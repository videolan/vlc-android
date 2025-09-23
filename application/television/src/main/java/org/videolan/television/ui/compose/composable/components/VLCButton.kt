/*
 * ************************************************************************
 *  VLCButton.kt
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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.videolan.television.R
import org.videolan.television.ui.compose.theme.BackgroundColorDark
import org.videolan.television.ui.compose.theme.White

@Composable
fun VLCButton(icon: Int, text:Int, onClick: ()->Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) White else BackgroundColorDark.copy(0.4F)),
        onClick = onClick,
        modifier = Modifier
            .padding(8.dp)
            .onFocusChanged {
                focused = it.isFocused
            }
    ) {
        Icon(
            painter = painterResource(icon),
            tint = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
            contentDescription = stringResource(id = R.string.preferences),
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .align(Alignment.CenterVertically)
        )
        Text(
            text = stringResource(text),
            color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
            modifier = Modifier
                .padding(8.dp)
        )
    }
}