/*
 * ************************************************************************
 *  TVAudioPlayer.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.television.ui.compose.composable.screens

import android.graphics.Bitmap
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.ui.compose.composable.components.MiniVisualizer
import org.videolan.television.ui.compose.theme.BackgroundColorDarkTransparent50
import org.videolan.television.ui.compose.theme.Orange500
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent25
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.getTvIconRes
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.viewmodels.PlaylistModel

@Composable
fun TVAudioPlayer() {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .weight(1F)
                .fillMaxWidth()
        ) {
            Box(Modifier.weight(0.65f)) {
                AudioCover()
            }
            Box(
                Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .background(WhiteTransparent25)
            ) {
                AudioPlayQueue()
            }
        }
        Column(Modifier.padding(horizontal = 32.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                AudioPlayerControls()
            }
        }
    }
}

@Composable
fun AudioCover(viewModel: PlaylistModel = viewModel()) {
    val playerState = viewModel.playerState.observeAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AudioUtil.readCoverBitmap(Uri.decode(viewModel.currentMediaWrapper?.artworkURL), 300)?.asImageBitmap()?.let {
            Image(bitmap = it, contentDescription = null)
        }
        Text(playerState.value?.title ?: "")
        Text(playerState.value?.artist ?: "")
    }
}

@Composable
fun AudioPlayQueue(viewModel: PlaylistModel = viewModel()) {
    val queue = viewModel.dataset.observeAsState()
    queue.value?.let { queue ->
        LazyColumn {
            items(count = queue.size) { index ->
                AudioPlayerQueueItem(queue, index)


            }
        }
    }
}

@Composable
fun AudioPlayerQueueItem(queue: MutableList<MediaWrapper>, index: Int, viewModel: PlaylistModel = viewModel()) {
    var isFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val item = queue[index]
    val currentMedia = PlaylistManager.currentPlayedMedia.observeAsState()
    Row(
        Modifier
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .height(64.dp)
            .clickable {
                if (BuildConfig.DEBUG) Log.d("FocusTest", "Clicked on index: $index")
                viewModel.play(index)

            }
            .focusable()
            .background(
                color = if (isFocused) WhiteTransparent10 else Transparent,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )) {
        Box(
            Modifier
                .padding(8.dp)
                .size(48.dp), contentAlignment = Alignment.Center
        ) {
            if (currentMedia.value != item)
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
            else
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .background(BackgroundColorDarkTransparent50, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter
                ) {
                    MiniVisualizer(Orange500)
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
            Text(
                MediaUtils.getMediaSubtitle(item),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )

        }
    }
}

@Composable
fun AudioPlayerControls(viewModel: PlaylistModel = viewModel()) {
    val progress by viewModel.progress.observeAsState()
    val playerState = viewModel.playerState.observeAsState()
    val repeatType = PlaylistManager.repeating.collectAsState()
    val shuffling = PlaylistManager.shuffling.collectAsState()


    AudioProgressBar()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                viewModel.shuffle()
            }
        ) {
            Icon(
                modifier = Modifier.padding(4.dp),
                painter = painterResource(R.drawable.ic_shuffle_audio),
                tint = if (shuffling.value) MaterialTheme.colorScheme.primary else White,
                contentDescription = stringResource(if (shuffling.value) R.string.shuffle_on else R.string.shuffle)
            )
        }
        IconButton(
            onClick = {
                viewModel.previous()
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_previous),
                contentDescription = stringResource(R.string.previous)
            )
        }
        IconButton(
            onClick = {
                viewModel.togglePlayPause()
            }
        ) {
            Icon(
                painter = painterResource(if (playerState.value?.playing == true) R.drawable.ic_pause_player else R.drawable.ic_play_player),
                contentDescription = stringResource(if (viewModel.playing) R.string.pause else R.string.play)
            )
        }
        IconButton(
            onClick = {
                viewModel.next()
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = stringResource(R.string.next)
            )
        }
        IconButton(
            onClick = {
                when (viewModel.repeatType) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> {
                        viewModel.repeatType = PlaybackStateCompat.REPEAT_MODE_ALL
                    }

                    PlaybackStateCompat.REPEAT_MODE_ALL -> {
                        viewModel.repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
                    }

                    PlaybackStateCompat.REPEAT_MODE_ONE -> {
                        viewModel.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                    }
                }
            }
        ) {
            Icon(
                painter = when (repeatType.value) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> painterResource(R.drawable.ic_repeat_audio)
                    PlaybackStateCompat.REPEAT_MODE_ONE -> painterResource(R.drawable.ic_repeat_one_audio)
                    else -> painterResource(R.drawable.ic_repeat_all_audio)
                },
                tint = if (repeatType.value == PlaybackStateCompat.REPEAT_MODE_ALL) MaterialTheme.colorScheme.primary else White,
                contentDescription = when (repeatType.value) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> stringResource(R.string.repeat_none)
                    PlaybackStateCompat.REPEAT_MODE_ONE -> stringResource(R.string.repeat_single)
                    else -> stringResource(R.string.repeat_all)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioProgressBar(viewModel: PlaylistModel = viewModel()) {
    val progress by viewModel.progress.observeAsState()
    var sliderPosition by rememberSaveable { mutableFloatStateOf(progress?.let { it.time.toFloat() / it.length } ?: 0F) }


    Row(Modifier.fillMaxWidth()) {
        Text(progress?.timeText ?: "")
        Spacer(Modifier.weight(1.0f))
        Text(progress?.lengthText ?: "")
    }

    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth(),
        progress = { progress?.let { it.time.toFloat() / it.length } ?: 0F },
        drawStopIndicator = {}
    )
}