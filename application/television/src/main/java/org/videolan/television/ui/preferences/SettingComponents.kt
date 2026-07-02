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

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.videolan.television.ui.compose.theme.VlcTVSettingsTheme
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
    onSelected: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
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
            .padding(end = 16.dp) // Space between item and detail pane edge
            .heightIn(min = 56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { 
                hasFocus = it.hasFocus 
                Log.d("VLC/Settings", "CategoryItem: ${category.title} hasFocus=$hasFocus")
                if (it.hasFocus) onSelected()
            }
            .clip(CircleShape) // Standard pill shape
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
 * A simple non-focusable header for settings sections.
 */
@Composable
fun SettingHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
    )
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
    modifier: Modifier = Modifier,
    summary: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = summary ?: item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        content = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    )
}

/**
 * A setting item component for simple clickable actions.
 *
 * @param item The [SettingItem.Action] definition.
 * @param modifier The [Modifier] to be applied.
 * @param summary An optional summary string (overrides the one in [item]).
 * @param onClick Callback triggered when the action is clicked.
 */
@Composable
fun ActionSettingItem(
    item: SettingItem.Action,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: () -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = summary ?: item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        modifier = modifier,
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
 * @param modifier The [Modifier] to be applied.
 * @param summary An optional summary string (overrides the one in [item]).
 * @param onClick Callback triggered to open the selection UI.
 */
@Composable
fun OptionsSettingItem(
    item: SettingItem.Options,
    currentValue: String?,
    modifier: Modifier = Modifier,
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
        modifier = modifier,
        onClick = onClick,
        content = {
             Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    )
}

/**
 * A setting item for color selection, displaying a color preview.
 *
 * @param item The [SettingItem.Color] definition.
 * @param currentValue The current color value as an ARGB integer.
 * @param modifier The [Modifier] to be applied.
 * @param onClick Callback triggered to open the color picker.
 */
@Composable
fun ColorSettingItem(
    item: SettingItem.Color,
    currentValue: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = item.summary?.let { stringResource(id = it) },
        icon = item.icon,
        modifier = modifier,
        onClick = onClick,
        content = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(currentValue))
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
            )
        }
    )
}

/**
 * A setting item for text/number input.
 *
 * @param item The [SettingItem.Input] definition.
 * @param currentValue The current input value.
 * @param modifier The [Modifier] to be applied.
 * @param summary An optional summary string.
 * @param onClick Callback to open the input dialog.
 */
@Composable
fun InputSettingItem(
    item: SettingItem.Input,
    currentValue: String?,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: () -> Unit
) {
    SettingItemRow(
        title = stringResource(id = item.title),
        summary = summary ?: currentValue,
        icon = item.icon,
        modifier = modifier,
        onClick = onClick,
        content = {
            Icon(
                painter = painterResource(id = R.drawable.ic_edit),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    )
}

/**
 * A TV-optimized selection dialog for [SettingItem.Options].
 *
 * @param item The options setting item.
 * @param currentValue The currently selected value key.
 * @param onDismiss Callback to dismiss the dialog.
 * @param onValueSelected Callback when a new value is selected.
 */
@Composable
fun SelectionDialog(
    item: SettingItem.Options,
    currentValue: String?,
    onDismiss: () -> Unit,
    onValueSelected: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp.dp * 0.9f)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .wrapContentHeight()
            ) {
                Text(
                    text = stringResource(id = item.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val listState = rememberLazyListState()
                val focusRequesters = remember(item.entryValues) {
                    List(item.entryValues.size) { FocusRequester() }
                }
                val selectedIndex = remember(currentValue, item.entryValues) {
                    item.entryValues.indexOf(currentValue).coerceAtLeast(0)
                }

                // Auto-scroll and focus selected item
                LaunchedEffect(Unit) {
                    if (selectedIndex != -1) {
                        listState.scrollToItem(selectedIndex)
                        if (selectedIndex < focusRequesters.size) {
                            focusRequesters[selectedIndex].requestFocus()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    itemsIndexed(item.entries) { index, entry ->
                        val value = item.entryValues[index]
                        val isSelected = value == currentValue
                        
                        SelectionDialogItem(
                            title = entry,
                            isSelected = isSelected,
                            focusRequester = focusRequesters[index],
                            onClick = {
                                onValueSelected(value)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(id = R.string.cancel),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .focusProperties { 
                            focusRequesters.getOrNull(selectedIndex)?.let { up = it }
                        }
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * An item row within the [SelectionDialog].
 */
@Composable
private fun SelectionDialogItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
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
            .clip(CircleShape)
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.hasFocus }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (hasFocus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A TV-optimized input dialog for [SettingItem.Input].
 *
 * @param item The input setting item.
 * @param currentValue The currently stored value.
 * @param onDismiss Callback to dismiss the dialog.
 * @param onValueConfirmed Callback when a new value is confirmed.
 */
@Composable
fun InputDialog(
    item: SettingItem.Input,
    currentValue: String?,
    onDismiss: () -> Unit,
    onValueConfirmed: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentValue ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = stringResource(id = item.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(id = R.string.not_set)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { 
                        onValueConfirmed(text)
                        onDismiss()
                    }) {
                        Text(stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }
}

/**
 * Preview for the setting components.
 */
@Preview(device = "id:tv_1080p")
@Composable
private fun SettingComponentsPreview() {
    VlcTVSettingsTheme {
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
            SettingHeader(title = "Advanced")
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
            Spacer(modifier = Modifier.size(16.dp))
            InputSettingItem(
                item = SettingItem.Input("key", R.string.custom_libvlc_options),
                currentValue = "",
                onClick = {}
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SelectionDialogPreview() {
    VlcTVSettingsTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            SelectionDialog(
                item = SettingItem.Options(
                    "key",
                    R.string.hardware_acceleration,
                    entries = listOf("Automatic", "Disabled", "Decoding only", "Full acceleration"),
                    entryValues = listOf("-1", "0", "1", "2")
                ),
                currentValue = "-1",
                onDismiss = {},
                onValueSelected = {}
            )
        }
    }
}
