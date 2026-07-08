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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.videolan.vlc.R
import org.videolan.vlc.gui.preferences.search.PreferenceItem

@Composable
fun CategoryItem(
    category: SettingCategory,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        label = "backgroundColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    LaunchedEffect(isFocused) {
        if (isFocused && !isSelected) {
            onSelected()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .selectable(
                selected = isSelected,
                onClick = onAction,
                interactionSource = interactionSource,
                indication = null
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(40.dp) // Capsule shape for TV
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            category.icon?.let { iconRes ->
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = stringResource(id = category.title),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SettingHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun ToggleSettingItem(
    item: SettingItem.Toggle,
    checked: Boolean,
    summary: String?,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = summary,
        icon = item.icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
fun ActionSettingItem(
    item: SettingItem.Action,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = summary,
        icon = item.icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun OptionsSettingItem(
    item: SettingItem.Options,
    currentValue: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val displayValue = remember(currentValue, item.entries, item.entryValues) {
        val index = item.entryValues.indexOf(currentValue)
        if (index != -1 && index < item.entries.size) item.entries[index] else currentValue
    }

    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = displayValue,
        icon = item.icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MultiOptionsSettingItem(
    item: SettingItem.MultiOptions,
    currentValues: Set<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val displayValue = remember(currentValues, item.entries, item.entryValues) {
        val selectedEntries = item.entryValues.mapIndexedNotNull { index, value ->
            if (currentValues.contains(value)) item.entries[index] else null
        }
        if (selectedEntries.isEmpty()) "-" else selectedEntries.joinToString(", ")
    }

    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = displayValue,
        icon = item.icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun ColorSettingItem(
    item: SettingItem.Color,
    currentValue: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = null,
        icon = item.icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(currentValue))
                .alpha(if (enabled) 1f else 0.5f)
        )
    }
}

@Composable
fun InputSettingItem(
    item: SettingItem.Input,
    currentValue: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = summary ?: currentValue,
        icon = item.icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SliderSettingItem(
    item: SettingItem.Slider,
    currentValue: Int,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    BaseSettingItem(
        title = stringResource(id = item.title),
        summary = summary,
        icon = item.icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun BaseSettingItem(
    title: String,
    summary: String?,
    icon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.05f else 1f,
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else Color.Transparent,
        label = "backgroundColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    val summaryColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "summaryColor"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(40.dp), // Capsule shape for TV
        color = backgroundColor,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    painter = painterResource(id = it),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
                if (!summary.isNullOrEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = summaryColor
                    )
                }
            }
            if (content != null) {
                Spacer(modifier = Modifier.width(16.dp))
                content()
            }
        }
    }
}

/**
 * A TV-optimized button for dialogs with high-contrast focus states.
 */
@Composable
fun VlcDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        label = "backgroundColor"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.onPrimary
            isPrimary -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        label = "contentColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .height(48.dp)
            .widthIn(min = 120.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(24.dp),
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SelectionDialog(
    item: SettingItem.Options,
    currentValue: String?,
    onDismiss: () -> Unit,
    onValueSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(id = item.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                val listState = rememberLazyListState()
                val selectedIndex = item.entryValues.indexOf(currentValue)
                val focusRequesters = remember(item.entryValues) { List(item.entries.size) { FocusRequester() } }

                LaunchedEffect(Unit) {
                    if (selectedIndex != -1) {
                        listState.scrollToItem(selectedIndex)
                        focusRequesters[selectedIndex].requestFocus()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(item.entries.size) { index ->
                        val entry = item.entries[index]
                        val value = item.entryValues[index]
                        val isSelected = value == currentValue

                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Surface(
                            onClick = {
                                onValueSelected(value)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(40.dp),
                            color = when {
                                isFocused -> MaterialTheme.colorScheme.primary
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else -> Color.Transparent
                            },
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[index])
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    VlcDialogButton(
                        text = stringResource(id = R.string.cancel),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun MultiSelectionDialog(
    item: SettingItem.MultiOptions,
    currentValues: Set<String>,
    onDismiss: () -> Unit,
    onValuesSelected: (Set<String>) -> Unit
) {
    var selectedValues by remember { mutableStateOf(currentValues) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(400.dp)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(id = item.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(item.entries.size) { index ->
                        val entry = item.entries[index]
                        val value = item.entryValues[index]
                        val isChecked = selectedValues.contains(value)

                        Surface(
                            onClick = {
                                selectedValues = if (isChecked) {
                                    selectedValues - value
                                } else {
                                    selectedValues + value
                                }
                            },
                            shape = RoundedCornerShape(40.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null // Handled by Surface click
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onValuesSelected(selectedValues)
                        onDismiss()
                    }) {
                        Text(stringResource(id = R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
fun InputDialog(
    item: SettingItem.Input,
    currentValue: String,
    onDismiss: () -> Unit,
    onValueConfirmed: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentValue) }
    val okButtonFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { down = okButtonFocusRequester },
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
                    VlcDialogButton(
                        text = stringResource(id = R.string.cancel),
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VlcDialogButton(
                        text = stringResource(id = R.string.ok),
                        onClick = {
                            onValueConfirmed(text)
                            onDismiss()
                        },
                        isPrimary = true,
                        focusRequester = okButtonFocusRequester
                    )
                }
            }
        }
    }
}

@Composable
fun SliderDialog(
    item: SettingItem.Slider,
    currentValue: Int,
    onDismiss: () -> Unit,
    onValueConfirmed: (Int) -> Unit
) {
    var sliderValue by remember { mutableStateOf(currentValue.toFloat()) }
    val okButtonFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(480.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = item.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                val displayValue = if (item.valueDisplay == SliderValueDisplay.PERCENT) {
                    "${(sliderValue.toInt() * 100 / item.max)}%"
                } else {
                    sliderValue.toInt().toString()
                }

                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = item.min.toFloat()..item.max.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) {
                                okButtonFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                        .focusProperties { down = okButtonFocusRequester },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VlcDialogButton(
                        text = stringResource(id = R.string.cancel),
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VlcDialogButton(
                        text = stringResource(id = R.string.ok),
                        onClick = {
                            onValueConfirmed(sliderValue.toInt())
                            onDismiss()
                        },
                        isPrimary = true,
                        focusRequester = okButtonFocusRequester
                    )
                }
            }
        }
    }
}

@Composable
fun SearchPane(
    query: String,
    results: List<PreferenceItem>,
    onQueryChanged: (String) -> Unit,
    onResultClick: (PreferenceItem) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(id = R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (results.isEmpty() && query.length >= 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.search_no_result),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { item ->
                    SearchResultItem(item = item, onClick = { onResultClick(item) })
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    item: PreferenceItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else Color.Transparent,
        label = "backgroundColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(40.dp),
        color = backgroundColor,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (item.category.isNotEmpty()) {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
