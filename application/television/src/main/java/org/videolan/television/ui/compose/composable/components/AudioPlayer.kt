/*
 * ************************************************************************
 *  AudioPlayer.kt
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

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.liveplotgraph.BuildConfig
import org.videolan.medialibrary.Tools
import org.videolan.television.R
import org.videolan.television.ui.audioplayer.AudioPlayerActivity
import org.videolan.tools.Settings
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.viewmodels.PlaylistModel

private const val TAG = "VLC/AudioPlayer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayer(playlistModel: PlaylistModel = viewModel()) {
    val activity = LocalActivity.current
    val visible = PlaylistManager.showAudioPlayer.observeAsState()
    val coroutineScope = rememberCoroutineScope()
    val progress = playlistModel.progress.observeAsState()
    val playerState = playlistModel.playerState.observeAsState()
    val currentMedia = PlaylistManager.currentPlayedMedia.observeAsState()
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    sliderPosition = ((progress.value?.time ?: 0).toFloat() / (progress.value?.length ?: 1)).coerceIn(0F, 1F)
    val focusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible.value == true,
        enter = expandHorizontally(expandFrom = Alignment.Start),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
    ) {

        Column(
            modifier = Modifier
                .padding(top = 32.dp, bottom = 32.dp)
                .fillMaxHeight()
                .width(180.dp)
                .dropShadow(
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    shadow = Shadow(
                        radius = 8.dp,
                        spread = 3.dp,
                        color = Color(0x40000000),
                        offset = DpOffset(x = 0.dp, 0.dp)
                    )
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)

                )
                .padding(16.dp)
                .focusProperties {
                    onEnter = {
                        focusRequester.requestFocus()
                    }
                }
                .focusGroup(),
            horizontalAlignment = Alignment.End
        ) {

            LabeledIconButton(
                label = stringResource(R.string.open_audio_player),
                vectorImage = Icons.Outlined.OpenInFull,
                modifier =  Modifier
                    .focusRequester(focusRequester = focusRequester),
            ) {
                activity?.startActivity(Intent(activity, AudioPlayerActivity::class.java))
            }

            Spacer(modifier = Modifier.weight(1F))

            val mapBitmap: MutableState<Pair<String?, Bitmap?>> = remember { mutableStateOf(Pair(null, null)) }
            if (mapBitmap.value.first != currentMedia.value?.artworkMrl) {
                mapBitmap.value = Pair(currentMedia.value?.artworkMrl, null)
            }
            if (mapBitmap.value.second != null) {

                Image(
                    bitmap = mapBitmap.value.second!!.asImageBitmap(),
                    contentDescription = "Map snapshot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1F)
                )
            } else {
                val defaultIconId = R.drawable.ic_song_big
                Image(
                    painter = painterResource(id = defaultIconId),
                    contentDescription = "Map snapshot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1F)
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "LaunchedEffect with key ${currentMedia.value?.artworkMrl}")
                LaunchedEffect(key1 = currentMedia.value?.artworkMrl) {

                    coroutineScope.launch {
                        playlistModel.service?.coverArt?.let {
                            mapBitmap.value = Pair(playlistModel.service?.coverArt, AudioUtil.readCoverBitmap(Uri.decode(it), 512))
                        }
                    }
                }
            }

            Text(
                playlistModel.service?.title ?: "",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                playlistModel.service?.artist ?: "",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1F))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1F))
                IconButton(
                    onClick = {
                        playlistModel.jump(forward = false, long = false, activity!!)
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_player_rewind_10),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(R.string.previous),
                    )
                    Text(Settings.audioJumpDelay.toString(), fontSize = 7.sp)
                }
                IconButton(
                    onClick = {
                        playlistModel.jump(forward = true, long = false, activity!!)
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.ic_player_forward_10),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(R.string.pause),
                    )
                    Text(Settings.audioJumpDelay.toString(), fontSize = 7.sp)
                }
                Spacer(modifier = Modifier.weight(1F))
            }
            Row(modifier = Modifier.fillMaxWidth()) {

                Text(
                    Tools.millisToString(progress.value?.time ?: 0),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1F))
                Text(
                    Tools.millisToString(progress.value?.length ?: 0),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { sliderPosition },
                drawStopIndicator = {}
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1F))
                IconButton(
                    onClick = {
                        playlistModel.previous()
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(R.string.previous),
                    )
                }
                IconButton(
                    onClick = {
                        playlistModel.togglePlayPause()
                    },
                ) {
                    if (playerState.value?.playing == true)
                        Icon(
                            painterResource(R.drawable.ic_pause_player),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = stringResource(R.string.pause),
                        )
                    else
                        Icon(
                            painterResource(R.drawable.ic_play_player),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = stringResource(R.string.play),
                        )
                }
                IconButton(
                    onClick = {
                        playlistModel.next()
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.ic_next),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = stringResource(R.string.next),
                    )
                }
                Spacer(modifier = Modifier.weight(1F))
            }

        }
    }
}
