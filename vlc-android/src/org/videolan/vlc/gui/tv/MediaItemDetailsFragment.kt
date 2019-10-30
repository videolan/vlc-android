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
import androidx.fragment.app.activityViewModels
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.database.models.MediaImageType
import org.videolan.vlc.database.models.MediaMetadata
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.repository.MediaMetadataRepository
import org.videolan.vlc.repository.MediaPersonRepository
import org.videolan.vlc.util.ACTION_REMOTE_STOP
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.MediaMetadataFull
import org.videolan.vlc.viewmodels.MediaMetadataModel

private const val TAG = "MediaItemDetailsFragment"
private const val ID_PLAY = 1
private const val ID_LISTEN = 2
private const val ID_FAVORITE_ADD = 3
private const val ID_FAVORITE_DELETE = 4
private const val ID_BROWSE = 5
private const val ID_DL_SUBS = 6
private const val ID_PLAY_FROM_START = 7
private const val ID_PLAYLIST = 8
private const val ID_GET_INFO = 9

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MediaItemDetailsFragment : DetailsSupportFragment(), CoroutineScope by MainScope() {

    private lateinit var detailsDescriptionPresenter: DetailsDescriptionPresenter
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var browserFavRepository: BrowserFavRepository
    private lateinit var mediaMetadataRepository: MediaMetadataRepository
    private lateinit var mediaPersonRepository: MediaPersonRepository
    private lateinit var mediaMetadataModel: MediaMetadataModel
    private lateinit var detailsOverview: DetailsOverviewRow
    private var mediaStarted: Boolean = false
    private val viewModel: MediaItemDetailsModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundManager = BackgroundManager.getInstance(requireActivity())
        backgroundManager.isAutoReleaseOnStop = false
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
        viewModel.mediaStarted = false
        detailsDescriptionPresenter = DetailsDescriptionPresenter()

        val extras = requireActivity().intent.extras ?: savedInstanceState ?: return
        viewModel.mediaItemDetails = extras.getParcelable("item") ?: return
        val hasMedia = extras.containsKey("media")
        val media = (extras.getParcelable<Parcelable>("media")
                ?: MLServiceLocator.getAbstractMediaWrapper(AndroidUtil.LocationToUri(viewModel.mediaItemDetails.location))) as AbstractMediaWrapper

        viewModel.media = media
        if (!hasMedia) viewModel.media.setDisplayTitle(viewModel.mediaItemDetails.title)
        title = viewModel.media.title
        mediaMetadataRepository = MediaMetadataRepository.getInstance(requireContext())
        mediaPersonRepository = MediaPersonRepository.getInstance(requireContext())
        mediaStarted = false
        buildDetails()

        mediaMetadataModel = ViewModelProviders.of(this, MediaMetadataModel.Factory(requireActivity(), media.id)).get(media.uri.path
                ?: "", MediaMetadataModel::class.java)

        mediaMetadataModel.updateLiveData.observe(this, Observer {
            updateMetadata(it)
        })
    }

    override fun onResume() {
        super.onResume()
//        buildDetails()
        if (!backgroundManager.isAttached) backgroundManager.attachToView(view)
    }

    override fun onPause() {
        backgroundManager.release()
        super.onPause()
        if (viewModel.mediaStarted) {
            context?.sendBroadcast(Intent(ACTION_REMOTE_STOP))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("item", viewModel.mediaItemDetails)
        outState.putParcelable("media", viewModel.media)
        super.onSaveInstanceState(outState)
    }

    private fun updateMetadata(mediaMetadataFull: MediaMetadataFull?) {
        mediaMetadataFull?.let { mediaMetadata ->
            detailsDescriptionPresenter.metadata = mediaMetadata.metadata?.metadata

            val items = ArrayList<Any>()
            items.add(detailsOverview)

            if (!mediaMetadata.writers.isNullOrEmpty()) {
                val arrayObjectAdapterWriters = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterWriters.setItems(mediaMetadata.writers, null)
                val headerWriters = HeaderItem(0, getString(R.string.written_by))
                items.add(ListRow(headerWriters, arrayObjectAdapterWriters))
            }

            if (!mediaMetadata.actors.isNullOrEmpty()) {
                val arrayObjectAdapterActors = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterActors.setItems(mediaMetadata.actors, null)
                val headerActors = HeaderItem(0, getString(R.string.casting))
                items.add(ListRow(headerActors, arrayObjectAdapterActors))
            }

            if (!mediaMetadata.directors.isNullOrEmpty()) {
                val arrayObjectAdapterDirectors = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterDirectors.setItems(mediaMetadata.directors, null)
                val headerDirectors = HeaderItem(0, getString(R.string.directed_by))
                items.add(ListRow(headerDirectors, arrayObjectAdapterDirectors))
            }

            if (!mediaMetadata.producers.isNullOrEmpty()) {
                val arrayObjectAdapterProducers = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterProducers.setItems(mediaMetadata.producers, null)
                val headerProducers = HeaderItem(0, getString(R.string.produced_by))
                items.add(ListRow(headerProducers, arrayObjectAdapterProducers))
            }

            if (!mediaMetadata.musicians.isNullOrEmpty()) {
                val arrayObjectAdapterMusicians = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterMusicians.setItems(mediaMetadata.musicians, null)
                val headerMusicians = HeaderItem(0, getString(R.string.music_by))
                items.add(ListRow(headerMusicians, arrayObjectAdapterMusicians))
            }

//                }
            mediaMetadata.metadata?.let { metadata ->
                if (metadata.images.any { it.imageType == MediaImageType.POSTER }) {
                    val arrayObjectAdapterPosters = ArrayObjectAdapter(MediaImageCardPresenter(requireActivity(), MediaImageType.POSTER))
                    arrayObjectAdapterPosters.setItems(metadata.images.filter { it.imageType == MediaImageType.POSTER }, null)
                    val headerPosters = HeaderItem(0, getString(R.string.posters))
                    items.add(ListRow(headerPosters, arrayObjectAdapterPosters))
                }

                if (metadata.images.any { it.imageType == MediaImageType.BACKDROP }) {
                    val arrayObjectAdapterBackdrops = ArrayObjectAdapter(MediaImageCardPresenter(requireActivity(), MediaImageType.BACKDROP))
                    arrayObjectAdapterBackdrops.setItems(metadata.images.filter { it.imageType == MediaImageType.BACKDROP }, null)
                    val headerBackdrops = HeaderItem(0, getString(R.string.backdrops))
                    items.add(ListRow(headerBackdrops, arrayObjectAdapterBackdrops))
                }
            }
            rowsAdapter.setItems(items, null)
        }
    }

    private fun buildDetails() {
        val selector = ClassPresenterSelector()

        // Attach your media item details presenter to the row presenter:
        val rowPresenter = FullWidthDetailsOverviewRowPresenter(detailsDescriptionPresenter)
        val videoPresenter = VideoDetailsPresenter(requireActivity(), requireActivity().getScreenWidth())

        val activity = requireActivity()
        detailsOverview = DetailsOverviewRow(viewModel.mediaItemDetails)
        val actionAdd = Action(ID_FAVORITE_ADD.toLong(), getString(R.string.favorites_add))
        val actionDelete = Action(ID_FAVORITE_DELETE.toLong(), getString(R.string.favorites_remove))

        rowPresenter.backgroundColor = ContextCompat.getColor(activity, R.color.orange500)
        rowPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id.toInt()) {
                ID_LISTEN -> {
                    MediaUtils.openMedia(activity, viewModel.media)
                    viewModel.mediaStarted = true
                }
                ID_PLAY -> {
                    viewModel.mediaStarted = false
                    TvUtil.playMedia(activity, viewModel.media)
                    activity.finish()
                }
                ID_PLAYLIST -> UiTools.addToPlaylist(activity, arrayListOf(viewModel.media))
                ID_FAVORITE_ADD -> {
                    val uri = Uri.parse(viewModel.mediaItemDetails.location)
                    val local = "file" == uri.scheme
                    if (local)
                        browserFavRepository.addLocalFavItem(uri, viewModel.mediaItemDetails.title
                                ?: "", viewModel.mediaItemDetails.artworkUrl)
                    else
                        browserFavRepository.addNetworkFavItem(uri, viewModel.mediaItemDetails.title
                                ?: "", viewModel.mediaItemDetails.artworkUrl)
                    detailsOverview.removeAction(actionAdd)
                    detailsOverview.addAction(actionDelete)
                    rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    Toast.makeText(activity, R.string.favorite_added, Toast.LENGTH_SHORT).show()
                }
                ID_FAVORITE_DELETE -> {
                    browserFavRepository.deleteBrowserFav(Uri.parse(viewModel.mediaItemDetails.location))
                    detailsOverview.removeAction(actionDelete)
                    detailsOverview.addAction(actionAdd)
                    rowsAdapter.notifyArrayItemRangeChanged(0, rowsAdapter.size())
                    Toast.makeText(activity, R.string.favorite_removed, Toast.LENGTH_SHORT).show()
                }
                ID_BROWSE -> TvUtil.openMedia(activity, viewModel.media, null)
                ID_DL_SUBS -> MediaUtils.getSubs(requireActivity(), viewModel.media)
                ID_PLAY_FROM_START -> {
                    viewModel.mediaStarted = false
                    VideoPlayerActivity.start(requireActivity(), viewModel.media.uri, true)
                    activity.finish()
                }
                ID_GET_INFO -> startActivity(Intent(requireActivity(), NextTvActivity::class.java).apply { putExtra(NextTvActivity.MEDIA, viewModel.media) })
            }
        }
        selector.addClassPresenter(DetailsOverviewRow::class.java, rowPresenter)
        selector.addClassPresenter(VideoDetailsOverviewRow::class.java, videoPresenter)
        selector.addClassPresenter(ListRow::class.java,
                ListRowPresenter())
        rowsAdapter = ArrayObjectAdapter(selector)
        lifecycleScope.launchWhenStarted {
            val cover = if (viewModel.media.type == AbstractMediaWrapper.TYPE_AUDIO || viewModel.media.type == AbstractMediaWrapper.TYPE_VIDEO)
                withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(viewModel.mediaItemDetails.artworkUrl, 512) }
            else null
            val blurred = cover?.let { withContext(Dispatchers.IO) { UiTools.blurBitmap(it) } }
            val browserFavExists = withContext(Dispatchers.IO) { browserFavRepository.browserFavExists(Uri.parse(viewModel.mediaItemDetails.location)) }
            val isDir = viewModel.media.type == AbstractMediaWrapper.TYPE_DIR
            val canSave = isDir && withContext(Dispatchers.IO) { FileUtils.canSave(viewModel.media) }
            if (activity.isFinishing) return@launchWhenStarted
            val res = resources
            if (isDir) {
                detailsOverview.imageDrawable = ContextCompat.getDrawable(activity, if (TextUtils.equals(viewModel.media.uri.scheme, "file"))
                    R.drawable.ic_menu_folder_big
                else
                    R.drawable.ic_menu_network_big)
                detailsOverview.isImageScaleUpAllowed = true
                detailsOverview.addAction(Action(ID_BROWSE.toLong(), res.getString(R.string.browse_folder)))
                if (canSave) detailsOverview.addAction(if (browserFavExists) actionDelete else actionAdd)
            } else if (viewModel.media.type == AbstractMediaWrapper.TYPE_AUDIO) {
                // Add images and action buttons to the details view
                if (cover == null) {
                    detailsOverview.imageDrawable = ContextCompat.getDrawable(activity, R.drawable.ic_default_cone)
                } else {
                    detailsOverview.setImageBitmap(context, cover)
                }
                detailsOverview.addAction(Action(ID_PLAY.toLong(), res.getString(R.string.play)))
                detailsOverview.addAction(Action(ID_LISTEN.toLong(), res.getString(R.string.listen)))
                detailsOverview.addAction(Action(ID_PLAYLIST.toLong(), res.getString(R.string.add_to_playlist)))
            } else if (viewModel.media.type == AbstractMediaWrapper.TYPE_VIDEO) {
                // Add images and action buttons to the details view
                if (cover == null) {
                    detailsOverview.imageDrawable = ContextCompat.getDrawable(activity, R.drawable.ic_default_cone)
                } else {
                    detailsOverview.setImageBitmap(context, cover)
                }
                detailsOverview.addAction(Action(ID_PLAY.toLong(), res.getString(R.string.play)))
                detailsOverview.addAction(Action(ID_PLAY_FROM_START.toLong(), res.getString(R.string.play_from_start)))
                if (FileUtils.canWrite(viewModel.media.uri))
                    detailsOverview.addAction(Action(ID_DL_SUBS.toLong(), res.getString(R.string.download_subtitles)))
                detailsOverview.addAction(Action(ID_PLAYLIST.toLong(), res.getString(R.string.add_to_playlist)))
                detailsOverview.addAction(Action(ID_GET_INFO.toLong(), res.getString(R.string.find_metadata)))
            }
            adapter = rowsAdapter
            //    updateMetadata(mediaMetadataModel.updateLiveData.value)
            blurred?.let { backgroundManager.setBitmap(blurred) }
        }
    }
}

class MediaItemDetailsModel : ViewModel() {
    lateinit var mediaItemDetails: MediaItemDetails
    lateinit var media: AbstractMediaWrapper
    var mediaStarted = false
}

class VideoDetailsOverviewRow(val item: MediaMetadata) : DetailsOverviewRow(item)
