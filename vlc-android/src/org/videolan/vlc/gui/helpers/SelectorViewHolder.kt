/**
 * **************************************************************************
 * SelectorViewHolder.java
 * ****************************************************************************
 * Copyright © 2017 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.helpers

import android.view.View
import androidx.core.content.ContextCompat
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.callbackFlow
import org.videolan.vlc.BR
import org.videolan.vlc.R

open class SelectorViewHolder<T : ViewDataBinding>(vdb: T) : RecyclerView.ViewHolder(vdb.root) {

    var binding: T = vdb
    private val ITEM_FOCUS_ON: Int = ContextCompat.getColor(vdb.root.context, R.color.orange500transparent)
    private val ITEM_FOCUS_OFF: Int = ContextCompat.getColor(vdb.root.context, R.color.transparent)
    private val ITEM_SELECTION_ON: Int = ContextCompat.getColor(vdb.root.context, R.color.orange200transparent)

    protected open fun isSelected() = false

    init {
        itemView.setOnFocusChangeListener { _, hasFocus -> if (layoutPosition >= 0) setViewBackground(hasFocus, isSelected()) }
    }

    open fun selectView(selected: Boolean) {
        setViewBackground(itemView.hasFocus(), selected)
    }

    private fun setViewBackground(focus: Boolean, selected: Boolean) {
        val color = if (focus) ITEM_FOCUS_ON else if (selected) ITEM_SELECTION_ON else ITEM_FOCUS_OFF
        binding.setVariable(BR.bgColor, color)
    }

}
