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
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.InvalidationComposable
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.components.MiniVisualizer
import org.videolan.television.ui.compose.theme.BackgroundColorDark
import org.videolan.television.ui.compose.theme.BackgroundColorDarkTransparent50
import org.videolan.television.ui.compose.theme.BlackTransparent50
import org.videolan.television.ui.compose.theme.Grey900Transparent
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent25
import org.videolan.television.ui.compose.theme.WhiteTransparent50
import org.videolan.television.ui.compose.theme.WhiteTransparent70
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.viewmodels.mobile.AlbumSongsViewModel

@Composable
fun AlbumScreen(album: Album, albumSongsViewModel: AlbumSongsViewModel = viewModel(factory = AlbumSongsViewModel.Factory(LocalContext.current, album))) {
    val tracks by albumSongsViewModel.tracksProvider.pagedList.observeAsState()
    val context = LocalContext.current
    var blurredCover by remember { mutableStateOf<Bitmap?>(null) }
    val activity = LocalActivity.current

    LaunchedEffect(album.artworkMrl) {
        album.artworkMrl?.let { mrl ->
            val bitmap = AudioUtil.readCoverBitmap(Uri.decode(mrl), 500)
            bitmap?.let {
                blurredCover = UiTools.blurBitmap(it, 15f)
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(BackgroundColorDark)) {

        blurredCover?.let {
            Image(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Grey900Transparent, BlendMode.SrcAtop)
            )
        }

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp, start = 48.dp, end = 48.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AlbumHeaderArt(album, modifier = Modifier.size(160.dp))

                Spacer(modifier = Modifier.width(32.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title ?: "",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = White,
                        maxLines = 1
                    )
                    Text(
                        text = album.albumArtist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.headlineSmall,
                        color = WhiteTransparent70,
                        maxLines = 1
                    )
                    Text(
                        text = Tools.millisToString(album.duration),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiteTransparent50
                    )
                }

                Row(modifier = Modifier.focusGroup()) {
                    LabeledIconButton(
                        label = stringResource(R.string.play),
                        painterResource = painterResource(R.drawable.ic_play_tv),
                        tint = White
                    ) {
                        MediaUtils.playTracks(context, album, 0, false)
                    }
                    LabeledIconButton(
                        label = stringResource(R.string.insert_next),
                        painterResource = painterResource(R.drawable.ic_tv_list_playnext),
                        tint = White
                    ) {
                        MediaUtils.appendMedia(context, album.tracks.toList())
                    }
                    LabeledIconButton(
                        label = stringResource(R.string.append),
                        painterResource = painterResource(R.drawable.ic_tv_list_append),
                        tint = White
                    ) {
                        MediaUtils.insertNext(context, album.tracks)
                    }
                    LabeledIconButton(
                        label = stringResource(R.string.add_to_playlist),
                        painterResource = painterResource(R.drawable.ic_addtoplaylist),
                        tint = White
                    ) {
                        (activity as FragmentActivity).addToPlaylist(album.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            tracks?.let { trackList ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    itemsIndexed(trackList) { index, track ->
                        if (track is MediaWrapper) {
                            AlbumTrackItem(track)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumHeaderArt(album: Album, modifier: Modifier = Modifier) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    LaunchedEffect(album.artworkMrl) {
        album.artworkMrl?.let { mrl ->
            mapBitmap.value = AudioUtil.readCoverBitmap(Uri.decode(mrl), 300)
        }
    }

    Box(modifier = modifier
        .clip(RoundedCornerShape(8.dp))) {
        mapBitmap.value?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            Image(
                painter = painterResource(R.drawable.ic_album_big),
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
fun AlbumTrackItem(track: MediaWrapper) {
    var isFocused by remember { mutableStateOf(false) }
    var itemHasFocus by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var boxWidth by remember { mutableStateOf(300.dp) }
    var rootPosition by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val activity = LocalActivity.current
    val itemFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .onGloballyPositioned {
                rootPosition = it.positionInRoot().x
            }
            .focusProperties {
                onEnter = {
                    itemFocusRequester.requestFocus()
                    itemHasFocus = true
                }
                onExit = {
                    itemHasFocus = false
                }
            }
            .focusGroup()

    ) {
        Box (Modifier
            .focusRequester(itemFocusRequester)
            .width(width = boxWidth)
            .fillMaxHeight()
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
            .background(if (isFocused) WhiteTransparent25 else Transparent, shape = RoundedCornerShape(topStart = CornerSize(0), bottomStart = CornerSize(0), topEnd = CornerSize(50), bottomEnd = CornerSize(50)))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(WhiteTransparent10)) {

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
                    }

                    val currentMedia by PlaylistManager.currentPlayedMedia.observeAsState()
                    InvalidationComposable(currentMedia?.tag) {
                        if (currentMedia?.equals(track) == true) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(BlackTransparent50, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                MiniVisualizer(MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    if (isFocused) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Grey900Transparent),
                            contentAlignment = Alignment.Center) {
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = White,
                        maxLines = 1,
                        modifier = if (isFocused) Modifier.basicMarquee() else Modifier
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiteTransparent70,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .onGloballyPositioned {
                    boxWidth = with(density) { (it.positionInRoot().x - rootPosition).toDp() }
                }
                .graphicsLayer { alpha = if (itemHasFocus) 1f else 0f }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LabeledIconButton(
                label = stringResource(R.string.append),
                painterResource = painterResource(R.drawable.ic_tv_list_append),
                tint = White
            ) {
                MediaUtils.appendMedia(context, track)
            }
            LabeledIconButton(
                label = stringResource(R.string.insert_next),
                painterResource = painterResource(R.drawable.ic_tv_list_playnext),
                tint = White
            ) {
                MediaUtils.insertNext(context, track)
            }
            LabeledIconButton(
                label = stringResource(R.string.add_to_playlist),
                painterResource = painterResource(R.drawable.ic_addtoplaylist),
                tint = White
            ) {
                (activity as FragmentActivity).addToPlaylist(arrayOf(track), SavePlaylistDialog.KEY_NEW_TRACKS)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 0.dp), color = WhiteTransparent25, thickness = 1.dp)
    }
}
