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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.vlc.R

/**
 * A TV-optimized focusable row for a setting item.
 *
 * It uses a pill shape and scales slightly when focused to provide clear visual feedback
 * on TV screens, while remaining fully clickable for mouse and touch users.
 *
 * @param title The title string to display.
 * @param modifier The [Modifier] to be applied to the row.
 * @param summary An optional summary/description string to display below the title.
 * @param icon An optional drawable resource ID to display as an icon on the left.
 * @param onClick The callback to trigger when the row is clicked or selected.
 * @param content An optional `@Composable` block for trailing content.
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
    
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.05f else 1f,
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    val summaryColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "summaryColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { hasFocus = it.hasFocus }
            .clip(CircleShape) // Pill shape
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
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
 * A TV-optimized setting category item for the sidebar.
 *
 * @param category The [SettingCategory] definition.
 * @param isSelected Whether this category is currently selected.
 * @param onSelected Callback triggered when the category is selected or focused.
 */
@Composable
fun CategoryItem(
    category: SettingCategory,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            hasFocus -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.surfaceVariant
            else -> Color.Transparent
        },
        label = "backgroundColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .onFocusChanged { 
                hasFocus = it.hasFocus 
                if (it.hasFocus) onSelected()
            }
            .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
            .background(backgroundColor)
            .clickable(onClick = onSelected)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category.icon != null) {
            Icon(
                painter = painterResource(id = category.icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = stringResource(id = category.title),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A setting item component that displays a toggle switch.
 *
 * @param item The [SettingItem.Toggle] definition.
 * @param checked The current checked state.
 * @param summary An optional summary string (overrides the one in [item]).
 * @param onCheckedChange Callback triggered when the state is changed.
 */
@Composable
fun ToggleSettingItem(
    item: SettingItem.Toggle,
    checked: Boolean,
    summary: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = summary ?: item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        onClick = { onCheckedChange(!checked) },
        content = {
            Switch(
                checked = checked,
                onCheckedChange = null,
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
 * @param summary An optional summary string (overrides the one in [item]).
 * @param onClick Callback triggered when the action is clicked.
 */
@Composable
fun ActionSettingItem(
    item: SettingItem.Action,
    summary: String? = null,
    onClick: () -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = summary ?: item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        onClick = onClick
    )
}

/**
 * A setting item component for multiple options selection.
 *
 * Displays the current value in the summary or as trailing content.
 *
 * @param item The [SettingItem.Options] definition.
 * @param currentValue The current value key.
 * @param summary An optional summary string (overrides the one in [item]).
 * @param onClick Callback triggered to open the selection UI.
 */
@Composable
fun OptionsSettingItem(
    item: SettingItem.Options,
    currentValue: String?,
    summary: String? = null,
    onClick: () -> Unit
) {
    val currentTitle = remember(currentValue, item.entryValues, item.entries) {
        val index = item.entryValues.indexOf(currentValue)
        if (index != -1) item.entries[index] else currentValue ?: ""
    }

    SettingItemRow(
        title = stringResource(id = item.title),
        summary = summary ?: currentTitle,
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
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(48.dp)) {
            CategoryItem(
                category = SettingCategory(R.string.video_prefs_category, emptyList(), R.drawable.ic_pref_video),
                isSelected = true,
                onSelected = {}
            )
            Spacer(modifier = Modifier.size(16.dp))
            ToggleSettingItem(
                item = SettingItem.Toggle("key", R.string.auto_rescan, R.string.auto_rescan_summary),
                checked = true,
                onCheckedChange = {}
            )
            Spacer(modifier = Modifier.size(16.dp))
            ActionSettingItem(
                item = SettingItem.Action("key", R.string.directories, R.string.directories_summary),
                onClick = {}
            )
            Spacer(modifier = Modifier.size(16.dp))
            OptionsSettingItem(
                item = SettingItem.Options(
                    "key", 
                    R.string.hardware_acceleration, 
                    entries = listOf("Automatic", "Disabled"), 
                    entryValues = listOf("-1", "0")
                ),
                currentValue = "-1",
                onClick = {}
            )
        }
    }
}
