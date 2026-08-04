package org.videolan.television.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore.Video.VideoColumns.CATEGORY
import androidx.fragment.app.FragmentActivity
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.DummyItem
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.television.util.EXTRA_ITEM
import org.videolan.television.util.EXTRA_ITEM
import org.videolan.vlc.R
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.media.MediaUtils

object TvUtil {

    private const val TAG = "VLC/TvUtil"

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
        MediaUtils.openList(activity, media, position)
    }

    fun playPlaylist(activity: Activity, playlist: Playlist, position: Int = 0) {
        MediaUtils.openPlaylist(activity, playlist.id, position)
    }

    @Suppress("UNCHECKED_CAST")
    fun openMedia(activity: FragmentActivity, item: Any?) {
        when (item) {
            is MediaWrapper -> when (item.type) {
                MediaWrapper.TYPE_DIR -> {
                    val intent = Intent(activity, BrowserActivity::class.java)
                    intent.putExtra(EXTRA_ITEM, item)
                    intent.putExtra(CATEGORY, CATEGORY_ALBUMS)
                    intent.putExtra(BROWSER_TYPE, HEADER_CATEGORIES)
                    activity.startActivity(intent)
                }
                else -> {
                   MediaUtils.openMedia(activity, item)
                }
            }
            is DummyItem -> when (item.id) {
                HEADER_STREAM, HEADER_ADD_STREAM -> {
                    val intent = Intent(activity, StreamActivity::class.java)
                    intent.putExtra(BROWSER_TYPE, HEADER_STREAM)
                    activity.startActivity(intent)
                }
                HEADER_SERVER -> activity.startActivity(Intent(activity, DialogActivity::class.java).setAction(DialogActivity.KEY_SERVER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {

                }
            }
            is MediaLibraryItem -> openAudioCategory(activity, item)
        }
    }

    private fun playAudioList(activity: Activity, list: List<MediaWrapper>, position: Int) {
        MediaUtils.openList(activity, list, position)
    }

    fun openAudioCategory(context: Activity, mediaLibraryItem: MediaLibraryItem) {
        when (mediaLibraryItem.itemType) {
            MediaLibraryItem.TYPE_ALBUM, MediaLibraryItem.TYPE_PLAYLIST, MediaLibraryItem.TYPE_ARTIST, MediaLibraryItem.TYPE_GENRE -> {
                val intent = Intent(context, AudioCategoryActivity::class.java)
                intent.putExtra(EXTRA_ITEM, mediaLibraryItem)
                context.startActivity(intent)
            }
            MediaLibraryItem.TYPE_MEDIA -> {
                val list = ArrayList<MediaWrapper>().apply { add(mediaLibraryItem as MediaWrapper) }
                playAudioList(context, list, 0)
            }
            else -> {
                val intent = Intent(context, BrowserActivity::class.java)
                intent.putExtra(EXTRA_ITEM, mediaLibraryItem)
                context.startActivity(intent)
            }
        }
    }
}
