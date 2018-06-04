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
package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.widget.DiffCallback
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.Row
import android.support.v4.content.ContextCompat
import android.text.TextUtils
import android.view.View
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.BitmapUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity
import org.videolan.vlc.gui.tv.browser.VerticalGridActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Constants
import org.videolan.vlc.util.Constants.*
import java.util.*
import kotlin.collections.ArrayList

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
object TvUtil {

    private const val TAG = "VLC/TvUtil"

    var diffCallback: DiffCallback<MediaLibraryItem> = object : DiffCallback<MediaLibraryItem>() {
        override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            return oldItem.equals(newItem)
        }

        override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            if (oldItem.itemType == MediaLibraryItem.TYPE_DUMMY) return TextUtils.equals(oldItem.description, newItem.description)
            if (oldItem.itemType != MediaLibraryItem.TYPE_MEDIA) return true
            val oldMedia = oldItem as MediaWrapper
            val newMedia = newItem as MediaWrapper
            return oldMedia === newMedia || (oldMedia.time == newMedia.time
                    && TextUtils.equals(oldMedia.artworkMrl, newMedia.artworkMrl)
                    && oldMedia.seen == newMedia.seen)
        }

        override fun getChangePayload(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Any {
            if (oldItem.itemType == MediaLibraryItem.TYPE_DUMMY) return Constants.UPDATE_DESCRIPTION
            val oldMedia = oldItem as MediaWrapper
            val newMedia = newItem as MediaWrapper
            if (oldMedia.time != newMedia.time) return Constants.UPDATE_TIME
            return if (!TextUtils.equals(oldMedia.artworkMrl, newMedia.artworkMrl)) Constants.UPDATE_THUMB
            else Constants.UPDATE_SEEN
        }
    }

    val listDiffCallback: DiffCallback<ListRow> = object : DiffCallback<ListRow>() {
        override fun areItemsTheSame(oldItem: ListRow, newItem: ListRow) = oldItem.contentDescription == newItem.contentDescription
        override fun areContentsTheSame(oldItem: ListRow, newItem: ListRow) = true
    }

    fun applyOverscanMargin(activity: Activity) {
        val hm = activity.resources.getDimensionPixelSize(R.dimen.tv_overscan_horizontal)
        val vm = activity.resources.getDimensionPixelSize(R.dimen.tv_overscan_vertical)
        activity.findViewById<View>(android.R.id.content).setPadding(hm, vm, hm, vm)
    }

    fun playMedia(activity: Activity, media: MediaWrapper) {
        if (media.type == MediaWrapper.TYPE_AUDIO) {
            val tracks = ArrayList<MediaWrapper>()
            tracks.add(media)
            val intent = Intent(activity, AudioPlayerActivity::class.java)
            intent.putExtra(AudioPlayerActivity.MEDIA_LIST, tracks)
            activity.startActivity(intent)
        } else
            MediaUtils.openMedia(activity, media)
    }

    fun openMedia(activity: Activity, item: Any?, row: Row?) {
        when (item) {
            is MediaWrapper -> when {
                item.type == MediaWrapper.TYPE_AUDIO -> openAudioCategory(activity, item)
                item.type == MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, if ("file" == item.uri.scheme) Constants.HEADER_DIRECTORIES else Constants.HEADER_NETWORK)
                    intent.data = item.uri
                    activity.startActivity(intent)
                }
                item.type == MediaWrapper.TYPE_GROUP -> {
                    val intent = Intent(activity, VerticalGridActivity::class.java)
                    intent.putExtra(MainTvActivity.BROWSER_TYPE, Constants.HEADER_VIDEO)
                    val title = item.title.substring(if (item.title.toLowerCase().startsWith("the")) 4 else 0)
                    intent.putExtra(Constants.KEY_GROUP, title)
                    activity.startActivity(intent)
                }
                else -> MediaUtils.openMedia(activity, item)
            }
            is DummyItem -> if (item.id == Constants.HEADER_STREAM) {
                activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_STREAM)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                val intent = Intent(activity, VerticalGridActivity::class.java)
                intent.putExtra(MainTvActivity.BROWSER_TYPE, item.id)
                activity.startActivity(intent)
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    fun showMediaDetail(activity: Context, mediaWrapper: MediaWrapper) {
        val intent = Intent(activity, DetailsActivity::class.java)
        intent.putExtra("media", mediaWrapper)
        intent.putExtra("item", MediaItemDetails(mediaWrapper.title, mediaWrapper.artist, mediaWrapper.album, mediaWrapper.location, mediaWrapper.artworkURL))
        activity.startActivity(intent)
    }

    fun browseFolder(activity: Activity, type: Long, uri: Uri) {
        val intent = Intent(activity, VerticalGridActivity::class.java)
        intent.putExtra(MainTvActivity.BROWSER_TYPE, type)
        intent.data = uri
        activity.startActivity(intent)
    }

    private fun playAudioList(activity: Activity, array: Array<MediaWrapper>, position: Int) {
        playAudioList(activity, ArrayList(Arrays.asList(*array)), position)
    }

    fun playAudioList(activity: Activity, list: ArrayList<MediaWrapper>, position: Int) {
        val intent = Intent(activity, AudioPlayerActivity::class.java)
        intent.putExtra(AudioPlayerActivity.MEDIA_LIST, list)
        intent.putExtra(AudioPlayerActivity.MEDIA_POSITION, position)
        activity.startActivity(intent)
    }

    fun openAudioCategory(context: Activity, mediaLibraryItem: MediaLibraryItem) {
        when {
            mediaLibraryItem.itemType == MediaLibraryItem.TYPE_ALBUM -> TvUtil.playAudioList(context, mediaLibraryItem.tracks, 0)
            mediaLibraryItem.itemType == MediaLibraryItem.TYPE_MEDIA -> {
                val list = ArrayList<MediaWrapper>().apply { add(mediaLibraryItem as MediaWrapper) }
                playAudioList(context, list, 0)
            }
            else -> {
                val intent = Intent(context, VerticalGridActivity::class.java)
                intent.putExtra(AUDIO_ITEM, mediaLibraryItem)
                intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS)
                intent.putExtra(MainTvActivity.BROWSER_TYPE, Constants.HEADER_CATEGORIES)
                context.startActivity(intent)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun updateBackground(bm: BackgroundManager?, item: Any?) {
        if (bm === null || item === null) return
        if (item is MediaLibraryItem) launch(UI, CoroutineStart.UNDISPATCHED){
            val crop = item.itemType != MediaLibraryItem.TYPE_MEDIA || (item as MediaWrapper).type == MediaWrapper.TYPE_AUDIO
            val artworkMrl = item.artworkMrl
            if (!TextUtils.isEmpty(artworkMrl)) {
                val blurred = withContext(CommonPool) {
                    if (bm == null) return@withContext null
                    var cover: Bitmap? = AudioUtil.readCoverBitmap(Uri.decode(artworkMrl), 512)
                            ?: return@withContext null
                    if (crop)
                        cover = BitmapUtil.centerCrop(cover, cover!!.width, cover.width * 10 / 16)
                    UiTools.blurBitmap(cover, 10f)
                }
                if (bm !== null) {
                    bm.color = 0
                    bm.drawable = BitmapDrawable(VLCApplication.getAppResources(), blurred)
                }
            }
        }
        clearBackground(bm)
    }

    private fun clearBackground(bm: BackgroundManager) {
        bm.color = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.tv_bg)
        bm.drawable = null
    }

    fun getIconRes(mediaLibraryItem: MediaLibraryItem): Int {
        when (mediaLibraryItem.itemType) {
            MediaLibraryItem.TYPE_ALBUM -> return R.drawable.ic_album_big
            MediaLibraryItem.TYPE_ARTIST -> return R.drawable.ic_artist_big
            MediaLibraryItem.TYPE_GENRE -> return R.drawable.ic_genre_big
            MediaLibraryItem.TYPE_MEDIA -> {
                val mw = mediaLibraryItem as MediaWrapper
                return when {
                    mw.type == MediaWrapper.TYPE_VIDEO -> R.drawable.ic_browser_video_big_normal
                    else -> if (mw.type == MediaWrapper.TYPE_DIR && TextUtils.equals(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY, mw.location))
                        R.drawable.ic_menu_folder_big
                    else
                        R.drawable.ic_song_big
                }
            }
            MediaLibraryItem.TYPE_DUMMY -> {
                return when (mediaLibraryItem.id) {
                    Constants.HEADER_VIDEO -> R.drawable.ic_video_collection_big
                    Constants.HEADER_DIRECTORIES -> R.drawable.ic_menu_folder_big
                    Constants.HEADER_NETWORK -> R.drawable.ic_menu_network_big
                    Constants.HEADER_STREAM -> R.drawable.ic_menu_stream_big
                    Constants.ID_SETTINGS -> R.drawable.ic_menu_preferences_big
                    Constants.ID_ABOUT_TV, Constants.ID_LICENCE -> R.drawable.ic_default_cone
                    Constants.CATEGORY_ARTISTS -> R.drawable.ic_artist_big
                    Constants.CATEGORY_ALBUMS -> R.drawable.ic_album_big
                    Constants.CATEGORY_GENRES -> R.drawable.ic_genre_big
                    Constants.CATEGORY_SONGS, Constants.CATEGORY_NOW_PLAYING -> R.drawable.ic_song_big
                    else -> R.drawable.ic_browser_unknown_big_normal
                }
            }
            else -> return R.drawable.ic_browser_unknown_big_normal
        }
    }
}
