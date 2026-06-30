/*
 * ************************************************************************
 *  SettingComponents.kt
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

package org.videolan.television.ui.preferences

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.vlc.R

/**
 * A base focusable row for a setting item in the TV settings.
 *
 * This component handles the common TV UI patterns like focus state background,
 * layout of icon, title, and summary, and provides a slot for trailing content.
 *
 * @param title The title string to display.
 * @param modifier The [Modifier] to be applied to the row.
 * @param summary An optional summary/description string to display below the title.
 * @param icon An optional drawable resource ID to display as an icon on the left.
 * @param onClick The callback to trigger when the row is clicked or selected via D-pad.
 * @param content An optional `@Composable` block for trailing content (e.g., a Toggle).
 */
@Composable
fun SettingItemRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Int? = null,
    onClick: () -> Unit = {},
    content: @Composable (() -> Unit)? = null
) {
    var hasFocus by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent,
        label = "backgroundColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (content != null) {
            content()
        }
    }
}

/**
 * A setting item component that displays a toggle switch.
 *
 * @param item The [SettingItem.Toggle] definition.
 * @param checked The current checked state of the toggle.
 * @param onCheckedChange Callback triggered when the toggle state is changed.
 */
@Composable
fun ToggleSettingItem(
    item: SettingItem.Toggle,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        onClick = { onCheckedChange(!checked) },
        content = {
            Switch(
                checked = checked,
                onCheckedChange = null, // Interaction is handled by the parent row
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

/**
 * A setting item component for simple clickable actions.
 *
 * @param item The [SettingItem.Action] definition.
 * @param onClick Callback triggered when the action is clicked.
 */
@Composable
fun ActionSettingItem(
    item: SettingItem.Action,
    onClick: () -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        onClick = onClick
    )
}

/**
 * Preview for the setting components.
 */
@Preview(device = "id:tv_1080p")
@Composable
private fun SettingComponentsPreview() {
    VlcTVTheme {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            ToggleSettingItem(
                item = SettingItem.Toggle("key", R.string.auto_rescan, R.string.auto_rescan_summary),
                checked = true,
                onCheckedChange = {}
            )
            ActionSettingItem(
                item = SettingItem.Action("key", R.string.directories, R.string.directories_summary),
                onClick = {}
            )
        }
    }
}
