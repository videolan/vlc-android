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
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.television.R
import org.videolan.television.ui.AudioPlayerActivity
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.utils.LocalMainContentFocusRequester
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.ui.compose.utils.rememberAudioCoverBitmap
import org.videolan.tools.KEY_AUDIO_PLAYER_PINNED
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.viewmodels.PlaybackProgress
import org.videolan.vlc.viewmodels.PlayerState
import org.videolan.vlc.viewmodels.PlaylistModel

private enum class PlayerDisplayState {
    Hidden,
    Badge,
    Expanded,
    Pinned
}

private const val AUDIO_PLAYER_ANIMATION_DURATION = 500

@Composable
fun AudioPlayer(
    modifier: Modifier = Modifier,
    playlistModel: PlaylistModel = viewModel(),
    requestFocus: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val activity = LocalActivity.current
    val visible = PlaylistManager.showAudioPlayer.observeAsState()
    val progress = playlistModel.progress.observeAsState()
    val playerState = playlistModel.playerState.observeAsState()
    val currentMedia = PlaylistManager.currentPlayedMedia.observeAsState()
    val isPinned = Settings.audioPlayerPinned.observeAsState(false)

    AudioPlayer(
        modifier = modifier,
        visible = visible.value == true,
        isPinned = isPinned.value,
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
        onPinToggled = { pinned ->
            Settings.audioPlayerPinned.postValue(pinned)
            Settings.getInstance(activity!!).putSingle(KEY_AUDIO_PLAYER_PINNED, pinned)
        },
        requestFocus = requestFocus,
        focusRequester = focusRequester
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AudioPlayer(
    modifier: Modifier = Modifier,
    visible: Boolean,
    isPinned: Boolean = false,
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
    onPinToggled: (Boolean) -> Unit,
    requestFocus: Boolean = true,
    forceExpanded: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val focusManager = LocalFocusManager.current
    val mainContentFocusRequester = LocalMainContentFocusRequester.current
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    sliderPosition = ((progress?.time ?: 0).toFloat() / (progress?.length ?: 1)).coerceIn(0F, 1F)
    val playPauseFocusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(forceExpanded) }

    val displayState by remember(visible, isFocused, isPinned) {
        derivedStateOf {
            when {
                !visible -> PlayerDisplayState.Hidden
                isPinned -> PlayerDisplayState.Pinned
                isFocused -> PlayerDisplayState.Expanded
                else -> PlayerDisplayState.Badge
            }
        }
    }

    var cachedMedia by remember { mutableStateOf<MediaWrapper?>(null) }
    var cachedCoverArt by remember { mutableStateOf<String?>(null) }
    var cachedTitle by remember { mutableStateOf<String?>(null) }
    var cachedArtist by remember { mutableStateOf<String?>(null) }
    var cachedProgress by remember { mutableStateOf<PlaybackProgress?>(null) }
    var cachedPlayerState by remember { mutableStateOf<PlayerState?>(null) }

    if (visible) {
        cachedMedia = currentMedia
        cachedCoverArt = serviceCoverArt
        cachedTitle = serviceTitle
        cachedArtist = serviceArtist
        cachedProgress = progress
        cachedPlayerState = playerState
    }

    val coverBitmap = rememberAudioCoverBitmap(cachedCoverArt, 512)

    SharedTransitionLayout(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { isFocused = it.hasFocus }
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) {
                    if (!focusManager.moveFocus(FocusDirection.Right)) {
                        mainContentFocusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                }
                false
            }
    ) {
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            AnimatedContent(
                targetState = displayState,
                modifier = Modifier.fillMaxHeight(),
                transitionSpec = {
                    when {
                        // Hidden <-> Badge: Scale in/out
                        (targetState == PlayerDisplayState.Badge && initialState == PlayerDisplayState.Hidden) ||
                                (targetState == PlayerDisplayState.Hidden && initialState == PlayerDisplayState.Badge) -> {
                            fadeIn(tween(AUDIO_PLAYER_ANIMATION_DURATION)) + scaleIn(initialScale = 0f, transformOrigin = TransformOrigin.Center, animationSpec = tween(AUDIO_PLAYER_ANIMATION_DURATION)) togetherWith fadeOut(tween(AUDIO_PLAYER_ANIMATION_DURATION)) + scaleOut(targetScale = 0f, transformOrigin = TransformOrigin.Center, animationSpec = tween(AUDIO_PLAYER_ANIMATION_DURATION))
                        }
                        // Any other transition: Simple Fade (Shared elements handle the rest)
                        else -> {
                            fadeIn(tween(AUDIO_PLAYER_ANIMATION_DURATION)) togetherWith fadeOut(tween(AUDIO_PLAYER_ANIMATION_DURATION))
                        }
                    }.using(SizeTransform(clip = false) { _, _ ->
                        if (targetState == PlayerDisplayState.Hidden || initialState == PlayerDisplayState.Hidden) snap()
                        else tween(AUDIO_PLAYER_ANIMATION_DURATION)
                    })
                },
                label = "player_expansion_state",
                contentAlignment = Alignment.CenterStart
            ) { state ->
                when (state) {
                    PlayerDisplayState.Expanded, PlayerDisplayState.Pinned -> {
                        AudioPlayerExpanded(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            progress = cachedProgress,
                            sliderPosition = sliderPosition,
                            playerState = cachedPlayerState,
                            serviceTitle = cachedTitle,
                            serviceArtist = cachedArtist,
                            coverBitmap = coverBitmap,
                            onStop = onStop,
                            onOpenFull = onOpenFull,
                            onJump = onJump,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onTogglePlayPause = onTogglePlayPause,
                            isPinned = isPinned,
                            onPinToggled = onPinToggled,
                            playPauseFocusRequester = playPauseFocusRequester
                        )
                    }

                    PlayerDisplayState.Badge -> {
                        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                            AudioPlayerBadge(
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                modifier = Modifier.padding(start = 32.dp),
                                playerState = cachedPlayerState,
                                sliderPosition = sliderPosition,
                                coverBitmap = coverBitmap
                            )
                        }
                    }

                    PlayerDisplayState.Hidden -> {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
            }
        }
    }

    var initialLaunch by remember { mutableStateOf(true) }
    LaunchedEffect(displayState) {
        if ((displayState == PlayerDisplayState.Expanded || displayState == PlayerDisplayState.Pinned) && (requestFocus || !initialLaunch)) {
            playPauseFocusRequester.requestFocus()
        }
        initialLaunch = false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AudioPlayerExpanded(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    progress: PlaybackProgress?,
    sliderPosition: Float,
    playerState: PlayerState?,
    serviceTitle: String?,
    serviceArtist: String?,
    coverBitmap: Bitmap?,
    onStop: () -> Unit,
    onOpenFull: () -> Unit,
    onJump: (forward: Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    isPinned: Boolean,
    onPinToggled: (Boolean) -> Unit,
    playPauseFocusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .padding(vertical = 32.dp)
            .fillMaxHeight()
            .width(VlcTVTheme.dimens.miniPlayerWidth)
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
                    label = if (isPinned) "Unpin" else "Pin",
                    vectorImage = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                ) {
                    onPinToggled(!isPinned)
                }
                LabeledIconButton(
                    label = stringResource(R.string.open_audio_player),
                    vectorImage = Icons.Outlined.OpenInFull,
                ) {
                    onOpenFull()
                }
            }

            Spacer(modifier = Modifier.weight(1F))

            with(sharedTransitionScope) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1F)
                            .sharedElement(
                                rememberSharedContentState(key = "audio_cover"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(AUDIO_PLAYER_ANIMATION_DURATION) }
                            )
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_song_big),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1F)
                            .sharedElement(
                                rememberSharedContentState(key = "audio_cover"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(AUDIO_PLAYER_ANIMATION_DURATION) }
                            )
                            .clip(RoundedCornerShape(12.dp))
                    )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AudioPlayerBadge(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    playerState: PlayerState?,
    sliderPosition: Float,
    coverBitmap: Bitmap?
) {
    val isTransitionFinished = animatedVisibilityScope.transition.currentState == animatedVisibilityScope.transition.targetState

    Surface(
        modifier = modifier
            .requiredSize(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        onClick = {} // Makes it focusable
    ) {
        with(animatedVisibilityScope) {
            Box(contentAlignment = Alignment.Center) {
                // Background Image
                with(sharedTransitionScope) {
                    coverBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .sharedElement(
                                    rememberSharedContentState(key = "audio_cover"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(AUDIO_PLAYER_ANIMATION_DURATION) }
                                )
                                .clip(CircleShape)
                        )
                    } ?: run {
                        Image(
                            painter = painterResource(id = R.drawable.ic_song_big),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxSize()
                                .sharedElement(
                                    rememberSharedContentState(key = "audio_cover"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(AUDIO_PLAYER_ANIMATION_DURATION) }
                                )
                                .clip(CircleShape),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                with(sharedTransitionScope) {
                    // Mini Visualizer on top
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .renderInSharedTransitionScopeOverlay(
                                zIndexInOverlay = 1f,
                            )
                            .graphicsLayer {
                                alpha = if (isTransitionFinished) 1f else 0f
                            }
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        MiniVisualizer(
                            color = Color.White,
                            isPlaying = playerState?.playing == true
                        )
                    }

                    // Circular progress
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .renderInSharedTransitionScopeOverlay(
                                zIndexInOverlay = 1f,
                            )
                            .graphicsLayer {
                                alpha = if (isTransitionFinished) 1f else 0f
                            }
                            .padding(1.5.dp)
                    ) {
                        // Background circle (track)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
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
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview
@Composable
private fun AudioPlayerBadgePreview() {
    VlcPreview {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SharedTransitionLayout {
                AnimatedContent(targetState = true, label = "") { visible ->
                    if (visible) {
                        AudioPlayerBadge(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            playerState = PlayerState(playing = true, title = "Title", artist = "Artist"),
                            sliderPosition = 0.7f,
                            coverBitmap = null
                        )
                    }
                }
            }
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
                onPinToggled = {},
                forceExpanded = true
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
