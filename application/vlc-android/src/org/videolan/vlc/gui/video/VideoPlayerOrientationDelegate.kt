/*
 * ************************************************************************
 *  VideoPlayerOrientationDelegate.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
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

import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.ViewStubCompat
import androidx.core.widget.NestedScrollView
import androidx.leanback.widget.BrowseFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.videolan.tools.Settings
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideoScaleItemBinding
import org.videolan.vlc.gui.helpers.MARQUEE_ACTION
import org.videolan.vlc.gui.helpers.enableMarqueeEffect
import org.videolan.vlc.util.LifecycleAwareScheduler

class VideoPlayerOrientationDelegate(private val player: VideoPlayerActivity) {
    private val overlayDelegate: VideoPlayerOverlayDelegate
        get() = player.overlayDelegate
    private lateinit var orientationMainView: View
    private lateinit var scrollView: NestedScrollView
    private lateinit var orientationList: RecyclerView
    private lateinit var orientationAdapter: OrientationAdapter

    /**
     * Check if the orientation overlay is currently shown
     * @return true if it's shown
     */
    fun isShowing() = ::orientationMainView.isInitialized && orientationMainView.visibility == View.VISIBLE

    /**
     * Show the orientation overlay. Inflate it if it's not yet
     */
    private fun showOrientationOverlay() {
        player.findViewById<ViewStubCompat?>(R.id.player_orientation_stub)?.let {
            it.setVisible()
        }
        player.findViewById<FrameLayout>(R.id.orientation_background)?.let {
            orientationMainView = it
            val browseFrameLayout = orientationMainView.findViewById<BrowseFrameLayout>(R.id.orientation_background)
            browseFrameLayout.onFocusSearchListener = BrowseFrameLayout.OnFocusSearchListener { focused, _ ->
                if (orientationList.hasFocus()) focused // keep focus on recyclerview! DO NOT return recyclerview, but focused, which is a child of the recyclerview
                else null // someone else will find the next focus
            }
            orientationList = orientationMainView.findViewById(R.id.orientation_list)
            scrollView = orientationMainView.findViewById(R.id.orientation_scrollview)

            orientationMainView.findViewById<View>(R.id.close).setOnClickListener {
                hideOrientationOverlay()
            }

            orientationList.layoutManager = LinearLayoutManager(player)
            orientationAdapter = OrientationAdapter(if (player.orientationMode.locked) player.orientationMode.orientation else -1)
            orientationAdapter.setOnSizeSelectedListener { orientation ->
                player.setOrientation(orientation.value)
                hideOrientationOverlay()
            }
            orientationList.adapter = orientationAdapter

        }
    }

    /**
     * Hide the overlay
     */
    fun hideOrientationOverlay() {
        orientationMainView.setGone()
    }


    /**
     * display the orientation overlay and hide everything else
     */
    fun displayOrientation(): Boolean {
        if (player.service?.hasRenderer() == true) return false
        showOrientationOverlay()
        overlayDelegate.hideOverlay(true)
        orientationMainView.setVisible()
        return true
    }

}

/**
 * Adapter showing the different available aspect ratios
 */
class OrientationAdapter(currentOrientation:Int) : RecyclerView.Adapter<OrientationAdapter.ViewHolder>() {


    var selectedSize = OrientationMode.findByValue(currentOrientation).ordinal
        set(value) {
            notifyItemChanged(field)
            field = value
            notifyItemChanged(field)
        }
    lateinit var sizeSelectedListener: (OrientationMode) -> Unit
    private var scheduler: LifecycleAwareScheduler? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = VideoScaleItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    fun setOnSizeSelectedListener(listener: (OrientationMode) -> Unit) {
        sizeSelectedListener = listener
    }

    override fun getItemCount() = OrientationMode.values().size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(OrientationMode.values()[position], position == selectedSize)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (Settings.listTitleEllipsize == 4) scheduler = enableMarqueeEffect(recyclerView)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        scheduler?.cancelAction(MARQUEE_ACTION)
        super.onViewRecycled(holder)
    }


    inner class ViewHolder(val binding: VideoScaleItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {

            itemView.setOnClickListener {
                selectedSize = layoutPosition
                sizeSelectedListener.invoke(OrientationMode.values()[layoutPosition])
            }
        }

        fun bind(orientationMode: OrientationMode, selected: Boolean) {
            binding.scaleName =binding.root.context.getString(orientationMode.title)
            binding.selected = selected
            binding.executePendingBindings()
        }
    }
}

enum class OrientationMode(@StringRes val title: Int, val value: Int) {
    SENSOR(R.string.screen_orientation_sensor, -1),
    PORTRAIT(R.string.screen_orientation_portrait, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    PORTRAIT_REVERSE(R.string.screen_orientation_portrait_reverse, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    LANDSCAPE(R.string.screen_orientation_landscape, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LANDSCAPE_REVERSE(R.string.screen_orientation_landscape_reverse, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    LANDSCAPE_SENSOR(R.string.screen_orientation_landscape_sensor, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

    companion object {
        fun findByValue(value: Int) = values().firstOrNull { it.value == value } ?: PORTRAIT
    }
}