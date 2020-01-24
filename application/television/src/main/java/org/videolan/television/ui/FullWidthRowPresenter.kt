/*
 * ************************************************************************
 *  FullWidthRowPresenter.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.television.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.RowPresenter
import org.videolan.television.R

open class FullWidthRowPresenter : RowPresenter() {

    open inner class FullWidthRowPresenterViewHolder(view: View) : ViewHolder(view) {

//        init {
//            val container = view.findViewById<ConstraintLayout>(R.id.container)
//        }
    }

    init {
        selectEffectEnabled = false
    }

    override fun createRowViewHolder(parent: ViewGroup): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.tv_description_row, parent, false)
        return FullWidthRowPresenterViewHolder(v)
    }

    override fun isUsingDefaultSelectEffect(): Boolean {
        return true
    }
}