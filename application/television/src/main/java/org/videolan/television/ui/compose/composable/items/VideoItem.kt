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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.FAVORITE_FLAG
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.lists.vlcBorder
import org.videolan.television.ui.compose.theme.BlackTransparent50
import org.videolan.television.ui.compose.theme.WhiteTransparent05
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.utils.conditional
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.generateResolutionClass
import org.videolan.vlc.util.getPresenceDescription

@Composable
fun VideoItem(video: MediaLibraryItem, modifier: Modifier = Modifier) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    Column(modifier = modifier.width(280.dp)) {
        Card(
            border = vlcBorder(focused),
            modifier = Modifier
                .onFocusChanged {
                    focused = it.isFocused
                }
                .combinedClickable(
                    onClick = {
                        TvUtil.openMedia(activity as FragmentActivity, video)
                    },
                    onLongClick = {
                        TvUtil.showMediaDetail(activity!!, video as MediaWrapper, false)
                    },
                    indication = null,
                    interactionSource = null
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                if (mapBitmap.value != null) {
                    Image(
                        bitmap = mapBitmap.value!!.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9)
                            .scale(if (focused) 1.1f else 1f)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_video),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9)
                            .scale(if (focused) 1.1f else 1f)
                    )
                    LaunchedEffect(key1 = "") {

                        coroutineScope.launch {
                            mapBitmap.value = ThumbnailsProvider.obtainBitmap(video, 280.dp.value.toInt())
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
            }
            val lastTime = (video as MediaWrapper).displayTime
            if (lastTime > 0) {
                val max = (video.length / 1000).toInt()
                val progress = (lastTime / 1000).toInt()
                LinearProgressIndicator(
                    trackColor = WhiteTransparent50,
                    gapSize = 0.dp,
                    strokeCap = StrokeCap.Butt,
                    progress = { progress.toFloat() / max },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    video.title ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp, top = 4.dp)
                        .fillMaxWidth()
                        .conditional(focused, { Modifier.basicMarquee(initialDelayMillis = 0) }, { Modifier })
                )
                Text(
                    video.getVideoDescription(activity!!, false) ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp)
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
fun VideoItemList(video: MediaLibraryItem, modifier: Modifier = Modifier) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

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
                    TvUtil.openMedia(activity as FragmentActivity, video)
                },
                onLongClick = {
                    TvUtil.showMediaDetail(activity!!, video as MediaWrapper, false)
                },
                indication = null,
                interactionSource = null
            )
            .background(color = if (focused) WhiteTransparent10 else WhiteTransparent05, shape = MaterialTheme.shapes.medium)
    ) {
        Card {
            Box(
                modifier = Modifier
                    .padding(0.dp)
                    .fillMaxHeight()
                    .aspectRatio(16f / 9)
            ) {
                if (mapBitmap.value != null) {
                    Image(
                        bitmap = mapBitmap.value!!.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(16f / 9)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_video),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .aspectRatio(16f / 9)
                    )
                    LaunchedEffect(key1 = "") {

                        coroutineScope.launch {
                            mapBitmap.value = ThumbnailsProvider.obtainBitmap(video, 280.dp.value.toInt())
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
                val lastTime = (video as MediaWrapper).displayTime
                if (lastTime > 0) {
                    val max = (video.length / 1000).toInt()
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
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )

            val resolution = if (video is MediaWrapper) generateResolutionClass(video.width, video.height) else null
            val description = if (resolution == null) video.description else TextUtils.separatedString(video.description, resolution)

            Text(
                video.getVideoDescription(activity!!, true) ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
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