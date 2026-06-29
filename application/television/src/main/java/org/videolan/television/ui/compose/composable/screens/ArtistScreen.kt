/*
 * ************************************************************************
 *  ArtistScreen.kt
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.resources.R as ResourcesR
import org.videolan.television.R
import org.videolan.television.ui.TvUtil
import org.videolan.television.ui.compose.composable.components.AudioPlayer
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.components.PaginatedList
import org.videolan.television.ui.compose.composable.components.VLCTabRow
import org.videolan.television.ui.compose.composable.items.AudioItemCard
import org.videolan.television.ui.compose.composable.items.AudioItemList
import org.videolan.television.ui.compose.theme.*
import org.videolan.television.viewmodel.MainActivityViewModel
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.MediaListEntry
import org.videolan.vlc.viewmodels.mobile.AlbumSongsViewModel

import androidx.compose.ui.tooling.preview.Preview
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.stubs.StubAlbum
import org.videolan.medialibrary.stubs.StubArtist
import org.videolan.medialibrary.stubs.StubMediaWrapper
import org.videolan.resources.util.getYear
import org.videolan.television.ui.compose.theme.Orange500
import org.videolan.television.ui.compose.theme.VlcTVTheme
import org.videolan.vlc.util.TextUtils

@Serializable
sealed class ArtistDestination(@param:StringRes val titleRes: Int) : NavKey {
    @Serializable
    data object Albums : ArtistDestination(R.string.albums)

    @Serializable
    data object Songs : ArtistDestination(R.string.songs)
}


@Composable
fun ArtistScreen(
    artist: Artist,
    viewModel: AlbumSongsViewModel = viewModel(factory = AlbumSongsViewModel.Factory(LocalContext.current, artist)),
    mainActivityViewModel: MainActivityViewModel? = if (LocalInspectionMode.current) null else viewModel()
) {
    val albums = viewModel.albumsProvider.pager.collectAsLazyPagingItems()
    val songs = viewModel.tracksProvider.pager.collectAsLazyPagingItems()
    val backStack = rememberNavBackStack(ArtistDestination.Albums)
    val context = LocalContext.current

    var blurredCover by remember { mutableStateOf<Bitmap?>(null) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var darkMutedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(artist) {
        artist.artworkMrl?.let { mrl ->
            val bitmap = AudioUtil.readCoverBitmap(Uri.decode(mrl), 500)
            coverBitmap = bitmap
            bitmap?.let {
                blurredCover = UiTools.blurBitmap(it, 15f)
                Palette.from(it).generate().let { palette ->
                    darkMutedColor = palette.darkMutedSwatch?.rgb?.let { rgb -> Color(rgb) }?.copy(alpha = 0.8f)
                        ?: palette.darkVibrantSwatch?.rgb?.let { rgb -> Color(rgb) }?.copy(alpha = 0.8f)
                }
            }
        }
    }

    ArtistScreenContent(
        artist = artist,
        albums = albums,
        songs = songs,
        backStack = backStack,
        mainActivityViewModel = mainActivityViewModel,
        coverBitmap = coverBitmap,
        blurredCover = blurredCover,
        darkMutedColor = darkMutedColor,
        onAlbumClick = { album -> TvUtil.openAudioCategory(context as FragmentActivity, album) },
        onSongClick = { index ->
            MediaUtils.playAll(context as FragmentActivity, viewModel.tracksProvider, index, shuffle = false)
        }
    )
}

@Composable
fun ArtistScreenContent(
    artist: Artist,
    albums: LazyPagingItems<Album>,
    songs: LazyPagingItems<MediaWrapper>,
    backStack: NavBackStack<NavKey>,
    mainActivityViewModel: MainActivityViewModel? = null,
    coverBitmap: Bitmap? = null,
    blurredCover: Bitmap? = null,
    darkMutedColor: Color? = null,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (Int) -> Unit
) {
    val playFocusRequester = remember { FocusRequester() }
    val tabsFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current

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
                    .padding(top = 32.dp),
            ) {
                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtistHeaderArt(artist, modifier = Modifier.size(160.dp), bitmap = coverBitmap)

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
                            text = artist.title ?: "",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = textShadow
                            ),
                            color = White,
                        )
                        val albumsCount = artist.albumsCount
                        val tracksCount = artist.tracksCount
                        val subtitleList = arrayListOf<String>()
                        if (albumsCount > 0) {
                            subtitleList.add(pluralStringResource(org.videolan.vlc.R.plurals.albums_quantity, albumsCount, albumsCount))
                        }
                        if (tracksCount > 0) {
                            subtitleList.add(pluralStringResource(org.videolan.vlc.R.plurals.track_quantity, tracksCount, tracksCount))
                        }
                        val subtitle = TextUtils.separatedString(subtitleList.toTypedArray())
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow),
                                color = WhiteTransparent90,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(darkMutedColor ?: WhiteTransparent10)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .focusGroup()
                            .focusProperties { down = tabsFocusRequester },
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
                            MediaUtils.playTracks(context, artist, 0, shuffle = false)
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.insert_next),
                            painterResource = painterResource(R.drawable.ic_tv_list_append),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            MediaUtils.insertNext(context, artist.tracks)
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.append),
                            painterResource = painterResource(R.drawable.ic_tv_list_playnext),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            MediaUtils.appendMedia(context, artist.tracks.toList())
                        }
                        LabeledIconButton(
                            label = stringResource(R.string.add_to_playlist),
                            painterResource = painterResource(R.drawable.ic_addtoplaylist),
                            focusedBackgroundColor = WhiteTransparent25,
                            tint = White
                        ) {
                            (context as FragmentActivity).addToPlaylist(artist.tracks, org.videolan.vlc.gui.dialogs.SavePlaylistDialog.KEY_NEW_TRACKS)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tab row
                val tabs = listOf(ArtistDestination.Albums, ArtistDestination.Songs)
                VLCTabRow(
                    selectedTabIndex = tabs.indexOf(backStack.lastOrNull()),
                    onSelected = { index ->
                        backStack.clear()
                        backStack.add(tabs[index])
                    },
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .focusRequester(tabsFocusRequester),
                    tabNumber = tabs.size,
                    indicator = { hasFocus ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (hasFocus) White else WhiteTransparent50, RoundedCornerShape(50))
                        )
                    },
                    key = "artist_tabs",
                    containerColor = darkMutedColor ?: WhiteTransparent10,
                    mainActivityViewModel = mainActivityViewModel,
                    getTab = { index, focused ->
                        val tab = tabs[index]
                        val animatedColor by animateColorAsState(
                            targetValue = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.6F),
                            label = "color"
                        )
                        Text(
                            text = stringResource(tab.titleRes).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = animatedColor,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content area
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.weight(1f),
                ) { destination ->
                    NavEntry(destination) { destinationKey ->
                        ArtistContent(destinationKey as ArtistDestination, albums, songs, darkMutedColor, onAlbumClick, onSongClick)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistHeaderArt(artist: Artist, modifier: Modifier = Modifier, bitmap: Bitmap? = null) {
    val mapBitmap: MutableState<Bitmap?> = remember { mutableStateOf(null) }
    LaunchedEffect(artist, bitmap) {
        if (bitmap != null) {
            mapBitmap.value = bitmap
        } else {
            artist.artworkMrl?.let { mrl ->
                mapBitmap.value = AudioUtil.readCoverBitmap(Uri.decode(mrl), 300)
            }
        }
    }

    Box(
        modifier = modifier
            .border(4.dp, WhiteTransparent10, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(CircleShape)
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
                    painter = painterResource(ResourcesR.drawable.ic_artist_big),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize(),
                    colorFilter = ColorFilter.tint(White)
                )
            }
        }
    }
}

@Composable
private fun ArtistContent(
    destination: ArtistDestination,
    albums: LazyPagingItems<Album>,
    songs: LazyPagingItems<MediaWrapper>,
    contentColor: Color? = null,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (Int) -> Unit
) {
    when (destination) {
        ArtistDestination.Albums -> ArtistAlbums(albums, onAlbumClick)
        ArtistDestination.Songs -> ArtistSongs(songs, contentColor, onSongClick)
    }
}

@Composable
private fun ArtistAlbums(albums: LazyPagingItems<Album>, onAlbumClick: (Album) -> Unit) {
    val gridState = rememberLazyGridState()

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(count = albums.itemCount) { i ->
            val album = albums[i]
            if (album != null) {
                AudioItemCard(
                    item = album,
                    position = i,
                    entry = MediaListEntry.ALBUMS,
                    modifier = Modifier,
                    topStartContent = {
                        val year = album.getYear()
                        if (year != "0" && year != "-" && year.isNotEmpty()) {
                            Text(
                                text = year,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                ) { onAlbumClick(album) }
            }
        }
    }
}

@Composable
private fun ArtistSongs(songs: LazyPagingItems<MediaWrapper>, contentColor: Color? = null, onSongClick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    PaginatedList(
        items = songs,
        listState = listState,
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize()
    ) { song, index, focusModifier ->
        // Use null-safe access for album and provide a default title if missing.
        // This prevents NPE especially in previews where StubMediaWrapper returns a null album.
        val currentAlbumName = (song as MediaWrapper).album?.title ?: stringResource(org.videolan.vlc.R.string.unknown_album)
        val previousAlbumName = if (index > 0) songs.peek(index - 1)?.album?.title ?: stringResource(org.videolan.vlc.R.string.unknown_album) else null
        val nextAlbumName = if (index < (songs.itemCount - 1)) songs.peek(index + 1)?.album?.title ?: stringResource(org.videolan.vlc.R.string.unknown_album) else null
        val isNewAlbum = currentAlbumName != previousAlbumName
        val isLastOfAlbum = currentAlbumName != nextAlbumName

        Column {
            if (isNewAlbum) {
                Text(
                    text = currentAlbumName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp)
                )
            }
            AudioItemList(
                item = song,
                position = index,
                entry = MediaListEntry.TRACKS,
                modifier = focusModifier,
                isFirst = isNewAlbum,
                isLast = isLastOfAlbum,
                containerColor = contentColor ?: MaterialTheme.colorScheme.surface,
                actionContent = {
                    LabeledIconButton(
                        label = stringResource(R.string.insert_next),
                        painterResource = painterResource(R.drawable.ic_tv_list_append),
                        tint = White,
                        focusedBackgroundColor = WhiteTransparent25,
                        focusHeight = 72.dp
                    ) {
                        MediaUtils.insertNext(context, song)
                    }
                    LabeledIconButton(
                        label = stringResource(R.string.append),
                        painterResource = painterResource(R.drawable.ic_tv_list_playnext),
                        tint = White,
                        focusedBackgroundColor = WhiteTransparent25,
                        focusHeight = 72.dp
                    ) {
                        MediaUtils.appendMedia(context, song)
                    }
                    LabeledIconButton(
                        label = stringResource(R.string.add_to_playlist),
                        painterResource = painterResource(R.drawable.ic_addtoplaylist),
                        tint = White,
                        focusedBackgroundColor = WhiteTransparent25,
                        focusHeight = 72.dp
                    ) {
                        (context as FragmentActivity).addToPlaylist(arrayOf(song), org.videolan.vlc.gui.dialogs.SavePlaylistDialog.KEY_NEW_TRACKS)
                    }
                }
            ) {
                onSongClick(index)
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ArtistAlbumsPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().context = context

    val artist = StubArtist(
        1L, "Sample Artist", "Bio", "", "", 3, 42, 42, false
    )
    val albumsList = (1..10).map {
        StubAlbum(it.toLong(), "Album $it", 2020 + it, "", "Sample Artist", 1L, 10, 10, 3600000L, false) as Album
    }
    val albums = flowOf(PagingData.from(albumsList)).collectAsLazyPagingItems()
    val songs = flowOf(PagingData.from(emptyList<MediaWrapper>())).collectAsLazyPagingItems()

    VlcTVTheme {
        ArtistScreenContent(
            artist = artist,
            albums = albums,
            songs = songs,
            backStack = rememberNavBackStack(ArtistDestination.Albums),
            onAlbumClick = {},
            onSongClick = {}
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ArtistSongsPreview() {
    val context = LocalContext.current
    org.videolan.medialibrary.MLContextTools.getInstance().context = context

    val artist = StubArtist(
        1L, "Sample Artist", "Bio", "", "", 3, 42, 42, false
    )
    val tracks = (1..20).map {
        val year = if (it <= 5) 2024 else if (it <= 12) 2022 else 2020
        StubMediaWrapper(
            it.toLong(), "file:///track$it.mp3", 0L, 0f, 300000L,
            MediaWrapper.TYPE_AUDIO,
            "Track $it", "track$it.mp3", 1L, 1L, "Sample Artist", "Genre",
            1L, "Album $year", "Sample Artist", 0, 0, "", 0, 0, it, 1,
            0L, 0L, false, false, year, true, 0L
        ).apply { tag = "tag_$it" } as MediaWrapper
    }
    val albums = flowOf(PagingData.from(emptyList<Album>())).collectAsLazyPagingItems()
    val songs = flowOf(PagingData.from(tracks)).collectAsLazyPagingItems()

    VlcTVTheme {
        ArtistScreenContent(
            artist = artist,
            albums = albums,
            songs = songs,
            backStack = rememberNavBackStack(ArtistDestination.Songs),
            onAlbumClick = {},
            onSongClick = {}
        )
    }
}
