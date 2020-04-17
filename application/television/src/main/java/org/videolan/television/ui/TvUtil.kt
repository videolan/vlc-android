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
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BackgroundManager
import androidx.leanback.widget.DiffCallback
import androidx.leanback.widget.ListRow
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.resources.*
import org.videolan.television.ui.audioplayer.AudioPlayerActivity
import org.videolan.television.ui.browser.TVActivity
import org.videolan.television.ui.browser.VerticalGridActivity
import org.videolan.television.ui.details.MediaListActivity
import org.videolan.tools.HttpImageLoader
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
import org.videolan.vlc.viewmodels.BaseModel
import org.videolan.vlc.viewmodels.browser.BrowserModel

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
object TvUtil {

    private const val TAG = "VLC/TvUtil"

    var diffCallback: DiffCallback<MediaLibraryItem> = object : DiffCallback<MediaLibraryItem>() {
        override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            return oldItem.equals(newItem)
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            if (oldItem.itemType == MediaLibraryItem.TYPE_DUMMY) return TextUtils.equals(oldItem.description, newItem.description)
            val oldMedia = oldItem as? MediaWrapper
                    ?: return true
            val newMedia = newItem as? MediaWrapper
                    ?: return true
            return oldMedia === newMedia || (oldMedia.time == newMedia.time
                    && TextUtils.equals(oldMedia.artworkMrl, newMedia.artworkMrl)
                    && oldMedia.seen == newMedia.seen)
        }

