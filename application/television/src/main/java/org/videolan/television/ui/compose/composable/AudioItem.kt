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

package org.videolan.television.ui.compose.composable

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.askStoragePermission
import org.videolan.vlc.util.ThumbnailsProvider

@Composable
fun AudioItem(audios: List<MediaLibraryItem>, index: Int, border: Border) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalActivity.current


    StandardCardContainer(
        modifier = Modifier
            .width(150.dp)
            .padding(0.dp, 16.dp, 0.dp, 8.dp),
        imageCard = { interactionSource ->
            Card(
                onClick = {
                    val item = audios[index]
                    when (item) {
                        is Artist -> TvUtil.openAudioCategory(activity!!, item)
                        is Album -> TvUtil.openAudioCategory(activity!!, item)
                        is Genre -> TvUtil.openAudioCategory(activity!!, item)
                        is Playlist -> TvUtil.openAudioCategory(activity!!, item)
                        else -> TvUtil.openMedia(activity as FragmentActivity, item)
                    }
                },
                onLongClick = {
                    val item = audios[index]
                    if (item is MediaWrapper)
                        TvUtil.showMediaDetail(activity!!, item, false)
                    else
                        (activity as? FragmentActivity)?.askStoragePermission(false, null)
                },
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
                            .width(150.dp)
                            .aspectRatio(1F)
                    )
                } else {
                    val defaultIconId = when  (audios[index]) {
                        is Artist -> R.drawable.ic_artist_big
                        is Album -> R.drawable.ic_album_big
                        is Genre-> R.drawable.ic_genre_big
                        is Playlist-> R.drawable.ic_playlist_big
                        else -> R.drawable.ic_folder
                    }
                    Image(
                        painter = painterResource(id = defaultIconId),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .width(150.dp)
                            .aspectRatio(1F)
                    )
                    LaunchedEffect(key1 = "") {

                        coroutineScope.launch {
                            audios[index].let {
                                mapBitmap.value = ThumbnailsProvider.obtainBitmap(it, 150.dp.value.toInt())
                            }
                        }
                    }
                }

            }
        },
        title = {
            Text(
                audios[index].title ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(0.dp, 8.dp)
                    .fillMaxWidth()
            )
        },
        subtitle = {
            Text(
                audios[index].description ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(0.dp, 0.dp)
                    .fillMaxWidth()
            )
        }
    )
}