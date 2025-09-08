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

package org.videolan.television.ui.compose.composable

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.vlc.util.ThumbnailsProvider

@Composable
fun VideoItem(videos: List<MediaLibraryItem>, index: Int, border: Border) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        onClick = { },
        modifier = Modifier
            .width(280.dp)
            .padding(8.dp),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = border,
            pressedBorder = border,
        ),
        colors = ClickableSurfaceDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceScale.None,
    ) {
        StandardCardContainer(
            modifier = Modifier
                .padding(0.dp, 0.dp, 0.dp, 8.dp),
            imageCard = { interactionSource ->
                Card(
                    onClick = { },
                    border = CardDefaults.border(
                        border = Border.None,
                        focusedBorder = border,
                    ),
                    interactionSource = interactionSource,
//                            modifier = Modifier.focusRestorer(foc)
                ) {
                    if (mapBitmap.value != null) {

                        Image(
                            bitmap = mapBitmap.value!!.asImageBitmap(),
                            contentDescription = "Map snapshot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(280.dp)
                                .aspectRatio(16f / 9)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_video),
                            contentDescription = "Map snapshot",
                            modifier = Modifier
                                .width(280.dp)
                                .aspectRatio(16f / 9)
                        )
                        LaunchedEffect(key1 = "") {

                            coroutineScope.launch {
                                videos.get(index)?.let {
                                    mapBitmap.value = ThumbnailsProvider.obtainBitmap(it, 280.dp.value.toInt())
                                }
                            }
                        }
                    }

                }
            },
            title = {
                Text(
                    videos.get(index)?.title ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(4.dp, 8.dp)
                        .fillMaxWidth()
                )
            },
            subtitle = {
                Text(
                    videos.get(index)?.description ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(4.dp, 0.dp)
                        .fillMaxWidth()
                )
            }
        )
    }
}