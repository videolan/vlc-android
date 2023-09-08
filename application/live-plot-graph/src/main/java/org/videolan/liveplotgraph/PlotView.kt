/*
 * ************************************************************************
 *  PlotView.kt
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import org.videolan.tools.dp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

class PlotView : FrameLayout {
    private val textPaint: Paint by lazy {
        val p = Paint()
        p.color = color
        p.textSize = 10.dp.toFloat()
        p
    }
    val data = ArrayList<LineGraph>()
    private val maxsY = ArrayList<Float>()
    private val maxsX = ArrayList<Long>()
    private val minsX = ArrayList<Long>()
    private var color: Int = 0xFFFFFF
    private var listeners = ArrayList<PlotViewDataChangeListener>()

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
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.LPGPlotView, 0, defStyle)
            try {
                color = a.getInt(R.styleable.LPGPlotView_lpg_color, 0xFFFFFF)
            } catch (e: Exception) {
                Log.w("", e.message, e)
            } finally {
                a.recycle()
            }
        }
    }

    fun addData(index: Int, value: Pair<Long, Float>) {
        data.forEach { lineGraph ->
            if (lineGraph.index == index) {
                lineGraph.data[value.first] = value.second
                if (lineGraph.data.size > 30) {
                    lineGraph.data.remove(lineGraph.data.toSortedMap().firstKey())
                }
                invalidate()
                val listenerValue = ArrayList<Pair<LineGraph, String>>(data.size)
                data.forEach { line ->
                    listenerValue.add(Pair(line, "${String.format("%.0f", line.data[line.data.keys.maxOrNull()])} kb/s"))
                }
                listeners.forEach { it.onDataChanged(listenerValue) }
            }
        }
    }

    fun addListener(listener: PlotViewDataChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PlotViewDataChangeListener) {
        listeners.remove(listener)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        maxsY.clear()
        maxsX.clear()
        minsX.clear()

        data.forEach {
            maxsY.add(it.data.maxByOrNull { it.value }?.value ?: 0f)
        }
        val maxY = maxsY.maxOrNull() ?: 0f

        data.forEach {
            maxsX.add(it.data.maxByOrNull { it.key }?.key ?: 0L)
        }
        val maxX = maxsX.maxOrNull() ?: 0L

        data.forEach {
            minsX.add(it.data.minByOrNull { it.key }?.key ?: 0L)
        }
        val minX = minsX.minOrNull() ?: 0L

        drawLines(maxY, minX, maxX, canvas)
        drawGrid(canvas, maxY, minX, maxX)
    }

    private fun drawGrid(canvas: Canvas?, maxY: Float, minX: Long, maxX: Long) {
        canvas?.let {
            if (maxY <= 0F) return
            //            it.drawText("0", 10F, it.height.toFloat() - 2.dp, textPaint)
            it.drawText("${String.format("%.0f", maxY)} kb/s", 10F, 10.dp.toFloat(), textPaint)

            var center = maxY / 2
            center = getRoundedByUnit(center)
            if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Center: $center")
            val centerCoord = measuredHeight * ((maxY - center) / maxY)
            it.drawLine(0f, centerCoord, measuredWidth.toFloat(), centerCoord, textPaint)
            it.drawText("${String.format("%.0f", center)} kb/s", 10F, centerCoord - 2.dp, textPaint)

            //timestamps

            var index = maxX - 1000
            if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "FirstIndex: $index")
            while (index > minX) {
                val xCoord = (measuredWidth * ((index - minX).toDouble() / (maxX - minX).toDouble())).toFloat()
                it.drawLine(xCoord, 0F, xCoord, measuredHeight.toFloat() - 12.dp, textPaint)
                val formattedText = "${String.format("%.0f", getRoundedByUnit((index - maxX).toFloat()) / 1000)}s"
                it.drawText(formattedText, xCoord - (textPaint.measureText(formattedText) / 2), measuredHeight.toFloat(), textPaint)
                index -= 1000
            }

        }
    }

    private fun getRoundedByUnit(number: Float): Float {
        val lengthX = log10(number.toDouble()).toInt()
        return (round(number / (10.0.pow(lengthX.toDouble()))) * (10.0.pow(lengthX.toDouble()))).toFloat()
    }

    private fun drawLines(maxY: Float, minX: Long, maxX: Long, canvas: Canvas?) {
        data.forEach { line ->
            var initialPoint: Pair<Float, Float>? = null
            line.data.toSortedMap().forEach { point ->
                if (initialPoint == null) {
                    initialPoint = getCoordinates(point, maxY, minX, maxX, measuredWidth, measuredHeight)
                } else {
                    val currentPoint = getCoordinates(point, maxY, minX, maxX, measuredWidth, measuredHeight)
                    currentPoint.let {
                        canvas?.drawLine(initialPoint!!.first, initialPoint!!.second, it.first, it.second, line.paint)
                        initialPoint = it
                    }
                }
            }
        }
    }

//    fun drawLines2(maxY: Float, minX: Long, maxX: Long, canvas: Canvas?) {
//
//
//        data.forEach { line ->
//            path.reset()
//            val points = line.data.map {
//                val coord = getCoordinates(it, maxY, minX, maxX, measuredWidth, measuredHeight)
//                GraphPoint(coord.first, coord.second)
//            }.sortedBy { it.x }
//            for (i in points.indices) {
//                val point = points[i]
//                val smoothing = 100
//                when (i) {
//                    0 -> {
//                        val next: GraphPoint = points[i + 1]
//                        point.dx = (next.x - point.x) / smoothing
//                        point.dy = (next.y - point.y) / smoothing
//                    }
//                    points.size - 1 -> {
//                        val prev: GraphPoint = points[i - 1]
//                        point.dx = (point.x - prev.x) / smoothing
//                        point.dy = (point.y - prev.y) / smoothing
//                    }
//                    else -> {
//                        val next: GraphPoint = points[i + 1]
//                        val prev: GraphPoint = points[i - 1]
//                        point.dx = next.x - prev.x / smoothing
//                        point.dy = (next.y - prev.y) / smoothing
//                    }
//                }
//            }
//            for (i in points.indices) {
//                val point: GraphPoint = points[i]
//                when {
//                    i == 0 -> {
//                        path.moveTo(point.x, point.y)
//                    }
//                    i < points.size - 1 -> {
//                        val prev: GraphPoint = points[i - 1]
//                        path.cubicTo(prev.x + prev.dx, prev.y + prev.dy, point.x - point.dx, point.y - point.dy, point.x, point.y)
//                        canvas?.drawCircle(point.x, point.y, 2.dp.toFloat(), line.paint)
//                    }
//                    else -> {
//                        path.lineTo(point.x, point.y)
//                    }
//                }
//            }
//            canvas?.drawPath(path, line.paint)
//        }
//
//    }

    private fun getCoordinates(point: Map.Entry<Long, Float>, maxY: Float, minX: Long, maxX: Long, measuredWidth: Int, measuredHeight: Int): Pair<Float, Float> = Pair((measuredWidth * ((point.key - minX).toDouble() / (maxX - minX).toDouble())).toFloat(), measuredHeight * ((maxY - point.value) / maxY))
    fun clear() {
        data.forEach {
            it.data.clear()
        }
    }

    fun addLine(lineGraph: LineGraph) {
        if (!data.contains(lineGraph)) {
            data.add(lineGraph)
        }
    }
}

data class GraphPoint(val x: Float, val y: Float) {
    var dx: Float = 0F
    var dy: Float = 0F
}

interface PlotViewDataChangeListener {
    fun onDataChanged(data: List<Pair<LineGraph, String>>)
}