/*
 * *************************************************************************
 *  PlaylistAdapter.java
 * **************************************************************************
 *  Copyright © 2015-2017 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.audio

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.tools.Settings
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlaylistItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.MARQUEE_ACTION
import org.videolan.vlc.gui.helpers.MarqueeViewHolder
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.isTablet
import org.videolan.vlc.gui.helpers.enableMarqueeEffect
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.view.MiniVisualizer
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.MediaItemDiffCallback
import org.videolan.vlc.util.SchedulerCallback
import org.videolan.vlc.viewmodels.PlaylistModel
import java.util.Collections

private const val ACTION_MOVE = "action_move"
private const val ACTION_MOVED = "action_moved"

class PlaylistAdapter(private val player: IPlayer) : DiffUtilAdapter<MediaWrapper, PlaylistAdapter.ViewHolder>(), SwipeDragHelperAdapter, SchedulerCallback {

    var showTrackNumbers: Boolean = false
    var showReorderButtons: Boolean = true
    private var defaultCoverVideo: BitmapDrawable
    private var defaultCoverAudio: BitmapDrawable
    private var model: PlaylistModel? = null
    private var currentPlayingVisu: MiniVisualizer? = null
    lateinit var scheduler: LifecycleAwareScheduler
    var marqueeScheduler: LifecycleAwareScheduler? = null
    var stopAfter: Int = -1
        set(value) {
            val old = field
            field = value
            if (old in 1 until itemCount) notifyItemChanged(old)
            if (value in 1 until itemCount) notifyItemChanged(value)

        }

    init {
        val ctx = when (player) {
            is Context -> player
            is Fragment -> player.requireContext()
            else -> AppContextProvider.appContext
        }

        defaultCoverAudio = BitmapDrawable(ctx.resources, getBitmapFromDrawable(ctx, R.drawable.ic_song_background))
        defaultCoverVideo = UiTools.getDefaultVideoDrawable(ctx)
        scheduler =  LifecycleAwareScheduler(this)
    }

    var currentIndex = 0
        set(position) {
            if (position >= itemCount) return
            val former = currentIndex
            field = position
            if (former >= 0) notifyItemChanged(former)
            if (position >= 0) {
                notifyItemChanged(position)
                player.onSelectionSet(position)
            }
        }

    interface IPlayer {
        fun onPopupMenu(view: View, position: Int, item: MediaWrapper?)
        fun onSelectionSet(position: Int)
        fun playItem(position: Int, item: MediaWrapper)
        fun getLifeCycle(): Lifecycle
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.playlist_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = getItemByPosition(position)
        holder.binding.media = media
        holder.binding.subTitle = MediaUtils.getMediaSubtitle(media)
        holder.binding.scaleType = ImageView.ScaleType.CENTER_CROP
        holder.binding.stopAfter.visibility = if (stopAfter == position) View.VISIBLE else View.GONE
        holder.binding.stopAfterThis = (position == stopAfter)
        holder.binding.showTrackNumbers = showTrackNumbers
        if (currentIndex == position) {
            if (model?.playing != false) holder.binding.playing.start() else holder.binding.playing.stop()
            holder.binding.playing.visibility = View.VISIBLE
            holder.binding.coverImage.visibility = View.INVISIBLE
            holder.binding.audioItemTitle.setTypeface(null, Typeface.BOLD)
            holder.binding.audioItemSubtitle.setTypeface(null, Typeface.BOLD)
            currentPlayingVisu = holder.binding.playing
        } else {
            holder.binding.playing.stop()
            holder.binding.playing.visibility = View.INVISIBLE
            holder.binding.audioItemTitle.typeface = null
            holder.binding.coverImage.visibility = View.VISIBLE
        }

        if (media.type == MediaWrapper.TYPE_VIDEO) {
            (holder.binding.coverImage.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "16:10"
            holder.binding.cover = defaultCoverVideo
        } else {
            (holder.binding.coverImage.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = "1"
            holder.binding.cover = defaultCoverAudio
        }

        val tablet = holder.binding.itemDelete.context.isTablet() || AndroidDevices.isTv
        if (tablet) holder.binding.itemDelete.setVisible() else holder.binding.itemDelete.setGone()
        holder.binding.showReorderButtons = showReorderButtons && tablet

        holder.binding.executePendingBindings()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        marqueeScheduler?.cancelAction("")
        currentPlayingVisu?.stop()
        currentPlayingVisu = null
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (Settings.listTitleEllipsize == 4) marqueeScheduler = enableMarqueeEffect(recyclerView)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        marqueeScheduler?.cancelAction(MARQUEE_ACTION)
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = dataset.size

    @MainThread
    override fun getItemByPosition(position: Int) = dataset[position]

    override fun onUpdateFinished() {
        model?.run { currentIndex = selection }
    }

    @MainThread
    fun remove(position: Int) {
        model?.run { remove(position) }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(dataset, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        scheduler.startAction(ACTION_MOVE, bundleOf("from" to fromPosition, "to" to toPosition))
    }

    override fun onItemMoved(dragFrom: Int, dragTo: Int) {
    }

    override fun onItemDismiss(position: Int) {
        model?.let {
            val media = getItemByPosition(position)
            val message = String.format(AppContextProvider.appResources.getString(R.string.remove_playlist_item), media.title)
            val originalPosition = it.getOriginalPosition(position)
            if (player is Fragment) {
                UiTools.snackerWithCancel(player.requireActivity(), message, overAudioPlayer = true, action = {}) {
                    model?.run { insertMedia(originalPosition, media) }
                }
            } else if (player is Activity) {
                UiTools.snackerWithCancel(player, message, action = {}) {
                    model?.run { insertMedia(originalPosition, media) }
                }
            }
            remove(position)
        }
    }

    fun setModel(model: PlaylistModel) {
        this.model = model
    }

    inner class ViewHolder @TargetApi(Build.VERSION_CODES.M)
    constructor(v: View) : RecyclerView.ViewHolder(v), MarqueeViewHolder {
        var binding: PlaylistItemBinding = DataBindingUtil.bind(v)!!
        override val titleView = binding.audioItemTitle

        init {
            binding.holder = this
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { view ->
                    onMoreClick(view)
                    true
                }
        }

        fun onClick(@Suppress("UNUSED_PARAMETER") v: View, media: MediaWrapper) {
            val position = layoutPosition //getMediaPosition(media);
            player.playItem(position, media)
        }

        fun onMoreClick(v: View) {
            val position = layoutPosition
            player.onPopupMenu(v, position, getItemByPosition(position))
        }

        fun onDeleteClick(@Suppress("UNUSED_PARAMETER") v: View) {
            onItemDismiss(layoutPosition)
        }
        fun onMoveUpClick(@Suppress("UNUSED_PARAMETER") v: View) {
            if (layoutPosition != 0) onItemMove(layoutPosition, layoutPosition - 1)
        }

        fun onMoveDownClick(@Suppress("UNUSED_PARAMETER") v: View) {
            if (layoutPosition != itemCount - 1) onItemMove(layoutPosition, layoutPosition + 1)
        }
    }

    override fun createCB(): DiffCallback<MediaWrapper> = MediaItemDiffCallback()

    fun setCurrentlyPlaying(playing: Boolean) {
        if (playing) currentPlayingVisu?.start() else currentPlayingVisu?.stop()
    }

    var from = -1
    var to = -1
    override fun onTaskTriggered(id: String, data: Bundle) {
        when (id) {
            ACTION_MOVE -> {
                scheduler.cancelAction(ACTION_MOVED)
                if (from == -1) from = data.getInt("from")
                to = data.getInt("to")
                scheduler.scheduleAction(ACTION_MOVED, 1000)
            }
            ACTION_MOVED -> {
                val model = model ?: return
                if (to > from) ++to
                model.move(from, to)
                to = -1
                from = to
            }
        }
    }

    override val lifecycle: Lifecycle
        get() = player.getLifeCycle()
}
