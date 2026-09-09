/*
 * ************************************************************************
 *  StreamScreen.kt
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

package org.videolan.television.ui.compose.composable.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.television.ui.compose.composable.items.AudioItem
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.utils.TvGridBringIntoViewSpec
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.tools.isValidUrl
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.viewmodels.StreamsModel

@Composable
fun StreamScreen(
    streamsModel: StreamsModel = viewModel(factory = StreamsModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    val dataset by streamsModel.dataset.observeAsState(emptyList())
    var urlText by remember { mutableStateOf("") }
    var showClipboardHint by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val clipBoardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipBoardManager?.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isValidUrl()) {
            urlText = text
            showClipboardHint = true
        }
    }

    StreamScreen(
        dataset = dataset,
        urlText = urlText,
        showClipboardHint = showClipboardHint,
        onUrlChange = {
            urlText = it
            showClipboardHint = false
        },
        onPlayClick = {
            if (it.isNotBlank()) {
                val mw = MLServiceLocator.getAbstractMediaWrapper(it.trim().toUri())
                MediaUtils.openMedia(context, mw)
                urlText = ""
            }
        },
        onItemClick = {
            MediaUtils.openMedia(context, it as? MediaWrapper)
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamScreen(
    dataset: List<MediaLibraryItem>,
    urlText: String,
    showClipboardHint: Boolean,
    onUrlChange: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onItemClick: (MediaLibraryItem) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = VlcTVTheme.dimens.overscanHorizontal)
            .padding(top = VlcTVTheme.dimens.overscanVertical)
    ) {
        Text(
            text = stringResource(id = org.videolan.vlc.R.string.streams),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        // URL Entry Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = urlText,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                label = { Text(stringResource(id = org.videolan.vlc.R.string.open_mrl_dialog_msg)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onPlayClick(urlText) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(id = org.videolan.vlc.R.string.play_button))
                    }
                }
            )
        }

        if (showClipboardHint) {
            Text(
                text = stringResource(id = org.videolan.vlc.R.string.copied_from_clipboard),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Recent Streams Grid
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvGridBringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = VlcTVTheme.dimens.overscanVertical,
                    top = 36.dp,
                    start = VlcTVTheme.dimens.overscanHorizontal,
                    end = VlcTVTheme.dimens.overscanHorizontal
                ),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(dataset) { index, item ->
                    AudioItem(
                        audios = dataset,
                        entry = MediaListEntry.STREAMS,
                        index = index
                    ) { onItemClick(item) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun StreamScreenPreview() {
    val dataset = (1..10).map { i ->
        StubMediaWrapper(
            i.toLong(), "http://stream$i.com/live", 0, 0f, 0, MediaWrapper.TYPE_STREAM,
            "Stream $i", "live", 0, 0, "", "", 0, "", "", 0, 0, "", 0, 0, 0, 0, 0, 0, false, false, 0, true, 0
        )
    }

    VlcPreview {
        StreamScreen(
            dataset = dataset,
            urlText = "http://example.com",
            showClipboardHint = true,
            onUrlChange = {},
            onPlayClick = {},
            onItemClick = {}
        )
    }
}
