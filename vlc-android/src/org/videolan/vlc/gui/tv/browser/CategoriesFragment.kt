/*
 * *************************************************************************
 *  CategoriesFragment.kt
 * **************************************************************************
 *  Copyright © 2018 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.tv.browser

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.*
import android.support.v4.content.ContextCompat
import android.view.View
import android.widget.ImageView
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.CardPresenter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.util.Constants
import org.videolan.vlc.viewmodels.BaseModel

private const val TAG = "VLC/CategoriesFragment"

open class CategoriesFragment<T : BaseModel<out MediaLibraryItem>> : BrowseSupportFragment(), Sortable, OnItemViewSelectedListener, OnItemViewClickedListener, BrowserFragmentInterface {

    private lateinit var selecteditem: MediaLibraryItem
    private lateinit var backgroundManager: BackgroundManager
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var categoryRows: Map<String, ListRow>
    lateinit var viewModel: T
    private var restart = false
    protected val preferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI setting
        headersState = BrowseSupportFragment.HEADERS_DISABLED
        brandColor = ContextCompat.getColor(activity!!, R.color.orange800)
        if (savedInstanceState == null) backgroundManager = BackgroundManager.getInstance(requireActivity())
        setOnSearchClickedListener { sort(requireActivity().findViewById(R.id.title_orb)) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter
        onItemViewClickedListener = this
        onItemViewSelectedListener = this
        backgroundManager.attachToView(view)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.orange500)
        requireActivity().findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_menu_sort)
    }

    override fun onStart() {
        super.onStart()
        if (this::selecteditem.isInitialized) TvUtil.updateBackground(backgroundManager, selecteditem)
        if (restart) refresh()
        restart = true
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        if (item === null) return
        selecteditem = item as MediaWrapper
        TvUtil.updateBackground(backgroundManager, item)
    }

    override fun onItemClicked(viewHolder: Presenter.ViewHolder, item: Any, viewHolder1: RowPresenter.ViewHolder, row: Row) {
        val media = item as MediaWrapper
        if (media.type == MediaWrapper.TYPE_DIR) TvUtil.browseFolder(requireActivity(), getCategoryId(), item.uri)
        else TvUtil.openMedia(requireActivity(), item, null)
    }

    override fun refresh() {
        if (this::viewModel.isInitialized) viewModel.refresh()
    }

    protected fun update(map: Map<String, List<MediaLibraryItem>>?) {
        if (map === null) return
        val rows = mutableMapOf<String, ListRow>()
        for ((key, list) in map) {
            val row = getCategoryRow(key)
            (row.adapter as ArrayObjectAdapter).setItems(list, TvUtil.diffCallback)
            rows[key] = row
        }
        //TODO  Activate animations once IndexOutOfRange Exception is fixed
        rowsAdapter.setItems(rows.values.toList(), null /*TvUtil.listDiffCallback*/)
        categoryRows = rows
    }

    private fun getCategoryRow(key: String): ListRow {
        val fromCache = if (this::categoryRows.isInitialized) categoryRows[key] else null
        return fromCache ?: ListRow(HeaderItem(0, key), ArrayObjectAdapter(CardPresenter(activity)))
    }

    private fun getCategoryId() = when(this) {
        is NetworkBrowserFragment ->  Constants.HEADER_NETWORK
        is DirectoryBrowserFragment -> Constants.HEADER_DIRECTORIES
        else -> -1
    }

    override fun getVM() = viewModel
}