/*
 * ************************************************************************
 *  LineGraph.kt
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

import android.graphics.Paint
import org.videolan.tools.dp

data class LineGraph(val index: Int, val title: String, val color: Int, val data: HashMap<Long, Float> = HashMap()) {
    val paint: Paint by lazy {
        val p = Paint()
        p.color = color
        p.strokeWidth = 2.dp.toFloat()
        p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p
    }

    override fun equals(other: Any?): Boolean {
        if (other is LineGraph && other.index == index) return true
        return super.equals(other)
    }
}