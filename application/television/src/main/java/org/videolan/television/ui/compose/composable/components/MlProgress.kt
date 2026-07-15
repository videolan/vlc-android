/*
 * ************************************************************************
 *  MlProgress.kt
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.ScanProgress

@Composable
fun MlProgress(modifier: Modifier, mainActivityViewModel: MainActivityViewModel? = if (LocalInspectionMode.current) null else hiltViewModel()) {
    val mlProgress = mainActivityViewModel?.progress?.observeAsState()
    MlProgressContent(
        modifier = modifier,
        progress = mlProgress?.value,
        isWorking = if (LocalInspectionMode.current) false else Medialibrary.getInstance().isWorking
    )
}

@Composable
fun MlProgressContent(
    modifier: Modifier,
    progress: ScanProgress?,
    isWorking: Boolean
) {
    if (progress != null && isWorking) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceDim)
                .padding(8.dp)
                .widthIn(0.dp, 400.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = progress.progressText,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1F),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis
            )
            if (progress.inDiscovery)
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            else
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    progress = {
                        progress.parsing / 100F
                    }
                )
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun MlProgressPreview() {
    VlcPreview {
        MlProgressContent(
            modifier = Modifier,
            progress = ScanProgress(50f, "Scanning...", false),
            isWorking = true
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun MlProgressDiscoveryPreview() {
    VlcPreview {
        MlProgressContent(
            modifier = Modifier,
            progress = ScanProgress(0f, "Discovering...", true),
            isWorking = true
        )
    }
}
