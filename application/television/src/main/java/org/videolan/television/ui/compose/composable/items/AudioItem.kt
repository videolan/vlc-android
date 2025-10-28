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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.FAVORITE_FLAG
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.lists.vlcBorder
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.WhiteTransparent05
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.utils.getDescriptionAnnotated
import org.videolan.television.ui.compose.utils.inlineContentMap
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.getTvIconRes
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.askStoragePermission
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.getDescriptionSpan

@Composable
fun AudioItem(audios: List<MediaLibraryItem>, index: Int, inCard: Boolean = true, spannableDescription: Boolean = false) {
    if (inCard) AudioItemCard(audios[index], spannableDescription = spannableDescription) else AudioItemList(audios[index], spannableDescription = spannableDescription)
}

@Composable
fun AudioItemCard(item: MediaLibraryItem, modifier: Modifier = Modifier, spannableDescription: Boolean = false) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val focused = remember { mutableStateOf(false) }


    Column {
        Card(
            border = vlcBorder(focused.value),
            modifier = modifier
                .onFocusChanged {
                    focused.value = it.isFocused
                }
                .combinedClickable(
                    onClick = {
                        val item = item
                        when (item) {
                            is Artist -> TvUtil.openAudioCategory(activity!!, item)
                            is Album -> TvUtil.openAudioCategory(activity!!, item)
                            is Genre -> TvUtil.openAudioCategory(activity!!, item)
                            is Playlist -> TvUtil.openAudioCategory(activity!!, item)
                            else -> TvUtil.openMedia(activity as FragmentActivity, item)
                        }
                    },
                    onLongClick = {
                        val item = item
                        if (item is MediaWrapper)
                            TvUtil.showMediaDetail(activity!!, item, false)
                        else
                            (activity as? FragmentActivity)?.askStoragePermission(false, null)
                    },
                    indication = null,
                    interactionSource = null
                )
        ) {
            Box {
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
                    val defaultIconId = when (item) {
                        is Artist -> R.drawable.ic_artist_big
                        is Album -> R.drawable.ic_album_big
                        is Genre -> R.drawable.ic_genre_big
                        is Playlist -> R.drawable.ic_playlist_big
                        is DummyItem -> getTvIconRes(item)
                        else -> R.drawable.ic_folder
                    }
                    Image(
                        painter = painterResource(id = defaultIconId),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .width(150.dp)
                            .aspectRatio(1F)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    LaunchedEffect(key1 = "") {

                        coroutineScope.launch {
                            item.let {
                                if (item !is DummyItem)
                                    mapBitmap.value = ThumbnailsProvider.obtainBitmap(it, 150.dp.value.toInt())
                            }
                        }
                    }
                }
            }
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
fun AudioItemList(item: MediaLibraryItem, modifier: Modifier = Modifier, spannableDescription: Boolean = false) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val focused = remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged {
                    focused.value = it.isFocused
                }
//            .border(vlcBorder(focused.value))
//            .padding(vertical = if (focused.value) 8.dp else 0.dp)
            .background(color = if (focused.value) WhiteTransparent10 else WhiteTransparent05, shape = MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is Artist -> TvUtil.openAudioCategory(activity!!, item)
                            is Album -> TvUtil.openAudioCategory(activity!!, item)
                            is Genre -> TvUtil.openAudioCategory(activity!!, item)
                            is Playlist -> TvUtil.openAudioCategory(activity!!, item)
                            else -> TvUtil.openMedia(activity as FragmentActivity, item)
                        }
                    },
                    onLongClick = {
                        if (item is MediaWrapper)
                            TvUtil.showMediaDetail(activity!!, item, false)
                        else
                            (activity as? FragmentActivity)?.askStoragePermission(false, null)
                    },
                    indication = null,
                    interactionSource = null
                )
        ) {
            Card(
                modifier = Modifier
            ) {
                if (mapBitmap.value != null) {

                    Image(
                        bitmap = mapBitmap.value!!.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1F)
                    )
                } else {
                    val defaultIconId = when (item) {
                        is Artist -> R.drawable.ic_artist_big
                        is Album -> R.drawable.ic_album_big
                        is Genre -> R.drawable.ic_genre_big
                        is Playlist -> R.drawable.ic_playlist_big
                        else -> R.drawable.ic_folder
                    }
                    Image(
                        painter = painterResource(id = defaultIconId),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1F)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    LaunchedEffect(key1 = "") {

                        coroutineScope.launch {
                            item.let {
                                mapBitmap.value = ThumbnailsProvider.obtainBitmap(it, 150.dp.value.toInt())
                            }
                        }
                    }
                }

            }

            Column(modifier = Modifier
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
}