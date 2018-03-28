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

import android.os.Bundle
import android.support.v17.leanback.app.BackgroundManager
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.*
import android.support.v4.content.ContextCompat
import android.view.View
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.CardPresenter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.viewmodels.RefreshModel

private const val TAG = "VLC/CategoriesFragment"
private const val SELECTED_ITEM = "selected"

open class CategoriesFragment<T : RefreshModel> : BrowseSupportFragment(), OnItemViewSelectedListener, OnItemViewClickedListener, BrowserFragmentInterface {
    private lateinit var selecteditem: MediaLibraryItem
    private lateinit var backgroundManager: BackgroundManager
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var categoryRows: Map<String, ListRow>
    lateinit var provider: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI setting
        headersState = BrowseSupportFragment.HEADERS_HIDDEN
        brandColor = ContextCompat.getColor(activity!!, R.color.orange800)
        if (savedInstanceState !== null) selecteditem = savedInstanceState.getParcelable<MediaWrapper>(SELECTED_ITEM)
        else backgroundManager = BackgroundManager.getInstance(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = rowsAdapter
        onItemViewClickedListener = this
        onItemViewSelectedListener = this
        backgroundManager.attachToView(view)
    }

    override fun onStart() {
        super.onStart()
        if (this::selecteditem.isInitialized) TvUtil.updateBackground(backgroundManager, selecteditem)
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::selecteditem.isInitialized) outState.putParcelable(SELECTED_ITEM, selecteditem)
    }

    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        if (item === null) return
        selecteditem = item as MediaWrapper
        TvUtil.updateBackground(backgroundManager, item)
    }

    override fun onItemClicked(viewHolder: Presenter.ViewHolder, item: Any, viewHolder1: RowPresenter.ViewHolder, row: Row) {
        val media = item as MediaWrapper
        if (media.type == MediaWrapper.TYPE_DIR) TvUtil.browseFolder(activity, getCategoryId(), item.uri)
        else TvUtil.openMedia(activity, item, null)
    }

    override fun refresh() {
        provider.refresh()
    }

    override fun updateList() {}

    protected fun update(map: Map<String, List<MediaLibraryItem>>?) {
        if (map === null) return
        val rows = mutableMapOf<String, ListRow>()
        for ((key, list) in map) {
            val row = getCategoryRow(key)
            (row.adapter as ArrayObjectAdapter).setItems(list, TvUtil.diffCallback)
            rows[key] = row
        }
        rowsAdapter.setItems(rows.values.toList(), TvUtil.listDiffCallback)
        categoryRows = rows
    }

    private fun getCategoryRow(key: String): ListRow {
        val fromCache = if (this::categoryRows.isInitialized) categoryRows[key] else null
        return fromCache ?: ListRow(HeaderItem(0, key), ArrayObjectAdapter(CardPresenter(activity)))
    }

    private fun getCategoryId(): Long {
//        if (this is NetworkBrowserFragment)
//            return Constants.HEADER_NETWORK
//        else if (this is DirectoryBrowserFragment)
//            return Constants.HEADER_DIRECTORIES
        return -1
    }
}