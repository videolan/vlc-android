/*
 * ************************************************************************
 *  TvFocusFrameLayout.kt
 * *************************************************************************
 * Copyright © 2026 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A simple [FrameLayout] that allows intercepting focus search events.
 */
class TvFocusFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Listener for focus search events.
     */
    var onFocusSearchListener: OnFocusSearchListener? = null

    interface OnFocusSearchListener {
        fun onFocusSearch(focused: View, direction: Int): View?
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        onFocusSearchListener?.let {
            val result = it.onFocusSearch(focused, direction)
            if (result != null) return result
        }
        return super.focusSearch(focused, direction)
    }
}
