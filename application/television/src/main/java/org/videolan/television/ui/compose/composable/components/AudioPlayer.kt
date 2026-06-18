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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.liveplotgraph.BuildConfig
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.television.R
import org.videolan.television.ui.audioplayer.AudioPlayerActivity
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.tools.Settings
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.viewmodels.PlaybackProgress
import org.videolan.vlc.viewmodels.PlayerState
import org.videolan.vlc.viewmodels.PlaylistModel

private const val TAG = "VLC/AudioPlayer"

@Composable
fun AudioPlayer(playlistModel: PlaylistModel = viewModel(), requestFocus: Boolean = true) {
    val activity = LocalActivity.current
    val visible = PlaylistManager.showAudioPlayer.observeAsState()
    val progress = playlistModel.progress.observeAsState()
    val playerState = playlistModel.playerState.observeAsState()
    val currentMedia = PlaylistManager.currentPlayedMedia.observeAsState()

    AudioPlayer(
        visible = visible.value == true,
        progress = progress.value,
        playerState = playerState.value,
        currentMedia = currentMedia.value,
        serviceCoverArt = playlistModel.service?.coverArt,
        serviceTitle = playlistModel.service?.title,
        serviceArtist = playlistModel.service?.artist,
        onStop = { MediaUtils.stop(activity!!) },
        onOpenFull = { activity?.startActivity(Intent(activity, AudioPlayerActivity::class.java)) },
        onJump = { forward -> playlistModel.jump(forward = forward, long = false, activity!!) },
        onPrevious = { playlistModel.previous() },
        onNext = { playlistModel.next() },
        onTogglePlayPause = { playlistModel.togglePlayPause() },
        requestFocus = requestFocus
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayer(
    visible: Boolean,
    progress: PlaybackProgress?,
    playerState: PlayerState?,
    currentMedia: MediaWrapper?,
    serviceCoverArt: String?,
    serviceTitle: String?,
    serviceArtist: String?,
    onStop: () -> Unit,
    onOpenFull: () -> Unit,
    onJump: (forward: Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    requestFocus: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    sliderPosition = ((progress?.time ?: 0).toFloat() / (progress?.length ?: 1)).coerceIn(0F, 1F)
    val playPauseFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible,
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
                .focusProperties {
                    onEnter = {
                        playPauseFocusRequester.requestFocus()
                    }
                }
                .focusGroup(),
            horizontalAlignment = Alignment.End
        ) {

            Row(modifier = Modifier.fillMaxWidth()) {
                LabeledIconButton(
                    label = stringResource(R.string.stop),
                    vectorImage = Icons.Outlined.Close,
                ) {
                    onStop()
                }
                Spacer(modifier = Modifier.weight(1F))
                LabeledIconButton(
                    label = stringResource(R.string.open_audio_player),
                    vectorImage = Icons.Outlined.OpenInFull,
                ) {
                    onOpenFull()
                }
            }

            Spacer(modifier = Modifier.weight(1F))

            val mapBitmap: MutableState<Pair<String?, Bitmap?>> = remember { mutableStateOf(Pair(null, null)) }
            if (mapBitmap.value.first != currentMedia?.artworkMrl) {
                mapBitmap.value = Pair(currentMedia?.artworkMrl, null)
            }
            if (mapBitmap.value.second != null) {

                Image(
                    bitmap = mapBitmap.value.second!!.asImageBitmap(),
                    contentDescription = "Map snapshot",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1F)
                )
            } else {
                val defaultIconId = R.drawable.ic_song_big
                Image(
                    painter = painterResource(id = defaultIconId),
                    contentDescription = "Map snapshot",
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1F)
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "LaunchedEffect with key ${currentMedia?.artworkMrl}")
                LaunchedEffect(key1 = currentMedia?.artworkMrl) {

                    coroutineScope.launch {
                        serviceCoverArt?.let {
                            mapBitmap.value = Pair(it, AudioUtil.readCoverBitmap(Uri.decode(it), 512))
                        }
                    }
                }
            }

            Text(
                serviceTitle ?: "",
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                serviceArtist ?: "",
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1F))
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1F))
                LabeledIconButton(stringResource(R.string.talkback_action_rewind, Settings.audioJumpDelay), painterResource = painterResource(R.drawable.ic_player_rewind_10), customImage = {  tint ->
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_player_rewind_10),
                            contentDescription = stringResource(R.string.talkback_action_rewind, Settings.audioJumpDelay),
                            tint = tint
                        )
                        Text(Settings.audioJumpDelay.toString(), fontSize = 7.sp, color = tint)
                    }
                }) {
                    onJump(false)
                }
                LabeledIconButton(stringResource(R.string.talkback_action_forward, Settings.audioJumpDelay), painterResource = painterResource(R.drawable.ic_player_forward_10), customImage = {  tint ->
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_player_forward_10),
                            contentDescription = stringResource(R.string.talkback_action_forward, Settings.audioJumpDelay),
                            tint = tint
                        )
                        Text(Settings.audioJumpDelay.toString(), fontSize = 7.sp, color = tint)
                    }
                }) {
                    onJump(true)
                }
                Spacer(modifier = Modifier.weight(1F))
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            ) {

                Text(
                    Tools.millisToString(progress?.time ?: 0),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1F))
                Text(
                    Tools.millisToString(progress?.length ?: 0),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
                progress = { sliderPosition },
                drawStopIndicator = {}
            )
            Row(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1F))
                LabeledIconButton(
                    label = stringResource(R.string.previous),
                    painterResource = painterResource(R.drawable.ic_previous),
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                ) {
                    onPrevious()
                }

                val playPauseString = if (playerState?.playing == true) stringResource(R.string.pause) else stringResource(R.string.play)
                LabeledIconButton(
                    playPauseString,
                    painterResource = painterResource(R.drawable.ic_player_forward_10),
                    modifier = Modifier
                        .focusRequester(focusRequester = playPauseFocusRequester)
                        .padding(vertical = 4.dp),
                    customImage = { tint ->
                        Box(contentAlignment = Alignment.Center) {
                            if (playerState?.playing == true)
                                Icon(
                                    painterResource(R.drawable.ic_pause_player),
                                    tint = tint,
                                    contentDescription = stringResource(R.string.pause),
                                )
                            else
                                Icon(
                                    painterResource(R.drawable.ic_play_player),
                                    tint = tint,
                                    contentDescription = stringResource(R.string.play),
                                )
                        }
                    }) {
                    onTogglePlayPause()
                }
                LabeledIconButton(
                    label = stringResource(R.string.next),
                    painterResource = painterResource(R.drawable.ic_next),
                    modifier = Modifier
                        .padding(vertical = 4.dp),
                ) {
                    onNext()
                }
                Spacer(modifier = Modifier.weight(1F))
            }

        }
    }
    var initialLaunch by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (visible && (requestFocus || !initialLaunch)) playPauseFocusRequester.requestFocus()
        initialLaunch = false
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioPlayerPreview() {
    VlcPreview {
        AudioPlayer(
            visible = true,
            progress = PlaybackProgress(time = 10000, length = 30000),
            playerState = PlayerState(playing = true, title = "Title", artist = "Artist"),
            currentMedia = null,
            serviceCoverArt = null,
            serviceTitle = "Title",
            serviceArtist = "Artist",
            onStop = {},
            onOpenFull = {},
            onJump = {},
            onPrevious = {},
            onNext = {},
            onTogglePlayPause = {}
        )
    }
}
