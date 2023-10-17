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
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.liveplotgraph.LineGraph
import org.videolan.tools.dp
import org.videolan.tools.readableSize
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerHudBinding
import org.videolan.vlc.getAllTracks
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.util.LocaleUtil

class VideoStatsDelegate(private val player: VideoPlayerActivity, val scrolling: () -> Unit, val idle: () -> Unit) {
    lateinit var container: ConstraintLayout
    private var lastMediaUri: Uri? = null
    private var started = false
    private val plotHandler: Handler = Handler(Looper.getMainLooper())
    private val firstTimecode = System.currentTimeMillis()
    lateinit var binding: PlayerHudBinding
    private lateinit var constraintSet: ConstraintSet
    private lateinit var constraintSetLarge: ConstraintSet

    fun stop() {
        started = false
        plotHandler.removeCallbacks(runnable)
        container.visibility = View.GONE
        binding.plotView.clear()
    }

    fun start() {
        started = true
        plotHandler.postDelayed(runnable, 300)
        container.visibility = View.VISIBLE
    }

    fun initPlotView(binding: PlayerHudBinding) {
        this.binding = binding
        if (!::constraintSet.isInitialized) {
            constraintSet = ConstraintSet()
            constraintSetLarge = ConstraintSet()
            constraintSet.clone(binding.statsScrollviewContent)
            constraintSetLarge.clone(binding.statsScrollviewContent)
            constraintSetLarge.connect(R.id.stats_graphs, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            constraintSetLarge.clear(R.id.info_grids, ConstraintSet.END)
            constraintSetLarge.connect(R.id.info_grids, ConstraintSet.END, R.id.stats_graphs, ConstraintSet.START)
            constraintSetLarge.connect(R.id.stats_graphs, ConstraintSet.START, R.id.info_grids, ConstraintSet.END)
        }

        binding.statsScrollview.scrollState({ idle() }, { scrolling() })
        binding.plotView.addLine(LineGraph(StatIndex.DEMUX_BITRATE.ordinal, player.getString(R.string.demux_bitrate), ContextCompat.getColor(player, R.color.material_blue)))
        binding.plotView.addLine(LineGraph(StatIndex.INPUT_BITRATE.ordinal, player.getString(R.string.input_bitrate), ContextCompat.getColor(player, R.color.material_pink)))
        setupLayout()
    }

    @SuppressLint("SetTextI18n")
    private val runnable = Runnable {
        val media = player.service?.mediaplayer?.media as? Media ?: return@Runnable

        val stats = media.stats
        if (BuildConfig.DEBUG) Log.i(this::class.java.simpleName, "Stats: demuxBitrate: ${stats?.demuxBitrate} demuxCorrupted: ${stats?.demuxCorrupted} demuxDiscontinuity: ${stats?.demuxDiscontinuity} demuxReadBytes: ${stats?.demuxReadBytes}")
        val now = System.currentTimeMillis() - firstTimecode
        stats?.demuxBitrate?.let {
            binding.plotView.addData(StatIndex.DEMUX_BITRATE.ordinal, Pair(now, it * 8 * 1024))
        }
        stats?.inputBitrate?.let {
            binding.plotView.addData(StatIndex.INPUT_BITRATE.ordinal, Pair(now, it * 8 * 1024))
        }

        if (lastMediaUri != media.uri) {
            lastMediaUri = media.uri
            binding.infoGrids.removeAllViews()
            for (track in media.getAllTracks()) {
                val grid = GridLayout(player)
                grid.columnCount = 2

                if (track.bitrate > 0) addStreamGridView(grid, player.getString(R.string.bitrate), player.getString(R.string.bitrate_value, track.bitrate.toLong().readableSize()))
                addStreamGridView(grid, player.getString(R.string.codec), track.codec)
                if (track.language != null && !track.language.equals("und", ignoreCase = true))
                    addStreamGridView(grid, player.getString(R.string.language), LocaleUtil.getLocaleName(track.language))

                when (track.type) {
                    IMedia.Track.Type.Audio -> {
                        (track as? IMedia.AudioTrack)?.let {
                            addStreamGridView(grid, player.getString(R.string.channels), track.channels.toString())
                            addStreamGridView(grid, player.getString(R.string.track_samplerate), player.getString(R.string.track_samplerate_value, track.rate))
                        }
                    }
                    IMedia.Track.Type.Video -> {
                        (track as? IMedia.VideoTrack)?.let {
                            val frameRate = track.frameRateNum / track.frameRateDen.toDouble()
                            if (track.width != 0 && track.height != 0)
                                addStreamGridView(grid, player.getString(R.string.resolution), player.getString(R.string.resolution_value, track.width, track.height))
                            if (!frameRate.isNaN())
                                addStreamGridView(grid, player.getString(R.string.framerate), player.getString(R.string.framerate_value, frameRate))
                        }
                    }
                }

                val trackTitle = TextView(player, null, R.style.TextAppearance_MaterialComponents_Headline2)
                trackTitle.setTextColor(ContextCompat.getColor(player, R.color.orange500))
                trackTitle.text = when (track.type) {
                    IMedia.Track.Type.Video -> player.getString(R.string.video)
                    IMedia.Track.Type.Audio -> player.getString(R.string.audio)
                    IMedia.Track.Type.Text -> player.getString(R.string.text)
                    else -> player.getString(R.string.unknown)
                }
                val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                layoutParams.bottomMargin = 4.dp
                layoutParams.topMargin = 8.dp
                trackTitle.layoutParams = layoutParams
                if (grid.childCount != 0) {
                    binding.infoGrids.addView(trackTitle)
                    binding.infoGrids.addView(grid)
                }
            }
        }

        if (started) {
            start()
        }
    }

    private fun addStreamGridView(grid: GridLayout, titleString: String, valueString: String) {
        val title = TextView(player, null, R.style.TextAppearance_MaterialComponents_Subtitle1)
        title.text = titleString
        grid.addView(title)

        val value = TextView(player)
        value.text = valueString
        val layoutParams = GridLayout.LayoutParams()
        layoutParams.leftMargin = 4.dp
        value.layoutParams = layoutParams
        grid.addView(value)
    }

    fun onConfigurationChanged() {
        setupLayout()
    }

    private fun setupLayout() {
        if (!::constraintSetLarge.isInitialized) return
        if (player.isTablet()) {
            constraintSetLarge.applyTo(binding.statsScrollviewContent)
        } else {
            constraintSet.applyTo(binding.statsScrollviewContent)
        }
    }
}

enum class StatIndex {
    INPUT_BITRATE, DEMUX_BITRATE
}

inline fun NestedScrollView.scrollState(crossinline idle: () -> Unit, crossinline scrolling: () -> Unit) {
    setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_SCROLL,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_DOWN -> {
                scrolling()
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                idle()
            }
        }
        false
    }
}