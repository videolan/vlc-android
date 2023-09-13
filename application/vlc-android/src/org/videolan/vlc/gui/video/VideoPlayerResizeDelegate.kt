/*
 * ************************************************************************
 *  VideoPlayerResizeDelegate.kt
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.appcompat.widget.ViewStubCompat
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView
import androidx.leanback.widget.BrowseFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.ALLOW_FOLD_AUTO_LAYOUT
import org.videolan.tools.AppScope
import org.videolan.tools.DISPLAY_UNDER_NOTCH
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_RATIO
import org.videolan.tools.putSingle
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideoScaleItemBinding
import org.videolan.vlc.gui.helpers.MARQUEE_ACTION
import org.videolan.vlc.gui.helpers.enableMarqueeEffect
import org.videolan.vlc.util.LifecycleAwareScheduler

class VideoPlayerResizeDelegate(private val player: VideoPlayerActivity) {
    private val overlayDelegate: VideoPlayerOverlayDelegate
        get() = player.overlayDelegate
    private lateinit var resizeMainView: View
    private lateinit var notchCheckbox: CheckBox
    private lateinit var foldCheckbox: CheckBox
    private lateinit var scrollView: NestedScrollView
    private lateinit var sizeList: RecyclerView
    private lateinit var sizeAdapter: SizeAdapter

    /**
     * Check if the resize overlay is currently shown
     * @return true if it's shown
     */
    fun isShowing() = ::resizeMainView.isInitialized && resizeMainView.visibility == View.VISIBLE

    /**
     * Show the resize overlay. Inflate it if it's not yet
     */
    fun showResizeOverlay() {
        player.findViewById<ViewStubCompat?>(R.id.player_resize_stub)?.let {
            it.setVisible()
        }
        player.findViewById<FrameLayout>(R.id.resize_background)?.let {
            resizeMainView = it
            val browseFrameLayout = resizeMainView.findViewById<BrowseFrameLayout>(R.id.resize_background)
            browseFrameLayout.onFocusSearchListener = BrowseFrameLayout.OnFocusSearchListener { focused, _ ->
                if (sizeList.hasFocus()) focused // keep focus on recyclerview! DO NOT return recyclerview, but focused, which is a child of the recyclerview
                else null // someone else will find the next focus
            }
            notchCheckbox = resizeMainView.findViewById(R.id.notch)
            val notchTitle = resizeMainView.findViewById<View>(R.id.notch_title)
            sizeList = resizeMainView.findViewById(R.id.size_list)
            scrollView = resizeMainView.findViewById(R.id.resize_scrollview)

            foldCheckbox = resizeMainView.findViewById(R.id.foldable)
            val foldTitle = resizeMainView.findViewById<View>(R.id.foldable_title)

            resizeMainView.findViewById<View>(R.id.close).setOnClickListener {
                hideResizeOverlay()
            }

            sizeList.layoutManager = LinearLayoutManager(player)
            sizeAdapter = SizeAdapter()
            sizeAdapter.setOnSizeSelectedListener { scale ->
                setVideoScale(scale)
            }
            sizeList.adapter = sizeAdapter

            val settings = Settings.getInstance(player)
            if (player.overlayDelegate.foldingFeature != null) {
                foldCheckbox.setVisible()
                foldTitle.setVisible()
                foldCheckbox.isChecked = settings.getBoolean(ALLOW_FOLD_AUTO_LAYOUT, true)
                foldCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    settings.edit { putBoolean(ALLOW_FOLD_AUTO_LAYOUT, isChecked) }
                    player.overlayDelegate.manageHinge()
                }
            } else {
                foldCheckbox.setGone()
                foldTitle.setGone()
            }

            if (player.hasPhysicalNotch) {
                notchCheckbox.setVisible()
                notchTitle.setVisible()
                notchCheckbox.isChecked = settings.getInt(DISPLAY_UNDER_NOTCH, WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                notchCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    val cutoutsAttributes = if (isChecked) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                    player.window.attributes.layoutInDisplayCutoutMode = cutoutsAttributes
                    settings.edit { putInt(DISPLAY_UNDER_NOTCH, cutoutsAttributes) }
                    // needed to apply the new pref
                    overlayDelegate.showOverlay(true)
                    overlayDelegate.hideOverlay(true)
                }
            } else {
                notchTitle.setGone()
                notchCheckbox.setGone()
            }

            resizeMainView.setOnClickListener { hideResizeOverlay() }
            sizeAdapter.selectedSize = MediaPlayer.ScaleType.values().indexOf(player.service?.mediaplayer?.videoScale ?: MediaPlayer.ScaleType.SURFACE_BEST_FIT)
            scrollView.scrollTo(0, 0)
            resizeMainView.visibility = View.VISIBLE
            if (Settings.showTvUi) AppScope.launch {
                delay(100L)
                val position = (sizeList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                (sizeList.layoutManager as LinearLayoutManager).findViewByPosition(position)?.requestFocus()
            }
        }
    }

    /**
     * Hide the overlay
     */
    fun hideResizeOverlay() {
        resizeMainView.setGone()
    }

    /**
     * Resize the video layout to a aspect ratio. It uses the next aspect ratio in line to loop in the different aspect ratio of  [MediaPlayer.ScaleType.getMainScaleTypes()]
     */
    fun resizeVideo() = player.service?.run {
        val currentScaleIndex = MediaPlayer.ScaleType.getMainScaleTypes().indexOf(mediaplayer.videoScale) + 1
        val nextScale = MediaPlayer.ScaleType.getMainScaleTypes()[currentScaleIndex.coerceAtLeast(0) % MediaPlayer.ScaleType.getMainScaleTypes().size]
        setVideoScale(nextScale)
        player.handler.sendEmptyMessage(VideoPlayerActivity.SHOW_INFO)
    }

    /**
     * Resize the video layout to a given aspect ratio
     * @param scale the new aspect ratio
     */
    fun setVideoScale(scale: MediaPlayer.ScaleType) = player.service?.run {
        mediaplayer.videoScale = scale
        when (scale) {
            MediaPlayer.ScaleType.SURFACE_BEST_FIT -> overlayDelegate.showInfo(R.string.surface_best_fit, 1000, R.string.resize_tip)
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN -> overlayDelegate.showInfo(R.string.surface_fit_screen, 1000, R.string.resize_tip)
            MediaPlayer.ScaleType.SURFACE_FILL -> overlayDelegate.showInfo(R.string.surface_fill, 1000, R.string.resize_tip)
            MediaPlayer.ScaleType.SURFACE_16_9 -> overlayDelegate.showInfo("16:9", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_4_3 -> overlayDelegate.showInfo("4:3", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_16_10 -> overlayDelegate.showInfo("16:10", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_221_1 -> overlayDelegate.showInfo("2.21:1", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_235_1 -> overlayDelegate.showInfo("2.35:1", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_239_1 -> overlayDelegate.showInfo("2.39:1", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_5_4 -> overlayDelegate.showInfo("5:4", 1000, player.getString(R.string.resize_tip))
            MediaPlayer.ScaleType.SURFACE_ORIGINAL -> overlayDelegate.showInfo(R.string.surface_original, 1000, R.string.resize_tip)
        }
        settings.putSingle(VIDEO_RATIO, scale.ordinal)
    }

    /**
     * display the resize overlay and hide everything else
     */
    fun displayResize(): Boolean {
        if (player.service?.hasRenderer() == true) return false
        showResizeOverlay()
        overlayDelegate.hideOverlay(true)
        return true
    }
}

/**
 * Adapter showing the different available aspect ratios
 */
class SizeAdapter : RecyclerView.Adapter<SizeAdapter.ViewHolder>() {


    var selectedSize = MediaPlayer.ScaleType.values().indexOf(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
        set(value) {
            notifyItemChanged(field)
            field = value
            notifyItemChanged(field)
        }
    lateinit var sizeSelectedListener: (MediaPlayer.ScaleType) -> Unit
    private var scheduler: LifecycleAwareScheduler? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = VideoScaleItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    fun setOnSizeSelectedListener(listener: (MediaPlayer.ScaleType) -> Unit) {
        sizeSelectedListener = listener
    }

    override fun getItemCount() = MediaPlayer.ScaleType.values().size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(MediaPlayer.ScaleType.values()[position], position == selectedSize)
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
                sizeSelectedListener.invoke(MediaPlayer.ScaleType.values()[layoutPosition])
            }
        }

        fun bind(scaleType: MediaPlayer.ScaleType, selected: Boolean) {
            binding.scaleName = when (scaleType){
                MediaPlayer.ScaleType.SURFACE_BEST_FIT -> binding.trackTitle.context.getString(R.string.surface_best_fit)
                MediaPlayer.ScaleType.SURFACE_FIT_SCREEN -> binding.trackTitle.context.getString(R.string.surface_fit_screen)
                MediaPlayer.ScaleType.SURFACE_FILL -> binding.trackTitle.context.getString(R.string.surface_fill)
                MediaPlayer.ScaleType.SURFACE_ORIGINAL -> binding.trackTitle.context.getString(R.string.surface_original)
                MediaPlayer.ScaleType.SURFACE_16_9 -> "16:9"
                MediaPlayer.ScaleType.SURFACE_4_3 -> "4:3"
                MediaPlayer.ScaleType.SURFACE_16_10 -> "16:10"
                MediaPlayer.ScaleType.SURFACE_221_1 -> "2.21:1"
                MediaPlayer.ScaleType.SURFACE_235_1 -> "2.35:1"
                MediaPlayer.ScaleType.SURFACE_239_1 -> "2.39:1"
                MediaPlayer.ScaleType.SURFACE_5_4 -> "5:4"
            }
            binding.selected = selected
            binding.executePendingBindings()
        }
    }
}