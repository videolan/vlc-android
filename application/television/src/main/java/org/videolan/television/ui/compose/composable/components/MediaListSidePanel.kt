/*
 * ************************************************************************
 *  MediaListSidePanel.kt
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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import kotlinx.coroutines.launch
import org.videolan.television.R
import org.videolan.television.ui.compose.theme.Orange500
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.tools.KEY_SIDE_PANEL_DISCOVERED
import org.videolan.tools.Settings
import org.videolan.vlc.util.MediaListEntry

@Composable
fun MediaListSidePanel(modifier: Modifier = Modifier, content: MediaListSidePanelContent, onFocusExit: () -> Unit = {}, listener: (MediaListSidePanelListenerKey, Any) -> Unit = { _, _ -> }) {
    if (!content.show) return
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val settings = remember { Settings.getInstance(context) }
    var hasFocus by remember { mutableStateOf(false) }
    var isDiscovered by remember { mutableStateOf(settings.getBoolean(KEY_SIDE_PANEL_DISCOVERED, false)) }

    // Dimensions: Animate from a circle-like small box to a full-height column
    val width by animateDpAsState(if (hasFocus) 64.dp else 40.dp, label = "width")
    
    // Background Color Animation
    val backgroundColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.surfaceDim else Color.Transparent,
        label = "backgroundColor"
    )

    // Shadow Animation
    val shadowRadius by animateDpAsState(if (hasFocus) 8.dp else 0.dp, label = "shadowRadius")
    val shadowColor by animateColorAsState(
        targetValue = if (hasFocus) Color(0x40000000) else Color.Transparent,
        label = "shadowColor"
    )
    
    // Alpha for content vs chevron
    val contentAlpha by animateFloatAsState(if (hasFocus) 1f else 0f, label = "alpha")
    val chevronAlphaBase by animateFloatAsState(if (hasFocus) 0f else 1f, label = "chevronAlpha")
    
    // Rotation: 90 to -90 as per top tabs consistency
    val chevronRotation by animateFloatAsState(if (hasFocus) 90f else -90f, label = "chevronRotation")

    // Sonar Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "sonarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!isDiscovered && !hasFocus) 2.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (!isDiscovered && !hasFocus) 0f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    LaunchedEffect(hasFocus) {
        if (hasFocus && !isDiscovered) {
            isDiscovered = true
            settings.edit { putBoolean(KEY_SIDE_PANEL_DISCOVERED, true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
                    onFocusExit()
                    return@onKeyEvent true
                }
                false
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = 32.dp, top = 16.dp, end = 16.dp)
                .zIndex(1f)
                .dropShadow(
                    shape = RoundedCornerShape(20.dp),
                    shadow = Shadow(
                        radius = shadowRadius,
                        spread = 2.dp,
                        color = shadowColor,
                        offset = DpOffset(x = 0.dp, 0.dp)
                    )
                )
                .onFocusChanged { hasFocus = it.hasFocus }
                .width(width)
                // Animate ratio from 1 (collapsed) to wrap content (expanded)
                .heightIn(max = if (hasFocus) 1000.dp else width)
                .animateContentSize()
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .focusProperties {
                    onEnter = {
                        focusRequester.requestFocus()
                    }
                }
                .focusGroup(),
            contentAlignment = Alignment.Center
        ) {
            // The Sonar Pulse (Visible only when collapsed and undiscovered)
            if (!isDiscovered)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha = pulseAlpha * chevronAlphaBase
                        }
                        .background(Orange500, CircleShape)
                )

            // The Chevron Hint (Visible only when collapsed)
            // We use alpha instead of conditional logic to keep the node measured
            Icon(
                painter = painterResource(R.drawable.ic_collapse_arrow),
                contentDescription = null,
                modifier = Modifier
                    .padding(vertical = 8.dp) // Ensure it fits the 40dp collapsed height
                    .graphicsLayer {
                        rotationZ = chevronRotation
                        scaleX = 1f
                        scaleY = 1f
                        alpha = chevronAlphaBase
                    }
                    .size(24.dp)
            )

            // The Utility Icons (Expanded)
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .alpha(contentAlpha)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val itemCount = 1 + (if (content.showResumePlayback) 1 else 0) + (if (content.isFavorite != null) 1 else 0) + (if (content.entry != null) 1 else 0)
                var currentIndex = 0
                
                LabeledIconButton(
                    stringResource(R.string.scroll_to_top),
                    modifier = Modifier
                        .focusRequester(focusRequester = focusRequester)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            if (itemCount == 1) down = FocusRequester.Cancel
                        },
                    vectorImage = Icons.Outlined.ArrowUpward,
                    horizontalTooltip = true
                ) {
                    coroutineScope.launch {
                        when (content.listState) {
                            is LazyListState -> content.listState.animateScrollToItem(0)
                            is LazyGridState -> content.listState.animateScrollToItem(0)
                        }
                    }
                }
                currentIndex++

                if (content.showResumePlayback) {
                    val isLast = currentIndex == itemCount - 1
                    LabeledIconButton(
                        stringResource(R.string.resume_playback_short_title), 
                        modifier = Modifier.focusProperties { if (isLast) down = FocusRequester.Cancel },
                        painterResource = painterResource(R.drawable.ic_resume_playback),
                        horizontalTooltip = true
                    ) {
                        listener(MediaListSidePanelListenerKey.RESUME_PLAYBACK, 0)
                    }
                    currentIndex++
                }

                if (content.isFavorite == true) {
                    val isLast = currentIndex == itemCount - 1
                    LabeledIconButton(
                        stringResource(R.string.favorites_remove), 
                        modifier = Modifier.focusProperties { if (isLast) down = FocusRequester.Cancel },
                        painterResource = painterResource(R.drawable.ic_fav_remove),
                        horizontalTooltip = true
                    ) {
                        listener(MediaListSidePanelListenerKey.CHANGE_FAVORITE, false)
                    }
                    currentIndex++
                } else if (content.isFavorite == false) {
                    val isLast = currentIndex == itemCount - 1
                    LabeledIconButton(
                        stringResource(R.string.favorites_add), 
                        modifier = Modifier.focusProperties { if (isLast) down = FocusRequester.Cancel },
                        painterResource = painterResource(R.drawable.ic_fav_add),
                        horizontalTooltip = true
                    ) {
                        listener(MediaListSidePanelListenerKey.CHANGE_FAVORITE, true)
                    }
                    currentIndex++
                }

                if (content.entry != null) {
                    val isLast = currentIndex == itemCount - 1
                    LabeledIconButton(
                        stringResource(R.string.display_settings), 
                        modifier = Modifier.focusProperties { if (isLast) down = FocusRequester.Cancel },
                        painterResource = painterResource(R.drawable.ic_display_settings),
                        horizontalTooltip = true
                    ) {
                        listener(MediaListSidePanelListenerKey.DISPLAY_SETTINGS, content.entry)
                    }
                    currentIndex++
                }
            }
        }
    }
}

enum class MediaListSidePanelListenerKey {
    DISPLAY_MODE, RESUME_PLAYBACK, CHANGE_FAVORITE, DISPLAY_SETTINGS
}

data class MediaListSidePanelContent(
    val show: Boolean = true,
    val showScrollToTop: Boolean = true,
    val showResumePlayback: Boolean = true,
    val isFavorite: Boolean? = null,
    val listState: ScrollableState,
    val entry: MediaListEntry?
)

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaListSidePanelPreview() {
    VlcPreview {
        MediaListSidePanel(
            content = MediaListSidePanelContent(
                show = true,
                showScrollToTop = true,
                showResumePlayback = true,
                isFavorite = true,
                listState = rememberLazyListState(),
                entry = MediaListEntry.ALBUMS
            )
        )
    }
}
