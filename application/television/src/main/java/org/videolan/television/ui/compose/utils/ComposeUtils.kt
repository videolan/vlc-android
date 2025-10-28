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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
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