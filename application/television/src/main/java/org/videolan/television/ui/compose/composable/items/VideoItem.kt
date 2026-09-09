/*
 * ************************************************************************
 *  VideoItem.kt
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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.compose.composable.components.ItemOptions
import org.videolan.television.ui.compose.theme.BlackTransparent50
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.WhiteTransparent05
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.theme.WhiteTransparent70
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.ui.compose.utils.fadingMarquee
import org.videolan.television.util.FAVORITE_FLAG
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.generateResolutionClass
import org.videolan.vlc.util.getPresenceDescription

@Composable
fun VideoItem(video: MediaLibraryItem, entry: MediaListEntry, position: Int, modifier: Modifier = Modifier, initialFocused: Boolean = false, onClick: () -> Unit) {
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(initialFocused) }
    val context = LocalContext.current
    if (video != mapBitmap.value?.first) mapBitmap.value = null
    var expanded by remember { mutableStateOf(false) }

    val animDuration = 180
    val animEasing = FastOutSlowInEasing

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "videoScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (focused) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "shadowElevation"
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "shadowAlpha"
    )
    val containerColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.surfaceVariant else Transparent,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "containerColor"
    )
    val outerBorderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.onSurface else Transparent,
        animationSpec = tween(durationMillis = animDuration, easing = animEasing),
        label = "outerBorderColor"
    )

    val outerShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .zIndex(if (focused || scale > 1.001f) 100f else 0f)
            .onFocusChanged {
                focused = it.isFocused
            }
    ) {
        if (shadowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = shadowAlpha
                    }
                    .shadow(shadowElevation, outerShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant, outerShape)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(containerColor, outerShape)
                .border(
                    border = if (outerBorderColor.alpha > 0.01f) BorderStroke(2.5.dp, outerBorderColor) else BorderStroke(0.dp, Transparent),
                    shape = outerShape
                )
                .clip(outerShape)
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
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val thumbShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9)
                    .clip(thumbShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (mapBitmap.value?.second != null) {
                    Image(
                        bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_video),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(WhiteTransparent70),
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxSize()
                    )
                    LaunchedEffect(key1 = video) {
                        coroutineScope.launch {
                            if (video !is DummyItem)
                                mapBitmap.value = Pair(video, ThumbnailsProvider.obtainBitmap(video, 480))
                        }
                    }
                }
                if (video is MediaWrapper && video.seen > 0) {
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
                if (video is MediaWrapper) generateResolutionClass(video.width, video.height)?.let {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .background(BlackTransparent50, RoundedCornerShape(4.dp))
                                .padding(4.dp),
                        )
                    }
                }
                val lastTime = (video as? MediaWrapper)?.displayTime ?: -1
                if (lastTime > 0) {
                    val max = ((video as MediaWrapper).length / 1000).toInt()
                    val progress = (lastTime / 1000).toInt()
                    LinearProgressIndicator(
                        trackColor = WhiteTransparent50,
                        gapSize = 0.dp,
                        strokeCap = StrokeCap.Butt,
                        progress = { progress.toFloat() / max },
                        drawStopIndicator = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                    )
                }
            }

            if (expanded)
                ItemOptions(video, position, entry, onDismiss = {
                    expanded = false
                })

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        video.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fadingMarquee(edgeWidth = 4.dp, marqueeOnlyOnFocus = true, isFocused = focused)
                    )
                    val descriptionText = video.getVideoDescription(context, false) ?: ""
                    if (descriptionText.isNotEmpty()) {
                        Text(
                            descriptionText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }
                }
                if (video.isFavorite || (video as? MediaWrapper)?.hasFlag(FAVORITE_FLAG) == true) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        contentDescription = stringResource(R.string.favorite),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                        tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun MediaLibraryItem.getVideoDescription(context: Context, inList: Boolean) = when (this) {
    is Folder -> {
        val count = mediaCount(Folder.TYPE_FOLDER_VIDEO)
        context.resources.getQuantityString(org.videolan.vlc.R.plurals.videos_quantity, count, count)
    }

    is VideoGroup -> {
        val count = mediaCount()
        if (count < 2)
            this.description
        else if (presentCount == mediaCount())
            context.resources.getQuantityString(org.videolan.vlc.R.plurals.videos_quantity, count, count)
        else if (presentCount == 0)
            context.resources.getString(org.videolan.vlc.R.string.no_video)
        else getPresenceDescription()
    }

    is MediaWrapper -> {
        if (length > 0) {
            val resolution = generateResolutionClass(width, height)
            if (inList && resolution !== null) {
                "${Tools.millisToString(length)}  •  $resolution"
            } else Tools.millisToString(length)
        } else null
    }

    else -> null
}


@Composable
fun VideoItemList(video: MediaLibraryItem, position: Int, entry: MediaListEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (video != mapBitmap.value?.first) mapBitmap.value = null
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged {
                focused = it.isFocused
            }
            .combinedClickable(
                onClick = {
                    onClick()
                },
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
            .background(color = if (focused) WhiteTransparent10 else WhiteTransparent05, shape = MaterialTheme.shapes.medium)
    ) {
        Card {
            Box(
                modifier = Modifier
                    .padding(0.dp)
                    .fillMaxHeight()
                    .aspectRatio(16f / 9)
            ) {
                if (mapBitmap.value?.second != null) {
                    Image(
                        bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(16f / 9)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_video),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .aspectRatio(16f / 9)
                    )
                    LaunchedEffect(key1 = video) {
                        coroutineScope.launch {
                            if (video !is DummyItem)
                                mapBitmap.value = Pair(video, ThumbnailsProvider.obtainBitmap(video, 480))
                        }
                    }
                }
                if (video is MediaWrapper && video.seen > 0) {
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
                val lastTime = (video as? MediaWrapper)?.displayTime ?: -1
                if (lastTime > 0) {
                    val max = ((video as MediaWrapper).length / 1000).toInt()
                    val progress = (lastTime / 1000).toInt()
                    LinearProgressIndicator(
                        trackColor = WhiteTransparent50,
                        gapSize = 0.dp,
                        strokeCap = StrokeCap.Butt,
                        progress = { progress.toFloat() / max },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    )
                }
            }
            if (expanded)
                ItemOptions(video, position, entry, onDismiss = {
                    expanded = false
                })
        }
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                video.title ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )

            Text(
                video.getVideoDescription(context, true) ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )
        }
        if (video.isFavorite || (video as? MediaWrapper)?.hasFlag(FAVORITE_FLAG) == true) {
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

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoItemPreview() {
    VlcPreview {
        Box(modifier = Modifier
            .padding(32.dp)
            .size(200.dp)) {
            VideoItem(
                video = DummyItem(1, "Video Title", "1:20:30"),
                entry = MediaListEntry.VIDEO,
                position = 0,
                onClick = {},
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoItemGridPreview() {
    val sampleVideos = listOf(
        DummyItem(1, "A Place of Peace and Beauty Movie", "1:42:00"),
        DummyItem(2, "A really long video title to test the UI of VLC and see if it causes issues", "2:15:30"),
        DummyItem(3, "Archangel Live Concert", "0:58:12"),
    )
    VlcPreview {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            sampleVideos.forEachIndexed { index, item ->
                Box(modifier = Modifier.weight(1f)) {
                    VideoItem(
                        video = item,
                        entry = MediaListEntry.VIDEO,
                        position = index,
                        initialFocused = (index == 1),
                        onClick = {}
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoItemListPreview() {
    VlcPreview {
        VideoItemList(
            video = DummyItem(1, "Video Title", "1:20:30"),
            entry = MediaListEntry.VIDEO,
            position = 0,
            onClick = {}
        )
    }
}
