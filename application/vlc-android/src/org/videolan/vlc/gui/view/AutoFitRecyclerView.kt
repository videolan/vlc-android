/*
 * *************************************************************************
 *  AutoFitRecyclerView.java
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

package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AutoFitRecyclerView : RecyclerView {

    private var gridLayoutManager: GridLayoutManager? = null
    var columnWidth = -1
    private var spanCount = -1

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val attrsArray = intArrayOf(android.R.attr.columnWidth)
            val array = context.obtainStyledAttributes(attrs, attrsArray)
            columnWidth = array.getDimensionPixelSize(0, -1)
            array.recycle()
        }

        gridLayoutManager = GridLayoutManager(getContext(), 1)
        layoutManager = gridLayoutManager
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (spanCount == -1 && columnWidth > 0) {
            val ratio = measuredWidth / columnWidth
            val spanCount = 2.coerceAtLeast(ratio)
            gridLayoutManager!!.spanCount = spanCount
        } else
            gridLayoutManager!!.spanCount = spanCount

    }

    fun getPerfectColumnWidth(columnWidth: Int, margin: Int): Int {

        val wm = context.applicationContext.getSystemService<WindowManager>()!!
        val display = wm.defaultDisplay
        val displayWidth = display.width - margin

        val remainingSpace = displayWidth % columnWidth
        val ratio = displayWidth / columnWidth
        val spanCount = 2.coerceAtLeast(ratio)

        return columnWidth + remainingSpace / spanCount
    }

    fun setNumColumns(spanCount: Int) {
        this.spanCount = spanCount
    }


}
