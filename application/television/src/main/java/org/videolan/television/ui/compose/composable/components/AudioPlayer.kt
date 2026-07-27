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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.television.R
import org.videolan.television.ui.AudioPlayerActivity
import org.videolan.television.ui.compose.theme.VlcTVTheme
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
fun AudioPlayer(modifier: Modifier = Modifier, playlistModel: PlaylistModel = viewModel(), requestFocus: Boolean = true) {
    val activity = androidx.activity.compose.LocalActivity.current
    val visible = PlaylistManager.showAudioPlayer.observeAsState()
    val progress = playlistModel.progress.observeAsState()
    val playerState = playlistModel.playerState.observeAsState()
    val currentMedia = PlaylistManager.currentPlayedMedia.observeAsState()

    AudioPlayer(
        modifier = modifier,
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
    modifier: Modifier = Modifier,
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
    requestFocus: Boolean = true,
    forceExpanded: Boolean = false
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    sliderPosition = ((progress?.time ?: 0).toFloat() / (progress?.length ?: 1)).coerceIn(0F, 1F)
    val playPauseFocusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(forceExpanded) }

    AnimatedVisibility(
        visible,
        modifier = modifier.fillMaxHeight(),
        enter = expandHorizontally(expandFrom = Alignment.Start),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start)
    ) {
        AnimatedContent(
            targetState = isFocused,
            modifier = Modifier
                .fillMaxHeight()
                .onFocusChanged { isFocused = it.hasFocus },
            contentAlignment = Alignment.CenterStart,
            transitionSpec = {
                if (targetState) {
                    fadeIn() + slideInHorizontally { -it } togetherWith fadeOut() + slideOutHorizontally { -it }
                } else {
                    fadeIn() + slideInHorizontally { -it } togetherWith fadeOut() + slideOutHorizontally { -it }
                }.using(SizeTransform(clip = false))
            },
            label = "player_expansion"
        ) { focused ->
            if (focused) {
                AudioPlayerExpanded(
                    progress = progress,
                    sliderPosition = sliderPosition,
                    playerState = playerState,
                    currentMedia = currentMedia,
                    serviceCoverArt = serviceCoverArt,
                    serviceTitle = serviceTitle,
                    serviceArtist = serviceArtist,
                    onStop = onStop,
                    onOpenFull = onOpenFull,
                    onJump = onJump,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onTogglePlayPause = onTogglePlayPause,
                    playPauseFocusRequester = playPauseFocusRequester
                )
            } else {
                AudioPlayerBadge(
                    currentMedia = currentMedia,
                    serviceCoverArt = serviceCoverArt,
                    playerState = playerState,
                    sliderPosition = sliderPosition
                )
            }
        }
    }

    var initialLaunch by remember { mutableStateOf(true) }
    LaunchedEffect(visible, isFocused) {
        if (visible && isFocused && (requestFocus || !initialLaunch)) playPauseFocusRequester.requestFocus()
        initialLaunch = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPlayerExpanded(
    progress: PlaybackProgress?,
    sliderPosition: Float,
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
    playPauseFocusRequester: FocusRequester
) {
    val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .padding(vertical = 32.dp)
                .fillMaxHeight()
                .width(212.dp)
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
                .padding(horizontal = 16.dp)
                .focusProperties {
                    onEnter = {
                        playPauseFocusRequester.requestFocus()
                    }
                }
                .focusGroup(),
            horizontalAlignment = Alignment.End
        ) {

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                serviceArtist ?: "",
                modifier = Modifier
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1F))
            Row(
                modifier = Modifier
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
                    .fillMaxWidth(),
                progress = { sliderPosition },
                drawStopIndicator = {}
            )
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp)
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
                        PlayPause(
                            atEnd = playerState?.playing == true,
                            tint = tint
                        )
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

@Composable
private fun AudioPlayerBadge(
    modifier: Modifier = Modifier,
    currentMedia: MediaWrapper?,
    serviceCoverArt: String?,
    playerState: PlayerState?,
    sliderPosition: Float
) {
    val coroutineScope = rememberCoroutineScope()
    val mapBitmap: MutableState<Pair<String?, Bitmap?>> = remember { mutableStateOf(Pair(null, null)) }
    if (mapBitmap.value.first != currentMedia?.artworkMrl) {
        mapBitmap.value = Pair(currentMedia?.artworkMrl, null)
    }

    LaunchedEffect(key1 = currentMedia?.artworkMrl) {
        coroutineScope.launch {
            serviceCoverArt?.let {
                mapBitmap.value = Pair(it, AudioUtil.readCoverBitmap(Uri.decode(it), 320))
            }
        }
    }

    Surface(
        modifier = modifier
            .requiredSize(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        onClick = {} // Makes it focusable
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Background Image
            mapBitmap.value.second?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
                Image(
                    painter = painterResource(id = R.drawable.ic_song_big),
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            // Mini Visualizer on top
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.BottomCenter
            ) {
                MiniVisualizer(
                    color = Color.White,
                    isPlaying = playerState?.playing == true
                )
            }

            // Circular progress
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxSize().padding(1.5.dp)) {
                // Background circle (track)
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    style = Stroke(width = 3.dp.toPx())
                )
                // Progress arc
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360f * sliderPosition,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

@Preview
@Composable
private fun AudioPlayerBadgePreview() {
    val media = StubMediaWrapper(
        1L, "file:///track.mp3", 0L, 0f, 300000L,
        MediaWrapper.TYPE_AUDIO,
        "Title", "track.mp3", 1L, 1L, "Artist", "Genre",
        1L, "Album", "Artist", 0, 0, "", 0, 0, 1, 1,
        0L, 0L, false, false, 2024, true, 0L
    )
    VlcPreview {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AudioPlayerBadge(
                currentMedia = media,
                serviceCoverArt = null,
                playerState = PlayerState(playing = true, title = "Title", artist = "Artist"),
                sliderPosition = 0.5f
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioPlayerPreview() {
    val media = StubMediaWrapper(
        1L, "file:///track.mp3", 0L, 0f, 300000L,
        MediaWrapper.TYPE_AUDIO,
        "Title", "track.mp3", 1L, 1L, "Artist", "Genre",
        1L, "Album", "Artist", 0, 0, "", 0, 0, 1, 1,
        0L, 0L, false, false, 2024, true, 0L
    )
    VlcPreview {
        Row(Modifier.fillMaxSize()) {
            AudioPlayer(
                visible = true,
                progress = PlaybackProgress(time = 10000, length = 30000),
                playerState = PlayerState(playing = true, title = "Title", artist = "Artist"),
                currentMedia = media,
                serviceCoverArt = null,
                serviceTitle = "Title",
                serviceArtist = "Artist",
                onStop = {},
                onOpenFull = {},
                onJump = {},
                onPrevious = {},
                onNext = {},
                onTogglePlayPause = {},
                forceExpanded = true
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
