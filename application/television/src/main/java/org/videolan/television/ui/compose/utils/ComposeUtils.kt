/*
 * ************************************************************************
 *  ComposeUtils.kt
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

package org.videolan.television.ui.compose.utils

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.vlc.R
import org.videolan.vlc.util.fileReplacementMarker
import org.videolan.vlc.util.folderReplacementMarker


fun CharSequence?.getDescriptionAnnotated(): AnnotatedString  {
    return buildAnnotatedString {
        this@getDescriptionAnnotated?.split(folderReplacementMarker)?.forEachIndexed { index, splitByFolder ->
            if (index > 0) appendInlineContent(id = folderReplacementMarker)
            splitByFolder.split(fileReplacementMarker).forEachIndexed { index, it ->
                if (index > 0) appendInlineContent(id = fileReplacementMarker)
                append(it)

            }
        }
    }
}

val inlineContentMap = mapOf(
    folderReplacementMarker to InlineTextContent(
        Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextTop)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_emoji_folder),
            modifier = Modifier.fillMaxSize(),
            contentDescription = ""
        )
    },
    fileReplacementMarker to InlineTextContent(
        Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextTop)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_emoji_file),
            modifier = Modifier.fillMaxSize(),
            contentDescription = ""
        )
    }
)


inline fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
    ifFalse: Modifier.() -> Modifier = { this },
): Modifier = if (condition) {
    then(ifTrue(this))
} else {
    then(ifFalse(this))
}


fun ContentDrawScope.drawFadedEdge(edgeWidthPx: Float, leftEdge: Boolean) {
    drawRect(
        topLeft = Offset(if (leftEdge) 0f else size.width - edgeWidthPx, 0f),
        size = Size(edgeWidthPx, size.height),
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = if (leftEdge) 0f else size.width,
            endX = if (leftEdge) edgeWidthPx else size.width - edgeWidthPx
        ),
        blendMode = BlendMode.DstIn
    )
}

fun Modifier.fadingMarquee(
    edgeWidth: Dp = 16.dp,
    leftEdge: Boolean = true,
    rightEdge: Boolean = true,
    marqueeOnlyOnFocus: Boolean = false,
    isFocused: Boolean = true
): Modifier {
    val isMarqueeActive = !marqueeOnlyOnFocus || isFocused
    return this
        .conditional(isMarqueeActive, {
            graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (leftEdge) drawFadedEdge(edgeWidth.toPx(), leftEdge = true)
                    if (rightEdge) drawFadedEdge(edgeWidth.toPx(), leftEdge = false)
                }
                .basicMarquee()
        })
        .padding(horizontal = edgeWidth)
}

/**
 * Vlc preview: setup everything for the preview: the context, the compose theme and the Android theme.
 *
 * @param content The content to preview
 * @receiver
 */
@Composable
fun VlcPreview(content: @Composable (Context) -> Unit) {
    val context = LocalContext.current
    val themedContext = remember(context) { ContextThemeWrapper(context, org.videolan.television.R.style.Theme_VLC) }
    CompositionLocalProvider(LocalContext provides themedContext) {
        VlcTVTheme {
            content(context)
        }
    }
}
