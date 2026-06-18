/*
 * ************************************************************************
 *  BookmarkItem.kt
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

package org.videolan.television.ui.compose.composable.items

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.medialibrary.stubs.StubBookmark
import org.videolan.television.ui.compose.composable.components.ItemOptionsLine
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.viewmodels.BookmarkModel

@Composable
fun BookmarkItem(bookmark: Bookmark, bookmarkModel: BookmarkModel = viewModel()) {
    val activity = LocalActivity.current
    BookmarkItem(
        bookmark = bookmark,
        onBookmarkClick = { bookmarkModel.service?.setTime(bookmark.time) },
        onRenameClick = { RenameDialog.newInstance(bookmark).show((activity as FragmentActivity).supportFragmentManager, "sleep") },
        onDeleteClick = { bookmarkModel.delete(bookmark) }
    )
}

@Composable
fun BookmarkItem(
    bookmark: Bookmark,
    onBookmarkClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    isFocused = it.isFocused
                }
                .padding(horizontal = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onBookmarkClick()

                }
                .background(if (isFocused) WhiteTransparent10 else Transparent, RoundedCornerShape(8.dp))
                .padding(vertical = 16.dp)
                .focusable()
        ) {
            Text(
                bookmark.title ?: "",
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp).basicMarquee(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                Tools.millisToString(bookmark.time),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Box {
            LabeledIconButton(
                stringResource(R.string.more_actions),
                painterResource = painterResource(R.drawable.ic_more),
                onClick = { expanded = true },
            )
            if (expanded)
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { expanded = false }
                ) {

                    ItemOptionsLine(stringResource(R.string.rename), R.drawable.ic_edit) {
                        onRenameClick()
                        expanded = false
                    }
                    ItemOptionsLine(stringResource(R.string.delete), R.drawable.ic_delete) {
                        onDeleteClick()
                        expanded = false
                    }
                }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun BookmarkItemPreview() {
    VlcPreview {
        BookmarkItem(
            bookmark = StubBookmark(1, "My Bookmark", "Description", 1, 120000L),
            onBookmarkClick = {},
            onRenameClick = {},
            onDeleteClick = {}
        )
    }
}
