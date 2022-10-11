/*
 * ************************************************************************
 *  DiscoverBrowserFragment.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.discover

import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment

class DiscoverBrowserFragment : BaseFragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.discover_browser, container, false)
    }


    override fun getTitle() = getString(R.string.discover)

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

    override fun onDestroyActionMode(mode: ActionMode?) { }

}