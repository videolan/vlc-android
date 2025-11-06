/*
 * ************************************************************************
 *  DisplaySettings.kt
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.tools.KEY_ARTISTS_SHOW_ALL
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.viewmodels.DisplaySettingsEventManager
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettings(viewModel: MainActivityViewModel = viewModel()) {
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val current by viewModel.currentMediaListEntry.collectAsState()

    if (current != null) {
        ModalBottomSheet(
            modifier = Modifier,
            sheetState = sheetState,
            onDismissRequest = {
                coroutineScope.launch {
                    sheetState.hide()
                }
                viewModel.hideDisplaySettings()
            },
            dragHandle = {}
        ) {
            Column(modifier = Modifier) {
                Text(
                    stringResource(R.string.display_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp)
                )

                //Display in cards
                var inCard by remember { mutableStateOf(Settings.getInstance(context).getBoolean(current!!.inCardsKey, current!!.defaultInCard)) }
                DisplaySettingsLine(
                    painterResource(if (inCard) R.drawable.ic_view_list else R.drawable.ic_view_grid),
                    stringResource(if (inCard) R.string.display_in_list else R.string.display_in_grid)
                ) {
                    inCard = !inCard
                    Settings.getInstance(context).putSingle(current!!.inCardsKey, inCard)
                    viewModel.changeDisplaySettings(current!!)
                }

                //Show all artists
                if (current!! == MediaListEntry.ARTISTS) {
                    var showAllArtists by remember { mutableStateOf(Settings.getInstance(context).getBoolean(KEY_ARTISTS_SHOW_ALL, false)) }
                    val onShowAllClicked = {
                        showAllArtists = !showAllArtists
                        Settings.getInstance(context).putSingle(KEY_ARTISTS_SHOW_ALL, showAllArtists)
                        viewModel.changeDisplaySettings(current!!)
                        coroutineScope.launch {
                            DisplaySettingsEventManager.onShowAllArtistsChanged(current!!, showAllArtists)
                        }
                    }
                    DisplaySettingsLine(
                        painterResource(R.drawable.ic_sort_artist),
                        stringResource(R.string.artists_show_all_title),
                        endView = {
                            Checkbox(checked = showAllArtists, onCheckedChange = { onShowAllClicked() })
                        }
                    ) { onShowAllClicked() }
                }

                //Only favs
                var onlyFavs by remember { mutableStateOf(Settings.getInstance(context).getBoolean(current!!.onlyFavsKey, false)) }
                val onFavClicked = {
                    onlyFavs = !onlyFavs
                    Settings.getInstance(context).putSingle(current!!.onlyFavsKey, onlyFavs)
                    coroutineScope.launch {
                        DisplaySettingsEventManager.onOnlyFavsChanged(current!!, onlyFavs)
                    }
                }
                DisplaySettingsLine(painterResource(R.drawable.ic_fav_remove), stringResource(R.string.show_only_favs), endView = {
                    Checkbox(checked = onlyFavs, onCheckedChange = { onFavClicked() })
                }, onClick = { onFavClicked() })

                //Video grouping
                var expanded by remember { mutableStateOf(false) }
                if (current!! in arrayOf(MediaListEntry.VIDEO, MediaListEntry.VIDEO_GROUPS, MediaListEntry.VIDEO_FOLDER)) {
                    DisplaySettingsLine(painterResource(R.drawable.ic_group_display), stringResource(R.string.video_min_group_length_title), endView = {
                        Box(
                            modifier = Modifier
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        when (current) {
                                            MediaListEntry.VIDEO_GROUPS -> R.string.video_min_group_length_name
                                            MediaListEntry.VIDEO_FOLDER -> R.string.video_min_group_length_folder
                                            else -> R.string.video_min_group_length_disable
                                        }

                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    contentDescription = "DropDown Icon"
                                )
                            }
                            DropdownMenu(
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim),
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {

                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (current!! == MediaListEntry.VIDEO_GROUPS)
                                            Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    text = { Text(stringResource(org.videolan.television.R.string.video_min_group_length_name)) },
                                    onClick = {
                                        val newEntry = MediaListEntry.VIDEO_GROUPS
                                        coroutineScope.launch {
                                            DisplaySettingsEventManager.onGroupingChanged(newEntry)
                                        }
                                        Settings.getInstance(context).putSingle(KEY_GROUP_VIDEOS, VideoGroupingType.NAME.settingsKey)
                                        viewModel.changeDisplaySettings(newEntry)
                                        viewModel.changeCurrentMediaListEntry(newEntry)
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (current!! == MediaListEntry.VIDEO_FOLDER)
                                            Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    text = { Text(stringResource(org.videolan.television.R.string.video_min_group_length_folder)) },
                                    onClick = {
                                        val newEntry = MediaListEntry.VIDEO_FOLDER
                                        coroutineScope.launch {
                                            DisplaySettingsEventManager.onGroupingChanged(newEntry)
                                        }
                                        Settings.getInstance(context).putSingle(KEY_GROUP_VIDEOS, VideoGroupingType.FOLDER.settingsKey)
                                        viewModel.changeDisplaySettings(newEntry)
                                        viewModel.changeCurrentMediaListEntry(newEntry)
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (current!! == MediaListEntry.VIDEO)
                                            Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    text = { Text(stringResource(org.videolan.television.R.string.video_min_group_length_disable)) },
                                    onClick = {
                                        val newEntry = MediaListEntry.VIDEO
                                        coroutineScope.launch {
                                            DisplaySettingsEventManager.onGroupingChanged(newEntry)
                                        }
                                        Settings.getInstance(context).putSingle(KEY_GROUP_VIDEOS, VideoGroupingType.NONE.settingsKey)
                                        viewModel.changeDisplaySettings(newEntry)
                                        viewModel.changeCurrentMediaListEntry(newEntry)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }, onClick = { expanded = !expanded })
                }
            }
        }
    }
}

@Composable
fun DisplaySettingsLine(painter: Painter, text: String, endView: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(
                onClick = { onClick() }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter, contentDescription = stringResource(R.string.display_in_list), modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .size(24.dp)
        )
        Text(text, modifier = Modifier.weight(1f))
        endView?.invoke()
    }
}

data class DisplaySettingsDescription(
    val incard: Boolean,
    val onlyFavs: Boolean,
    val grouping: VideoGroupingType? = null,
    val currentSort: Int = -1,
    val currentSortDesc: Boolean = false,
    val showOnlyMultimediaFiles: Boolean? = null,
    val defaultPlaybackActions: List<DefaultPlaybackAction>? = null,
    val defaultActionType: String? = null,
    val currentDefaultAction: DefaultPlaybackAction? = null
)