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

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.lists.vlcBorder
import org.videolan.television.ui.compose.theme.BlackTransparent50
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.generateResolutionClass

@Composable
fun VideoItem(video: MediaLibraryItem) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    Column(modifier = Modifier.width(280.dp)) {
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
                if ((video as MediaWrapper).seen > 0) {
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
                generateResolutionClass(video.width, video.height)?.let {
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

        }
        Text(
            video.title ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp, top = 4.dp)
                .fillMaxWidth()
        )
        Text(
            video.description ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
                .fillMaxWidth()
        )
    }
}