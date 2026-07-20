/*
 * ************************************************************************
 *  AudioCategoryScreen.kt
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

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.stubs.StubAlbum
import org.videolan.medialibrary.stubs.StubArtist
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.medialibrary.stubs.StubPlaylist
import org.videolan.resources.util.getYear
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.BackgroundColorDark
import org.videolan.television.ui.compose.theme.Grey900Transparent
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent25
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.theme.WhiteTransparent90
import org.videolan.television.ui.compose.utils.fadingMarquee
import org.videolan.television.ui.dialogs.ConfirmationTvActivity
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.viewmodels.mobile.AlbumSongsViewModel
import java.util.UUID
import org.videolan.resources.R as ResourcesR

@Serializable
sealed class CategoryDestination(@param:StringRes val titleRes: Int) : NavKey {
    @Serializable
    data object Albums : CategoryDestination(R.string.albums)

    @Serializable
    data object Songs : CategoryDestination(R.string.songs)
}

@Composable
fun AudioCategoryScreen(
    item: MediaLibraryItem,
    viewModel: AlbumSongsViewModel = viewModel(factory = AlbumSongsViewModel.Factory(LocalContext.current, item)),
    mainActivityViewModel: MainActivityViewModel? = if (LocalInspectionMode.current) null else hiltViewModel()
) {
    val albums = viewModel.albumsProvider.pager.collectAsLazyPagingItems()
    val tracksPaged = viewModel.tracksProvider.pagedList.observeAsState()
    val trackList = remember { mutableStateListOf<MediaWrapper>() }
    
    val initialDestination = if (item is Artist || item is Genre) CategoryDestination.Albums else CategoryDestination.Songs
    val backStack = rememberNavBackStack(initialDestination)
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    var blurredCover by remember { mutableStateOf<Bitmap?>(null) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var darkMutedColor by remember { mutableStateOf<Color?>(null) }

    val playFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }
    val removeFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val moveUpFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val moveDownFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()
    var listHeight by remember { mutableIntStateOf(0) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ConfirmationTvActivity.ACTION_ID_POSITIVE) {
            MediaUtils.deletePlaylist(item as Playlist)
            activity?.finish()
        }
    }

    // Tracks synchronization for Playlists (to support stable IDs and animations)
    tracksPaged.value?.let { pagedList ->
        val snapshot = pagedList.snapshot()
        if (trackList.isEmpty() && snapshot.isNotEmpty()) {
            snapshot.forEach { if (it.tag == null) it.tag = UUID.randomUUID().toString() }
            trackList.addAll(snapshot)
        }
    }

    LaunchedEffect(tracksPaged.value) {
        val snapshot = tracksPaged.value?.snapshot() ?: emptyList()
        if (snapshot.isEmpty()) {
            trackList.clear()
            return@LaunchedEffect
        }

        val tagMap = trackList.asSequence()
            .filter { it.tag != null }
            .groupBy { it.id }
            .mapValues { entry -> entry.value.asSequence().map { it.tag!! }.toMutableList() }

        snapshot.forEach { newTrack ->
            val tags = tagMap[newTrack.id]
            newTrack.tag = if (!tags.isNullOrEmpty()) {
                tags.removeAt(0)
            } else {
                UUID.randomUUID().toString()
            }
        }

        if (trackList.size != snapshot.size || trackList.indices.any { i -> trackList[i].id != snapshot[i].id || trackList[i].tag != snapshot[i].tag }) {
            trackList.clear()
            trackList.addAll(snapshot)
        }
    }

    LaunchedEffect(item) {
        val bitmap = when (item) {
            is Playlist -> ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${item.id}_500", item.tracks.toList(), 500)
            is Genre -> ThumbnailsProvider.getPlaylistOrGenreImage("genre:${item.id}_500", item.tracks.toList(), 500)
            else -> item.artworkMrl?.let { mrl -> AudioUtil.readCoverBitmap(Uri.decode(mrl), 500) }
        }
        coverBitmap = bitmap
        bitmap?.let {
            blurredCover = UiTools.blurBitmap(it, 15f)
            Palette.from(it).generate().let { palette ->
                darkMutedColor = palette.darkMutedSwatch?.rgb?.let { rgb -> Color(rgb) }?.copy(alpha = 0.8f)
                    ?: palette.darkVibrantSwatch?.rgb?.let { rgb -> Color(rgb) }?.copy(alpha = 0.8f)
            }
        }
    }

    AudioCategoryScreenContent(
        item = item,
        albums = albums,
        trackList = trackList,
        backStack = backStack,
        mainActivityViewModel = mainActivityViewModel,
        coverBitmap = coverBitmap,
        blurredCover = blurredCover,
        darkMutedColor = darkMutedColor,
        playFocusRequester = playFocusRequester,
        listFocusRequester = listFocusRequester,
        removeFocusRequesters = removeFocusRequesters,
        moveUpFocusRequesters = moveUpFocusRequesters,
        moveDownFocusRequesters = moveDownFocusRequesters,
        listState = listState,
        onListHeightChanged = { listHeight = it },
        onAlbumClick = { album -> TvUtil.openAudioCategory(context as FragmentActivity, album) },
        onSongClick = { index ->
            if (item is Playlist || item is Album) {
                TvUtil.openMedia(context as FragmentActivity, trackList[index])
            } else {
                MediaUtils.playAll(context as FragmentActivity, viewModel.tracksProvider, index, shuffle = false)
            }
        },
        onPlay = { MediaUtils.playTracks(context, item, 0, shuffle = false) },
        onDelete = {
            if (item is Playlist) {
                val intent = Intent(context, ConfirmationTvActivity::class.java).apply {
                    putExtra(ConfirmationTvActivity.CONFIRMATION_DIALOG_TITLE, activity?.getString(ResourcesR.string.validation_delete_playlist))
                    putExtra(ConfirmationTvActivity.CONFIRMATION_DIALOG_TEXT, activity?.getString(ResourcesR.string.validation_delete_playlist_text))
                }
                deleteLauncher.launch(intent)
            }
        },
        onInsertNext = { MediaUtils.insertNext(context, item.tracks) },
        onAppend = { MediaUtils.appendMedia(context, item.tracks.toList()) },
        onAddToPlaylist = { (activity as FragmentActivity).addToPlaylist(item.tracks, SavePlaylistDialog.KEY_NEW_TRACKS) },
        onMoveUp = { index, track ->
            val tag = track.tag!!
            Snapshot.withMutableSnapshot {
                val moved = trackList.removeAt(index)
                trackList.add(index - 1, moved)
            }
            if (index - 1 == 0) moveDownFocusRequesters[tag]?.requestFocus() else moveUpFocusRequesters[tag]?.requestFocus()
            scope.launch { listState.requestScrollToItem(index - 1, -(listHeight / 2 - with(density) { 36.dp.toPx() }.toInt())) }
            scope.launch(Dispatchers.IO) {
                (item as Playlist).move(index, index - 1)
                viewModel.refresh()
            }
        },
        onMoveDown = { index, track ->
            val tag = track.tag!!
            Snapshot.withMutableSnapshot {
                val moved = trackList.removeAt(index)
                trackList.add(index + 1, moved)
            }
            if (index + 1 == trackList.size - 1) moveUpFocusRequesters[tag]?.requestFocus() else moveDownFocusRequesters[tag]?.requestFocus()
            scope.launch { listState.requestScrollToItem(index + 1, -(listHeight / 2 - with(density) { 36.dp.toPx() }.toInt())) }
            scope.launch(Dispatchers.IO) {
                (item as Playlist).move(index, index + 1)
                viewModel.refresh()
            }
        },
        onRemove = { index, _ ->
            val nextFocusIndex = if ((index + 1) < trackList.size) index + 1 else if (index > 0) index - 1 else -1
            if (nextFocusIndex != -1) {
                val nextTrack = trackList[nextFocusIndex]
                nextTrack.tag?.let { removeFocusRequesters[it]?.requestFocus() }
            }
            trackList.removeAt(index)
            scope.launch(Dispatchers.IO) {
                (item as Playlist).remove(index)
                viewModel.refresh()
            }
        }
    )
}

@Composable
fun AudioCategoryScreenContent(
    item: MediaLibraryItem,
    albums: LazyPagingItems<Album>,
    trackList: List<MediaWrapper>,
    backStack: NavBackStack<NavKey>,
    mainActivityViewModel: MainActivityViewModel? = null,
    coverBitmap: Bitmap? = null,
    blurredCover: Bitmap? = null,
    darkMutedColor: Color? = null,
    playFocusRequester: FocusRequester,
    listFocusRequester: FocusRequester,
    removeFocusRequesters: Map<String, FocusRequester>,
    moveUpFocusRequesters: Map<String, FocusRequester>,
    moveDownFocusRequesters: Map<String, FocusRequester>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onListHeightChanged: (Int) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (Int) -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onInsertNext: () -> Unit,
    onAppend: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMoveUp: (Int, MediaWrapper) -> Unit,
    onMoveDown: (Int, MediaWrapper) -> Unit,
    onRemove: (Int, MediaWrapper) -> Unit
) {
    val tabsFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(BackgroundColorDark)) {
        blurredCover?.let {
            Image(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Grey900Transparent, BlendMode.SrcAtop)
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BackgroundColorDark.copy(alpha = 0.8f), Transparent, BackgroundColorDark.copy(alpha = 0.9f)))))
        }

        Row(modifier = Modifier.fillMaxSize()) {
            AudioPlayer(requestFocus = false)
            Column(modifier = Modifier.weight(1f).padding(top = 32.dp).padding(horizontal = 56.dp)) {
                // Header
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AudioCategoryHeaderArt(item, modifier = Modifier.size(160.dp), bitmap = coverBitmap)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        val textShadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(2f, 2f), blurRadius = 4f)
                        Text(text = item.title ?: "", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, shadow = textShadow), color = White, maxLines = 1, modifier = Modifier.fadingMarquee())
                        
                        val subtitle = when (item) {
                            is Album -> item.albumArtist ?: stringResource(R.string.unknown_artist)
                            is Playlist -> stringResource(R.string.track_number, item.tracksCount)
                            is Artist -> {
                                val list = arrayListOf<String>()
                                if (item.albumsCount > 0) list.add(pluralStringResource(org.videolan.vlc.R.plurals.albums_quantity, item.albumsCount, item.albumsCount))
                                if (item.tracksCount > 0) list.add(pluralStringResource(org.videolan.vlc.R.plurals.track_quantity, item.tracksCount, item.tracksCount))
                                TextUtils.separatedString(list.toTypedArray())
                            }
                            is Genre -> {
                                val list = arrayListOf<String>()
                                if (item.albumsCount > 0) list.add(pluralStringResource(org.videolan.vlc.R.plurals.albums_quantity, item.albumsCount, item.albumsCount))
                                if (item.tracksCount > 0) list.add(pluralStringResource(org.videolan.vlc.R.plurals.track_quantity, item.tracksCount, item.tracksCount))
                                TextUtils.separatedString(list.toTypedArray())
                            }
                            else -> ""
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(text = subtitle, style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow), color = WhiteTransparent90, maxLines = 1, modifier = Modifier.fadingMarquee())
                        }
                        
                        val duration = when (item) {
                            is Album -> item.duration
                            is Playlist -> item.tracks.sumOf { it.length }
                            else -> 0L
                        }
                        if (duration > 0) {
                            Text(text = Tools.millisToString(duration), style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow), color = WhiteTransparent90)
                        }
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    // Actions
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(darkMutedColor ?: WhiteTransparent10).padding(4.dp).focusGroup().focusProperties { down = if (item is Artist || item is Genre) tabsFocusRequester else listFocusRequester },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledIconButton(label = stringResource(R.string.play), painterResource = painterResource(R.drawable.ic_play_tv), modifier = Modifier.focusRequester(playFocusRequester), focusedBackgroundColor = WhiteTransparent25, tint = White, onClick = onPlay)
                        if (item is Playlist) LabeledIconButton(label = stringResource(R.string.delete), painterResource = painterResource(R.drawable.ic_tv_list_delete), focusedBackgroundColor = WhiteTransparent25, tint = White, onClick = onDelete)
                        LabeledIconButton(label = stringResource(R.string.insert_next), painterResource = painterResource(R.drawable.ic_tv_list_append), focusedBackgroundColor = WhiteTransparent25, tint = White, onClick = onInsertNext)
                        LabeledIconButton(label = stringResource(R.string.append), painterResource = painterResource(R.drawable.ic_tv_list_playnext), focusedBackgroundColor = WhiteTransparent25, tint = White, onClick = onAppend)
                        LabeledIconButton(label = stringResource(R.string.add_to_playlist), painterResource = painterResource(R.drawable.ic_addtoplaylist), focusedBackgroundColor = WhiteTransparent25, tint = White, onClick = onAddToPlaylist)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Content
                if (item is Artist || item is Genre) {
                    val tabs = listOf(CategoryDestination.Albums, CategoryDestination.Songs)
                    VLCTabRow(
                        selectedTabIndex = tabs.indexOf(backStack.lastOrNull()),
                        onSelected = { index -> backStack.clear(); backStack.add(tabs[index]) },
                        modifier = Modifier.focusRequester(tabsFocusRequester),
                        tabNumber = tabs.size,
                        indicator = { hasFocus -> Box(Modifier.fillMaxSize().background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))) },
                        key = "category_tabs",
                        containerColor = darkMutedColor ?: WhiteTransparent10,
                        mainActivityViewModel = mainActivityViewModel,
                        getTab = { index, focused ->
                            val tab = tabs[index]
                            val animatedColor by animateColorAsState(targetValue = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.6F), label = "color")
                            Text(text = stringResource(tab.titleRes).uppercase(), style = MaterialTheme.typography.labelLarge, color = animatedColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NavDisplay(backStack = backStack, modifier = Modifier.weight(1f)) { destination ->
                        NavEntry(destination) { destinationKey ->
                            CategoryContent(destinationKey as CategoryDestination, albums, trackList, item, darkMutedColor, onAlbumClick, onSongClick, onMoveUp, onMoveDown, onRemove, listState, onListHeightChanged, listFocusRequester, playFocusRequester, removeFocusRequesters, moveUpFocusRequesters, moveDownFocusRequesters)
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f)) {
                        CategorySongs(trackList, item, darkMutedColor, onSongClick, onMoveUp, onMoveDown, onRemove, listState, onListHeightChanged, listFocusRequester, playFocusRequester, removeFocusRequesters, moveUpFocusRequesters, moveDownFocusRequesters)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioCategoryHeaderArt(item: MediaLibraryItem, modifier: Modifier = Modifier, bitmap: Bitmap? = null) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    LaunchedEffect(item, bitmap) {
        if (bitmap != null) mapBitmap.value = bitmap
        else if (item is Playlist) mapBitmap.value = ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${item.id}_300", item.tracks.toList(), 300)
        else if (item is Genre) mapBitmap.value = ThumbnailsProvider.getPlaylistOrGenreImage("genre:${item.id}_300", item.tracks.toList(), 300)
        else item.artworkMrl?.let { mrl -> mapBitmap.value = AudioUtil.readCoverBitmap(Uri.decode(mrl), 300) }
    }

    val isRound = item is Artist || item is Genre
    val shape = if (isRound) CircleShape else RoundedCornerShape(8.dp)
    
    Box(modifier = modifier.border(4.dp, WhiteTransparent10, shape)) {
        Box(modifier = Modifier.fillMaxSize().padding(4.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            mapBitmap.value?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } ?: run {
                val iconRes = when (item) {
                    is Artist -> ResourcesR.drawable.ic_artist_big
                    is Genre -> R.drawable.ic_genre_big
                    is Playlist -> R.drawable.ic_playlist_big
                    else -> R.drawable.ic_album_big
                }
                Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.padding(24.dp).fillMaxSize(), colorFilter = ColorFilter.tint(White))
            }
        }
    }
}

@Composable
private fun CategoryContent(
    destination: CategoryDestination,
    albums: LazyPagingItems<Album>,
    songs: List<MediaWrapper>,
    parentItem: MediaLibraryItem,
    contentColor: Color? = null,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (Int) -> Unit,
    onMoveUp: (Int, MediaWrapper) -> Unit,
    onMoveDown: (Int, MediaWrapper) -> Unit,
    onRemove: (Int, MediaWrapper) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onListHeightChanged: (Int) -> Unit,
    listFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    removeFocusRequesters: Map<String, FocusRequester>,
    moveUpFocusRequesters: Map<String, FocusRequester>,
    moveDownFocusRequesters: Map<String, FocusRequester>
) {
    when (destination) {
        CategoryDestination.Albums -> CategoryAlbums(albums, onAlbumClick)
        CategoryDestination.Songs -> CategorySongs(songs, parentItem, contentColor, onSongClick, onMoveUp, onMoveDown, onRemove, listState, onListHeightChanged, listFocusRequester, playFocusRequester, removeFocusRequesters, moveUpFocusRequesters, moveDownFocusRequesters)
    }
}

@Composable
private fun CategoryAlbums(albums: LazyPagingItems<Album>, onAlbumClick: (Album) -> Unit) {
    val gridState = rememberLazyGridState()
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(count = albums.itemCount) { i ->
            val album = albums[i]
            if (album != null) {
                AudioItemCard(item = album, position = i, entry = MediaListEntry.ALBUMS, topStartContent = {
                    val year = album.getYear()
                    if (year != "0" && year != "-" && year.isNotEmpty()) {
                        Text(text = year, modifier = Modifier.padding(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }) { onAlbumClick(album) }
            }
        }
    }
}

@Composable
private fun CategorySongs(
    songs: List<MediaWrapper>,
    parentItem: MediaLibraryItem,
    contentColor: Color? = null,
    onSongClick: (Int) -> Unit,
    onMoveUp: (Int, MediaWrapper) -> Unit,
    onMoveDown: (Int, MediaWrapper) -> Unit,
    onRemove: (Int, MediaWrapper) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onListHeightChanged: (Int) -> Unit,
    listFocusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    removeFocusRequesters: Map<String, FocusRequester>,
    moveUpFocusRequesters: Map<String, FocusRequester>,
    moveDownFocusRequesters: Map<String, FocusRequester>
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { onListHeightChanged(it.size.height) }.focusGroup().focusRequester(listFocusRequester).focusProperties { up = playFocusRequester },
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp, start = 56.dp, end = 56.dp)
    ) {
        itemsIndexed(songs, key = { _, track -> track.tag ?: track.hashCode().toString() }) { index, song ->
            val tag = song.tag ?: song.hashCode().toString()
            val removeFocusRequester = removeFocusRequesters[tag] ?: remember(tag) { FocusRequester().also { (removeFocusRequesters as MutableMap)[tag] = it } }
            val moveUpFocusRequester = moveUpFocusRequesters[tag] ?: remember(tag) { FocusRequester().also { (moveUpFocusRequesters as MutableMap)[tag] = it } }
            val moveDownFocusRequester = moveDownFocusRequesters[tag] ?: remember(tag) { FocusRequester().also { (moveDownFocusRequesters as MutableMap)[tag] = it } }

            val currentAlbumName = song.album?.title ?: stringResource(org.videolan.vlc.R.string.unknown_album)
            val previousAlbumName = if (index > 0) songs[index - 1].album?.title ?: stringResource(org.videolan.vlc.R.string.unknown_album) else null
            val nextAlbumName = if (index < (songs.size - 1)) songs[index + 1].album?.title ?: stringResource(org.videolan.vlc.R.string.unknown_album) else null
            val isNewAlbum = (parentItem is Artist || parentItem is Genre) && currentAlbumName != previousAlbumName
            val isLastOfAlbum = (parentItem is Artist || parentItem is Genre) && currentAlbumName != nextAlbumName

            Column {
                if (isNewAlbum) {
                    Text(text = currentAlbumName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp))
                }
                AudioItemList(
                    item = song,
                    position = index,
                    entry = MediaListEntry.TRACKS,
                    modifier = Modifier.animateItem().fillMaxWidth(),
                    isFirst = if (parentItem is Artist || parentItem is Genre) isNewAlbum else index == 0,
                    isLast = if (parentItem is Artist || parentItem is Genre) isLastOfAlbum else index == songs.size - 1,
                    containerColor = contentColor ?: MaterialTheme.colorScheme.surface,
                    actionContent = {
                        if (parentItem is Playlist) {
                            if (index > 0) LabeledIconButton(label = stringResource(R.string.move_up), painterResource = painterResource(R.drawable.ic_playlist_moveup), modifier = Modifier.focusRequester(moveUpFocusRequester), tint = White, focusedBackgroundColor = WhiteTransparent25, focusHeight = 72.dp) { onMoveUp(index, song) }
                            else Spacer(modifier = Modifier.width(48.dp).height(72.dp))
                            if (index < songs.size - 1) LabeledIconButton(label = stringResource(R.string.move_down), painterResource = painterResource(R.drawable.ic_playlist_movedown), modifier = Modifier.focusRequester(moveDownFocusRequester), tint = White, focusedBackgroundColor = WhiteTransparent25, focusHeight = 72.dp) { onMoveDown(index, song) }
                            else Spacer(modifier = Modifier.width(48.dp).height(72.dp))
                            LabeledIconButton(label = stringResource(R.string.remove), painterResource = painterResource(R.drawable.ic_remove_from_playlist), modifier = Modifier.focusRequester(removeFocusRequester), tint = White, focusedBackgroundColor = WhiteTransparent25, focusHeight = 72.dp) { onRemove(index, song) }
                        }
                        LabeledIconButton(label = stringResource(R.string.insert_next), painterResource = painterResource(R.drawable.ic_tv_list_append), tint = White, focusedBackgroundColor = WhiteTransparent25, focusHeight = 72.dp) { MediaUtils.insertNext(context, song) }
                        LabeledIconButton(label = stringResource(R.string.append), painterResource = painterResource(R.drawable.ic_tv_list_playnext), tint = White, focusedBackgroundColor = WhiteTransparent25, focusHeight = 72.dp) { MediaUtils.appendMedia(context, song) }
                        LabeledIconButton(label = stringResource(R.string.add_to_playlist), painterResource = painterResource(R.drawable.ic_addtoplaylist), tint = White, focusedBackgroundColor = WhiteTransparent25, focusHeight = 72.dp) { (activity as FragmentActivity).addToPlaylist(arrayOf(song), SavePlaylistDialog.KEY_NEW_TRACKS) }
                    }
                ) { onSongClick(index) }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ArtistCategoryPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().context = context

    val artist = StubArtist(1L, "Sample Artist", "Bio", "", "", 3, 42, 42, false)
    val albumsList = (1..5).map { StubAlbum(it.toLong(), "Album $it", 2020 + it, "", "Sample Artist", 1L, 10, 10, 3600000L, false) as Album }
    val albums = flowOf(PagingData.from(albumsList)).collectAsLazyPagingItems()
    val tracks = (1..10).map { StubMediaWrapper(it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L, MediaWrapper.TYPE_AUDIO, "Track $it", "track$it.mp3", 1L, 1L, "Sample Artist", "Genre", 1L, "Album 1", "Sample Artist", 0, 0, "", 0, 0, it, 1, 0L, 0L, false, false, 2024, true, 0L) as MediaWrapper }

    VlcTVTheme {
        AudioCategoryScreenContent(
            item = artist,
            albums = albums,
            trackList = tracks,
            backStack = rememberNavBackStack(CategoryDestination.Albums),
            playFocusRequester = remember { FocusRequester() },
            listFocusRequester = remember { FocusRequester() },
            removeFocusRequesters = remember { mutableMapOf() },
            moveUpFocusRequesters = remember { mutableMapOf() },
            moveDownFocusRequesters = remember { mutableMapOf() },
            listState = rememberLazyListState(),
            onListHeightChanged = {},
            onAlbumClick = {},
            onSongClick = {},
            onPlay = {},
            onDelete = {},
            onInsertNext = {},
            onAppend = {},
            onAddToPlaylist = {},
            onMoveUp = { _, _ -> },
            onMoveDown = { _, _ -> },
            onRemove = { _, _ -> }
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AlbumCategoryPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().context = context

    val album = StubAlbum(1L, "Sample Album", 2024, "", "Sample Artist", 1L, 10, 10, 3600000L, false)
    val albums = flowOf(PagingData.from(emptyList<Album>())).collectAsLazyPagingItems()
    val tracks = (1..10).map { StubMediaWrapper(it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L, MediaWrapper.TYPE_AUDIO, "Track $it", "track$it.mp3", 1L, 1L, "Sample Artist", "Genre", 1L, "Sample Album", "Sample Artist", 0, 0, "", 0, 0, it, 1, 0L, 0L, false, false, 2024, true, 0L) as MediaWrapper }

    VlcTVTheme {
        AudioCategoryScreenContent(
            item = album,
            albums = albums,
            trackList = tracks,
            backStack = rememberNavBackStack(CategoryDestination.Songs),
            playFocusRequester = remember { FocusRequester() },
            listFocusRequester = remember { FocusRequester() },
            removeFocusRequesters = remember { mutableMapOf() },
            moveUpFocusRequesters = remember { mutableMapOf() },
            moveDownFocusRequesters = remember { mutableMapOf() },
            listState = rememberLazyListState(),
            onListHeightChanged = {},
            onAlbumClick = {},
            onSongClick = {},
            onPlay = {},
            onDelete = {},
            onInsertNext = {},
            onAppend = {},
            onAddToPlaylist = {},
            onMoveUp = { _, _ -> },
            onMoveDown = { _, _ -> },
            onRemove = { _, _ -> }
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun GenreCategoryPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().context = context

    val genre = org.videolan.medialibrary.stubs.StubGenre(1L, "Rock", 50, 50, false)
    val albumsList = (1..5).map { StubAlbum(it.toLong(), "Album $it", 2020 + it, "", "Various", 1L, 10, 10, 3600000L, false) as Album }
    val albums = flowOf(PagingData.from(albumsList)).collectAsLazyPagingItems()
    val tracks = (1..10).map { StubMediaWrapper(it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L, MediaWrapper.TYPE_AUDIO, "Track $it", "track$it.mp3", 1L, 1L, "Various", "Rock", 1L, "Album 1", "Various", 0, 0, "", 0, 0, it, 1, 0L, 0L, false, false, 2024, true, 0L) as MediaWrapper }

    VlcTVTheme {
        AudioCategoryScreenContent(
            item = genre,
            albums = albums,
            trackList = tracks,
            backStack = rememberNavBackStack(CategoryDestination.Albums),
            playFocusRequester = remember { FocusRequester() },
            listFocusRequester = remember { FocusRequester() },
            removeFocusRequesters = remember { mutableMapOf() },
            moveUpFocusRequesters = remember { mutableMapOf() },
            moveDownFocusRequesters = remember { mutableMapOf() },
            listState = rememberLazyListState(),
            onListHeightChanged = {},
            onAlbumClick = {},
            onSongClick = {},
            onPlay = {},
            onDelete = {},
            onInsertNext = {},
            onAppend = {},
            onAddToPlaylist = {},
            onMoveUp = { _, _ -> },
            onMoveDown = { _, _ -> },
            onRemove = { _, _ -> }
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PlaylistCategoryPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().context = context

    val playlist = StubPlaylist(1L, "Sample Playlist", 5, 1800000L, 0, 5, 0, 0, false)
    val tracks = (1..5).map { StubMediaWrapper(it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L, MediaWrapper.TYPE_AUDIO, "Playlist Track $it", "track$it.mp3", 1L, 1L, "Various Artists", "Genre", 1L, "Various Albums", "Various Artists", 0, 0, "", 0, 0, it, 1, 0L, 0L, false, false, 2024, true, 0L).apply { tag = "tag_$it" } as MediaWrapper }
    val albums = flowOf(PagingData.from(emptyList<Album>())).collectAsLazyPagingItems()

    VlcTVTheme {
        AudioCategoryScreenContent(
            item = playlist,
            albums = albums,
            trackList = tracks,
            backStack = rememberNavBackStack(CategoryDestination.Songs),
            playFocusRequester = remember { FocusRequester() },
            listFocusRequester = remember { FocusRequester() },
            removeFocusRequesters = remember { mutableMapOf() },
            moveUpFocusRequesters = remember { mutableMapOf() },
            moveDownFocusRequesters = remember { mutableMapOf() },
            listState = rememberLazyListState(),
            onListHeightChanged = {},
            onAlbumClick = {},
            onSongClick = {},
            onPlay = {},
            onDelete = {},
            onInsertNext = {},
            onAppend = {},
            onAddToPlaylist = {},
            onMoveUp = { _, _ -> },
            onMoveDown = { _, _ -> },
            onRemove = { _, _ -> }
        )
    }
}
