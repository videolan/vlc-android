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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.stubs.StubArtist
import org.videolan.resources.CATEGORY_SONGS
import org.videolan.television.R
import org.videolan.television.ui.FAVORITE_FLAG
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.ItemOptions
import org.videolan.television.ui.compose.composable.components.MiniVisualizer
import org.videolan.television.ui.compose.composable.lists.vlcBorder
import org.videolan.television.ui.compose.theme.BlackTransparent50
import org.videolan.television.ui.compose.theme.BlackTransparent70
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.ui.compose.utils.fadingMarquee
import org.videolan.television.ui.compose.utils.getDescriptionAnnotated
import org.videolan.television.ui.compose.utils.inlineContentMap
import org.videolan.vlc.gui.helpers.getTvIconRes
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.viewmodels.PlaylistModel

@Composable
fun AudioItem(
    audios: List<MediaLibraryItem>,
    entry: MediaListEntry,
    index: Int,
    modifier: Modifier = Modifier,
    inCard: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    spannableDescription: Boolean = false,
    browserRoot: Boolean = false,
    onClick: () -> Unit
) {
    if (inCard)
        AudioItemCard(
            audios[index],
            index,
            entry,
            modifier = modifier,
            spannableDescription = spannableDescription,
            browserRoot = browserRoot,
            onClick = onClick
        )
    else
        AudioItemList(
            item = audios[index],
            position = index,
            entry = entry,
            modifier = modifier,
            isFirst = isFirst,
            isLast = isLast,
            spannableDescription = spannableDescription,
            onClick = onClick
        )
}

@Composable
fun AudioItemCard(item: MediaLibraryItem, position: Int, entry: MediaListEntry, modifier: Modifier = Modifier, spannableDescription: Boolean = false, browserRoot: Boolean = false, onClick: () -> Unit) {
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    if (item != mapBitmap.value?.first) mapBitmap.value = null
    var expanded by remember { mutableStateOf(false) }


    val isArtist = item is Artist
    val shape = if (isArtist) CircleShape else MaterialTheme.shapes.medium

    Column(modifier = modifier
        .width(148.dp)
        .zIndex(if (focused) 1f else 0f)) {
        Card(
            shape = shape,
            border = vlcBorder(focused),
            modifier = Modifier
                .onFocusChanged {
                    focused = it.isFocused
                }
                .shadow(if (focused) 12.dp else 0.dp, shape)
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
                                focused = true
                            }
                            if (event.type == PointerEventType.Exit) {
                                focused = false
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
                    .fillMaxWidth()
                    .aspectRatio(1F)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (mapBitmap.value?.second != null) {
                    Image(
                        bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = getTvIconRes(item)),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxSize()
                    )
                    LaunchedEffect(key1 = item) {
                        coroutineScope.launch {
                            if (item !is DummyItem)
                                mapBitmap.value = Pair(item, ThumbnailsProvider.obtainBitmap(item = item, 280.dp.value.toInt()))
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
                if (item is MediaWrapper && item.type == MediaWrapper.TYPE_VIDEO && item.seen > 0) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = stringResource(R.string.media_seen),
                            modifier = Modifier
                                .background(BlackTransparent50, RoundedCornerShape(4.dp))
                                .padding(4.dp),
                        )
                    }
                }
                val isFavorite = item.isFavorite || (item as? MediaWrapper)?.hasFlag(FAVORITE_FLAG) == true
                if (isFavorite) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier
                            .padding(8.dp)
                            .background(BlackTransparent50, CircleShape)
                            .padding(4.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (expanded)
                ItemOptions(item, position, entry, onDismiss =  {
                    expanded = false
                })
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                item.title ?: "",
                maxLines = 1,
                overflow = if (focused) TextOverflow.Visible else TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .fadingMarquee(edgeWidth = 4.dp, marqueeOnlyOnFocus = true, isFocused = focused)
            )
            val description = if (spannableDescription) item.description.getDescriptionAnnotated() else AnnotatedString(item.description ?: "")
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    inlineContent = if (spannableDescription) inlineContentMap else emptyMap()
                )
            }
        }
    }
}

