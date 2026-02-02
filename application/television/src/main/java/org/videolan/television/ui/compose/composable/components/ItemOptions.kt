/*
 * ************************************************************************
 *  ItemOptions.kt
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

package org.videolan.television.ui.compose.composable.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.util.MediaListEntry

@Composable
fun ItemOptions(item: MediaLibraryItem, position: Int, entry: MediaListEntry, onDismiss: () -> Unit, mainActivityViewModel: MainActivityViewModel = viewModel()) {
    val activity = LocalActivity.current
    val items = mainActivityViewModel.getFlags(activity!!, entry, item) ?: return
    DropdownMenu(
        expanded = true,
        onDismissRequest = { onDismiss() }
    ) {
        Text(
            item.title,
            maxLines = 2,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .widthIn(0.dp, 250.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusable()
        )

        items.forEach {
            ItemOptionsLine(it.title, it.icon) {
                mainActivityViewModel.onCtxClick(entry, item, position, it)
                onDismiss()
            }
        }
    }
}

@Composable
fun ItemOptionsLine(title: String, icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        leadingIcon = {
            Image(
                painter = painterResource(id = icon),
                contentDescription = title,
                colorFilter = ColorFilter.tint(White),
                modifier = Modifier
                    .size(24.dp)
            )

        },
        onClick = { onClick() }
    )
}

object BrowserItemCtxFlags {
    val isFolderEmpty = 0x10000
    val hasMedias = 0x20000
    val hasSubfolders = 0x40000
}