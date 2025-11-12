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

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
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
            sheetGesturesEnabled = false,
            onDismissRequest = {
                coroutineScope.launch {
                    sheetState.hide()
                }
                viewModel.hideDisplaySettings()
            },
            dragHandle = null
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.display_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
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

                VideoGroupingItem(current!!) { newEntry, videoGroupingType ->
                    coroutineScope.launch {
                        DisplaySettingsEventManager.onGroupingChanged(newEntry)
                    }
                    Settings.getInstance(context).putSingle(KEY_GROUP_VIDEOS, videoGroupingType.settingsKey)
                    viewModel.changeDisplaySettings(newEntry)
                    viewModel.changeCurrentMediaListEntry(newEntry)
                }

                PlaybackActionsItem(current!!, context)

                Text(
                    stringResource(R.string.sortby),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 16.dp)
                )
                var currentSort by remember { mutableStateOf(current!!.currentSort) }
                var currentSortDesc by remember { mutableStateOf(current!!.currentSortDesc) }

                val onSortClick = { sort: Int, desc: Boolean ->
                    current!!.currentSortDesc = desc
                    current!!.currentSort = sort
                    currentSort = sort
                    currentSortDesc = desc
                    coroutineScope.launch {
                        DisplaySettingsEventManager.onSortChanged(current!!, currentSort, currentSortDesc)
                    }
                    viewModel.changeCurrentMediaListEntry(current)
                }

                current!!.sorts.forEach {
                    val isCurrentSort = (it == currentSort || currentSort == Medialibrary.SORT_DEFAULT && it == Medialibrary.SORT_ALPHA)
                    val sortItem = it.getSortItemDescriptor(isCurrentSort)

                    Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
                        Icon(
                            painterResource(sortItem.icon),
                            contentDescription = stringResource(sortItem.title),
                            modifier = Modifier
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                                .size(24.dp)
                        )
                        Text(
                            stringResource(sortItem.title), modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .wrapContentHeight(align = Alignment.CenterVertically)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .combinedClickable(
                                        onClick = { onSortClick(sortItem.sort, false) }
                                    )
                            ) {
                                Text(
                                    stringResource(sortItem.ascending),
                                    color = if (isCurrentSort && !currentSortDesc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .padding(horizontal = 16.dp)
                                )
                                if (isCurrentSort && !currentSortDesc)
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(vertical = 8.dp, horizontal = 16.dp)
                                            .size(24.dp)
                                    )
                            }
                            Row(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .combinedClickable(
                                        onClick = { onSortClick(sortItem.sort, true) }
                                    )
                            ) {
                                Text(
                                    stringResource(sortItem.descending),
                                    color = if (isCurrentSort && currentSortDesc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .padding(horizontal = 16.dp)
                                )
                                if (isCurrentSort && currentSortDesc)
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(vertical = 8.dp, horizontal = 16.dp)
                                            .size(24.dp)
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackActionsItem(current: MediaListEntry, context: Context) {
    //Playback actions
    var playbackActionsExpanded by remember { mutableStateOf(false) }
    var currentPlaybackAction = current.defaultPlaybackActionMediaType.getCurrentPlaybackAction(Settings.getInstance(context))
    DisplaySettingsLine(
        painterResource(R.drawable.ic_play),
        stringResource(R.string.default_playback_action),
        subtitle = stringResource(current.defaultPlaybackActionMediaType.title),
        endView = {
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
                        text = stringResource(currentPlaybackAction.title)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "DropDown Icon"
                    )
                }
                DropdownMenu(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceDim),
                    expanded = playbackActionsExpanded,
                    onDismissRequest = { playbackActionsExpanded = false }
                ) {

                    val entries =
                        if (current.defaultPlaybackActionMediaType.allowPlayAll) DefaultPlaybackAction.getEntriesWithSelection(currentPlaybackAction) else DefaultPlaybackAction.getEntriesWithoutPlayAll(
                            currentPlaybackAction
                        )

                    entries.forEach { playbackAction ->
                        DropdownMenuItem(
                            leadingIcon = {
                                if (playbackAction.selected)
                                    Icon(Icons.Default.Check, contentDescription = null)
                            },
                            text = { Text(stringResource(playbackAction.title)) },
                            onClick = {
                                current.defaultPlaybackActionMediaType.saveCurrentPlaybackAction(Settings.getInstance(context), playbackAction)
                                currentPlaybackAction = playbackAction
                                playbackActionsExpanded = false
                            }
                        )
                    }

                }
            }
        },
        onClick = { playbackActionsExpanded = !playbackActionsExpanded }
    )
}

