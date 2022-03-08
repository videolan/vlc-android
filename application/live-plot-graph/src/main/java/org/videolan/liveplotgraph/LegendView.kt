/*
 * ************************************************************************
 *  LegendView.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.liveplotgraph

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.videolan.tools.dp

class LegendView : ConstraintLayout, PlotViewDataChangeListener {

    private var plotViewId: Int = -1
    private lateinit var plotView: PlotView

    constructor(context: Context) : super(context) {
        setWillNotDraw(false)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initAttributes(attrs, 0)
        setWillNotDraw(false)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        initAttributes(attrs, defStyle)
        setWillNotDraw(false)
    }

    private fun initAttributes(attrs: AttributeSet, defStyle: Int) {
        attrs.let {

            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.LPGLegendView, 0, defStyle)
            try {
                plotViewId = a.getResourceId(R.styleable.LPGLegendView_lpg_plot_view, -1)
            } catch (e: Exception) {
                Log.w("LegendView", e.message, e)
            } finally {
                a.recycle()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        //workaround for editor
        if (!isInEditMode) {
            (context as? Activity)?.let { activity ->
                activity.findViewById<PlotView>(plotViewId)?.let {
                    plotView = it
                    plotView.addListener(this)
                }
            } ?: Log.w("LegendView", "Cannot find the plot view with id $plotViewId")
        }
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    override fun onDataChanged(data: List<Pair<LineGraph, String>>) {
        removeAllViews()
        val grid = GridLayout(context)
        grid.columnCount = 2
        addView(grid)
        data.forEach {
            val title = TextView(context)
            title.text = it.first.title
            title.setTextColor(it.first.color)
            grid.addView(title)

            val value = TextView(context)
            value.text = it.second
            val layoutParams = GridLayout.LayoutParams()
            layoutParams.leftMargin = 4.dp
            value.layoutParams = layoutParams
            grid.addView(value)
        }
    }
}