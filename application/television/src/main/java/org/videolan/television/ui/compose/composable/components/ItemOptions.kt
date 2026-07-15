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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.CtxMenuItem
import org.videolan.vlc.gui.dialogs.Simple
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.MediaListEntry

@Composable
fun ItemOptions(item: MediaLibraryItem, position: Int, entry: MediaListEntry, onDismiss: () -> Unit, mainActivityViewModel: MainActivityViewModel? = if (LocalInspectionMode.current) null else hiltViewModel()) {
    val activity = LocalActivity.current
    val items = mainActivityViewModel?.getFlags(activity!!, entry, item) ?: return
    ItemOptions(
        title = item.title,
        items = items,
        onDismiss = onDismiss,
        onItemClick = { ctxMenuItem ->
            mainActivityViewModel.onCtxClick(entry, item, position, ctxMenuItem)
            onDismiss()
        }
    )
}

@Composable
fun ItemOptions(
    title: String,
    items: List<CtxMenuItem>,
    onDismiss: () -> Unit,
    onItemClick: (CtxMenuItem) -> Unit
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = { onDismiss() }
    ) {
        ItemOptionsContent(title, items, onItemClick)
    }
}

@Composable
fun ItemOptionsContent(
    title: String,
    items: List<CtxMenuItem>,
    onItemClick: (CtxMenuItem) -> Unit
) {
    Text(
        title,
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
            onItemClick(it)
        }
    }
}

@Composable
fun ItemOptionsLine(title: String, icon: Int, enabled: Boolean? = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        leadingIcon = {
            Image(
                painter = painterResource(id = icon),
                contentDescription = title,
                colorFilter = ColorFilter.tint(if (enabled == true) MaterialTheme.colorScheme.secondary else White),
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

@Preview(device = "id:tv_1080p")
@Composable
private fun ItemOptionsPreview() {
    VlcPreview {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Column(modifier = Modifier.widthIn(min = 200.dp)) {
                    ItemOptionsContent(
                        title = "Item Title",
                        items = listOf(
                            Simple(ContextOption.CTX_PLAY, "Play", R.drawable.ic_play),
                            Simple(ContextOption.CTX_APPEND, "Append", R.drawable.ic_play_append),
                            Simple(ContextOption.CTX_DELETE, "Delete", R.drawable.ic_trash)
                        ),
                        onItemClick = {}
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ItemOptionsLinePreview() {
    VlcPreview {
        ItemOptionsLine(
            title = "Option Title",
            icon = R.drawable.ic_play,
            enabled = true,
            onClick = {}
        )
    }
}
