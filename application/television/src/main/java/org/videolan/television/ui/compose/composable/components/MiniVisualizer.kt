/*
 * ************************************************************************
 *  MiniVisualizer.kt
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

package org.videolan.television.ui.compose.composable.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.vlc.viewmodels.PlaylistModel

@Composable
fun MiniVisualizer(color: Color = WhiteTransparent50, viewModel: PlaylistModel = viewModel()) {
    val playerState = viewModel.playerState.observeAsState()
    MiniVisualizer(color, playerState.value?.playing == true)
}

@Composable
fun MiniVisualizer(color: Color = WhiteTransparent50, isPlaying: Boolean) {
    val firstBarValues = arrayOf(0.7f, 0.3f, 0.9f, 0.7f, 0.7f, 0.7f, 0.4f, 0.6f, 0.8f, 0.6f, 0.3f, 0.2f, 0.1f, 0.9f, 0.1f, 0.5f, 0.2f, 0.3f, 0.1f, 0.7f, 0.6f, 0.5f, 0.8f, 0.3f, 0.8f, 0.1f)
    val secondBarValues = arrayOf(0.2f, 0.8f, 0.7f, 0.8f, 0.8f, 0.3f, 0.5f, 0.4f, 0.8f, 0.3f, 0.7f, 0.9f, 0.5f, 0.8f, 0.1f, 0.3f, 0.2f, 0.5f, 0.2f, 0.7f, 0.3f, 0.4f, 0.1f, 0.5f, 0.7f, 0.2f)
    val thirdBarValues = arrayOf(0.3f, 0.1f, 0.3f, 0.3f, 0.3f, 0.7f, 0.7f, 0.9f, 0.3f, 0.7f, 0.0f, 0.9f, 0.3f, 0.2f, 0.4f, 0.8f, 0.5f, 1.0f, 0.2f, 0.4f, 1.0f, 0.3f, 0.2f, 0.5f, 0.7f, 0.5f)

    val firstBarAnimatable = remember { Animatable(firstBarValues[0]) }
    val secondBarAnimatable = remember { Animatable(secondBarValues[0]) }
    val thirdBarAnimatable = remember { Animatable(thirdBarValues[0]) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            launch {
                var index = 0
                while (true) {
                    index = (index + 1) % firstBarValues.size
                    firstBarAnimatable.animateTo(firstBarValues[index], tween(200, easing = LinearEasing))
                }
            }
            launch {
                var index = 0
                while (true) {
                    index = (index + 1) % secondBarValues.size
                    secondBarAnimatable.animateTo(secondBarValues[index], tween(150, easing = LinearEasing))
                }
            }
            launch {
                var index = 0
                while (true) {
                    index = (index + 1) % thirdBarValues.size
                    thirdBarAnimatable.animateTo(thirdBarValues[index], tween(220, easing = LinearEasing))
                }
            }
        } else {
            launch { firstBarAnimatable.animateTo(0.1f, tween(500)) }
            launch { secondBarAnimatable.animateTo(0.1f, tween(500)) }
            launch { thirdBarAnimatable.animateTo(0.1f, tween(500)) }
        }
    }

    Row(
        Modifier
            .width(32.dp)
            .aspectRatio(1F),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .weight(1F)
                .graphicsLayer(scaleY = firstBarAnimatable.value, transformOrigin = TransformOrigin(0.5F, 1F))
                .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            Modifier
                .fillMaxHeight()
                .weight(1F)
                .graphicsLayer(scaleY = secondBarAnimatable.value, transformOrigin = TransformOrigin(0.5F, 1F))
                .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            Modifier
                .fillMaxHeight()
                .weight(1F)
                .graphicsLayer(scaleY = thirdBarAnimatable.value, transformOrigin = TransformOrigin(0.5F, 1F))
                .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
    }
}

@Preview
@Composable
private fun MiniVisualizerPlayingPreview() {
    VlcPreview {
        MiniVisualizer(color = MaterialTheme.colorScheme.primary, isPlaying = true)
    }
}

@Preview
@Composable
private fun MiniVisualizerPausedPreview() {
    VlcPreview {
        MiniVisualizer(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), isPlaying = false)
    }
}
