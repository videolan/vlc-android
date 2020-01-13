/*
 * ************************************************************************
 *  VideoStatsDelegate.kt
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

package org.videolan.vlc.gui.video

import android.annotation.SuppressLint
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.GridLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.Media
import org.videolan.liveplotgraph.LineGraph
import org.videolan.liveplotgraph.PlotView
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class VideoStatsDelegate(private val player: VideoPlayerActivity) {
    lateinit var container: ConstraintLayout
    private var started = false
    private val plotHandler: Handler = Handler()
    private val firstTimecode = System.currentTimeMillis()
    lateinit var plotView: PlotView

    fun stop() {
        started = false
        plotHandler.removeCallbacks(runnable)
        container.visibility = View.GONE
        plotView.clear()
    }

    fun start() {
        started = true
        plotHandler.postDelayed(runnable, 300)
        container.visibility = View.VISIBLE
    }

    fun initPlotView(plotView: PlotView) {
        this.plotView = plotView
        plotView.addLine(LineGraph(StatIndex.DEMUX_BITRATE.ordinal, player.getString(R.string.demux_bitrate), ContextCompat.getColor(player, R.color.material_blue)))
        plotView.addLine(LineGraph(StatIndex.INPUT_BITRATE.ordinal, player.getString(R.string.input_bitrate), ContextCompat.getColor(player, R.color.material_pink)))
    }

    @SuppressLint("SetTextI18n")
    private val runnable = Runnable {
        val media = player.service?.mediaplayer?.media as? Media ?: return@Runnable

        if (BuildConfig.DEBUG) Log.i(this::class.java.simpleName, "Stats: demuxBitrate: ${media.stats?.demuxBitrate} demuxCorrupted: ${media.stats?.demuxCorrupted} demuxDiscontinuity: ${media.stats?.demuxDiscontinuity} demuxReadBytes: ${media.stats?.demuxReadBytes}")
        val now = System.currentTimeMillis() - firstTimecode
        media.stats?.demuxBitrate?.let {
            plotView.addData(StatIndex.DEMUX_BITRATE.ordinal, Pair(now, it * 8 * 1024))
        }
        media.stats?.inputBitrate?.let {
            plotView.addData(StatIndex.INPUT_BITRATE.ordinal, Pair(now, it * 8 * 1024))
        }

        media.let {
            for (i in 0 until it.trackCount) {
                val grid = GridLayout(player)
                grid.columnCount = 2
            }
        }

        if (started) {
            start()
        }
    }
}

enum class StatIndex {
    INPUT_BITRATE, DEMUX_BITRATE
}