@Composable
fun AudioItemList(
    item: MediaLibraryItem,
    position: Int,
    entry: MediaListEntry,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    spannableDescription: Boolean = false,
    onClick: () -> Unit
) {
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var itemHasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (itemHasFocus) 1.05f else 1f, label = "scale")
    val baseCornerRadius = 12.dp
    val topCornerRadius by animateDpAsState(if (itemHasFocus || isFirst) baseCornerRadius else 0.dp, label = "topCornerRadius")
    val bottomCornerRadius by animateDpAsState(if (itemHasFocus || isLast) baseCornerRadius else 0.dp, label = "bottomCornerRadius")
    val shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius, bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius)

    if (item != mapBitmap.value?.first) mapBitmap.value = null
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .zIndex(if (itemHasFocus) 1f else 0f)
            .height(72.dp)
            .onFocusChanged {
                itemHasFocus = it.hasFocus
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(if (itemHasFocus) 12.dp else 0.dp, shape)
                .border(if (itemHasFocus) 1.dp else 0.dp, WhiteTransparent10, shape)
//                .background(if (itemHasFocus) WhiteTransparent20 else WhiteTransparent05, shape)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .clip(shape)
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                Card(
                    shape = if (item is Artist) CircleShape else MaterialTheme.shapes.medium,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (mapBitmap.value?.second != null) {

                            Image(
                                bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                                contentDescription = "Map snapshot",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = getTvIconRes(item)),
                                contentDescription = "Map snapshot",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                            )
                            LaunchedEffect(key1 = item) {
                                coroutineScope.launch {
                                    if (item !is DummyItem)
                                        mapBitmap.value = Pair(item, ThumbnailsProvider.obtainBitmap(item = item, 280.dp.value.toInt()))
                                }
                            }
                        }
                        if (item is MediaWrapper && item.type == MediaWrapper.TYPE_VIDEO && item.seen > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = stringResource(R.string.media_seen),
                                    modifier = Modifier
                                        .background(BlackTransparent50, RoundedCornerShape(4.dp))
                                        .padding(4.dp),
                                )
                            }
                        }

                        if (item is MediaWrapper) {
                            val currentMedia by PlaylistManager.currentPlayedMedia.observeAsState()
                            val isCurrent = currentMedia?.equals(item) == true
                            if (isCurrent) {
                                val playlistModel: PlaylistModel = viewModel()
                                InvalidationComposable(currentMedia?.tag) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(BlackTransparent50, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        MiniVisualizer(MaterialTheme.colorScheme.onSurface, playlistModel)
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        item.title ?: "",
                        maxLines = 1,
                        overflow = if (itemHasFocus) TextOverflow.Visible else TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (itemHasFocus) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fadingMarquee(edgeWidth = 8.dp, marqueeOnlyOnFocus = true, isFocused = itemHasFocus)
                    )
                    val description = if (spannableDescription) item.description.getDescriptionAnnotated() else AnnotatedString(item.description ?: "")
                    if (description.isNotEmpty()) {
                        Text(
                            text = description,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis,
                            color = if (itemHasFocus) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            inlineContent = if (spannableDescription) inlineContentMap else emptyMap()
                        )
                    }
                }
                if (item.isFavorite || (item as? MediaWrapper)?.hasFlag(FAVORITE_FLAG) == true) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                        tint = if (itemHasFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (expanded)
        ItemOptions(item, position, entry, onDismiss =  {
            expanded = false
        })
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemCardPreview() {
    VlcPreview {
        AudioItemCard(
            item = DummyItem(1, "Song Title", "Artist Name - Album Name"),
            position = 0,
            entry = MediaListEntry.ALBUMS,
            onClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemCardLongTitlePreview() {
    VlcPreview {
        AudioItemCard(
            item = DummyItem(1, "Very long song title that should probably marquee when focused", "Very long artist name that should also probably marquee or ellipsis"),
            position = 0,
            entry = MediaListEntry.ALBUMS,
            onClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemArtistCardPreview() {
    VlcPreview {
        AudioItemCard(
            item = StubArtist(1, "Artist Name", "Artist Bio", null, null, 10, 50, 50, false),
            position = 0,
            entry = MediaListEntry.ARTISTS,
            onClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemPreview() {
    VlcPreview {
        AudioItem(
            audios = listOf(DummyItem(1, "Song Title", "Artist Name - Album Name")),
            entry = MediaListEntry.ALBUMS,
            index = 0,
            onClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemListPreview() {
    VlcPreview {
        Column(modifier = Modifier.padding(16.dp)) {
            AudioItemList(
                item = DummyItem(CATEGORY_SONGS, "Song Title 1", "Artist Name - Album Name"),
                position = 0,
                entry = MediaListEntry.TRACKS,
                isFirst = true,
                onClick = {}
            )
            AudioItemList(
                item = DummyItem(CATEGORY_SONGS, "Song Title 2", "Artist Name - Album Name"),
                position = 1,
                entry = MediaListEntry.TRACKS,
                onClick = {}
            )
            AudioItemList(
                item = StubArtist(1, "Artist Name", "Artist Bio", null, null, 10, 50, 50, false),
                position = 2,
                entry = MediaListEntry.ARTISTS,
                isLast = true,
                onClick = {}
            )
        }
    }
}
