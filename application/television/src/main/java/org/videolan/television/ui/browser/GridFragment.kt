/*****************************************************************************
 * GridFragment.java
 *
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
package org.videolan.television.ui.browser

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import org.videolan.television.ui.CardPresenter
import org.videolan.vlc.interfaces.BrowserFragmentInterface

open class GridFragment : VerticalGridSupportFragment(), BrowserFragmentInterface {

    protected lateinit var adapter: ArrayObjectAdapter
    internal var context: FragmentActivity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = activity
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = NUM_COLUMNS
        setGridPresenter(gridPresenter)
        adapter = ArrayObjectAdapter(CardPresenter(requireActivity()))
        adapter.clear()
        adapter = adapter
    }

    override fun refresh() {}

    companion object {

        protected const val TAG = "VLC/GridFragment"

        private const val NUM_COLUMNS = 4
    }
}