/*
 * ************************************************************************
 *  Placeholder.kt
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

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.theme.WhiteTransparent05
import org.videolan.television.ui.compose.utils.VlcPreview

/**
 * Shimmer modifier to apply a shimmering effect to a composable
 * @param shape the shape of the shimmer
 * @param targetValue the target value of the animation
 * @return a modifier with the shimmering effect
 */
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(8.dp),
    targetValue: Float = 1000f
): Modifier = composed {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.2f),
        Color.White.copy(alpha = 0.05f),
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
    this.background(brush, shape)
}

@Composable
fun VideoItemPlaceholder(modifier: Modifier = Modifier, inCard: Boolean = true) {
    if (inCard) {
        Column(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9)
                    .shimmer(MaterialTheme.shapes.medium)
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, start = 4.dp)
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .shimmer(RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, start = 4.dp)
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .shimmer(RoundedCornerShape(4.dp))
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(WhiteTransparent05, MaterialTheme.shapes.medium)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9)
                    .shimmer(MaterialTheme.shapes.small)
            )
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.3f)
                        .height(10.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun AudioItemPlaceholder(modifier: Modifier = Modifier, inCard: Boolean = true, isRound: Boolean = false, isFirst: Boolean = false, isLast: Boolean = false) {
    val shape = if (isRound) CircleShape else MaterialTheme.shapes.medium
    if (inCard) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shimmer(shape)
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.8f)
                    .height(14.dp)
                    .shimmer(RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .shimmer(RoundedCornerShape(4.dp))
            )
        }
    } else {
        val baseCornerRadius = 12.dp
        val topCornerRadius = if (isFirst) baseCornerRadius else 0.dp
        val bottomCornerRadius = if (isLast) baseCornerRadius else 0.dp
        val shapeRow = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius, bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shapeRow)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shimmer(shape)
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun SearchPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        repeat(3) {
            Column {
                Box(
                    modifier = Modifier
                        .padding(start = VlcTVTheme.dimens.overscanHorizontal, bottom = 12.dp)
                        .size(width = 120.dp, height = 24.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
                Row(
                    modifier = Modifier.padding(horizontal = VlcTVTheme.dimens.overscanHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(VlcTVTheme.dimens.itemFocusGlowRadius)
                ) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(width = 150.dp, height = 100.dp)
                                .shimmer(MaterialTheme.shapes.medium)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaGridPlaceholder(
    modifier: Modifier = Modifier,
    columns: GridCells = GridCells.Fixed(3),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    itemPlaceholder: @Composable () -> Unit
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        userScrollEnabled = false
    ) {
        items(12) {
            itemPlaceholder()
        }
    }
}

@Composable
fun MediaListPlaceholder(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    itemPlaceholder: @Composable (isFirst: Boolean, isLast: Boolean) -> Unit
) {
    val count = 8
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        userScrollEnabled = false
    ) {
        items(count) { index ->
            itemPlaceholder(index == 0, index == count - 1)
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoItemPlaceholderPreview() {
    VlcPreview {
        VideoItemPlaceholder(modifier = Modifier.width(200.dp))
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun VideoItemPlaceholderListPreview() {
    VlcPreview {
        VideoItemPlaceholder(inCard = false)
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemPlaceholderPreview() {
    VlcPreview {
        AudioItemPlaceholder(modifier = Modifier.width(150.dp))
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AudioItemPlaceholderRoundPreview() {
    VlcPreview {
        AudioItemPlaceholder(isRound = true, modifier = Modifier.width(150.dp))
    }
}
