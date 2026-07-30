/*
 * ************************************************************************
 *  VideoList.kt
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

import android.app.Application
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.television.R
import org.videolan.television.ui.MediaInfoActivity
import org.videolan.television.ui.compose.VideoDestination
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.MediaGridPlaceholder
import org.videolan.television.ui.compose.composable.components.MediaListPlaceholder
import org.videolan.television.ui.compose.composable.components.MediaListSidePanel
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelContent
import org.videolan.television.ui.compose.composable.components.MediaListSidePanelListenerKey
import org.videolan.television.ui.compose.composable.components.PaginatedGrid
import org.videolan.television.ui.compose.composable.components.PaginatedList
import org.videolan.television.ui.compose.composable.components.VideoItemPlaceholder
import org.videolan.television.ui.compose.composable.components.VlcEmptyViewLoader
import org.videolan.television.ui.compose.composable.items.VideoItem
import org.videolan.television.ui.compose.composable.items.VideoItemList
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.openVideoGroupFolder
import org.videolan.television.util.showParent
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.television.viewmodel.SnackbarContent
import org.videolan.tools.KEY_CASTING_AUDIO_ONLY
import org.videolan.tools.KEY_GROUP_VIDEOS
import org.videolan.tools.KEY_VIDEOS_CARDS
import org.videolan.tools.Settings
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.dialogs.CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
import org.videolan.vlc.gui.dialogs.ConfirmDeleteDialog
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.DefaultPlaybackAction
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.UiTools.addToGroup
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.showPinIfNeeded
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ContextOption.CTX_ADD_GROUP
import org.videolan.vlc.util.ContextOption.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.ContextOption.CTX_APPEND
import org.videolan.vlc.util.ContextOption.CTX_BAN_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_DELETE
import org.videolan.vlc.util.ContextOption.CTX_DOWNLOAD_SUBTITLES
import org.videolan.vlc.util.ContextOption.CTX_FAV_ADD
import org.videolan.vlc.util.ContextOption.CTX_FAV_REMOVE
import org.videolan.vlc.util.ContextOption.CTX_GO_TO_FOLDER
import org.videolan.vlc.util.ContextOption.CTX_GROUP_SIMILAR
import org.videolan.vlc.util.ContextOption.CTX_INFORMATION
import org.videolan.vlc.util.ContextOption.CTX_MARK_ALL_AS_PLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_ALL_AS_UNPLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_PLAYED
import org.videolan.vlc.util.ContextOption.CTX_MARK_AS_UNPLAYED
import org.videolan.vlc.util.ContextOption.CTX_PLAY
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_PLAY_AS_AUDIO
import org.videolan.vlc.util.ContextOption.CTX_PLAY_FROM_START
import org.videolan.vlc.util.ContextOption.CTX_PLAY_NEXT
import org.videolan.vlc.util.ContextOption.CTX_REMOVE_GROUP
import org.videolan.vlc.util.ContextOption.CTX_RENAME_GROUP
import org.videolan.vlc.util.ContextOption.CTX_SHARE
import org.videolan.vlc.util.ContextOption.CTX_UNGROUP
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.share
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType
import org.videolan.vlc.viewmodels.mobile.VideosViewModel

@Composable
fun VideoListScreen(subDestination: VideoDestination, onFocusExit: () -> Unit, onFocusEnter: () -> Unit) {
    when (subDestination) {
        VideoDestination.Videos -> VideoList(onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
        VideoDestination.Playlists -> MediaList(MediaListEntry.VIDEO_PLAYLISTS, -1, onFocusExit = onFocusExit, onFocusEnter = onFocusEnter)
    }
}


@Composable
fun VideoList(modifier: Modifier = Modifier, folder: Folder? = null, group: VideoGroup? = null, onFocusExit: () -> Unit, onFocusEnter: () -> Unit, mainActivityViewModel: MainActivityViewModel? = if (LocalInspectionMode.current) null else hiltViewModel()) {
    val activity = LocalActivity.current
    val settings = Settings.getInstance(activity!!)
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val displaySettingsChange by mainActivityViewModel?.currentDisplaySettingsChange?.collectAsState() ?: remember { mutableStateOf(null) }
    val isWorking by Medialibrary.getState().observeAsState(false)
    val castAsAudio = PlaybackService.renderer.value != null && settings.getBoolean(KEY_CASTING_AUDIO_ONLY, false)
    val coroutineScope = rememberCoroutineScope()
    InvalidationComposable(displaySettingsChange) { invalidate ->
        val context = LocalContext.current

        val extras = MutableCreationExtras().apply {
            set(VideosViewModel.PARENT_GROUP_KEY, group)
            set(VideosViewModel.PARENT_FOLDER_KEY, folder)
            set(APPLICATION_KEY, context.applicationContext as Application)
        }
        val viewModel: VideosViewModel = viewModel(
            factory = VideosViewModel.Factory,
            extras = extras,
        )

        val videos = viewModel.provider.pager.collectAsLazyPagingItems()
        var inCard by rememberSaveable { mutableStateOf(settings.getBoolean(KEY_VIDEOS_CARDS, true)) }

        LaunchedEffect(videos.itemCount, videos.loadState.refresh) {
            if (group != null && videos.loadState.refresh is LoadState.NotLoading && videos.itemCount == 1) {
                activity.finish()
            }
        }

        val entry = when (settings.getString(KEY_GROUP_VIDEOS, VideoGroupingType.NAME.settingsKey)) {
            VideoGroupingType.NAME.settingsKey -> MediaListEntry.VIDEO_GROUPS
            VideoGroupingType.FOLDER.settingsKey -> MediaListEntry.VIDEO_FOLDER
            else -> MediaListEntry.VIDEO
        }

        entry.sorts  = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia, Medialibrary.SORT_INSERTIONDATE).filter {
            viewModel.provider.canSortBy(it)
        }
        entry.currentSort = viewModel.provider.sort
        entry.currentSortDesc = viewModel.provider.desc
        entry.isGroup = group != null

        mainActivityViewModel?.addCtxClickListener(entry) { item, position, ctxMenuItem ->
            if (BuildConfig.DEBUG) Log.d("CtxClickListener", "Ctx clicked: ${ctxMenuItem.id} for $item in list $entry")
            when (ctxMenuItem.id) {
                CTX_PLAY, CTX_PLAY_ALL -> {
                    if (item is MediaWrapper) viewModel.playVideo(activity as FragmentActivity?, item, position, forceAll = ctxMenuItem.id == CTX_PLAY_ALL, forceAudio = castAsAudio)
                    else viewModel.play(position)
                }
                CTX_PLAY_FROM_START -> {
                    if (item is MediaWrapper) viewModel.playVideo(activity as FragmentActivity?, item, position, fromStart = true, forceAudio = castAsAudio)
                }
                CTX_PLAY_AS_AUDIO -> {
                    if (item is MediaWrapper) viewModel.playVideo(activity as FragmentActivity?, item, position, forceAudio = true)
                    else (item as? VideoGroup)?.let { viewModel.play(position) }
                }
                CTX_APPEND -> viewModel.append(item)
                CTX_PLAY_NEXT -> {
                    if (item is MediaWrapper) MediaUtils.insertNext(activity, item) {
                        mainActivityViewModel.showSnackbar(SnackbarContent(it))
                    }
                }
                CTX_DOWNLOAD_SUBTITLES -> {
                    if (item is MediaWrapper) MediaUtils.getSubs((activity as FragmentActivity), item)
                }
                CTX_INFORMATION -> MediaInfoActivity.start(activity, item.id, item.itemType)
                CTX_ADD_TO_PLAYLIST -> {
                    if (item is MediaWrapper) (activity as FragmentActivity).addToPlaylist(arrayOf(item), SavePlaylistDialog.KEY_NEW_TRACKS)
                    else viewModel.addItemToPlaylist(activity as FragmentActivity, position)
                }
                CTX_FAV_ADD, CTX_FAV_REMOVE -> coroutineScope.launch { item.isFavorite = ctxMenuItem.id == CTX_FAV_ADD }
                CTX_DELETE -> { ConfirmDeleteDialog.newInstance(arrayListOf(item)).show((activity as FragmentActivity).supportFragmentManager, ConfirmDeleteDialog::class.simpleName) }
                CTX_SHARE -> {
                    if (item is MediaWrapper) coroutineScope.launch { (activity as AppCompatActivity).share(item) }
                }
                CTX_ADD_GROUP -> {
                    if (item is MediaWrapper) (activity as FragmentActivity).addToGroup(listOf(item), true)
                }
                CTX_REMOVE_GROUP -> {
                    if (item is MediaWrapper) {
                        viewModel.removeFromGroup(item)
                    }
                }
                CTX_GROUP_SIMILAR -> {
                    if (item is MediaWrapper) coroutineScope.launch {
                        if (!(activity as FragmentActivity).showPinIfNeeded()) {
                            viewModel.groupSimilar(item)
                        }
                    }
                }
                CTX_MARK_AS_PLAYED -> coroutineScope.launch { viewModel.markAsPlayed(item) }
                CTX_MARK_AS_UNPLAYED -> coroutineScope.launch { viewModel.markAsUnplayed(item) }
                CTX_MARK_ALL_AS_PLAYED -> coroutineScope.launch { viewModel.markAsPlayed(item) }
                CTX_MARK_ALL_AS_UNPLAYED -> coroutineScope.launch { viewModel.markAsUnplayed(item) }
                CTX_BAN_FOLDER -> {
                    ConfirmDeleteDialog.newInstance(
                        medias = arrayListOf(item),
                        title = activity.resources.getString(R.string.group_ban_folder),
                        description = activity.resources.getString(R.string.ban_folder_explanation, activity.resources.getString(R.string.medialibrary_directories)),
                        buttonText = activity.resources.getString(R.string.ban_folder),
                        resultType = CONFIRM_DELETE_DIALOG_RESULT_BAN_FOLDER
                    ).show((activity as FragmentActivity).supportFragmentManager, ConfirmDeleteDialog::class.simpleName)
                }
                CTX_UNGROUP -> (item as? VideoGroup)?.let { viewModel.ungroup(it) }
                CTX_RENAME_GROUP -> (item as? VideoGroup)?.let { RenameDialog.newInstance(it).show((activity as FragmentActivity).supportFragmentManager, RenameDialog::class.simpleName) }
                CTX_GO_TO_FOLDER -> {
                    if (item is MediaWrapper) (activity as FragmentActivity).showParent(item)
                }
                else -> {
                    throw IllegalStateException("Ctx action not implemented: ${ctxMenuItem.id}")
                }
            }
        }

        val emptyState = if (videos.loadState.refresh == LoadState.Loading || (videos.itemCount == 0 && isWorking && !viewModel.provider.onlyFavorites))
            EmptyLoadingState.LOADING
        else if (videos.itemCount == 0 && !Permissions.canReadStorage(context))
            EmptyLoadingState.MISSING_PERMISSION
        else if (videos.itemCount == 0 && !Permissions.canReadVideos(context))
            EmptyLoadingState.MISSING_VIDEO_PERMISSION
        else if (videos.itemCount == 0 && viewModel.provider.onlyFavorites)
            EmptyLoadingState.EMPTY_FAVORITES
        else if (videos.itemCount == 0)
            EmptyLoadingState.EMPTY
        else
            EmptyLoadingState.NONE
        VlcEmptyViewLoader(emptyState, loadingContent = {
            if (inCard) {
                MediaGridPlaceholder(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(VlcTVTheme.dimens.itemFocusGlowRadius),
                    contentPadding = PaddingValues(top = VlcTVTheme.dimens.itemFocusGlowRadius, bottom = 96.dp, start = VlcTVTheme.dimens.overscanHorizontal, end = VlcTVTheme.dimens.overscanHorizontal),
                ) {
                    VideoItemPlaceholder(inCard = true)
                }
            } else {
                MediaListPlaceholder(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = VlcTVTheme.dimens.itemFocusGlowRadius, start = VlcTVTheme.dimens.overscanHorizontal, end = VlcTVTheme.dimens.overscanHorizontal),
                ) { _, _ ->
                    VideoItemPlaceholder(inCard = false)
                }
            }
        }) {
            val onClick:(MediaLibraryItem, Int) -> Unit = { video, position ->
                if (video is Folder || video is VideoGroup) {
                    activity.openVideoGroupFolder(video)
                } else {
                    if (video !is MediaWrapper) throw IllegalStateException("Wrong video type")
                    if (activity !is AppCompatActivity) throw IllegalStateException("Wrong activity type")
                    if (castAsAudio) {
                        video.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                        PlaylistManager.playingAsAudio = true
                    }
                    when(DefaultPlaybackActionMediaType.VIDEO.getCurrentPlaybackAction(settings)) {
                        DefaultPlaybackAction.PLAY -> viewModel.playVideo(activity, video, position, forceAudio = castAsAudio)
                        DefaultPlaybackAction.ADD_TO_QUEUE -> MediaUtils.appendMedia(activity, video, showSnackbar = {
                            mainActivityViewModel?.showSnackbar(SnackbarContent(it))
                        })
                        DefaultPlaybackAction.INSERT_NEXT -> MediaUtils.insertNext(activity, video, showSnackbar = {
                            mainActivityViewModel?.showSnackbar(SnackbarContent(it))
                        })
                        else  -> viewModel.playVideo(activity, video, position, forceAll = true, forceAudio = castAsAudio)
                    }

                }
            }
            Box(modifier = modifier
                .fillMaxSize()
                .focusProperties {
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Up) onFocusExit()
                    }
                    onEnter = {
                        onFocusEnter()
                    }
                }) {
                val gridFocusRequester = remember { FocusRequester() }
                if (emptyState != EmptyLoadingState.EMPTY_FAVORITES)
                    if (inCard) {
                        PaginatedGrid(
                            items = videos,
                            listState = gridState,
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(40.dp),
                            horizontalArrangement = Arrangement.spacedBy(VlcTVTheme.dimens.itemFocusGlowRadius),
                            contentPadding = PaddingValues(top = VlcTVTheme.dimens.itemFocusGlowRadius, bottom = 96.dp, start = VlcTVTheme.dimens.overscanHorizontal, end = VlcTVTheme.dimens.overscanHorizontal),
                            loaderAspectRatio = 16f / 9,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(gridFocusRequester)
                        ) { video, position, modifier ->
                            VideoItem(video, entry, position, modifier = modifier, onClick = { onClick(video, position) })
                        }
                    } else {
                        PaginatedList(
                            items = videos,
                            listState = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = VlcTVTheme.dimens.itemFocusGlowRadius, start = VlcTVTheme.dimens.overscanHorizontal, end = VlcTVTheme.dimens.overscanHorizontal),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(gridFocusRequester)
                        ) { video, position, modifier ->
                            VideoItemList(video,  position, entry, modifier = modifier, onClick = { onClick(video, position) })
                        }
                    }
                val showTabs by mainActivityViewModel?.showTabs?.collectAsState() ?: remember { mutableStateOf(false) }
                MediaListSidePanel(
                    content = MediaListSidePanelContent(
                        show = !showTabs,
                        showScrollToTop = true,
                        showResumePlayback = true,
                        listState = if (inCard) gridState else listState,
                        entry = entry
                    ),
                    onFocusExit = { gridFocusRequester.requestFocus() }
                ) { first, second ->
                    when (first) {
                        MediaListSidePanelListenerKey.DISPLAY_MODE -> {
                            inCard = second as Boolean
                            settings.edit { putBoolean(KEY_VIDEOS_CARDS, inCard) }
                            invalidate()
                        }
                        MediaListSidePanelListenerKey.RESUME_PLAYBACK -> {
                            MediaUtils.loadlastPlaylist(context, PLAYLIST_TYPE_VIDEO)
                        }
                        MediaListSidePanelListenerKey.DISPLAY_SETTINGS -> {
                            mainActivityViewModel?.openDisplaySettings(second as MediaListEntry)
                        }
                        else -> throw IllegalStateException("Invalid event")
                    }
                }
            }
        }
    }
}
