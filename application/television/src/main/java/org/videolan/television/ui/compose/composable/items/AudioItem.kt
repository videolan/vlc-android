/*
 * ************************************************************************
 *  AudioItem.kt
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

package org.videolan.television.ui.compose.composable.items

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.liveplotgraph.BuildConfig
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.FAVORITE_FLAG
import org.videolan.television.ui.compose.composable.components.ItemOptions
import org.videolan.television.ui.compose.composable.lists.vlcBorder
import org.videolan.television.ui.compose.theme.BlackTransparent70
import org.videolan.television.ui.compose.theme.WhiteTransparent05
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.utils.conditional
import org.videolan.television.ui.compose.utils.getDescriptionAnnotated
import org.videolan.television.ui.compose.utils.inlineContentMap
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.gui.helpers.getTvIconRes
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.ThumbnailsProvider

@Composable
fun AudioItem(audios: List<MediaLibraryItem>, entry: MediaListEntry, index: Int, inCard: Boolean = true, spannableDescription: Boolean = false, browserRoot: Boolean = false, onClick: () -> Unit) {
    if (inCard)
        AudioItemCard(audios[index], index, entry, spannableDescription = spannableDescription, browserRoot = browserRoot, onClick = onClick)
    else
        AudioItemList(audios[index], index, entry, spannableDescription = spannableDescription, onClick = onClick)
}

@Composable
fun AudioItemCard(item: MediaLibraryItem, position: Int, entry: MediaListEntry, modifier: Modifier = Modifier, spannableDescription: Boolean = false, browserRoot: Boolean = false, onClick: () -> Unit) {
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val focused = remember { mutableStateOf(false) }
    if (item != mapBitmap.value?.first) mapBitmap.value = null
    var expanded by remember { mutableStateOf(false) }
    if (BuildConfig.DEBUG) Log.d("CtxClickListener", "Expanded changed to $expanded")


    Column {
        Card(
            border = vlcBorder(focused.value),
            modifier = modifier
                .onFocusChanged {
                    focused.value = it.isFocused
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        expanded = true
                    },
                    indication = null,
                    interactionSource = null
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Enter) {
                                focused.value = true
                            }
                            if (event.type == PointerEventType.Exit) {
                                focused.value = false
                            }
                            if (event.type == PointerEventType.Press &&
                                event.buttons.isSecondaryPressed
                            ) {
                                event.changes.forEach { e -> e.consume() }
                                expanded = true
                            }
                        }
                    }
                }
        ) {
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .width(150.dp)
                    .aspectRatio(1F)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (mapBitmap.value?.second != null) {
                    Image(
                        bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(150.dp)
                            .aspectRatio(1F)
                    )
                } else {
                    Image(
                        painter = painterResource(id = getTvIconRes(item)),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxSize()
                    )
                    LaunchedEffect(key1 = "") {
                        coroutineScope.launch {
                            item.let {
                                if (item !is DummyItem)
                                    mapBitmap.value = Pair(item, ThumbnailsProvider.obtainBitmap(item = item, 280.dp.value.toInt()))
                            }
                        }
                    }
                }
                if (item is MediaWrapper && browserRoot)
                    (if (item.type != MediaWrapper.TYPE_DIR && item.uri.scheme?.contains("file") == false) null else item.uri.scheme)?.let {
                        Text(
                            it, modifier = Modifier
                                .padding(8.dp)
                                .background(BlackTransparent70)
                                .padding(4.dp)
                        )
                    }
            }
            if (expanded)
                ItemOptions(item, position, entry, onDismiss =  {
                    expanded = false
                })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    item.title ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp)
                        .fillMaxWidth()
                        .conditional(focused.value, { Modifier.basicMarquee(initialDelayMillis = 0) }, { Modifier })
                )
                if (spannableDescription)
                    Text(
                        text = item.description.getDescriptionAnnotated(),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 4.dp, end = 4.dp)
                            .fillMaxWidth(),
                        inlineContent = inlineContentMap
                    )
                else
                    Text(
                        text = item.description ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 4.dp, end = 4.dp)
                            .fillMaxWidth(),
                    )
            }
            if (item.isFavorite || (item as? MediaWrapper)?.hasFlag(FAVORITE_FLAG) == true) {
                Icon(
                    painterResource(R.drawable.ic_favorite),
                    contentDescription = stringResource(R.string.favorite),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AudioItemList(item: MediaLibraryItem, position: Int, entry: MediaListEntry, modifier: Modifier = Modifier, spannableDescription: Boolean = false, browserRoot: Boolean = false, viewModel: MainActivityViewModel = viewModel(), onClick: () -> Unit) {
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val focused = remember { mutableStateOf(false) }
    if (item != mapBitmap.value?.first) mapBitmap.value = null
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged {
                focused.value = it.isFocused
            }
            .background(color = if (focused.value) WhiteTransparent10 else WhiteTransparent05, shape = MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    expanded = true
                },
                indication = null,
                interactionSource = null
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter) {
                            focused.value = true
                        }
                        if (event.type == PointerEventType.Exit) {
                            focused.value = false
                        }
                        if (event.type == PointerEventType.Press &&
                            event.buttons.isSecondaryPressed
                        ) {
                            event.changes.forEach { e -> e.consume() }
                            expanded = true
                        }
                    }
                }
            }
    ) {
        Card(
            modifier = Modifier
        ) {
            if (mapBitmap.value?.second != null) {

                Image(
                    bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                    contentDescription = "Map snapshot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1F)
                )
            } else {
                Image(
                    painter = painterResource(id = getTvIconRes(item)),
                    contentDescription = "Map snapshot",
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1F)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                )
                LaunchedEffect(key1 = "") {
                    coroutineScope.launch {
                        item.let {
                            if (item !is DummyItem)
                                mapBitmap.value = Pair(item, ThumbnailsProvider.obtainBitmap(item = item, 280.dp.value.toInt()))
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                item.title ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )
            if (spannableDescription)
                Text(
                    text = item.description.getDescriptionAnnotated(),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(),
                    inlineContent = inlineContentMap
                )
            else
                Text(
                    item.description ?: "",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                )

        }
        if (item.isFavorite || (item as? MediaWrapper)?.hasFlag(FAVORITE_FLAG) == true) {
            Icon(
                painterResource(R.drawable.ic_favorite),
                contentDescription = stringResource(R.string.favorite),
                modifier = Modifier
                    .padding(8.dp)
                    .size(16.dp)
            )
        }
    }
    if (expanded)
        ItemOptions(item, position, entry, onDismiss =  {
            expanded = false
        })
}