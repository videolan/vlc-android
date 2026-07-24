/*
 * ************************************************************************
 *  MoreList.kt
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

package org.videolan.television.ui.compose.composable.lists

import android.content.Intent
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.resources.BROWSER_TYPE
import org.videolan.resources.HEADER_STREAM
import org.videolan.television.R
import org.videolan.television.ui.AboutActivity
import org.videolan.television.ui.MediaInfoActivity
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.browser.TVActivity
import org.videolan.television.ui.compose.composable.components.ContentLine
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.VLCButton
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.ui.preferences.PreferencesActivity
import org.videolan.television.util.showParent
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.MoreViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.copy
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_COPY
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_INFORMATION
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_RENAME
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions

@Composable
fun MoreScreen(onFocusExit: () -> Unit, onFocusEnter: () -> Unit, viewModel: MoreViewModel? = if (LocalInspectionMode.current) null else hiltViewModel(), mainViewmodel: MainActivityViewModel? = if (LocalInspectionMode.current) null else hiltViewModel()) {
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalActivity.current
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            viewModel?.updateHistory()
            viewModel?.updateStreams()
        }
    }

    val history by viewModel?.history?.observeAsState() ?: remember { mutableStateOf(null) }
    val streams by viewModel?.streamsFlow?.collectAsState() ?: remember { mutableStateOf(null) }
    val historyLoading by viewModel?.historyLoading?.observeAsState() ?: remember { mutableStateOf(false) }
    val streamsLoading by viewModel?.streamsLoading?.observeAsState() ?: remember { mutableStateOf(false) }
    val invalidateEntry by mainViewmodel?.invalidateMediaListEntry?.collectAsState() ?: remember { mutableStateOf(null) }

    val showSnackbar: (String) -> Unit = {
        mainViewmodel?.showSnackbar(SnackbarContent(it))
    }

    mainViewmodel?.addCtxClickListener(MediaListEntry.HISTORY) { item, _, ctxMenuItem ->
        when (ctxMenuItem.id) {
            CTX_PLAY -> MediaUtils.openMedia(activity, (item as MediaWrapper))
            CTX_APPEND -> MediaUtils.appendMedia(activity!!, item.tracks, showSnackbar)
            CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
            CTX_INFORMATION -> MediaInfoActivity.start(activity!!, item.id, item.itemType)
            CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_GO_TO_FOLDER -> (activity as FragmentActivity).showParent((item as MediaWrapper))
            else -> {}
        }
    }
    mainViewmodel?.addCtxClickListener(MediaListEntry.STREAMS) { item, _, ctxMenuItem ->
        when (ctxMenuItem.id) {
            CTX_APPEND -> MediaUtils.appendMedia(activity!!, item.tracks, showSnackbar)
            CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            CTX_RENAME -> RenameDialog.newInstance(item).show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
            CTX_COPY -> {
                activity!!.copy(item.title, (item as MediaWrapper).location)
                mainViewmodel.showSnackbar(SnackbarContent(activity.resources.getString(R.string.url_copied_to_clipboard)))
            }

            CTX_DELETE -> {
                viewModel?.deletingMedia = item as MediaWrapper
                UiTools.snackerWithCancel(activity!!, activity.getString(org.videolan.vlc.R.string.stream_deleted), action = { viewModel?.delete() }) {
                    viewModel?.deletingMedia = null
                    coroutineScope.launch { viewModel?.updateStreams() }
                }
                coroutineScope.launch { viewModel?.updateStreams() }
            }

            else -> {}
        }
    }

    MoreScreenContent(
        history = history,
        streams = streams,
        historyLoading = historyLoading ?: false,
        streamsLoading = streamsLoading ?: false,
        invalidateEntry = invalidateEntry,
        onFocusExit = onFocusExit,
        onRefreshDone = { mainViewmodel?.invalidationDone() },
        onInvalidate = { callback -> viewModel?.invalidate(callback) },
        onItemClick = { item, _ -> TvUtil.openMedia(activity as FragmentActivity, item) },
        onSettingsClick = { activity?.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES) },
        onRefreshClick = { activity?.reloadLibrary() },
        onAboutClick = { activity?.startActivity(Intent(activity, AboutActivity::class.java)) },
        onStreamsTitleClick = {
            val intent = Intent(activity, TVActivity::class.java)
            intent.putExtra(BROWSER_TYPE, HEADER_STREAM)
            activity?.startActivity(intent)
        }
    )
}

@Composable
fun MoreScreenContent(
    history: List<MediaLibraryItem>?,
    streams: List<MediaLibraryItem>?,
    historyLoading: Boolean,
    streamsLoading: Boolean,
    invalidateEntry: MediaListEntry?,
    onFocusExit: () -> Unit,
    onRefreshDone: () -> Unit,
    onInvalidate: (() -> Unit) -> Unit,
    onItemClick: (MediaLibraryItem, Int) -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAboutClick: () -> Unit,
    onStreamsTitleClick: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val isPreview = LocalInspectionMode.current
    val canReadStorage = if (isPreview) true else Permissions.canReadStorage(LocalContext.current)

    Column(
        modifier = Modifier
            .focusProperties {
                onExit = { onFocusExit() }
            }
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp)
            .focusGroup()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .focusProperties {
                    onEnter = { firstItemFocusRequester.requestFocus() }
                }
                .focusGroup()
        ) {
            VLCButton(R.drawable.ic_settings, R.string.preferences, modifier = Modifier.focusRequester(firstItemFocusRequester)) {
                onSettingsClick()
            }
            if (canReadStorage)
                VLCButton(R.drawable.ic_medialibrary_scan, R.string.refresh) {
                    if (isPreview || !Medialibrary.getInstance().isWorking) {
                        onRefreshClick()
                    }
                }
            VLCButton(R.drawable.ic_more_about, R.string.about) {
                onAboutClick()
            }
        }

        if (!history.isNullOrEmpty())
            ContentLine(history, MediaListEntry.HISTORY, historyLoading, R.string.history, onItemClick = { onItemClick(history[it], it) })

        InvalidationComposable(streams) { invalidate ->
            if (invalidateEntry == MediaListEntry.STREAMS) {
                onInvalidate {
                    if (BuildConfig.DEBUG) Log.d("MoreScreenContent", "Stream found: invalidate")
                    invalidate()
                }
                onRefreshDone()
            }
            ContentLine(
                items = streams,
                entry = MediaListEntry.STREAMS,
                historyLoading = streamsLoading,
                text = R.string.streams,
                onItemClick = { index -> streams?.get(index)?.let { onItemClick(it, index) } },
                onClick = onStreamsTitleClick
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MoreScreenPreview() {
    val history = (1..5).map { i ->
        StubMediaWrapper(i.toLong(), "file://history$i.mp4", 0, 0f, 1000, MediaWrapper.TYPE_VIDEO, "History $i", "history$i.mp4", 0, 0, "", "", 0, "", "", 0, 0, "", 0, 0, 0, 0, 0, 0, false, false, 0, true, 0)
    }
    val streams = (1..5).map { i ->
        StubMediaWrapper(i.toLong() + 10, "http://stream$i.com", 0, 0f, 0, MediaWrapper.TYPE_STREAM, "Stream $i", "stream$i", 0, 0, "", "", 0, "", "", 0, 0, "", 0, 0, 0, 0, 0, 0, false, false, 0, true, 0)
    }
    VlcPreview {
        MoreScreenContent(
            history = history,
            streams = streams,
            historyLoading = false,
            streamsLoading = false,
            invalidateEntry = null,
            onFocusExit = {},
            onRefreshDone = {},
            onInvalidate = {},
            onItemClick = { _, _ -> },
            onSettingsClick = {},
            onRefreshClick = {},
            onAboutClick = {},
            onStreamsTitleClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MoreScreenLoadingPreview() {
    VlcPreview {
        MoreScreenContent(
            history = null,
            streams = null,
            historyLoading = true,
            streamsLoading = true,
            invalidateEntry = null,
            onFocusExit = {},
            onRefreshDone = {},
            onInvalidate = {},
            onItemClick = { _, _ -> },
            onSettingsClick = {},
            onRefreshClick = {},
            onAboutClick = {},
            onStreamsTitleClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MoreScreenEmptyPreview() {
    VlcPreview {
        MoreScreenContent(
            history = emptyList(),
            streams = emptyList(),
            historyLoading = false,
            streamsLoading = false,
            invalidateEntry = null,
            onFocusExit = {},
            onRefreshDone = {},
            onInvalidate = {},
            onItemClick = { _, _ -> },
            onSettingsClick = {},
            onRefreshClick = {},
            onAboutClick = {},
            onStreamsTitleClick = {}
        )
    }
}