        override fun getChangePayload(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Any {
            if (oldItem.itemType == MediaLibraryItem.TYPE_DUMMY) return UPDATE_DESCRIPTION
            val oldMedia = oldItem as MediaWrapper
            val newMedia = newItem as MediaWrapper
            if (oldMedia.time != newMedia.time) return UPDATE_TIME
            return if (!TextUtils.equals(oldMedia.artworkMrl, newMedia.artworkMrl)) UPDATE_THUMB
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

    @Suppress("UNCHECKED_CAST")
    fun openMedia(activity: FragmentActivity, item: Any?, model: BaseModel<out MediaLibraryItem>?) {
        when (item) {
            is MediaWrapper -> when {
                item.type == MediaWrapper.TYPE_AUDIO -> {
                    val list = (model?.dataset?.getList() as? List<MediaWrapper>)?.filter { it.type != MediaWrapper.TYPE_DIR }
                            ?: return
                    val position = list.getposition(item)
                    playAudioList(activity, list, position)
                }
                item.type == MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                item.type == MediaWrapper.TYPE_GROUP -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, HEADER_VIDEO)
                    val title = item.title.substring(if (item.title.toLowerCase().startsWith("the")) 4 else 0)
                    intent.putExtra(KEY_GROUP, title)
                    activity.startActivity(intent)
                }
                else -> {
                    model?.run {
                        val list = (dataset.getList() as List<MediaWrapper>).filter { it.type != MediaWrapper.TYPE_DIR }
                        val position = list.getposition(item)
                        MediaUtils.openList(activity, list, position)
                    } ?: MediaUtils.openMedia(activity, item)
                }
            }
            is DummyItem -> when {
                item.id == HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                item.id == HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun openMedia(activity: FragmentActivity, item: Any?, model: BrowserModel) {
        when (item) {
            is MediaWrapper -> when {
                item.type == MediaWrapper.TYPE_AUDIO -> {
                    val list = (model.dataset.getList() as List<MediaWrapper>).filter { it.type != MediaWrapper.TYPE_DIR }
                    val position = list.getposition(item)
                    playAudioList(activity, list, position)
                }
                item.type == MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                item.type == MediaWrapper.TYPE_GROUP -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, HEADER_VIDEO)
                    val title = item.title.substring(if (item.title.toLowerCase().startsWith("the")) 4 else 0)
                    intent.putExtra(KEY_GROUP, title)
                    activity.startActivity(intent)
                }
                else -> {
                    model.run {
                        val list = (dataset.getList() as List<MediaWrapper>).filter { it.type != MediaWrapper.TYPE_DIR }
                        val position = list.getposition(item)
                        MediaUtils.openList(activity, list, position)
                    }
                }
            }
            is DummyItem -> when {
                item.id == HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                item.id == HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun openMediaFromPaged(activity: FragmentActivity, item: Any?, provider: MedialibraryProvider<out MediaLibraryItem>) {
        when (item) {
            is MediaWrapper -> when {
                item.type == MediaWrapper.TYPE_AUDIO -> {
                    val list = withContext(Dispatchers.IO) {
                        (provider.getAll().toList()).filter { it.itemType != MediaWrapper.TYPE_DIR } as ArrayList<MediaWrapper>
                    }
                    val position = list.getposition(item)
                    playAudioList(activity, list, position)
                }
                item.type == MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) HEADER_DIRECTORIES else HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                item.type == MediaWrapper.TYPE_GROUP -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, HEADER_VIDEO)
                    val title = item.title.substring(if (item.title.toLowerCase().startsWith("the")) 4 else 0)
                    intent.putExtra(KEY_GROUP, title)
                    activity.startActivity(intent)
                }
                else -> {
                    val list = withContext(Dispatchers.IO) {
                        (provider.getAll().toList() as List<MediaWrapper>).filter { it.type != MediaWrapper.TYPE_DIR }
                    }
                    val position = list.getposition(item)
                    MediaUtils.openList(activity, list, position)
                }
            }
            is DummyItem -> when {
                item.id == HEADER_STREAM -> {
                    val intent = Intent(activity, TVActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                item.id == HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, item.id)
                    activity.startActivity(intent)
                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    fun showMediaDetail(activity: Context, mediaWrapper: MediaWrapper) {
        val intent = Intent(activity, org.videolan.television.ui.DetailsActivity::class.java)
        intent.putExtra("media", mediaWrapper)
        intent.putExtra("item", org.videolan.television.ui.MediaItemDetails(mediaWrapper.title, mediaWrapper.artist, mediaWrapper.album, mediaWrapper.location, mediaWrapper.artworkURL))
        activity.startActivity(intent)
    }

    fun browseFolder(activity: Activity, type: Long, uri: Uri) {
        val intent = Intent(activity, VerticalGridActivity::class.java)
        intent.putExtra(org.videolan.television.ui.MainTvActivity.BROWSER_TYPE, type)
        intent.data = uri
        activity.startActivity(intent)
    }

    private fun playAudioList(activity: Activity, array: Array<MediaWrapper>, position: Int) {
        playAudioList(activity, array.toList(), position)
    }

    private fun playAudioList(activity: Activity, list: List<MediaWrapper>, position: Int) {
        MediaUtils.openList(activity, list, position)
        val intent = Intent(activity, AudioPlayerActivity::class.java)
        activity.startActivity(intent)
    }

    fun openAudioCategory(context: Activity, mediaLibraryItem: MediaLibraryItem) {
        when {
            mediaLibraryItem.itemType == MediaLibraryItem.TYPE_ALBUM || mediaLibraryItem.itemType == MediaLibraryItem.TYPE_PLAYLIST -> {
                val intent = Intent(context, MediaListActivity::class.java)
                intent.putExtra(ITEM, mediaLibraryItem)
                context.startActivity(intent)
            }
            mediaLibraryItem.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                val list = ArrayList<MediaWrapper>().apply { add(mediaLibraryItem as MediaWrapper) }
                playAudioList(context, list, 0)
            }
            else -> {
                val intent = Intent(context, VerticalGridActivity::class.java)
                intent.putExtra(ITEM, mediaLibraryItem)
                intent.putExtra(CATEGORY, CATEGORY_ALBUMS)
                intent.putExtra(MainTvActivity.BROWSER_TYPE, HEADER_CATEGORIES)
                context.startActivity(intent)
            }
        }
    }
}

@Suppress("UNNECESSARY_SAFE_CALL")
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
fun CoroutineScope.updateBackground(activity: Activity, bm: BackgroundManager?, item: Any?) {
    if (bm === null || item === null) {
        clearBackground(activity, bm)
        return
    }
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
            bm?.color = 0
            bm?.drawable = BitmapDrawable(activity.resources, blurred)
        } else if (item.itemType == MediaLibraryItem.TYPE_PLAYLIST) {
            val blurred = withContext(Dispatchers.IO) {
                var cover: Bitmap? = ThumbnailsProvider.getPlaylistImage("playlist:${item.id}", item.tracks.toList(), 512)
                        ?: return@withContext null
                cover = cover?.let { BitmapUtil.centerCrop(it, it.width, (it.width / screenRatio).toInt()) }
                UiTools.blurBitmap(cover, 10f)
            }
            if (!isActive) return@launch
            bm?.color = 0
            bm?.drawable = BitmapDrawable(activity.resources, blurred)
        }
    } else if (item is MediaMetadataWithImages) launch {
        val blurred = withContext(Dispatchers.IO) {
            var cover: Bitmap? = HttpImageLoader.downloadBitmap(item.metadata.currentPoster)
            cover?.let { cover = BitmapUtil.centerCrop(it, it.width, (it.width / screenRatio).toInt()) }
            UiTools.blurBitmap(cover, 10f)
        }
        if (!isActive) return@launch
        bm?.color = 0
        bm?.drawable = BitmapDrawable(activity.resources, blurred)

    }
}

fun clearBackground(context: Context, bm: BackgroundManager?) {
    if (bm === null) return
    bm.color = ContextCompat.getColor(context, R.color.tv_bg)
    bm.drawable = null
}
