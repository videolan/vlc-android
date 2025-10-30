/*
 * ************************************************************************
 *  PlaylistsList.kt
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

package org.videolan.television.ui.compose.composable.lists

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import org.videolan.television.ui.utils.MediaListEntry

@Composable
fun PlaylistsList(onFocusExit: () -> Unit, onFocusEnter: () -> Unit) {
    Box(modifier = Modifier
        .focusProperties {
            onEnter = {
                onFocusEnter()

            }
            onExit = {
                if (requestedFocusDirection == FocusDirection.Up) {
                    onFocusExit()
                }
            }
        }
        .focusGroup(),) {
        MediaList(MediaListEntry.ALL_PLAYLISTS)
    }
}