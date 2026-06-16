/*
 * ************************************************************************
 *  AlbumScreen.kt
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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.components.MiniVisualizer
import androidx.compose.ui.tooling.preview.Preview
import org.videolan.medialibrary.stubs.StubAlbum
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.medialibrary.stubs.StubPlaylist
import org.videolan.resources.R as ResourcesR
import org.videolan.television.ui.compose.theme.*
import org.videolan.television.ui.compose.utils.fadingMarquee
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.viewmodels.PlaylistModel
import org.videolan.vlc.viewmodels.mobile.AlbumSongsViewModel
import java.util.UUID

@Composable
fun AlbumPlaylistScreen(parentItem: MediaLibraryItem, albumSongsViewModel: AlbumSongsViewModel = viewModel(factory = AlbumSongsViewModel.Factory(LocalContext.current, parentItem))) {
    val tracks by albumSongsViewModel.tracksProvider.pagedList.observeAsState()
    val trackList = remember { mutableStateListOf<MediaWrapper>() }
    val context = LocalContext.current
    val density = LocalDensity.current
    var blurredCover by remember { mutableStateOf<Bitmap?>(null) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val activity = LocalActivity.current
    var darkMutedColor by remember { mutableStateOf<Color?>(null) }
    val scope = rememberCoroutineScope()
    val removeFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val moveUpFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val moveDownFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val playFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var listHeight by remember { mutableIntStateOf(0) }


    /**
     * As the playlists can contain duplicated media and the LazyColumn needs stable IDs
     * we have to use a copy of the list as SSOT and manage the tags ourselves
     */
    // Synchronous initial population: ensure tags exist before the first render
    tracks?.let { pagedList ->
        val snapshot = pagedList.snapshot()
        if (trackList.isEmpty() && snapshot.isNotEmpty()) {
            snapshot.forEach { if (it.tag == null) it.tag = UUID.randomUUID().toString() }
            trackList.addAll(snapshot)
        }
    }

    LaunchedEffect(Unit) {
        playFocusRequester.requestFocus()
    }

    LaunchedEffect(tracks) {
        // When the tracks are refreshed, update the tags
        val snapshot = tracks?.snapshot() ?: emptyList()
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

        val isDifferent = (trackList.size != snapshot.size) || trackList.indices.any { i ->
            (trackList[i].id != snapshot[i].id) || (trackList[i].tag != snapshot[i].tag)
        }

        if (isDifferent) {
            trackList.clear()
            trackList.addAll(snapshot)
        }
    }

    LaunchedEffect(parentItem) {
        val bitmap = if (parentItem is Playlist) {
            ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${parentItem.id}_500", parentItem.tracks.toList(), 500)
        } else {
            parentItem.artworkMrl?.let { mrl ->
                AudioUtil.readCoverBitmap(Uri.decode(mrl), 500)
            }
        }
        coverBitmap = bitmap
        bitmap?.let {
            blurredCover = UiTools.blurBitmap(it, 15f)
            Palette.from(it).generate().let { palette ->
                darkMutedColor = palette.darkMutedSwatch?.rgb?.let { rgb -> Color(rgb) }?.copy(alpha = 0.8f) ?: palette.darkVibrantSwatch?.rgb?.let { rgb -> Color(rgb) }?.copy(alpha = 0.8f)
            }
        }
    }

    AlbumPlaylistScreenContent(
        parentItem = parentItem,
        trackList = trackList,
        darkMutedColor = darkMutedColor,
        coverBitmap = coverBitmap,
        blurredCover = blurredCover,
        listState = listState,
        onListHeightChanged = { listHeight = it },
        playFocusRequester = playFocusRequester,
        removeFocusRequesters = removeFocusRequesters,
        moveUpFocusRequesters = moveUpFocusRequesters,
        moveDownFocusRequesters = moveDownFocusRequesters,
        onPlay = { MediaUtils.playTracks(context, parentItem, 0, shuffle = false) },
        onDelete = {
            if (parentItem is Playlist) {
                MediaUtils.deleteItem(activity as FragmentActivity, parentItem) {
                    activity.finish()
                }
            }
        },
        onInsertNext = { MediaUtils.appendMedia(context, parentItem.tracks.toList()) },
        onAppend = { MediaUtils.insertNext(context, parentItem.tracks) },
        onAddToPlaylist = { (activity as FragmentActivity).addToPlaylist(parentItem.tracks, SavePlaylistDialog.KEY_NEW_TRACKS) },
        onMoveUp = { index, track ->
            val tag = track.tag!!
            Snapshot.withMutableSnapshot {
                val item = trackList.removeAt(index)
                trackList.add(index - 1, item)
            }
            if (index - 1 == 0) {
                moveDownFocusRequesters[tag]?.requestFocus()
            } else {
                moveUpFocusRequesters[tag]?.requestFocus()
            }
            scope.launch {
                listState.requestScrollToItem(index - 1, listHeight / 2 - with(density) { 36.dp.toPx().toInt() })
            }
            scope.launch(Dispatchers.IO) {
                (parentItem as Playlist).move(index, index - 1)
                albumSongsViewModel.refresh()
            }
        },
        onMoveDown = { index, track ->
            val tag = track.tag!!
            Snapshot.withMutableSnapshot {
                val item = trackList.removeAt(index)
                trackList.add(index + 1, item)
            }
            if (index + 1 == trackList.size - 1) {
                moveUpFocusRequesters[tag]?.requestFocus()
            } else {
                moveDownFocusRequesters[tag]?.requestFocus()
            }
            scope.launch {
                listState.requestScrollToItem(index + 1, listHeight / 2 - with(density) { 36.dp.toPx().toInt() })
            }
            scope.launch(Dispatchers.IO) {
                (parentItem as Playlist).move(index, index + 1)
                albumSongsViewModel.refresh()
            }
        },
            onRemove = { index, _ ->
                // Move focus to another "remove" button before deleting
                val nextFocusIndex = if ((index + 1) < trackList.size) index + 1 else if (index > 0) index - 1 else -1
                if (nextFocusIndex != -1) {
                    val nextTrack = trackList[nextFocusIndex]
                    val nextTag = nextTrack.tag
                    if (nextTag != null) removeFocusRequesters[nextTag]?.requestFocus()
                }

                trackList.removeAt(index)
                scope.launch(Dispatchers.IO) {
                    (parentItem as Playlist).remove(index)
                    albumSongsViewModel.refresh()
                }
            }
    )
}

