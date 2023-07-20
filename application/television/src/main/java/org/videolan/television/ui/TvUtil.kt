/*****************************************************************************
 * TvUtil.java
 *
 * Copyright © 2014-2017 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
 *
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
 */
package org.videolan.television.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore.Video.VideoColumns.CATEGORY
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.ListRow
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.resources.*
import org.videolan.television.ui.audioplayer.AudioPlayerActivity
import org.videolan.television.ui.browser.TVActivity
import org.videolan.television.ui.browser.VerticalGridActivity
import org.videolan.television.ui.details.MediaListActivity
import org.videolan.tools.FORCE_PLAY_ALL_VIDEO
import org.videolan.tools.HttpImageLoader
import org.videolan.tools.Settings
import org.videolan.tools.getposition
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.util.ThumbnailsProvider
import org.videolan.vlc.util.getScreenHeight
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.browser.BrowserModel
import java.util.*

object TvUtil {

    private const val TAG = "VLC/TvUtil"

    var diffCallback: DiffCallback<MediaLibraryItem> = object : DiffCallback<MediaLibraryItem>() {
        override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            return oldItem.equals(newItem) && oldItem.title == newItem.title
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            if (oldItem.itemType == MediaLibraryItem.TYPE_DUMMY) return oldItem.description == newItem.description
            val oldMedia = oldItem as? MediaWrapper
                    ?: return true
            val newMedia = newItem as? MediaWrapper
                    ?: return true
            return oldMedia === newMedia || (oldMedia.time == newMedia.time
                    && oldMedia.artworkMrl == newMedia.artworkMrl
                    && oldMedia.seen == newMedia.seen)
        }

        override fun getChangePayload(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Any {
            if (oldItem.itemType == MediaLibraryItem.TYPE_DUMMY) return UPDATE_DESCRIPTION
            val oldMedia = oldItem as MediaWrapper
            val newMedia = newItem as MediaWrapper
            if (oldMedia.time != newMedia.time) return UPDATE_TIME
            return if (oldMedia.artworkMrl != newMedia.artworkMrl) UPDATE_THUMB
            else UPDATE_SEEN
        }
    }

    var metadataDiffCallback = object : DiffCallback<MediaMetadataWithImages>() {
        override fun areItemsTheSame(oldItem: MediaMetadataWithImages, newItem: MediaMetadataWithImages) = oldItem.metadata.moviepediaId == newItem.metadata.moviepediaId

        override fun areContentsTheSame(oldItem: MediaMetadataWithImages, newItem: MediaMetadataWithImages) = oldItem.metadata.moviepediaId == newItem.metadata.moviepediaId && oldItem.metadata.title == newItem.metadata.title && oldItem.metadata.currentPoster == newItem.metadata.currentPoster
    }

    val listDiffCallback: DiffCallback<ListRow> = object : DiffCallback<ListRow>() {
        override fun areItemsTheSame(oldItem: ListRow, newItem: ListRow) = oldItem.contentDescription == newItem.contentDescription
        override fun areContentsTheSame(oldItem: ListRow, newItem: ListRow) = true
    }

