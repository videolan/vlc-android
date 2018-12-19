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
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.CardPresenter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.interfaces.Sortable
import org.videolan.vlc.util.HEADER_DIRECTORIES
import org.videolan.vlc.util.HEADER_NETWORK
import org.videolan.vlc.util.Settings
import org.videolan.vlc.viewmodels.BaseModel

private const val TAG = "VLC/CategoriesFragment"

open class CategoriesFragment<T : BaseModel<out MediaLibraryItem>> : BrowseSupportFragment(), Sortable, OnItemViewSelectedListener, OnItemViewClickedListener, BrowserFragmentInterface {

    private lateinit var selecteditem: MediaLibraryItem
    private lateinit var backgroundManager: BackgroundManager
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private lateinit var categoryRows: Map<String, ListRow>
    lateinit var viewModel: T
    private var restart = false
    protected val preferences: SharedPreferences by lazy { Settings.getInstance(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI setting
        headersState = BrowseSupportFragment.HEADERS_DISABLED
        brandColor = ContextCompat.getColor(activity!!, R.color.orange800)
        if (!this::backgroundManager.isInitialized) backgroundManager = BackgroundManager.getInstance(requireActivity())
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

    private var currentArt : String? = null
    override fun onItemSelected(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        if (item === null) return
        selecteditem = item as MediaWrapper
        if (currentArt == item.artworkMrl) return
        currentArt = item.artworkMrl
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
        if (map.isNullOrEmpty()) {
            (activity as? VerticalGridActivity)?.run { updateEmptyView(true) }
            return
        }
        val rows = mutableMapOf<String, ListRow>()
        for ((key, list) in map) {
            val row = getCategoryRow(key)
            (row.adapter as ArrayObjectAdapter).setItems(list, TvUtil.diffCallback)
            rows[key] = row
        }
        (activity as? VerticalGridActivity)?.run { updateEmptyView(false) }
        //TODO  Activate animations once IndexOutOfRange Exception is fixed
        rowsAdapter.setItems(rows.values.toList(), null /*TvUtil.listDiffCallback*/)
        categoryRows = rows
    }

    private fun getCategoryRow(key: String): ListRow {
        val fromCache = if (this::categoryRows.isInitialized) categoryRows[key] else null
        return fromCache ?: ListRow(HeaderItem(0, key), ArrayObjectAdapter(CardPresenter(activity)))
    }

    private fun getCategoryId() = when(this) {
        is NetworkBrowserFragment ->  HEADER_NETWORK
        is DirectoryBrowserFragment -> HEADER_DIRECTORIES
        else -> -1
    }

    override fun getVM() = viewModel
}