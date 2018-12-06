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
package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.*
import org.videolan.tools.coroutineScope
import org.videolan.vlc.R
import org.videolan.vlc.util.getFromMl
import java.util.*

private const val TAG = "SearchFragment"
private const val REQUEST_SPEECH = 1

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val defaultItemClickedListener: OnItemViewClickedListener
        get() = OnItemViewClickedListener { _, item, _, row ->
            if (item is MediaWrapper) TvUtil.openMedia(requireActivity(), item, row)
            else TvUtil.openAudioCategory(requireActivity(), item as MediaLibraryItem)
            requireActivity().finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)
        setOnItemViewClickedListener(defaultItemClickedListener)
        val intent = requireActivity().intent
        if (Intent.ACTION_SEARCH == intent.action || "com.google.android.gms.actions.SEARCH_ACTION" == intent.action)
            onQueryTextSubmit(intent.getStringExtra(SearchManager.QUERY))
    }

    override fun getResultsAdapter() = rowsAdapter

    private fun queryByWords(words: String?) {
        if (words == null || words.length < 3) return
        rowsAdapter.clear()
        if (!TextUtils.isEmpty(words)) loadRows(words)
    }

    override fun onQueryTextChange(newQuery: String) = false

    override fun onQueryTextSubmit(query: String): Boolean {
        queryByWords(query)
        return true
    }

    private fun loadRows(query: String?) = coroutineScope.launch {
        val searchAggregate = context?.getFromMl { search(query) }
        val empty = searchAggregate == null || searchAggregate.isEmpty
        updateEmtyView(empty)
        if (searchAggregate == null || empty) return@launch
        val mediaEmpty = empty || (Tools.isArrayEmpty(searchAggregate.tracks) && Tools.isArrayEmpty(searchAggregate.videos))
        val cp = CardPresenter(requireActivity())
        val videoAdapter = ArrayObjectAdapter(cp)
        if (!mediaEmpty) videoAdapter.addAll(0, Arrays.asList(*searchAggregate.videos))
//        val episodesAdapter = ArrayObjectAdapter(cp)
//        if (!mediaEmpty) episodesAdapter.addAll(0, Arrays.asList(searchAggregate.mediaSearchAggregate.episodes))
//        val moviesAdapter = ArrayObjectAdapter(cp)
//        if (!mediaEmpty) moviesAdapter.addAll(0, Arrays.asList(searchAggregate.mediaSearchAggregate.movies))
        val songsAdapter = ArrayObjectAdapter(cp)
        if (!mediaEmpty) songsAdapter.addAll(0, Arrays.asList(*searchAggregate.tracks))
        val artistsAdapter = ArrayObjectAdapter(cp)
        if (!empty) artistsAdapter.addAll(0, Arrays.asList<Artist>(*searchAggregate.artists))
        val albumsAdapter = ArrayObjectAdapter(cp)
        if (!empty) albumsAdapter.addAll(0, Arrays.asList<Album>(*searchAggregate.albums))
        val genresAdapter = ArrayObjectAdapter(cp)
        if (!empty) genresAdapter.addAll(0, Arrays.asList<Genre>(*searchAggregate.genres))
        if (!mediaEmpty && videoAdapter.size() > 0)
            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.videos)), videoAdapter))
//        if (!mediaEmpty && episodesAdapter.size() > 0)
//            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.episodes)), episodesAdapter))
//        if (!mediaEmpty && moviesAdapter.size() > 0)
//            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.movies)), moviesAdapter))
        if (!mediaEmpty && songsAdapter.size() > 0)
            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.songs)), songsAdapter))
        if (!empty && artistsAdapter.size() > 0)
            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.artists)), artistsAdapter))
        if (!empty && albumsAdapter.size() > 0)
            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.albums)), albumsAdapter))
        if (!empty && genresAdapter.size() > 0)
            rowsAdapter.add(ListRow(HeaderItem(0, resources.getString(R.string.genres)), genresAdapter))
    }

    private fun updateEmtyView(empty: Boolean) {
        (activity as? SearchActivity)?.updateEmptyView(empty)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_SPEECH && resultCode == Activity.RESULT_OK) setSearchQuery(data, true)
    }
}
