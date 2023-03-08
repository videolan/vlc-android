/*
 * *************************************************************************
 *  AudioPagerAdapter.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter

class AudioPagerAdapter(private val lists: Array<View>?, private val titles: Array<String>) : PagerAdapter() {

    override fun getCount(): Int {
        return lists?.size ?: 0
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        return lists!![position]
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return if (position < 0 || position >= titles.size)
            ""
        else
            titles[position]
    }

}