@Composable
private fun VideoGroupingItem(
    current: MediaListEntry,
    onClick: (MediaListEntry, VideoGroupingType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    if (current in arrayOf(MediaListEntry.VIDEO, MediaListEntry.VIDEO_GROUPS, MediaListEntry.VIDEO_FOLDER)) {
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
                            if (current == MediaListEntry.VIDEO_GROUPS)
                                Icon(Icons.Default.Check, contentDescription = null)
                        },
                        text = { Text(stringResource(org.videolan.television.R.string.video_min_group_length_name)) },
                        onClick = {
                            onClick(MediaListEntry.VIDEO_GROUPS, VideoGroupingType.NAME)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            if (current == MediaListEntry.VIDEO_FOLDER)
                                Icon(Icons.Default.Check, contentDescription = null)
                        },
                        text = { Text(stringResource(org.videolan.television.R.string.video_min_group_length_folder)) },
                        onClick = {
                            onClick(MediaListEntry.VIDEO_FOLDER, VideoGroupingType.FOLDER)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            if (current == MediaListEntry.VIDEO)
                                Icon(Icons.Default.Check, contentDescription = null)
                        },
                        text = { Text(stringResource(org.videolan.television.R.string.video_min_group_length_disable)) },
                        onClick = {
                            onClick(MediaListEntry.VIDEO, VideoGroupingType.NONE)
                            expanded = false
                        }
                    )
                }
            }
        }, onClick = { expanded = !expanded })
    }
}

@Composable
fun DisplaySettingsLine(painter: Painter, text: String, subtitle: String? = null, endView: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(50))
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
        Column(modifier = Modifier.weight(1f)) {
            Text(text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            endView?.invoke()
        }
    }
}

fun Int.getSortItemDescriptor(isCurrentSort: Boolean) = when (this) {
    Medialibrary.TrackId -> SortItemDescriptor(this, R.string.sortby_track, R.string.ascending, R.string.descending, R.drawable.ic_sort_track, isCurrentSort)
    Medialibrary.SORT_ALPHA -> SortItemDescriptor(
        this,
        R.string.sortby_name,
        R.string.sort_alpha_asc,
        R.string.sort_alpha_desc,
        R.drawable.ic_sort_alpha,
        isCurrentSort
    )

    Medialibrary.SORT_FILENAME -> SortItemDescriptor(
        this,
        R.string.sortby_filename,
        R.string.sort_alpha_asc,
        R.string.sort_alpha_desc,
        R.drawable.ic_sort_filename,
        isCurrentSort
    )

    Medialibrary.SORT_ARTIST -> SortItemDescriptor(
        this,
        R.string.sortby_artist_name,
        R.string.sort_alpha_asc,
        R.string.sort_alpha_desc,
        R.drawable.ic_sort_artist,
        isCurrentSort
    )

    Medialibrary.SORT_DURATION -> SortItemDescriptor(
        this,
        R.string.sortby_length,
        R.string.sortby_length_asc,
        R.string.sortby_length_desc,
        R.drawable.ic_sort_length,
        isCurrentSort
    )

    Medialibrary.SORT_INSERTIONDATE -> SortItemDescriptor(
        this,
        R.string.sortby_date_insertion,
        R.string.sort_date_asc,
        R.string.sort_date_desc,
        R.drawable.ic_medialibrary_date,
        isCurrentSort
    )

    Medialibrary.SORT_LASTMODIFICATIONDATE -> SortItemDescriptor(
        this,
        R.string.sortby_date_last_modified,
        R.string.sort_date_asc,
        R.string.sort_date_desc,
        R.drawable.ic_medialibrary_scan,
        isCurrentSort
    )

    Medialibrary.SORT_ALBUM -> SortItemDescriptor(
        this,
        R.string.sortby_album_name,
        R.string.sort_alpha_asc,
        R.string.sort_alpha_desc,
        R.drawable.ic_sort_album,
        isCurrentSort
    )

    Medialibrary.SORT_RELEASEDATE -> SortItemDescriptor(
        this,
        R.string.sortby_date_release,
        R.string.sort_date_asc,
        R.string.sort_date_desc,
        R.drawable.ic_sort_date,
        isCurrentSort
    )

    Medialibrary.NbMedia -> SortItemDescriptor(
        this,
        R.string.sortby_number,
        R.string.sortby_number_asc,
        R.string.sortby_number_desc,
        R.drawable.ic_sort_number,
        isCurrentSort
    )
    else -> throw IllegalStateException("Unsupported sort: $this")
}


data class SortItemDescriptor(val sort: Int, val title: Int, val ascending: Int, val descending: Int, val icon: Int, val isCurrentSort: Boolean)