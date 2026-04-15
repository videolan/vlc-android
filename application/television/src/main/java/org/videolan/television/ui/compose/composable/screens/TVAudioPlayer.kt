/*
 * ************************************************************************
 *  TVAudioPlayer.kt
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
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.Color.Companion.Yellow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.VLCOptions
import org.videolan.television.ui.compose.composable.components.ItemOptionsLine
import org.videolan.television.ui.compose.composable.components.LabeledIconButton
import org.videolan.television.ui.compose.composable.components.MiniVisualizer
import org.videolan.television.ui.compose.composable.components.PlayPause
import org.videolan.television.ui.compose.composable.items.BookmarkItem
import org.videolan.television.ui.compose.theme.BackgroundColorDarkTransparent50
import org.videolan.television.ui.compose.theme.Black
import org.videolan.television.ui.compose.theme.BlackTransparent25
import org.videolan.television.ui.compose.theme.BlackTransparent50
import org.videolan.television.ui.compose.theme.BlackTransparent70
import org.videolan.television.ui.compose.theme.BlackTransparent90
import org.videolan.television.ui.compose.theme.Grey900Transparent
import org.videolan.television.ui.compose.theme.Transparent
import org.videolan.television.ui.compose.theme.White
import org.videolan.television.ui.compose.theme.WhiteTransparent10
import org.videolan.television.ui.compose.theme.WhiteTransparent25
import org.videolan.television.ui.compose.theme.WhiteTransparent90
import org.videolan.television.ui.compose.utils.drawFadedEdge
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.Settings
import org.videolan.tools.formatRateString
import org.videolan.tools.px
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.EqualizerFragmentDialog
import org.videolan.vlc.gui.dialogs.JumpToTimeDialog
import org.videolan.vlc.gui.dialogs.PlaybackSpeedDialog
import org.videolan.vlc.gui.dialogs.SleepTimerDialog
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.getTvIconRes
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.viewmodels.BookmarkModel
import org.videolan.vlc.viewmodels.PlaylistModel
import kotlin.math.absoluteValue


@Composable
fun TVAudioPlayer() {
    var queueBackground by remember { mutableFloatStateOf(0F) }
    val density : Density = LocalDensity.current
    val dpValue = with(density){ queueBackground.toInt().toDp() }

    Box(Modifier.fillMaxSize()) {
        var blurredCover by remember { mutableStateOf<Bitmap?>(null) }

        blurredCover?.let {
            Image(modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Grey900Transparent, BlendMode.SrcAtop)
                )
        }

        Box(
            modifier = Modifier
                .height(dpValue)
                .fillMaxWidth(0.35F)
                .background(BlackTransparent50)
                .align(Alignment.TopEnd),
        ) {
            AudioPlayQueue()
        }

        Column{
            Row(
                Modifier
                    .weight(1F)
                    .fillMaxWidth()
            ) {
                Box(Modifier.weight(0.65f)) {
                    AudioCover( {
                        blurredCover = it
                    })
                }
                Box(
                    Modifier
                        .weight(0.35f)
                )
            }
            Column(Modifier.padding(horizontal = 32.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AudioPlayerControls({
                       queueBackground = it
                    })
                }
            }
        }

        AudioPlayerChips()

        Box(
            modifier = Modifier
                .height(dpValue)
                .fillMaxWidth()
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.BottomCenter
        ) {
            Bookmarks()
        }
    }
}

@Composable
fun Bookmarks(bookmarkModel: BookmarkModel = viewModel(), viewModel: PlaylistModel = viewModel()) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val showBookmarks = viewModel.showBookmarks.observeAsState()
    val bookmarkList = bookmarkModel.dataset.observeAsState()

    if (showBookmarks.value == true) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth(0.6F)
                    .weight(1f)
                    .focusGroup()
                    .background(BlackTransparent90)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .background(Black),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LabeledIconButton(
                        stringResource(R.string.close),
                        painterResource = painterResource(R.drawable.ic_close_up),
                        onClick = {
                            viewModel.showBookmarks.value = false
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Text(stringResource(R.string.bookmarks), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    LabeledIconButton(
                        stringResource(R.string.add_bookmark),
                        painterResource = painterResource(R.drawable.ic_add),
                        onClick = {
                            bookmarkModel.addBookmark(context)
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                bookmarkList.value?.let { bookmarks ->
                    LazyColumn(modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)) {
                        items(count = bookmarks.size) { index ->
                            BookmarkItem(bookmarks[index])
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LabeledIconButton(
                        stringResource(R.string.previous_bookmark),
                        painterResource = painterResource(R.drawable.ic_player_bookmark_previous),
                        onClick = {
                            bookmarkModel.findPrevious()?.let {
                                bookmarkModel.service?.setTime(it.time)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        tint = White
                    )
                    LabeledIconButton(
                        stringResource(R.string.talkback_action_rewind, Settings.audioJumpDelay),
                        painterResource = painterResource(R.drawable.ic_player_rewind_10),
                        tint = White,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        viewModel.jump(forward = false, long = false, activity!!)
                    }

                    LabeledIconButton(
                        stringResource(R.string.talkback_action_forward, Settings.audioJumpDelay),
                        painterResource = painterResource(R.drawable.ic_player_forward_10),
                        tint = White,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        viewModel.jump(forward = true, long = false, activity!!)
                    }

                    LabeledIconButton(
                        stringResource(R.string.next_bookmark),
                        painterResource = painterResource(R.drawable.ic_player_bookmark_next),
                        onClick = {
                            bookmarkModel.findNext()?.let {
                                bookmarkModel.service?.setTime(it.time)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        tint = White
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {

                //background
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6F)
                        .fillMaxHeight()
                        .background(BlackTransparent90)
                )
                BoxWithConstraints (
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    val boxWithConstraintsScope = this
                    val containerWidth = boxWithConstraintsScope.maxWidth.value - 16
                    viewModel.service?.currentMediaWrapper?.length?.let { mediaLength ->
                        bookmarkList.value?.forEach { bookmark ->
                            if (BuildConfig.DEBUG) Log.d("BookmarkTest", "Bookmark at ${bookmark.time} offset: ${bookmark.time.toFloat() * containerWidth / mediaLength.toFloat()}")
                            Icon(
                                painterResource(R.drawable.ic_bookmark_marker),
                                contentDescription = "",
                                tint = White,
                                modifier = Modifier
                                    .offset(x = (bookmark.time.toFloat() * containerWidth / mediaLength.toFloat()).dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerChips(viewModel: PlaylistModel = viewModel()) {
    Row(Modifier.padding(top = 32.dp, start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val context = LocalContext.current

        val playbackSpeed = viewModel.speed.observeAsState()
        playbackSpeed.value?.let { speed ->
            if (speed != 1.0f) {
                AudioPlayerChip(speed.formatRateString(), R.drawable.ic_speed, {
                    PlaybackSpeedDialog.newInstance().show((context as FragmentActivity).supportFragmentManager, "playback_speed")
                })
            }
        }

        val sleepTimerValue = PlaybackService.playerSleepTime.observeAsState()
        if (sleepTimerValue.value != null) {
            AudioPlayerChip(DateFormat.getTimeFormat(context).format(sleepTimerValue.value!!.time), R.drawable.ic_sleep, {
                SleepTimerDialog.newInstance().show((context as FragmentActivity).supportFragmentManager, "sleep")
            })
        }
    }
}

@Composable
fun AudioPlayerChip(text: String, icon: Int, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()

            }
            .background(if (isFocused) WhiteTransparent90 else BlackTransparent70, RoundedCornerShape(50))
            .focusable()
            .padding(4.dp)
    ) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(R.string.sleep_title),
            modifier = Modifier.size(24.dp),
            tint = if (isFocused) Black else White
        )
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = if (isFocused) Black else White
        )
    }
}

@Composable
fun AudioCover(coverListener:(Bitmap?) -> Unit, viewModel: PlaylistModel = viewModel()) {
    val playerState = viewModel.playerState.observeAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AudioUtil.readCoverBitmap(Uri.decode(viewModel.currentMediaWrapper?.artworkURL), 300)?.let { bitmap ->
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
            LaunchedEffect(viewModel.currentMediaWrapper?.artworkURL) {
                val bitmap = UiTools.blurBitmap(bitmap,  15F)
                coverListener(bitmap)
            }
        } ?: run {
            Image(painterResource(R.drawable.ic_song_big), contentDescription = "")
        }
            val edgeWidth = 16.dp

            Text(
                playerState.value?.title ?: "",
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawFadedEdge(edgeWidth.toPx(), leftEdge = true)
                        drawFadedEdge(edgeWidth.toPx(), leftEdge = false)
                    }
                    .basicMarquee()
                    .padding(horizontal = 16.dp),
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    shadow = Shadow(
                        color = Black, offset = Offset(0.0f, 0.0f), blurRadius = 3f
                    )
                )
            )
            Text(
                TextUtils.separatedString(viewModel.artist, viewModel.album),
                modifier = Modifier
                    .padding(top = 4.dp, start = 16.dp, end = 16.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawFadedEdge(edgeWidth.toPx(), leftEdge = true)
                        drawFadedEdge(edgeWidth.toPx(), leftEdge = false)
                    }
                    .basicMarquee()
                    .padding(horizontal = 16.dp),
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    shadow = Shadow(
                        color = Black, offset = Offset(0.0f, 0.0f), blurRadius = 3f
                    )
                )
            )
    }
}

@Composable
fun AudioPlayQueue(viewModel: PlaylistModel = viewModel()) {
    val queue = viewModel.dataset.observeAsState()
    queue.value?.let { queue ->
        LazyColumn {
            items(count = queue.size) { index ->
                AudioPlayerQueueItem(queue, index)
            }
        }
    }
}

@Composable
fun AudioPlayerQueueItem(queue: MutableList<MediaWrapper>, index: Int, viewModel: PlaylistModel = viewModel()) {
    var isFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val mapBitmap: MutableState<Pair<MediaLibraryItem, Bitmap?>?> = remember { mutableStateOf(null) }
    val item = queue[index]
    val currentMedia = PlaylistManager.currentPlayedMedia.observeAsState()
    Row(
        Modifier
            .onFocusChanged {
                isFocused = it.isFocused
            }
            .height(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (BuildConfig.DEBUG) Log.d("FocusTest", "Clicked on index: $index")
                viewModel.play(index)

            }
            .focusable()
            .background(
                color = if (isFocused) WhiteTransparent10 else Transparent,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )) {
        Box(
            Modifier
                .padding(8.dp)
                .size(48.dp), contentAlignment = Alignment.Center
        ) {
            if (currentMedia.value != item)
                if (mapBitmap.value?.second != null) {

                    Image(
                        bitmap = mapBitmap.value!!.second!!.asImageBitmap(),
                        contentDescription = "Map snapshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1F)
                    )
                } else {
                    Image(
                        painter = painterResource(id = getTvIconRes(item)),
                        contentDescription = "Map snapshot",
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1F)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    )
                    LaunchedEffect(key1 = "") {
                        coroutineScope.launch {
                            item.let {
                                if (item !is DummyItem)
                                    mapBitmap.value = Pair(item, ThumbnailsProvider.obtainBitmap(item = item, 280.dp.value.toInt()))
                            }
                        }
                    }
                }
            else
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .background(BackgroundColorDarkTransparent50, RoundedCornerShape(4.dp)), contentAlignment = Alignment.BottomCenter
                ) {
                    MiniVisualizer(MaterialTheme.colorScheme.secondary)
                }
        }
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(
                item.title ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )
            Text(
                MediaUtils.getMediaSubtitle(item),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
            )

        }
    }
}

@Composable
fun AudioPlayerControls(progressCoordinates: (Float) -> Unit, viewModel: PlaylistModel = viewModel()) {
    val playerState = viewModel.playerState.observeAsState()
    val repeatType = PlaylistManager.repeating.collectAsState()
    val shuffling = PlaylistManager.shuffling.collectAsState()
    val activity = LocalActivity.current


    AudioProgressBar({ progressCoordinates(it) })

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.width(56.dp))
        Spacer(Modifier.weight(1.0f))

        LabeledIconButton(
            stringResource(R.string.shuffle_title),
            painterResource = painterResource(R.drawable.ic_shuffle_audio),
            tint = if (shuffling.value) MaterialTheme.colorScheme.secondary else White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            viewModel.shuffle()
        }

        LabeledIconButton(
            stringResource(R.string.previous),
            painterResource = painterResource(R.drawable.ic_previous),
            tint = White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            viewModel.previous()
        }
0
        LabeledIconButton(
            stringResource(R.string.talkback_action_rewind, Settings.audioJumpDelay),
            painterResource = painterResource(R.drawable.ic_player_rewind_10),
            tint = White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            viewModel.jump(forward = false, long = false, activity!!)
        }

        LabeledIconButton(
            stringResource(R.string.air_action_play_pause),
            customImage = {
                PlayPause(
                    atEnd = playerState.value?.playing == true,
                    click = { viewModel.togglePlayPause() },
                )
            },
            tint = White,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            viewModel.togglePlayPause()
        }

        LabeledIconButton(
            stringResource(R.string.talkback_action_forward, Settings.audioJumpDelay),
            painterResource = painterResource(R.drawable.ic_player_forward_10),
            tint = White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            viewModel.jump(forward = true, long = false, activity!!)
        }

        LabeledIconButton(
            stringResource(R.string.next),
            painterResource = painterResource(R.drawable.ic_next),
            tint = White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            viewModel.next()
        }
        LabeledIconButton(
            stringResource(R.string.repeat_title),
            painterResource = when (repeatType.value) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> painterResource(R.drawable.ic_repeat_audio)
                PlaybackStateCompat.REPEAT_MODE_ONE -> painterResource(R.drawable.ic_repeat_one_audio)
                else -> painterResource(R.drawable.ic_repeat_all_audio)
            },
            tint = if (repeatType.value == PlaybackStateCompat.REPEAT_MODE_ALL) MaterialTheme.colorScheme.secondary else White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            when (viewModel.repeatType) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> {
                    viewModel.repeatType = PlaybackStateCompat.REPEAT_MODE_ALL
                }

                PlaybackStateCompat.REPEAT_MODE_ALL -> {
                    viewModel.repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
                }

                PlaybackStateCompat.REPEAT_MODE_ONE -> {
                    viewModel.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
                }
            }
        }
        Spacer(Modifier.weight(1.0f))
        AudioAdvancedOptions()
    }
}

@Composable
fun AudioAdvancedOptions(viewModel: PlaylistModel = viewModel()) {
    var expanded by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val settings = Settings.getInstance(context)
    Box{
        LabeledIconButton(
            stringResource(R.string.advanced_options),
            painterResource = painterResource(R.drawable.ic_more),
            tint = White,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            expanded = true
        }

        if (expanded)
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false }
            ) {

                ItemOptionsLine(stringResource(R.string.sleep_title), R.drawable.ic_sleep) {
                    SleepTimerDialog.newInstance().show((activity as FragmentActivity).supportFragmentManager, "sleep")
                    expanded = false
                }
                ItemOptionsLine(stringResource(R.string.playback_speed), R.drawable.ic_speed) {
                    PlaybackSpeedDialog.newInstance().show((activity as FragmentActivity).supportFragmentManager, "playback_speed")
                    expanded = false
                }
                ItemOptionsLine(stringResource(R.string.jump_to_time), R.drawable.ic_jumpto) {
                    JumpToTimeDialog.newInstance().show((activity as FragmentActivity).supportFragmentManager, "time")
                    expanded = false
                }
                ItemOptionsLine(stringResource(R.string.equalizer), R.drawable.ic_equalizer) {
                    EqualizerFragmentDialog.newInstance().show((activity as FragmentActivity).supportFragmentManager, "equalizer")
                    expanded = false
                }
                ItemOptionsLine(stringResource(R.string.bookmarks), R.drawable.ic_bookmark) {
                    viewModel.showBookmarks.value = true
                    expanded = false
                }
                ItemOptionsLine(stringResource(R.string.playlist_save), R.drawable.ic_addtoplaylist) {
                    viewModel.service?.let {
                        (activity as FragmentActivity).addToPlaylist(it.media)
                    }
                    expanded = false
                }
                if (viewModel.service?.playlistManager?.player?.canDoPassthrough() == true && settings.getString(KEY_AOUT, "0") != "2") {
                    val enabled = VLCOptions.isAudioDigitalOutputEnabled(settings)
                    ItemOptionsLine(
                        stringResource(R.string.audio_digital_title),
                        if (enabled) R.drawable.ic_passthrough_on else R.drawable.ic_passthrough,
                        enabled = enabled
                    ) {
                        val enabled = !VLCOptions.isAudioDigitalOutputEnabled(settings)
                        val toast by lazy(LazyThreadSafetyMode.NONE) { Toast.makeText(activity, "", Toast.LENGTH_SHORT) }
                        if (viewModel.service?.setAudioDigitalOutputEnabled(enabled) == true) {
                            VLCOptions.setAudioDigitalOutputEnabled(settings, enabled)
                            toast.setText(if (enabled) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled)
                        } else
                            toast.setText(R.string.audio_digital_failed)
                        toast.show()
                        expanded = false
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioProgressBar(progressCoordinates: (Float) -> Unit, viewModel: PlaylistModel = viewModel()) {
    val progress by viewModel.progress.observeAsState()
    var isDragging by remember { mutableStateOf(false) }
    var isDraggingForward by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val currentProgress = progress?.let { if (it.length > 0L) it.time.toFloat() / it.length else 0f } ?: 0f
    var draggingPosition by remember { mutableFloatStateOf(currentProgress) }

    // Synchronize draggingPosition with actual progress when not dragging
    if (!isDragging) {
        draggingPosition = currentProgress
    }

    Row(Modifier.fillMaxWidth()) {
        Text(progress?.timeText ?: "")
        Spacer(Modifier.weight(1.0f))
        Text(progress?.lengthText ?: "")
    }

    Slider(
        value = draggingPosition,
        onValueChange = {
            isDragging = true
            draggingPosition = it
        },
        onValueChangeFinished = {
            if (isDragging) {
                progress?.let {
                    // move forward/backward for at least 1 second
                    var newTime = (draggingPosition * it.length).toLong()
                    val currentTime = viewModel.getTime()
                    if ((newTime - currentTime).absoluteValue < 1000) newTime = if (isDraggingForward) currentTime + 1000 else currentTime - 1000
                    viewModel.setTime(newTime)
                }
                isDragging = false
            }
        },
        modifier = Modifier
            .padding(0.dp)
            .fillMaxWidth()
            .heightIn(max = 18.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .onGloballyPositioned {
                progressCoordinates(it.positionInRoot().y + (it.size.height / 2) - 3.dp.value)
            }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    when (it.key) {
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }

                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }

                        Key.DirectionLeft -> {
                            isDraggingForward = false
                            false
                        }

                        Key.DirectionRight -> {
                            isDraggingForward = true
                            false
                        }

                        else -> false
                    }
                } else false
            },
        thumb = {
            Label(
                label = {
                    PlainTooltip(
                        modifier = Modifier
                            .sizeIn(45.dp, 25.dp)
                            .wrapContentWidth(),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        progress?.let {
                            Text(Tools.millisToString((it.length.toFloat() * currentProgress).toLong()))
                        }
                    }
                },
                isPersistent = isFocused,
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .scale(if (isFocused || isDragging) 1F else 0.8F)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(20.dp)

                        )
                )
            }
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                thumbTrackGapSize = 0.dp,
                modifier = Modifier.height(6.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = WhiteTransparent25,
                ),
                drawStopIndicator = null,
            )
        }
    )
}
