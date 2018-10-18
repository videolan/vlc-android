/*
 * *************************************************************************
 *  VideoBrowserFragment.kt
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

import android.annotation.TargetApi
import androidx.lifecycle.Observer
import android.os.Build
import android.os.Bundle
import org.videolan.vlc.util.KEY_GROUP
import org.videolan.vlc.viewmodels.VideosModel

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class VideoBrowserFragment : CategoriesFragment<VideosModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel = VideosModel.get(requireContext(), this, arguments?.getString(KEY_GROUP))
        viewModel.categories.observe(this, Observer { update(it) })
        super.onCreate(savedInstanceState)
    }
}
