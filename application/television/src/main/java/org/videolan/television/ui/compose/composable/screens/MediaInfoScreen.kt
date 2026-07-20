/*
 * ************************************************************************
 *  MediaInfoScreen.kt
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.Artist
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.AlbumImpl
import org.videolan.medialibrary.media.ArtistImpl
import org.videolan.medialibrary.media.GenreImpl
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapperImpl
import org.videolan.medialibrary.media.PlaylistImpl
import org.videolan.resources.AndroidDevices
import org.videolan.television.R
import org.videolan.television.ui.compose.theme.BackgroundColorDark
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent20
import org.videolan.television.ui.compose.theme.WhiteTransparent70
import org.videolan.television.ui.compose.utils.VlcPreview
import org.videolan.television.viewmodel.TrackData
import org.videolan.vlc.viewmodels.browser.PathOperationDelegate
import org.videolan.vlc.R as vlcR

@Composable
fun MediaInfoScreen(
    item: MediaLibraryItem,
    cover: Bitmap? = null,
    fileSize: Long = -1,
    tracks: List<TrackData> = emptyList(),
    onPlay: () -> Unit = {}
) {
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(56.dp)
    ) {
        // Left Panel: Context & Header
        MediaInfoHeader(
            modifier = Modifier.weight(0.4f),
            item = item,
            cover = cover,
            onPlay = onPlay,
            playButtonFocusRequester = playButtonFocusRequester
        )

        Spacer(modifier = Modifier.width(48.dp))

        // Right Panel: Technical Details
        MediaInfoDetails(
            modifier = Modifier.weight(0.6f),
            item = item,
            fileSize = fileSize,
            tracks = tracks
        )
    }
}

@Composable
private fun MediaInfoHeader(
    modifier: Modifier = Modifier,
    item: MediaLibraryItem,
    cover: Bitmap? = null,
    onPlay: () -> Unit,
    playButtonFocusRequester: FocusRequester
) {
    val isVideo = item is MediaWrapper && item.type == MediaWrapper.TYPE_VIDEO
    val iconRes = when (item.itemType) {
        MediaLibraryItem.TYPE_ARTIST -> R.drawable.ic_artist_big
        MediaLibraryItem.TYPE_ALBUM -> R.drawable.ic_album_big
        MediaLibraryItem.TYPE_GENRE -> R.drawable.ic_genre_big
        MediaLibraryItem.TYPE_PLAYLIST -> R.drawable.ic_playlist_big
        else -> if (isVideo) R.drawable.ic_video else R.drawable.ic_song_big
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        // Artwork
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (isVideo) 400.dp else 480.dp)
                .aspectRatio(if (isVideo) 16f / 9f else 1f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Primary Action
        MediaInfoButton(
            icon = R.drawable.ic_play,
            text = R.string.play,
            modifier = Modifier
                .widthIn(min = 200.dp)
                .focusRequester(playButtonFocusRequester)
        ) {
            onPlay()
        }
    }
}

@Composable
private fun Breadcrumbs(modifier: Modifier = Modifier, uri: Uri?) {
    if (uri == null) return
    val delegate = remember { PathOperationDelegate() }
    val internalMemoryLabel = stringResource(id = vlcR.string.internal_memory)

    val isPreview = LocalInspectionMode.current
    val segments = remember(uri, internalMemoryLabel) {
        // Initialize storages mapping like PathAdapter does
        if (!isPreview) {
            PathOperationDelegate.storages.put(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, delegate.makePathSafe(internalMemoryLabel))
        }

        val path = Uri.decode(uri.path) ?: ""
        val substitutedPath = delegate.replaceStoragePath(path)

        val pathParts = substitutedPath.split('/').filter { it.isNotEmpty() }
        val list = mutableListOf<String>()

        for (index in pathParts.indices) {
            val currentPathUri = Uri.Builder().scheme(uri.scheme).encodedAuthority(uri.authority)
            for (i in 0..index) delegate.appendPathToUri(pathParts[i], currentPathUri)
            list.add(currentPathUri.toString())
        }
        list
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        itemsIndexed(segments) { index, segmentUriString ->
            val segmentUri = segmentUriString.toUri()
            val path = segmentUri.path
            val text = when {
                path != null && PathOperationDelegate.storages.containsKey(path) -> delegate.retrieveSafePath(PathOperationDelegate.storages.get(path)!!)
                else -> segmentUri.lastPathSegment
            }

            var itemFocused by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .onFocusChanged { itemFocused = it.isFocused }
                    .focusable()
            ) {
                Text(
                    text = text ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiteTransparent70,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (itemFocused) WhiteTransparent20 else Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                if (index < segments.size - 1) {
                    Icon(
                        painter = painterResource(id = vlcR.drawable.ic_divider),
                        contentDescription = null,
                        tint = WhiteTransparent70,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoButton(icon: Int, text: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(
        colors = ButtonDefaults.buttonColors(containerColor = if (focused) White else BackgroundColorDark.copy(0.4F)),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        modifier = modifier
            .height(64.dp)
            .onFocusChanged {
                focused = it.isFocused
            }
    ) {
        Icon(
            painter = painterResource(icon),
            tint = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(0.4F)
        )
    }
}

@Composable
private fun MediaInfoDetails(
    modifier: Modifier = Modifier,
    item: MediaLibraryItem,
    fileSize: Long,
    tracks: List<TrackData>
) {
    val totalLength = remember(item) {
        when (item) {
            is MediaWrapper -> item.length
            is Artist -> item.tracks?.sumOf { it.length } ?: 0L
            is Album -> item.tracks?.sumOf { it.length } ?: 0L
            is Genre -> item.tracks?.sumOf { it.length } ?: 0L
            is Playlist -> item.tracks?.sumOf { it.length } ?: 0L
            else -> 0L
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                // Title
                Text(
                    text = item.title ?: "",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Breadcrumbs / Description
                if (item is MediaWrapper) {
                    Breadcrumbs(uri = item.uri)
                } else {
                    val description = when (item) {
                        is Artist -> item.shortBio ?: ""
                        else -> ""
                    }

                    if (description.isNotEmpty()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WhiteTransparent70,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // General Section
        item {
            var focused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .background(if (focused) WhiteTransparent10 else Transparent)
                    .focusable()
                    .padding(12.dp)
            ) {
                SectionHeader(stringResource(R.string.general))
                if (totalLength > 0) {
                    InfoLine(stringResource(R.string.length), Tools.millisToString(totalLength))
                }
                if (item is MediaWrapper && fileSize > 0) {
                    InfoLine(stringResource(R.string.file_size), android.text.format.Formatter.formatShortFileSize(null, fileSize))
                }
                if (item is Artist) {
                    InfoLine(stringResource(R.string.albums), item.albumsCount.toString())
                }
                if (item !is MediaWrapper) {
                    InfoLine(stringResource(R.string.tracks), item.tracksCount.toString())
                }
            }
        }

        // Tracks Sections (Technical details for files)
        items(tracks) { track ->
            TrackInfoSection(track)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            thickness = 1.dp,
            color = WhiteTransparent10
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = WhiteTransparent70)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrackInfoSection(track: TrackData) {
    val title = when (track.type) {
        IMedia.Track.Type.Audio -> stringResource(vlcR.string.track_audio)
        IMedia.Track.Type.Video -> stringResource(vlcR.string.track_video)
        IMedia.Track.Type.Text -> stringResource(vlcR.string.track_text)
        else -> stringResource(vlcR.string.track_unknown)
    }

    val codecLabel = stringResource(vlcR.string.codec)
    val bitrateLabel = stringResource(vlcR.string.bitrate)
    val languageLabel = stringResource(vlcR.string.language)
    val resolutionLabel = stringResource(vlcR.string.resolution)
    val channelsLabel = stringResource(vlcR.string.channels)
    val rateLabel = stringResource(vlcR.string.track_samplerate)

    val info = remember(track, codecLabel, bitrateLabel, languageLabel, resolutionLabel, channelsLabel, rateLabel) {
        val list = mutableListOf<Pair<String, String>>()
        list.add(codecLabel to track.codec)
        if (track.bitrate > 0) list.add(bitrateLabel to "${track.bitrate / 1000} kb/s")
        if (!track.language.isNullOrEmpty()) list.add(languageLabel to track.language!!)
        if (track.type == IMedia.Track.Type.Video) {
            list.add(resolutionLabel to "${track.width}x${track.height}")
        } else if (track.type == IMedia.Track.Type.Audio) {
            list.add(channelsLabel to track.channels.toString())
            list.add(rateLabel to "${track.rate} Hz")
        }
        list
    }

    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) WhiteTransparent10 else Transparent)
            .focusable()
            .padding(12.dp)
    ) {
        SectionHeader(title)
        info.forEach { (label, value) ->
            InfoLine(label, value)
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaInfoVideoPreview() {
    val dummyMedia = MediaWrapperImpl(android.net.Uri.parse("file:///sdcard/Movies/Agent327.mp4")).apply {
        title = "Agent 327: Operation Barbershop"
        length = 231000 // 3m 51s
        type = MediaWrapper.TYPE_VIDEO
    }
    VlcPreview {
        MediaInfoScreen(
            item = dummyMedia,
            fileSize = 38587596, // 36.8 MB
            tracks = listOf(
                TrackData(
                    type = IMedia.Track.Type.Video,
                    codec = "VP9",
                    bitrate = 5000000,
                    language = null,
                    description = null,
                    width = 2048,
                    height = 858
                ),
                TrackData(
                    type = IMedia.Track.Type.Audio,
                    codec = "Opus",
                    bitrate = 128000,
                    language = "English",
                    description = null,
                    channels = 2,
                    rate = 48000
                )
            )
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaInfoAudioPreview() {
    val dummyMedia = MediaWrapperImpl(android.net.Uri.parse("file:///sdcard/Music/AllThatYouAre.ogg")).apply {
        title = "All That You Are"
        length = 261000 // 4m 21s
        type = MediaWrapper.TYPE_AUDIO
    }
    VlcPreview {
        MediaInfoScreen(
            item = dummyMedia,
            fileSize = 996147, // 0.95 MB
            tracks = listOf(
                TrackData(
                    type = IMedia.Track.Type.Audio,
                    codec = "Vorbis",
                    bitrate = 22400,
                    language = null,
                    description = null,
                    channels = 1,
                    rate = 8000
                )
            )
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaInfoArtistPreview() {
    val dummyArtist = ArtistImpl(100, "A Ninja Slob Drew Me", "Short Bio", null, null, 6, 63, 63, false)
    VlcPreview {
        MediaInfoScreen(
            item = dummyArtist
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaInfoAlbumPreview() {
    val dummyAlbum = AlbumImpl(1, "Unknown Album", 2024, null, "Artist Name", 1, 2, 2, 3600000, false)
    VlcPreview {
        MediaInfoScreen(
            item = dummyAlbum
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaInfoGenrePreview() {
    val dummyGenre = GenreImpl(1, "Livre audio", 6, 6, false)
    VlcPreview {
        MediaInfoScreen(
            item = dummyGenre
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MediaInfoPlaylistPreview() {
    val dummyPlaylist = PlaylistImpl(1, "dead_by_dawn.m3u", 18, 5400000, 0, 18, 0, 0, false)
    VlcPreview {
        MediaInfoScreen(
            item = dummyPlaylist
        )
    }
}
