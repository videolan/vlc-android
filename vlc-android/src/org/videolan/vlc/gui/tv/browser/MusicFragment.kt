/*
 * *************************************************************************
 *  MusicFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import org.videolan.vlc.R
import org.videolan.vlc.viewmodels.audio.TracksModel

    class MusicFragment : MediaLibBrowserFragment<TracksModel>() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            title = currentItem?.title ?: getString(R.string.tracks)
            model = ViewModelProviders.of(this, TracksModel.Factory(requireContext(), currentItem)).get(TracksModel::class.java)
            model.dataset.observe(this, Observer { update(it!!) })
        }
}