@Composable
fun AlbumPlaylistScreenContent(
    parentItem: MediaLibraryItem,
    trackList: List<MediaWrapper>,
    darkMutedColor: Color?,
    coverBitmap: Bitmap?,
    blurredCover: Bitmap?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onListHeightChanged: (Int) -> Unit,
    playFocusRequester: FocusRequester,
    removeFocusRequesters: Map<String, FocusRequester>,
    moveUpFocusRequesters: Map<String, FocusRequester>,
    moveDownFocusRequesters: Map<String, FocusRequester>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onInsertNext: () -> Unit,
    onAppend: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMoveUp: (Int, MediaWrapper) -> Unit,
    onMoveDown: (Int, MediaWrapper) -> Unit,
    onRemove: (Int, MediaWrapper) -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColorDark),
    ) {

        blurredCover?.let {
            Image(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Grey900Transparent, BlendMode.SrcAtop)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                BackgroundColorDark.copy(alpha = 0.8f),
                                Transparent,
                                BackgroundColorDark.copy(alpha = 0.9f)
                            )
                        )
                    )
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            AudioPlayer(requestFocus = false)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 32.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumPlaylistHeaderArt(parentItem, modifier = Modifier.size(160.dp), bitmap = coverBitmap)

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        val textShadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                        Text(
                            text = parentItem.title ?: "",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = textShadow
                            ),
                            color = White,
                            maxLines = 1,
                            modifier = Modifier.fadingMarquee()
                        )
                        val subtitle = when (parentItem) {
                            is Album -> parentItem.albumArtist ?: stringResource(R.string.unknown_artist)
                            else -> stringResource(R.string.track_number, parentItem.tracks.size)
                        }
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow),
                                color = WhiteTransparent90,
                                maxLines = 1,
                                modifier = Modifier.fadingMarquee()
                            )
                        }
                        val duration = when (parentItem) {
                            is Album -> parentItem.duration
                            is Playlist -> parentItem.tracks.sumOf { it.length }
                            else -> 0L
                        }
                        Text(
                            text = Tools.millisToString(duration),
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            color = WhiteTransparent90,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(darkMutedColor ?: WhiteTransparent10)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .focusGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledIconButton(
                            label = stringResource(R.string.play),
                            painterResource = painterResource(R.drawable.ic_play_tv),
                            modifier = Modifier.focusRequester(playFocusRequester),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            onPlay()
                        }
                        if (parentItem is Playlist) {
                            LabeledIconButton(
                                label = stringResource(R.string.delete),
                                painterResource = painterResource(R.drawable.ic_tv_list_delete),
                                focusedBackgroundColor = WhiteTransparent25,
                                tint = White
                            ) {
                                onDelete()
                            }
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.insert_next),
                            painterResource = painterResource(R.drawable.ic_tv_list_playnext),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            onInsertNext()
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.append),
                            painterResource = painterResource(R.drawable.ic_tv_list_append),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            onAppend()
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.add_to_playlist),
                            painterResource = painterResource(R.drawable.ic_addtoplaylist),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            onAddToPlaylist()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer(clip = false)
                        .onGloballyPositioned {
                            onListHeightChanged(it.size.height)
                        }
                        .focusGroup(),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp)
                ) {
                    itemsIndexed(trackList, key = { _, track -> track.tag ?: track.hashCode().toString() }) { index, track ->
                        val tag = track.tag ?: track.hashCode().toString()
                        val removeFocusRequester = removeFocusRequesters[tag] ?: remember(tag) { FocusRequester().also { (removeFocusRequesters as MutableMap)[tag] = it } }
                        val moveUpFocusRequester = moveUpFocusRequesters[tag] ?: remember(tag) { FocusRequester().also { (moveUpFocusRequesters as MutableMap)[tag] = it } }
                        val moveDownFocusRequester = moveDownFocusRequesters[tag] ?: remember(tag) { FocusRequester().also { (moveDownFocusRequesters as MutableMap)[tag] = it } }
                        AlbumPlaylistTrackItem(
                            track = track,
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth(),
                            darkMutedColor = darkMutedColor ?: MaterialTheme.colorScheme.surface,
                            isFirst = index == 0,
                            isLast = index == trackList.size - 1,
                            onMoveUp = if (parentItem is Playlist && index > 0) {
                                { onMoveUp(index, track) }
                            } else null,
                            onMoveDown = if (parentItem is Playlist && (index < trackList.size - 1)) {
                                { onMoveDown(index, track) }
                            } else null,
                            onRemove = if (parentItem is Playlist) {
                                { onRemove(index, track) }
                            } else null,
                            removeFocusRequester = removeFocusRequester,
                            moveUpFocusRequester = moveUpFocusRequester,
                            moveDownFocusRequester = moveDownFocusRequester
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumPlaylistHeaderArt(item: MediaLibraryItem, modifier: Modifier = Modifier, bitmap: Bitmap? = null) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    LaunchedEffect(item, bitmap) {
        if (bitmap != null) {
            mapBitmap.value = bitmap
        } else if (item is Playlist) {
            mapBitmap.value = ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${item.id}_300", item.tracks.toList(), 300)
        } else {
            item.artworkMrl?.let { mrl ->
                mapBitmap.value = AudioUtil.readCoverBitmap(Uri.decode(mrl), 300)
            }
        }
    }

    Box(
        modifier = modifier
            .border(4.dp, WhiteTransparent10, RoundedCornerShape(8.dp))
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        mapBitmap.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            Image(
                painter = painterResource(if (item is Playlist) R.drawable.ic_playlist_big else R.drawable.ic_album_big),
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize(),
                colorFilter = ColorFilter.tint(White)
            )
        }
    }
}

@Composable
fun AlbumPlaylistTrackItem(
    track: MediaWrapper,
    modifier: Modifier = Modifier,
    darkMutedColor: Color = MaterialTheme.colorScheme.surface,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeFocusRequester: FocusRequester = remember { FocusRequester() },
    moveUpFocusRequester: FocusRequester = remember { FocusRequester() },
    moveDownFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var isFocused by remember { mutableStateOf(false) }
    var itemHasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (itemHasFocus) 1.05f else 1f, label = "scale")
    val baseCornerRadius = 12.dp
    val topCornerRadius by animateDpAsState(if (itemHasFocus || isFirst) baseCornerRadius else 0.dp, label = "topCornerRadius")
    val bottomCornerRadius by animateDpAsState(if (itemHasFocus || isLast) baseCornerRadius else 0.dp, label = "bottomCornerRadius")
    val context = LocalContext.current
    val activity = LocalActivity.current
    val itemFocusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius, bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius)
    val itemBackgroundColor = if (itemHasFocus) darkMutedColor.copy(alpha = 1f) else darkMutedColor

    Box(
        modifier = modifier
            .zIndex(if (itemHasFocus) 1f else 0f)
            .height(72.dp)
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection == FocusDirection.Up || (requestedFocusDirection == FocusDirection.Down)) {
                        itemFocusRequester.requestFocus()
                    }
                }
            }
            .onFocusChanged {
                itemHasFocus = it.hasFocus
            }
            .focusGroup()

    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(if (itemHasFocus) 12.dp else 0.dp, shape)
                .border(if (itemHasFocus) 1.dp else 0.dp, WhiteTransparent10, shape)
                .background(itemBackgroundColor, shape)
                .clip(shape)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(itemFocusRequester)
                        .onFocusChanged {
                            isFocused = it.isFocused
                        }
                        .combinedClickable(
                            onClick = {
                                TvUtil.openMedia(activity as FragmentActivity, track)
                            },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
                        .background(if (isFocused) WhiteTransparent25 else Transparent, shape = RoundedCornerShape(baseCornerRadius))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(WhiteTransparent10)
                        ) {

                            // Track thumbnail (usually same as album art)
                            val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
                            LaunchedEffect(track.artworkMrl) {
                                track.artworkMrl?.let { mrl ->
                                    mapBitmap.value = AudioUtil.readCoverBitmap(Uri.decode(mrl), 120)
                                }
                            }

                            mapBitmap.value?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } ?: run {
                                Image(
                                    painter = painterResource(R.drawable.ic_song_big),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .alpha(if (itemHasFocus) 0F else 1F)
                                        .fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.tint(White)
                                )
                            }

                            val currentMedia by PlaylistManager.currentPlayedMedia.observeAsState()
                            val playlistModel: PlaylistModel = viewModel()
                            val isCurrent = currentMedia?.equals(track) == true

                            InvalidationComposable(currentMedia?.tag) {
                                if (isCurrent) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(BlackTransparent50, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        MiniVisualizer(MaterialTheme.colorScheme.onSurface, playlistModel)
                                    }
                                }
                            }

                            if (isFocused && !isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Grey900Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_play_tv),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fadingMarquee(isFocused = isFocused)
                        ) {
                            Text(
                                text = track.title ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = White,
                                maxLines = 1
                            )
                            Text(
                                text = track.artistName ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WhiteTransparent70,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = Tools.millisToString(track.length),
                            style = MaterialTheme.typography.labelLarge,
                            color = WhiteTransparent70,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight()
                        .graphicsLayer { alpha = if (itemHasFocus) 1f else 0f }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onMoveUp != null) {
                            LabeledIconButton(
                                label = stringResource(R.string.move_up),
                                painterResource = painterResource(R.drawable.ic_playlist_moveup),
                                modifier = Modifier.focusRequester(moveUpFocusRequester),
                                tint = White,
                                focusedBackgroundColor = WhiteTransparent25,
                                focusHeight = 72.dp
                            ) {
                                onMoveUp()
                            }
                        } else if (onMoveDown != null) {
                            Spacer(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(72.dp)
                            )
                        }
                        if (onMoveDown != null) {
                            LabeledIconButton(
                                label = stringResource(R.string.move_down),
                                painterResource = painterResource(R.drawable.ic_playlist_movedown),
                                modifier = Modifier.focusRequester(moveDownFocusRequester),
                                tint = White,
                                focusedBackgroundColor = WhiteTransparent25,
                                focusHeight = 72.dp
                            ) {
                                onMoveDown()
                            }
                        } else if (onMoveUp != null) {
                            Spacer(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(72.dp)
                            )
                        }
                        if (onRemove != null) {
                            LabeledIconButton(
                                label = stringResource(R.string.remove),
                                painterResource = painterResource(R.drawable.ic_remove_from_playlist),
                                modifier = Modifier.focusRequester(removeFocusRequester),
                                tint = White,
                                focusedBackgroundColor = WhiteTransparent25,
                                focusHeight = 72.dp
                            ) {
                                onRemove()
                            }
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.append),
                            painterResource = painterResource(R.drawable.ic_tv_list_append),
                            tint = White,
                            focusedBackgroundColor = WhiteTransparent25,
                            focusHeight = 72.dp
                        ) {
                            MediaUtils.appendMedia(context, track)
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.insert_next),
                            painterResource = painterResource(R.drawable.ic_tv_list_playnext),
                            tint = White,
                            focusedBackgroundColor = WhiteTransparent25,
                            focusHeight = 72.dp
                        ) {
                            MediaUtils.insertNext(context, track)
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.add_to_playlist),
                            painterResource = painterResource(R.drawable.ic_addtoplaylist),
                            tint = White,
                            focusedBackgroundColor = WhiteTransparent25,
                            focusHeight = 72.dp
                        ) {
                            (activity as FragmentActivity).addToPlaylist(arrayOf(track), SavePlaylistDialog.KEY_NEW_TRACKS)
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AlbumPlaylistScreenPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().setContext(context)
    val album = StubAlbum(
        1L, "Sample Album", 2024, "", "Sample Artist", 1L, 10, 10, 3600000L, false
    )
    val tracks = (1..10).map {
        StubMediaWrapper(
            it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L,
            MediaWrapper.TYPE_AUDIO,
            "Track $it", "track$it.mp3", 1L, 1L, "Sample Artist", "Genre",
            1L, "Sample Album", "Sample Artist", 0, 0, "", 0, 0, it, 1,
            0L, 0L, false, false, 2024, true, 0L
        ).apply { tag = "tag_$it" }
    }

    // Load a real image for the preview. We use applicationContext.resources to avoid the LocalContextResources lint warning.
    val mockBitmap = remember { BitmapFactory.decodeResource(context.applicationContext.resources, ResourcesR.drawable.vlc_fake_cover) }

    VlcTVTheme {
        AlbumPlaylistScreenContent(
            parentItem = album,
            trackList = tracks,
            darkMutedColor = Color(0xFF221137),
            coverBitmap = mockBitmap,
            blurredCover = mockBitmap,
            listState = rememberLazyListState(),
            onListHeightChanged = {},
            playFocusRequester = remember { FocusRequester() },
            removeFocusRequesters = remember { mutableMapOf() },
            moveUpFocusRequesters = remember { mutableMapOf() },
            moveDownFocusRequesters = remember { mutableMapOf() },
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
private fun PlaylistScreenPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().setContext(context)
    val playlist = StubPlaylist(
        1L, "Sample Playlist", 5, 1800000L, 0, 5, 0, 0, false
    )
    val tracks = (1..5).map {
        StubMediaWrapper(
            it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L,
            MediaWrapper.TYPE_AUDIO,
            "Playlist Track $it", "track$it.mp3", 1L, 1L, "Various Artists", "Genre",
            1L, "Various Albums", "Various Artists", 0, 0, "", 0, 0, it, 1,
            0L, 0L, false, false, 2024, true, 0L
        ).apply { tag = "tag_$it" }
    }

    // Load a real image for the preview. We use applicationContext.resources to avoid the LocalContextResources lint warning.
    val mockBitmap = remember { BitmapFactory.decodeResource(context.applicationContext.resources, ResourcesR.drawable.vlc_fake_cover) }

    VlcTVTheme {
        AlbumPlaylistScreenContent(
            parentItem = playlist,
            trackList = tracks,
            darkMutedColor = Color(0xFF221137),
            coverBitmap = mockBitmap,
            blurredCover = mockBitmap,
            listState = rememberLazyListState(),
            onListHeightChanged = {},
            playFocusRequester = remember { FocusRequester() },
            removeFocusRequesters = remember { mutableMapOf() },
            moveUpFocusRequesters = remember { mutableMapOf() },
            moveDownFocusRequesters = remember { mutableMapOf() },
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
