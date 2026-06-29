/*
 * ************************************************************************
 *  BrowserList.kt
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

package org.videolan.television.ui.compose.composable.lists

import android.app.Application
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.resources.MEDIALIBRARY_PAGE_SIZE
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.BrowserItemCtxFlags
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelContent
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.VlcEmptyViewLoader
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.viewmodel.FileBrowserViewModel
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_BAN_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_DOWNLOAD_SUBTITLES
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_PLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_UNPLAYED
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_PLAY_AS_AUDIO
import org.videolan.vlc.util.ContextOption.CTX_PLAY_FROM_START
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_PLAY_SHUFFLE
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.viewmodels.browser.BrowserModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import java.security.SecureRandom
import java.util.LinkedList
import kotlin.math.min

@Composable
fun BrowserList(modifier: Modifier = Modifier, mainActivityViewModel: MainActivityViewModel? = null, fileBrowserViewModel: FileBrowserViewModel? = null) {
    val mainVM = mainActivityViewModel ?: viewModel()
    val fileVM = fileBrowserViewModel ?: viewModel()

    val context = LocalContext.current
    val root = fileVM.currentPathEntry.collectAsState()
    var isFavorite by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val browserFavRepository = BrowserFavRepository.getInstance(context)
    LaunchedEffect(Unit) {
        isFavorite = (root.value as? MediaWrapper)?.let { browserFavRepository.browserFavExists(it.uri) } == true
    }

    InvalidationComposable(root.value) {
        val entry = MediaListEntry.BROWSER
        val path = when (root.value) {
            is Storage -> (root.value as Storage).uri.toString()
            is MediaWrapper if (root.value as MediaWrapper).type == MediaWrapper.TYPE_DIR -> (root.value as MediaWrapper).uri.toString()
            else -> throw IllegalStateException("Invalid browsing item")
        }
        val browserExtras = MutableCreationExtras().apply {
            set(APPLICATION_KEY, context.applicationContext as Application)
            set(BrowserModel.TYPE_KEY, TYPE_FILE)
            set(BrowserModel.URL_KEY, path)
            set(BrowserModel.SHOW_DUMMY_KEY, false)
        }
        val browserModel: BrowserModel = viewModel(
            factory = BrowserModel.Factory,
            extras = browserExtras,
            key = path
        )
        entry.providerClass = browserModel.provider::class.java
        browserModel.dataset.convertToFlow()
        val items by browserModel.dataset.datasetFlow.collectAsState()
        val descriptionUpdates by browserModel.provider.descriptionUpdate.observeAsState()

        val emptyState =
            if (items.isEmpty() && !Permissions.canReadStorage(context))
                EmptyLoadingState.MISSING_PERMISSION
            else if (items.isEmpty() && !Permissions.canReadAudios(context))
                EmptyLoadingState.MISSING_AUDIO_PERMISSION
            else if (items.isEmpty())
                EmptyLoadingState.EMPTY
            else
                EmptyLoadingState.NONE
        val activity = LocalActivity.current
        val settings = Settings.getInstance(activity!!)
        val onClick: (MediaLibraryItem, Int, String) -> Unit = { item, position, lastFocusedKey ->
            if (item is MediaWrapper && item.type != MediaWrapper.TYPE_DIR) {
                when (DefaultPlaybackActionMediaType.FILE.getCurrentPlaybackAction(settings)) {
                    DefaultPlaybackAction.PLAY -> TvUtil.openMedia(activity as FragmentActivity, item)
                    DefaultPlaybackAction.PLAY_ALL -> MediaUtils.openList(activity, browserModel.dataset.value.mapNotNull { it as? MediaWrapper }, position, false)
                    DefaultPlaybackAction.ADD_TO_QUEUE -> MediaUtils.appendMedia(activity, listOf(*item.tracks), showSnackbar = {
                        mainVM.showSnackbar(SnackbarContent(it))
                    })
                    DefaultPlaybackAction.INSERT_NEXT -> MediaUtils.insertNext(activity, listOf(*item.tracks).toTypedArray(), showSnackbar = {
                        mainVM.showSnackbar(SnackbarContent(it))
                    })
                }
            } else
                fileVM.setCurrentPathEntry(item, lastFocusedKey)
        }

        entry.sorts = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME)
        entry.currentSort = browserModel.provider.sort
        entry.currentSortDesc = browserModel.provider.desc
        entry.isRoot = (root.value as? MediaWrapper)?.uri.toString().isEmpty()
        mainVM.addCtxClickListener(entry) { item, _, ctxMenuItem ->
            if (BuildConfig.DEBUG) Log.d("CtxClickListener", "Ctx clicked: ${ctxMenuItem.id} for $item in list $entry")
            val showSnackbar: (String) -> Unit = {
                mainVM.showSnackbar(SnackbarContent(it))
            }
            when(ctxMenuItem.id) {
                CTX_PLAY -> MediaUtils.openMedia(activity, (item as MediaWrapper))
                CTX_PLAY_FROM_START -> {
                    (item as MediaWrapper).addFlags(MediaWrapper.MEDIA_FROM_START)
                    MediaUtils.openMedia(activity, item)
                }
                CTX_PLAY_ALL -> coroutineScope.launch {
                    var positionInPlaylist = 0
                    val mediaLocations = LinkedList<MediaWrapper>()
//                    scheduler.scheduleAction(MSG_SHOW_ENQUEUING, 1000L)
                    withContext(Dispatchers.IO) {
                        val files = if (browserModel.url?.startsWith("file") == true) browserModel.provider.browseUrl(browserModel.url!!) else browserModel.dataset.getList()
                        for (file in files.filterIsInstance<MediaWrapper>())
                            if (file.type == MediaWrapper.TYPE_VIDEO || file.type == MediaWrapper.TYPE_AUDIO) {
                                mediaLocations.add(browserModel.getMediaWithMeta(activity, file))
                                if (file.equals(item))
                                    positionInPlaylist = mediaLocations.size - 1
                            }
                    }
//                    scheduler.startAction(MSG_HIDE_ENQUEUING)
                    activity.let { MediaUtils.openList(it, mediaLocations, positionInPlaylist, shuffle = PlaylistManager.shuffling.value) }
                }
                CTX_PLAY_SHUFFLE -> MediaUtils.playTracks(activity, item, SecureRandom().nextInt(min(item.tracksCount, MEDIALIBRARY_PAGE_SIZE)), true)
                CTX_PLAY_AS_AUDIO -> coroutineScope.launch(Dispatchers.IO) {
                    item.tracks?.let { trackArray ->
                        MediaUtils.openList(activity, trackArray.map {
                            it.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                            it
                        }.toList(), 0)
                    }
                }
                CTX_DOWNLOAD_SUBTITLES -> MediaUtils.getSubs((activity as FragmentActivity), (item as MediaWrapper))
                CTX_APPEND -> MediaUtils.appendMedia(activity, item.tracks, showSnackbar)
                CTX_PLAY_NEXT -> MediaUtils.insertNext(activity, item.tracks, showSnackbar)
                CTX_ADD_TO_PLAYLIST -> (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                CTX_DELETE -> {
                    val confirmDeleteDialog = ConfirmDeleteDialog.newInstance(arrayListOf(item))
                    confirmDeleteDialog.show((activity as FragmentActivity).supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
                    confirmDeleteDialog.setListener {
                        MediaUtils.deleteItem(activity, item) {
                            browserModel.refresh()
                        }
                    }
                }
                CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch(Dispatchers.IO) {
                    item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD
                    browserModel.refresh()
                }
                CTX_BAN_FOLDER -> {
                    val dialog = ConfirmDeleteDialog.newInstance(
                        medias = arrayListOf(item),
                        title = activity.resources.getString(R.string.group_ban_folder),
                        description = activity.resources.getString(R.string.ban_folder_explanation, activity.resources.getString(R.string.medialibrary_directories)),
                        buttonText = activity.resources.getString(R.string.ban_folder),
                        resultType = CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
                    )
                    dialog.show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName)
                }
                CTX_MARK_AS_PLAYED -> coroutineScope.launch(Dispatchers.IO) {
                    (item as? MediaWrapper)?.let { mw ->
                        mw.playCount = mw.seen + 1L
                        mw.seen += 1L
                        browserModel.refresh()
                    }
                }
                CTX_MARK_AS_UNPLAYED -> coroutineScope.launch(Dispatchers.IO) {
                    (item as? MediaWrapper)?.let { mw ->
                        mw.playCount = 0L
                        mw.seen = 0L
                        browserModel.refresh()
                    }
                }
                else -> mainVM.showSnackbar(SnackbarContent(activity.resources.getString(R.string.not_implemented)))

            }
        }
        val displaySettingsChange by mainVM.currentDisplaySettingsChange.collectAsState()
        InvalidationComposable(displaySettingsChange) { invalidate ->
            BrowserListContent(
                modifier = modifier,
                items = items,
                emptyState = emptyState,
                inCard = entry.displayInCard(context),
                isFavorite = isFavorite,
                entry = entry,
                descriptionUpdates = descriptionUpdates,
                fileBrowserViewModel = fileVM,
                mainActivityViewModel = mainVM,
                currentPath = path,
                onItemRendered = { item ->
                    if (item is MediaWrapper) {
                        if (browserModel.isFolderEmpty(item)) item.addFlags(BrowserItemCtxFlags.isFolderEmpty)
                        if (browserModel.provider.hasMedias(item)) item.addFlags(BrowserItemCtxFlags.hasMedias)
                        if (browserModel.provider.hasSubfolders(item)) item.addFlags(BrowserItemCtxFlags.hasSubfolders)
                    }
                },
                onClick = onClick,
                onSidePanelAction = { first, second ->
                    when (first) {
                        MediaListSidePanelListenerKey.DISPLAY_MODE -> {
                            val inCard = second as Boolean
                            Settings.getInstance(context).edit { putBoolean(entry.inCardsKey, inCard) }
                            invalidate()
                        }

                        MediaListSidePanelListenerKey.RESUME_PLAYBACK -> {
                            throw IllegalStateException("Cannot resume playback for file browser")
                        }

                        MediaListSidePanelListenerKey.CHANGE_FAVORITE -> {
                            (root.value as? MediaWrapper)?.let {
                                coroutineScope.launch {
                                    if (second as Boolean)
                                        browserFavRepository.addLocalFavItem(it.uri, it.title, it.artworkURL)
                                    else
                                        browserFavRepository.deleteBrowserFav(it.uri)
                                    isFavorite = second
                                }
                            }
                        }

                        MediaListSidePanelListenerKey.DISPLAY_SETTINGS -> {
                            mainVM.openDisplaySettings(second as MediaListEntry)
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun BrowserListContent(
    modifier: Modifier = Modifier,
    items: List<MediaLibraryItem>,
    emptyState: EmptyLoadingState,
    inCard: Boolean,
    isFavorite: Boolean,
    entry: MediaListEntry,
    descriptionUpdates: Pair<Int, String>?,
    fileBrowserViewModel: FileBrowserViewModel? = null,
    mainActivityViewModel: MainActivityViewModel? = null,
    currentPath: String,
    onItemRendered: (MediaLibraryItem) -> Unit,
    onClick: (MediaLibraryItem, Int, String) -> Unit,
    onSidePanelAction: (MediaListSidePanelListenerKey, Any) -> Unit
) {
    val initialFocusedItem by fileBrowserViewModel?.focusToRestore?.collectAsState() ?: remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    
    // Key lastFocusedItem to the directory path and INITIALIZE it with the restoration target
    // This ensures that onEnter redirection knows the correct target immediately.
    var lastFocusedItem by rememberSaveable(currentPath, initialFocusedItem) { 
        mutableStateOf(initialFocusedItem) 
    }
    
    // Stability fix: Maintain a persistent map of requesters.
    val focusRequesters = remember { HashMap<String, FocusRequester>() }
    
    // Sync the map with current items immediately during composition
    // We wrap this in remember(items) to satisfy the @RememberInComposition requirement
    // while preserving existing FocusRequesters to maintain focus during metadata updates.
    remember(items) {
        items.forEach { 
            val key = (it as? MediaWrapper)?.uri?.toString() ?: it.id.toString()
            if (!focusRequesters.containsKey(key)) {
                focusRequesters[key] = FocusRequester()
            }
        }
        true
    }

    var initialFocusRequested by remember(items, currentPath) { mutableStateOf(false) }

    var currentInCard by rememberSaveable { mutableStateOf(inCard) }

    LaunchedEffect(items, currentInCard, initialFocusedItem, currentPath) {
        if (items.isNotEmpty() && !initialFocusRequested) {
            // Wait for the Lazy container to actually compose and attach at least one child
            if (currentInCard) {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo }.filter { it.isNotEmpty() }.first()
            } else {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }.filter { it.isNotEmpty() }.first()
            }
            
            val itemKeys = items.map { (it as? MediaWrapper)?.uri?.toString() ?: it.id.toString() }
            val targetKey = if (lastFocusedItem.isNotEmpty() && itemKeys.contains(lastFocusedItem)) lastFocusedItem else itemKeys.firstOrNull() ?: ""
            
            if (focusRequesters.containsKey(targetKey)) {
                focusRequesters[targetKey]?.requestFocus()
                initialFocusRequested = true
            }
        }
    }

    VlcEmptyViewLoader(emptyState) {
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            val gridFocusRequester = remember { FocusRequester() }
            if (currentInCard) {
                LazyVerticalGrid(
                    GridCells.Fixed(6), Modifier
                        .fillMaxSize()
                        .graphicsLayer(clip = false)
                        .focusProperties {
                            onEnter = {
                                val firstKey = items.firstOrNull()?.let { (it as? MediaWrapper)?.uri?.toString() ?: it.id.toString() } ?: ""
                                val targetKey = if (lastFocusedItem.isNotEmpty()) lastFocusedItem else firstKey
                                Log.d("BrowserFocus", "Grid onEnter. lastFocusedItem: $lastFocusedItem, targeting: $targetKey")
                                focusRequesters[targetKey]?.requestFocus()
                            }
                        }
                        .focusRequester(gridFocusRequester), 
                    gridState, PaddingValues(top = 16.dp, bottom = 96.dp, start = 56.dp, end = 56.dp), verticalArrangement = Arrangement.spacedBy(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(count = items.size, key = { index -> (items[index] as? MediaWrapper)?.uri?.toString() ?: items[index].id.toString() }) { index ->
                        items[index].let { item ->
                            val itemKey = (item as? MediaWrapper)?.uri?.toString() ?: item.id.toString()
                            // Access descriptionUpdates here to trigger recomposition without disposing the node
                            val currentUpdate = if (descriptionUpdates?.first == index) descriptionUpdates.second else null
                            
                            androidx.compose.runtime.key(itemKey) {
                                onItemRendered(item)
                                val requester = focusRequesters[itemKey] ?: FocusRequester.Default
                                AudioItemCard(
                                    item, index, entry, Modifier
                                        .focusRequester(requester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                lastFocusedItem = itemKey
                                            } else if (lastFocusedItem == itemKey) {
                                                Log.d("BrowserFocus", "Item focus LOST: $itemKey. hasFocus: ${it.hasFocus}, isFocused: ${it.isFocused}")
                                            }
                                        }, 
                                    spannableDescription = true, 
                                    // Use the update if available, otherwise fallback to item description
                                    description = currentUpdate ?: item.description,
                                    onClick = { onClick(item, index, lastFocusedItem) }
                                )
                            }
                        }

                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(clip = false)
                        .focusProperties {
                            onEnter = {
                                val firstKey = items.firstOrNull()?.let { (it as? MediaWrapper)?.uri?.toString() ?: it.id.toString() } ?: ""
                                val targetKey = if (lastFocusedItem.isNotEmpty()) lastFocusedItem else firstKey
                                focusRequesters[targetKey]?.requestFocus()
                            }
                        }
                        .focusRequester(gridFocusRequester),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp, start = 56.dp, end = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    state = listState
                ) {
                    items(count = items.size, key = { index -> (items[index] as? MediaWrapper)?.uri?.toString() ?: items[index].id.toString() }) { index ->
                        items[index].let { item ->
                            val itemKey = (item as? MediaWrapper)?.uri?.toString() ?: item.id.toString()
                            // Access descriptionUpdates here to trigger recomposition without disposing the node
                            val currentUpdate = if (descriptionUpdates?.first == index) descriptionUpdates.second else null

                            androidx.compose.runtime.key(itemKey) {
                                onItemRendered(item)
                                val requester = focusRequesters[itemKey] ?: FocusRequester.Default
                                AudioItemList(
                                    item = item,
                                    position = index,
                                    entry = entry,
                                    modifier = Modifier
                                        .focusRequester(requester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                lastFocusedItem = itemKey
                                            } else if (lastFocusedItem == itemKey) {
                                                Log.d("BrowserFocus", "Item focus LOST: $itemKey. hasFocus: ${it.hasFocus}, isFocused: ${it.isFocused}")
                                            }
                                        },
                                    isFirst = index == 0,
                                    isLast = index == items.size - 1,
                                    spannableDescription = true,
                                    description = currentUpdate ?: item.description,
                                    onClick = { onClick(item, index, lastFocusedItem) })
                            }
                        }

                    }
                }
            }
            InvalidationComposable(isFavorite) {
                val showTabs by mainActivityViewModel?.showTabs?.collectAsState() ?: remember { mutableStateOf(false) }
                MediaListSidePanel(
                    content = MediaListSidePanelContent(
                        show = !showTabs,
                        showScrollToTop = true,
                        showResumePlayback = false,
                        isFavorite = isFavorite,
                        listState = if (currentInCard) gridState else listState,
                        entry = entry
                    ),
                    onFocusExit = { gridFocusRequester.requestFocus() }
                ) { first, second ->
                    if (first == MediaListSidePanelListenerKey.DISPLAY_MODE) {
                        currentInCard = second as Boolean
                    }
                    onSidePanelAction(first, second)
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun BrowserListPreview() {
    val items = (1..10).map {
        StubMediaWrapper(
            it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L,
            MediaWrapper.TYPE_AUDIO,
            "Track $it", "track$it.mp3", 1L, 1L, "Sample Artist", "Genre",
            1L, "Sample Album", "Sample Artist", 0, 0, "", 0, 0, it, 1,
            0L, 0L, false, false, 2024, true, 0L
        )
    }

    VlcTVTheme {
        BrowserListContent(
            items = items,
            emptyState = EmptyLoadingState.NONE,
            inCard = false,
            isFavorite = false,
            entry = MediaListEntry.BROWSER,
            descriptionUpdates = null,
            fileBrowserViewModel = null,
            mainActivityViewModel = null,
            currentPath = "/",
            onItemRendered = {},
            onClick = { _, _, _ -> },
            onSidePanelAction = { _, _ -> }
        )
    }
}

@Preview(device = "id:tv_1080p", showBackground = true, backgroundColor = 0xFF34434e)
@Composable
private fun BrowserListCardPreview() {
    val items = (1..10).map {
        StubMediaWrapper(
            it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L,
            MediaWrapper.TYPE_AUDIO,
            "Track $it", "track$it.mp3", 1L, 1L, "Sample Artist", "Genre",
            1L, "Sample Album", "Sample Artist", 0, 0, "", 0, 0, it, 1,
            0L, 0L, false, false, 2024, true, 0L
        )
    }

    VlcTVTheme {
        BrowserListContent(
            items = items,
            emptyState = EmptyLoadingState.NONE,
            inCard = true,
            isFavorite = false,
            entry = MediaListEntry.BROWSER,
            descriptionUpdates = null,
            fileBrowserViewModel = null,
            mainActivityViewModel = null,
            currentPath = "/",
            onItemRendered = {},
            onClick = { _, _, _ -> },
            onSidePanelAction = { _, _ -> }
        )
    }
}