    fun getOverscanHorizontal(context: Context) = context.resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal)
    fun getOverscanVertical(context: Context) = context.resources.getDimensionPixelSize(R.dimen.tv_overscan_vertical)

    fun playMedia(activity: Activity, media: MediaWrapper) {
        if (media.type == MediaWrapper.TYPE_AUDIO) {
            val tracks = ArrayList<MediaWrapper>()
            tracks.add(media)
            playMedia(activity, tracks)
        } else
            MediaUtils.openMedia(activity, media)
    }

    fun playMedia(activity: Activity, media: List<MediaWrapper>, position: Int = 0) {
        val intent = Intent(activity, AudioPlayerActivity::class.java)
        intent.putExtra(AudioPlayerActivity.MEDIA_LIST, ArrayList(media))
        intent.putExtra(AudioPlayerActivity.MEDIA_POSITION, position)
        activity.startActivity(intent)
    }

    fun playPlaylist(activity: Activity, playlist: Playlist, position: Int = 0) {
        val intent = Intent(activity, AudioPlayerActivity::class.java)
        intent.putExtra(AudioPlayerActivity.MEDIA_PLAYLIST, playlist.id)
        intent.putExtra(AudioPlayerActivity.MEDIA_POSITION, position)
        activity.startActivity(intent)
    }

    @Suppress("UNCHECKED_CAST")
    fun openMedia(activity: FragmentActivity, item: Any?) {
        when (item) {
            is MediaWrapper -> when (item.type) {
                MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                else -> {
                   MediaUtils.openMedia(activity, item)
                }
            }
            is DummyItem -> when (item.id) {
                HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    fun openMedia(activity: FragmentActivity, item: Any?, model: BrowserModel) {
        when (item) {
            is MediaWrapper -> when (item.type) {
                MediaWrapper.TYPE_AUDIO -> {
                    val list = (model.dataset.getList().filterIsInstance<MediaWrapper>()).filter { it.type != MediaWrapper.TYPE_DIR }
                    val position = list.getposition(item)
                    playAudioList(activity, list, position)
                }
                MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                else -> {
                    model.run {
                        if (!Settings.getInstance(activity).getBoolean(FORCE_PLAY_ALL_VIDEO, Settings.tvUI)) {
                            MediaUtils.openMedia(activity, item)
                        } else {
                            val list = (dataset.getList().filterIsInstance<MediaWrapper>()).filter { it.type != MediaWrapper.TYPE_DIR }
                            val position = list.getposition(item)
                            MediaUtils.openList(activity, list, position)
                        }
                    }
                }
            }
            is DummyItem -> when (item.id) {
                HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun openMediaFromPaged(activity: FragmentActivity, item: Any?, provider: MedialibraryProvider<out MediaLibraryItem>) {
        when (item) {
            is MediaWrapper -> when (item.type) {
                MediaWrapper.TYPE_AUDIO -> {
                    provider.loadPagedList(activity, {
                        (provider.getAll().toList()).filter { it.itemType != MediaWrapper.TYPE_DIR } as ArrayList<MediaWrapper>
                    }, { list, _ ->
                        playAudioList(activity, list, list.getposition(item))
                    })
                }
                MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                else -> {
                    provider.loadPagedList(activity, {
                        (provider.getAll().toList() as List<MediaWrapper>).filter { it.type != MediaWrapper.TYPE_DIR }
                    }, { list, _ ->
                        MediaUtils.openList(activity, list, list.getposition(item))
                    })
                }
            }
            is DummyItem -> when (item.id) {
                HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    fun showMediaDetail(activity: Context, mediaWrapper: MediaWrapper, fromHistory:Boolean = false) {
        val intent = Intent(activity, DetailsActivity::class.java)
        intent.putExtra("media", mediaWrapper)
        intent.putExtra("item", MediaItemDetails(mediaWrapper.title, mediaWrapper.artist, mediaWrapper.album, mediaWrapper.location, mediaWrapper.artworkURL))
        if (fromHistory) intent.putExtra(EXTRA_FROM_HISTORY, fromHistory)
        activity.startActivity(intent)
    }

    private fun playAudioList(activity: Activity, list: List<MediaWrapper>, position: Int) {
        MediaUtils.openList(activity, list, position)
        val intent = Intent(activity, AudioPlayerActivity::class.java)
        activity.startActivity(intent)
    }

    fun openAudioCategory(context: Activity, mediaLibraryItem: MediaLibraryItem) {
        when (mediaLibraryItem.itemType) {
            MediaLibraryItem.TYPE_ALBUM, MediaLibraryItem.TYPE_PLAYLIST -> {
                val intent = Intent(context, MediaListActivity::class.java)
                intent.putExtra(EXTRA_ITEM, mediaLibraryItem)
                context.startActivity(intent)
            }
            MediaLibraryItem.TYPE_MEDIA -> {
                val list = ArrayList<MediaWrapper>().apply { add(mediaLibraryItem as MediaWrapper) }
                playAudioList(context, list, 0)
            }
            else -> {
                val intent = Intent(context, VerticalGridActivity::class.java)
                intent.putExtra(EXTRA_ITEM, mediaLibraryItem)
                intent.putExtra(CATEGORY, CATEGORY_ALBUMS)
                intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_CATEGORIES)
                context.startActivity(intent)
            }
        }
    }
}

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
fun CoroutineScope.updateBackground(activity: Activity, bm: BackgroundManager?, item: Any?) {
    clearBackground(activity, bm)
    if (bm === null || item === null)  return
    val screenRatio: Float = activity.getScreenWidth().toFloat() / activity.getScreenHeight()
    if (item is MediaLibraryItem) launch {
        val artworkMrl = item.artworkMrl
        if (!artworkMrl.isNullOrEmpty()) {
            val blurred = withContext(Dispatchers.IO) {
                var cover = AudioUtil.readCoverBitmap(Uri.decode(artworkMrl), 512)
                        ?: return@withContext null
                cover = BitmapUtil.centerCrop(cover, cover.width, (cover.width / screenRatio).toInt())
                UiTools.blurBitmap(cover, 10f)
            }
            if (!isActive) return@launch
            blurred?.let { bm.drawable = BitmapDrawable(activity.resources, it) }
        } else if (item.itemType == MediaLibraryItem.TYPE_PLAYLIST) {
            val blurred = withContext(Dispatchers.IO) {
                var cover: Bitmap? = ThumbnailsProvider.getPlaylistOrGenreImage("playlist:${item.id}_512", item.tracks.toList(), 512)
                        ?: return@withContext null
                cover = cover?.let { BitmapUtil.centerCrop(it, it.width, (it.width / screenRatio).toInt()) }
                UiTools.blurBitmap(cover, 10f)
            }
            if (!isActive) return@launch
            blurred?.let { bm.drawable = BitmapDrawable(activity.resources, it) }
        }
    } else if (item is MediaMetadataWithImages) launch {
        val blurred = withContext(Dispatchers.IO) {
            var cover: Bitmap? = HttpImageLoader.downloadBitmap(item.metadata.currentPoster)
            cover?.let { cover = BitmapUtil.centerCrop(it, it.width, (it.width / screenRatio).toInt()) }
            UiTools.blurBitmap(cover, 10f)
        }
        if (!isActive) return@launch
        blurred?.let { bm.drawable = BitmapDrawable(activity.resources, it) }

    }
}

fun clearBackground(context: Context, bm: BackgroundManager?) {
    if (bm === null) return
    bm.color = ContextCompat.getColor(context, R.color.tv_bg)
    bm.drawable = null
}
