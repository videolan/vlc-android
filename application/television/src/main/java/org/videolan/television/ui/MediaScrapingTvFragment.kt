/*
 * ************************************************************************
 *  NextTvFragment.kt
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
 * SearchFragment.kt
 *
 * Copyright © 2014-2018 VLC authors, VideoLAN and VideoLabs
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

import android.annotation.TargetApi
import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.moviepedia.MediaScraper
import org.videolan.moviepedia.models.identify.MoviepediaMedia
import org.videolan.moviepedia.viewmodel.MediaScrapingModel
import org.videolan.resources.util.parcelable
import org.videolan.television.R
import org.videolan.television.util.manageHttpException
import org.videolan.tools.NetworkMonitor

private const val TAG = "SearchFragment"
private const val REQUEST_SPEECH = 1

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MediaScrapingTvFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private lateinit var viewModel: MediaScrapingModel
    lateinit var media: MediaWrapper

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val defaultItemClickedListener: OnItemViewClickedListener
        get() = OnItemViewClickedListener { _, item, _, _ ->
            if (item is MoviepediaMedia) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            MediaScraper.saveMediaMetadata(requireActivity(), media, item)
                        } catch (e: Exception) {
                            requireActivity().manageHttpException(e)
                        }
                    }
                    requireActivity().finish()
                }
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(defaultItemClickedListener)
        val intent = requireActivity().intent
        if (Intent.ACTION_SEARCH == intent.action || "com.google.android.gms.actions.SEARCH_ACTION" == intent.action)
            intent.getStringExtra(SearchManager.QUERY)?.let { onQueryTextSubmit(it) }

        val extras = requireActivity().intent.extras ?: savedInstanceState ?: return
        media = extras.parcelable(MediaScrapingTvActivity.MEDIA) ?: return

        viewModel = ViewModelProvider(this).get(media.uri.path
                ?: "", MediaScrapingModel::class.java)
        val cp = CardPresenter(requireActivity(), true)
        val videoAdapter = ArrayObjectAdapter(cp)
        viewModel.apiResult.observe(this) {
            val medias = it.getAllResults()
            videoAdapter.clear()
            videoAdapter.addAll(0, medias)
            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.moviepedia_result)), videoAdapter))
            updateEmptyView(medias.isEmpty())
        }
        viewModel.exceptionLiveData.observe(this) { e ->
            e?.let {
                requireActivity().manageHttpException(it)
                lifecycleScope.launchWhenStarted {
                    NetworkMonitor.getInstance(requireContext()).connectionFlow.first { it.connected }
                    refresh()
                }
            }
        }
        setSearchQuery(media.title, false)
        viewModel.search(media.uri)
    }

    override fun getResultsAdapter() = rowsAdapter

    private fun queryByWords(words: String?) {
        if (words == null || words.length < 3) return
        rowsAdapter.clear()
        viewModel.search(words)
    }

    override fun onQueryTextChange(newQuery: String) = false

    override fun onQueryTextSubmit(query: String): Boolean {
        queryByWords(query)
        return true
    }

    private fun updateEmptyView(empty: Boolean) {
        (activity as? SearchActivity)?.updateEmptyView(empty)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_SPEECH && resultCode == Activity.RESULT_OK) setSearchQuery(data, true)
    }

    fun refresh() {
        viewModel.search(media.uri)
    }
}

