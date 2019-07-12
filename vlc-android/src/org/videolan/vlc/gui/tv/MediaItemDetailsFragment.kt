/*****************************************************************************
 * MediaItemDetailsFragment.java
 *
 * Copyright © 2014-2019 VLC authors, VideoLAN and VideoLabs
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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.ACTION_REMOTE_STOP
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.runIO
import org.videolan.vlc.util.runOnMainThread

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MediaItemDetailsFragment : DetailsSupportFragment() {

    private lateinit var backgroundManager: BackgroundManager
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var mediaItemDetails: MediaItemDetails
    private lateinit var mediaWrapper: AbstractMediaWrapper
    private lateinit var browserFavRepository: BrowserFavRepository
    private var mediaStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundManager = BackgroundManager.getInstance(requireActivity())
        backgroundManager.isAutoReleaseOnStop = false
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
        mediaStarted = false
        buildDetails()
    }

    override fun onResume() {
        super.onResume()
        if (!backgroundManager.isAttached) backgroundManager.attachToView(view)
    }

    override fun onPause() {
        backgroundManager.release()
        super.onPause()
        if (mediaStarted) {
            val ctx = context
            ctx?.sendBroadcast(Intent(ACTION_REMOTE_STOP))
        }
    }

    private fun buildDetails() {
        val extras = requireActivity().intent.extras ?: return
        mediaItemDetails = extras.getParcelable("item") as MediaItemDetails
        val hasMedia = extras.containsKey("media")
        val selector = ClassPresenterSelector()
        val media = (if (hasMedia) extras.getParcelable<Parcelable>("media") else MLServiceLocator.getAbstractMediaWrapper(AndroidUtil.LocationToUri(mediaItemDetails.location))) as AbstractMediaWrapper
        if (!hasMedia) {
            media.setDisplayTitle(mediaItemDetails.title)
        }
        mediaWrapper = media
        title = media.title

        // Attach your media item details presenter to the row presenter:
        val rowPresenter = FullWidthDetailsOverviewRowPresenter(DetailsDescriptionPresenter())

        val activity = requireActivity()
        val detailsOverview = DetailsOverviewRow(mediaItemDetails)
        val actionAdd = Action(ID_FAVORITE_ADD.toLong(), getString(R.string.favorites_add))
        val actionDelete = Action(ID_FAVORITE_DELETE.toLong(), getString(R.string.favorites_remove))

        rowPresenter.backgroundColor = ContextCompat.getColor(activity, R.color.orange500)
        rowPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id.toInt()) {
                ID_LISTEN -> {
                    MediaUtils.openMedia(activity, mediaWrapper)
                    mediaStarted = true
                }
                ID_PLAY -> {
                    mediaStarted = false
                    TvUtil.playMedia(activity, media)
                    activity.finish()
                }
                ID_PLAYLIST -> {
                    UiTools.addToPlaylist(requireActivity(), arrayListOf(mediaWrapper))
                }
                ID_FAVORITE_ADD -> {
                    val uri = Uri.parse(mediaItemDetails.location)
                    val local = "file" == uri.scheme
                    if (local)
                        browserFavRepository.addLocalFavItem(uri, mediaItemDetails.title
                                ?: "", mediaItemDetails.artworkUrl)
                    else
                        browserFavRepository.addNetworkFavItem(uri, mediaItemDetails.title
                                ?: "", mediaItemDetails.artworkUrl)
                    detailsOverview.removeAction(actionAdd)
                    detailsOverview.addAction(actionDelete)
                    rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    Toast.makeText(activity, R.string.favorite_added, Toast.LENGTH_SHORT).show()
                }
                ID_FAVORITE_DELETE -> {
                    runIO(Runnable { browserFavRepository.deleteBrowserFav(Uri.parse(mediaItemDetails.location)) })
                    detailsOverview.removeAction(actionDelete)
                    detailsOverview.addAction(actionAdd)
                    rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    Toast.makeText(activity, R.string.favorite_removed, Toast.LENGTH_SHORT).show()
                }
                ID_BROWSE -> TvUtil.openMedia(activity, media, null)
                ID_DL_SUBS -> MediaUtils.getSubs(requireActivity(), media)
                ID_PLAY_FROM_START -> {
                    mediaStarted = false
                    VideoPlayerActivity.start(requireActivity(), media.uri, true)
                    activity.finish()
                }
            }
        }
        selector.addClassPresenter(DetailsOverviewRow::class.java, rowPresenter)
        selector.addClassPresenter(ListRow::class.java,
                ListRowPresenter())
        rowsAdapter = ArrayObjectAdapter(selector)
        runIO(Runnable {
            val cover = if (media.type == AbstractMediaWrapper.TYPE_AUDIO || media.type == AbstractMediaWrapper.TYPE_VIDEO)
                AudioUtil.readCoverBitmap(mediaItemDetails.artworkUrl, 512)
            else
                null
            val blurred = if (cover != null) UiTools.blurBitmap(cover) else null
            val browserFavExists = browserFavRepository.browserFavExists(Uri.parse(mediaItemDetails.location))
            val isDir = media.type == AbstractMediaWrapper.TYPE_DIR
            val canSave = isDir && FileUtils.canSave(media)
            runOnMainThread(Runnable {
                if (isDetached) return@Runnable
                val context = getActivity()
                if (context == null || context.isFinishing) return@Runnable
                val res = resources
                if (isDir) {
                    detailsOverview.imageDrawable = ContextCompat.getDrawable(context, if (TextUtils.equals(media.uri.scheme, "file"))
                        R.drawable.ic_menu_folder_big
                    else
                        R.drawable.ic_menu_network_big)
                    detailsOverview.isImageScaleUpAllowed = true
                    detailsOverview.addAction(Action(ID_BROWSE.toLong(), res.getString(R.string.browse_folder)))
                    if (canSave) detailsOverview.addAction(if (browserFavExists) actionDelete else actionAdd)

                } else if (media.type == AbstractMediaWrapper.TYPE_AUDIO) {
                    // Add images and action buttons to the details view
                    if (cover == null)
                        detailsOverview.imageDrawable = ContextCompat.getDrawable(context, R.drawable.ic_default_cone)
                    else
                        detailsOverview.setImageBitmap(context, cover)

                    detailsOverview.addAction(Action(ID_PLAY.toLong(), res.getString(R.string.play)))
                    detailsOverview.addAction(Action(ID_LISTEN.toLong(), res.getString(R.string.listen)))
                    detailsOverview.addAction(Action(ID_PLAYLIST.toLong(), res.getString(R.string.add_to_playlist)))
                } else if (media.type == AbstractMediaWrapper.TYPE_VIDEO) {
                    // Add images and action buttons to the details view
                    if (cover == null)
                        detailsOverview.imageDrawable = ContextCompat.getDrawable(context, R.drawable.ic_default_cone)
                    else
                        detailsOverview.setImageBitmap(context, cover)

                    detailsOverview.addAction(Action(ID_PLAY.toLong(), res.getString(R.string.play)))
                    detailsOverview.addAction(Action(ID_PLAY_FROM_START.toLong(), res.getString(R.string.play_from_start)))
                    if (FileUtils.canWrite(media.uri))
                        detailsOverview.addAction(Action(ID_DL_SUBS.toLong(), res.getString(R.string.download_subtitles)))
                    detailsOverview.addAction(Action(ID_PLAYLIST.toLong(), res.getString(R.string.add_to_playlist)))
                }
                rowsAdapter.add(detailsOverview)
                adapter = rowsAdapter
                if (blurred != null) backgroundManager.setBitmap(blurred)
            })
        })
    }

    companion object {
        private const val TAG = "MediaItemDetailsFragment"
        private const val ID_PLAY = 1
        private const val ID_LISTEN = 2
        private const val ID_FAVORITE_ADD = 3
        private const val ID_FAVORITE_DELETE = 4
        private const val ID_BROWSE = 5
        private const val ID_DL_SUBS = 6
        private const val ID_PLAY_FROM_START = 7
        private const val ID_PLAYLIST = 8
    }
}
