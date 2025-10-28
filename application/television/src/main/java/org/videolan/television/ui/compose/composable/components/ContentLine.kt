/*
 * ************************************************************************
 *  ContentLine.kt
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.ui.compose.composable.items.AudioItem
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.vlc.R

@Composable
fun ContentLine(items: List<MediaLibraryItem>?, historyLoading: Boolean?, text: Int, titleFocusable: Boolean = true, spannableDescription: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (titleFocusable)
                    focused = it.isFocused
            }
            .padding(top = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) WhiteTransparent10 else Transparent)
            .focusable(titleFocusable)
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .weight(1F)
                .padding(horizontal = if (titleFocusable) 16.dp else 0.dp)
        )
        if (titleFocusable)
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = stringResource(id = text),
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp)
                    .padding(end = 16.dp)
            )
    }
    VlcLoader(historyLoading) {
        LazyRow(
            contentPadding = PaddingValues(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.focusGroup()
        ) {
            items(items?.size ?: 0) { index ->
                Box(modifier = Modifier.width(150.dp)) {
                    AudioItem(items!!, index, spannableDescription = spannableDescription)
                }
            }
        }
    }
}