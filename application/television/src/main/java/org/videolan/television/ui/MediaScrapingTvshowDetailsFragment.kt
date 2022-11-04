/*
 * ************************************************************************
 *  MoviepediaTvshowDetailsFragment.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

/*****************************************************************************
 * MoviepediaTvshowDetailsFragment.java
 *
 * Copyright © 2014-2019 VLC authors, VideoLAN and VideoLabs
 * Author: Nicolas POMEPUY
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

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.videolan.moviepedia.database.models.*
import org.videolan.moviepedia.viewmodel.MediaMetadataFull
import org.videolan.moviepedia.viewmodel.MediaMetadataModel
import org.videolan.resources.util.getFromMl
import org.videolan.tools.HttpImageLoader
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.util.getScreenWidth

private const val ID_RESUME = 1
private const val ID_START_OVER = 2

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MediaScrapingTvshowDetailsFragment : DetailsSupportFragment(), CoroutineScope by MainScope(), OnItemViewClickedListener {

    private lateinit var actionsAdapter: SparseArrayObjectAdapter
    private lateinit var showId: String
    private lateinit var detailsDescriptionPresenter: org.videolan.television.ui.TvShowDescriptionPresenter
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var browserFavRepository: BrowserFavRepository
    private lateinit var detailsOverview: DetailsOverviewRow
    private lateinit var arrayObjectAdapterPosters: ArrayObjectAdapter
    private lateinit var viewModel: MediaMetadataModel

    private val imageDiffCallback = object : DiffCallback<MediaImage>() {
        override fun areItemsTheSame(oldItem: MediaImage, newItem: MediaImage) = oldItem.url == newItem.url

        override fun areContentsTheSame(oldItem: MediaImage, newItem: MediaImage) = oldItem.url == newItem.url
    }

    private val personsDiffCallback = object : DiffCallback<Person>() {
        override fun areItemsTheSame(oldItem: Person, newItem: Person) = oldItem.moviepediaId == newItem.moviepediaId

        override fun areContentsTheSame(oldItem: Person, newItem: Person) = oldItem.moviepediaId == newItem.moviepediaId && oldItem.image == newItem.image && oldItem.name == newItem.name
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundManager = BackgroundManager.getInstance(requireActivity())
        backgroundManager.isAutoReleaseOnStop = false
        browserFavRepository = BrowserFavRepository.getInstance(requireContext())
        detailsDescriptionPresenter = org.videolan.television.ui.TvShowDescriptionPresenter()
        arrayObjectAdapterPosters = ArrayObjectAdapter(org.videolan.television.ui.MediaImageCardPresenter(requireActivity(), MediaImageType.POSTER))

        val extras = requireActivity().intent.extras ?: savedInstanceState ?: return
        showId = extras.getString(org.videolan.television.ui.TV_SHOW_ID) ?: return
        viewModel = ViewModelProvider(this, MediaMetadataModel.Factory(requireActivity(), showId = showId)).get(showId, MediaMetadataModel::class.java)


        viewModel.updateLiveData.observe(this) {
            buildDetails(it)
            updateMetadata(it)
        }
        onItemViewClickedListener = this
    }

    override fun onResume() {
        super.onResume()
        if (!backgroundManager.isAttached) backgroundManager.attachToView(view)
    }

    override fun onPause() {
        backgroundManager.release()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(org.videolan.television.ui.TV_SHOW_ID, showId)
        super.onSaveInstanceState(outState)
    }

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        when (item) {
            is MediaImage -> {
                viewModel.updateMetadataImage(item)
            }
            is MediaMetadataWithImages -> {
                launch {
                    val media = item.media
                            ?: item.metadata.mlId?.let { requireActivity().getFromMl { getMedia(it) } }
                    media?.let {
                        org.videolan.television.ui.TvUtil.showMediaDetail(requireActivity(), it)
                    }
                }
            }
        }
    }

    private fun loadBackdrop(url: String? = null) {
        lifecycleScope.launchWhenStarted {
            when {
                !url.isNullOrEmpty() -> HttpImageLoader.downloadBitmap(url)
                else -> null
            }?.let { backgroundManager.setBitmap(it) }
        }
    }

    private fun updateMetadata(tvshow: MediaMetadataFull?) {
        var backdropLoaded = false
        tvshow?.let { tvShow ->
            loadBackdrop(tvShow.metadata?.metadata?.currentBackdrop)
            backdropLoaded = true
            lifecycleScope.launchWhenStarted {
                if (!tvShow.metadata?.metadata?.currentPoster.isNullOrEmpty()) {
                    detailsOverview.setImageBitmap(requireActivity(), HttpImageLoader.downloadBitmap(tvShow.metadata?.metadata?.currentPoster!!))
                }
            }
            title = tvShow.metadata?.metadata?.title

            val items = ArrayList<Any>()
            items.add(detailsOverview)

            tvShow.seasons?.let {
                it.forEach {
                    val arrayObjectAdapterSeason = ArrayObjectAdapter(MetadataCardPresenter(requireActivity()))
                    arrayObjectAdapterSeason.setItems(it.episodes, TvUtil.metadataDiffCallback)
                    val headerSeason = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                            ?: 0, getString(R.string.season_number, it.seasonNumber.toString()))
                    items.add(ListRow(headerSeason, arrayObjectAdapterSeason))
                }
            }

            if (!tvShow.writers.isNullOrEmpty()) {
                val arrayObjectAdapterWriters = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterWriters.setItems(tvShow.writers, personsDiffCallback)
                val headerWriters = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                        ?: 0, getString(R.string.written_by))
                items.add(ListRow(headerWriters, arrayObjectAdapterWriters))
            }

            if (!tvShow.actors.isNullOrEmpty()) {
                val arrayObjectAdapterActors = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterActors.setItems(tvShow.actors, personsDiffCallback)
                val headerActors = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                        ?: 0, getString(R.string.casting))
                items.add(ListRow(headerActors, arrayObjectAdapterActors))
            }

            if (!tvShow.directors.isNullOrEmpty()) {
                val arrayObjectAdapterDirectors = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterDirectors.setItems(tvShow.directors, personsDiffCallback)
                val headerDirectors = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                        ?: 0, getString(R.string.directed_by))
                items.add(ListRow(headerDirectors, arrayObjectAdapterDirectors))
            }

            if (!tvShow.producers.isNullOrEmpty()) {
                val arrayObjectAdapterProducers = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterProducers.setItems(tvShow.producers, personsDiffCallback)
                val headerProducers = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                        ?: 0, getString(R.string.produced_by))
                items.add(ListRow(headerProducers, arrayObjectAdapterProducers))
            }

            if (!tvShow.musicians.isNullOrEmpty()) {
                val arrayObjectAdapterMusicians = ArrayObjectAdapter(PersonCardPresenter(requireActivity()))
                arrayObjectAdapterMusicians.setItems(tvShow.musicians, personsDiffCallback)
                val headerMusicians = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                        ?: 0, getString(R.string.music_by))
                items.add(ListRow(headerMusicians, arrayObjectAdapterMusicians))
            }

            tvShow.metadata?.let { metadata ->
                if (metadata.images.any { it.imageType == MediaImageType.POSTER }) {
                    arrayObjectAdapterPosters.setItems(metadata.images.filter { it.imageType == MediaImageType.POSTER }, imageDiffCallback)
                    val headerPosters = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                            ?: 0, getString(R.string.posters))
                    items.add(ListRow(headerPosters, arrayObjectAdapterPosters))
                }

                if (metadata.images.any { it.imageType == MediaImageType.BACKDROP }) {
                    val arrayObjectAdapterBackdrops = ArrayObjectAdapter(org.videolan.television.ui.MediaImageCardPresenter(requireActivity(), MediaImageType.BACKDROP))
                    arrayObjectAdapterBackdrops.setItems(metadata.images.filter { it.imageType == MediaImageType.BACKDROP }, imageDiffCallback)
                    val headerBackdrops = HeaderItem(tvShow.metadata?.metadata?.moviepediaId?.toLong(36)
                            ?: 0, getString(R.string.backdrops))
                    items.add(ListRow(headerBackdrops, arrayObjectAdapterBackdrops))
                }
            }

            rowsAdapter.setItems(items, object : DiffCallback<Row>() {
                override fun areItemsTheSame(oldItem: Row, newItem: Row) = (oldItem is DetailsOverviewRow && newItem is DetailsOverviewRow && (oldItem.item == newItem.item)) || (oldItem is ListRow && newItem is ListRow && oldItem.contentDescription == newItem.contentDescription && oldItem.adapter.size() == newItem.adapter.size() && oldItem.id == newItem.id)

                override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean {
                    if (oldItem is DetailsOverviewRow && newItem is DetailsOverviewRow) {
                        return oldItem.item as org.videolan.television.ui.MediaItemDetails == newItem.item as org.videolan.television.ui.MediaItemDetails
                    }
                    return true
                }
            })
            rowsAdapter.notifyItemRangeChanged(0, 1)
        }
        if (!backdropLoaded) loadBackdrop()
    }

    private fun buildDetails(tvShow: MediaMetadataFull) {
        val selector = ClassPresenterSelector()

        // Attach your media item details presenter to the row presenter:
        val rowPresenter = FullWidthDetailsOverviewRowPresenter(detailsDescriptionPresenter)
        val videoPresenter = org.videolan.television.ui.VideoDetailsPresenter(requireActivity(), requireActivity().getScreenWidth())

        val activity = requireActivity()
        detailsOverview = DetailsOverviewRow(tvShow)
        rowPresenter.backgroundColor = ContextCompat.getColor(activity, R.color.orange500)
        rowPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id.toInt()) {
                ID_RESUME -> {
                    MediaUtils.openList(activity, viewModel.provider.getResumeMedias(viewModel.updateLiveData.value?.seasons), 0)
                }
                ID_START_OVER -> {
                    org.videolan.television.ui.TvUtil.playMedia(activity, viewModel.provider.getAllMedias(viewModel.updateLiveData.value?.seasons))
                }
            }
        }
        selector.addClassPresenter(DetailsOverviewRow::class.java, rowPresenter)
        selector.addClassPresenter(org.videolan.television.ui.VideoDetailsOverviewRow::class.java, videoPresenter)
        selector.addClassPresenter(ListRow::class.java,
                ListRowPresenter())
        rowsAdapter = ArrayObjectAdapter(selector)
        lifecycleScope.launchWhenStarted {
            actionsAdapter = SparseArrayObjectAdapter()
            val resumableEpisode = getFirstResumableEpisode()
            actionsAdapter.set(ID_RESUME, Action(ID_RESUME.toLong(), if (resumableEpisode == null) resources.getString(R.string.resume) else resources.getString(R.string.resume_episode, resumableEpisode.tvEpisodeSubtitle())))
            actionsAdapter.set(ID_START_OVER, Action(ID_START_OVER.toLong(), resources.getString(R.string.start_over)))
            detailsOverview.actionsAdapter = actionsAdapter

        }
        lifecycleScope.launchWhenStarted {
            if (activity.isFinishing) return@launchWhenStarted
            adapter = rowsAdapter
            //    updateMetadata(mediaMetadataModel.updateLiveData.value)
        }
    }

    private fun getFirstResumableEpisode(): MediaMetadataWithImages? {
        val seasons = viewModel.updateLiveData.value?.seasons
        seasons?.forEach {
            it.episodes.forEach { episode ->
                episode.media?.let { media ->
                    if (media.seen < 1) {
                        return episode
                    }
                }
            }
        }
        return null
    }
}

