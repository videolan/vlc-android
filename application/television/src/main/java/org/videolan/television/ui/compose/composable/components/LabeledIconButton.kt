/*
 * ************************************************************************
 *  LabeledIconButton.kt
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults.RippleAlpha
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import org.videolan.television.ui.compose.theme.VlcTVTheme

/**
 * Labeled icon button
 *
 * @param label the label to be shown in the tooltip and in the content description
 * @param modifier the modifier to be applied to the button
 * @param vectorImage the vector image to be displayed or null
 * @param painterResource the painter resource to be displayed or null
 * @param customImage the custom image to be displayed or null
 * @param tint the tint to be applied to the icon
 * @param backgroundColor the background color to be applied to the button
 * @param focusedBackgroundColor the background color to be applied to the button when focused
 * @param focusHeight the height of the button when focused
 * @param onClick the click listener
 * @receiver
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledIconButton(
    label: String,
    modifier: Modifier = Modifier,
    vectorImage: ImageVector? = null,
    painterResource: Painter? = null,
    customImage: (@Composable (tint: Color) -> Unit)? = null,
    tint: Color? = null,
    backgroundColor: Color = Color.Transparent,
    focusedBackgroundColor: Color = MaterialTheme.colorScheme.primary.copy(0.8F),
    focusHeight: Dp = 48.dp,
    horizontalTooltip: Boolean = false,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (hasFocus) 1.3f else 1.0f, label = "scale")
    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (hasFocus) focusedBackgroundColor else backgroundColor,
        label = "background"
    )
    val animatedIconColor by animateColorAsState(
        targetValue = if (hasFocus) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4F),
        label = "iconColor"
    )
    val tooltipPosition = if (horizontalTooltip) TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Start) else TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below)
    TooltipBox(
        positionProvider = tooltipPosition,
        tooltip = {
            PlainTooltip(
                shape = RoundedCornerShape(50),
                containerColor = MaterialTheme.colorScheme.surfaceDim,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50))
            ) {
                Text(label)
            }
        },
        state = rememberTooltipState()
    ) {
//        val interactionSource = remember { MutableInteractionSource() }
        val defaultRippleAlpha = RippleAlpha
        CompositionLocalProvider(
            LocalRippleConfiguration provides RippleConfiguration(
                rippleAlpha = RippleAlpha(
                    pressedAlpha = 0f,
                    draggedAlpha = defaultRippleAlpha.draggedAlpha,
                    focusedAlpha = 0f,
                    hoveredAlpha = defaultRippleAlpha.hoveredAlpha,
                )
            ),
        ) {
            Box(
                modifier = modifier
                    .height(focusHeight)
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .clickable(
                        onClick = onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = animatedBackgroundColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (customImage != null) {
                        Box(contentAlignment = Alignment.Center,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }) {
                            customImage(animatedIconColor)
                        }
                    } else if (vectorImage != null)
                        Icon(
                            imageVector = vectorImage,
                            tint = tint ?: animatedIconColor,
                            contentDescription = label,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                    else
                        Icon(
                            painter = painterResource!!,
                            modifier = Modifier.size(24.dp).graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                            tint = tint ?: animatedIconColor,
                            contentDescription = label,
                        )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LabeledIconButtonPreview() {
    VlcTVTheme {
        LabeledIconButton(
            label = "Play",
            vectorImage = Icons.Default.PlayArrow,
            onClick = {}
        )
    }
}
