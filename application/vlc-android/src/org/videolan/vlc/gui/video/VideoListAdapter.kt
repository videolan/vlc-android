/*****************************************************************************
 * VideoListAdapter.kt
 *
 * Copyright © 2019 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui.video

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.Folder
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.VideoGroup
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.safeOffer
import org.videolan.vlc.BR
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.util.generateResolutionClass
import org.videolan.vlc.util.scope
import org.videolan.vlc.viewmodels.mobile.VideoGroupingType

private const val TAG = "VLC/VideoListAdapter"

class VideoListAdapter(private var isSeenMediaMarkerVisible: Boolean
) : PagedListAdapter<MediaLibraryItem, VideoListAdapter.ViewHolder>(VideoItemDiffCallback),
        MultiSelectAdapter<MediaLibraryItem>, IEventsSource<VideoAction> by EventsSource() {

    var isListMode = false
    var dataType = VideoGroupingType.NONE
    private var gridCardWidth = 0
    val showFilename = ObservableBoolean(false)

    val multiSelectHelper = MultiSelectHelper(this, UPDATE_SELECTION)

    private val thumbObs = Observer<MediaWrapper> { media ->
        val position = currentList?.snapshot()?.indexOf(media) ?: return@Observer
        (getItem(position) as? MediaWrapper)?.run {
            artworkURL = media.artworkURL
            notifyItemChanged(position)
        }
    }

    init {
        Medialibrary.lastThumb.observeForever(thumbObs)
    }

    fun release() {
        Medialibrary.lastThumb.removeObserver(thumbObs)
    }

    val all: List<MediaLibraryItem>
        get() = currentList?.snapshot() ?: emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = DataBindingUtil.inflate<ViewDataBinding>(inflater, if (isListMode) R.layout.video_list_card else R.layout.video_grid_card, parent, false)
        if (!isListMode) {
            val params = binding.root.layoutParams as GridLayoutManager.LayoutParams
            params.width = gridCardWidth
            params.height = params.width * 10 / 16
            binding.root.layoutParams = params
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        holder.binding.setVariable(BR.scaleType, ImageView.ScaleType.CENTER_CROP)
        fillView(holder, item)
        holder.binding.setVariable(BR.media, item)
        holder.selectView(multiSelectHelper.isSelected(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty())
            onBindViewHolder(holder, position)
        else {
            val media = getItem(position)
            for (data in payloads) {
                when (data as Int) {
                    UPDATE_THUMB -> loadImage(holder.overlay, media)
                    UPDATE_TIME, UPDATE_SEEN -> fillView(holder, media as MediaWrapper)
                    UPDATE_SELECTION -> holder.selectView(multiSelectHelper.isSelected(position))
                    UPDATE_VIDEO_GROUP -> fillView(holder, media!!)
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.binding.setVariable(BR.cover, UiTools.getDefaultVideoDrawable(holder.itemView.context))
    }

    override fun getItem(position: Int) = if (isPositionValid(position)) super.getItem(position) else null

    private fun isPositionValid(position: Int) =  position in 0 until itemCount

    private fun fillView(holder: ViewHolder, item: MediaLibraryItem) {
        when (item) {
            is Folder -> {
                holder.title.text = item.title
                if (!isListMode) holder.binding.setVariable(BR.resolution, null)
                holder.binding.setVariable(BR.seen, 0L)
                holder.binding.setVariable(BR.max, 0)
                val count = item.mediaCount(Folder.TYPE_FOLDER_VIDEO)
                holder.binding.setVariable(BR.time, holder.itemView.context.resources.getQuantityString(R.plurals.videos_quantity, count, count))
            }
            is VideoGroup -> holder.itemView.scope.launch {
                val count = item.mediaCount()
                holder.binding.setVariable(BR.time, if (count < 2) null else holder.itemView.context.resources.getQuantityString(R.plurals.videos_quantity, count, count))
                holder.title.text = item.title
                if (!isListMode) holder.binding.setVariable(BR.resolution, null)
                holder.binding.setVariable(BR.seen, 0L)
                holder.binding.setVariable(BR.max, 0)
            }
            is MediaWrapper -> {
                holder.title.text = if (showFilename.get()) item.fileName else item.title
                val text: String?
                val resolution = generateResolutionClass(item.width, item.height)
                var max = 0
                var progress = 0
                var seen = 0L

                text = if (item.type == MediaWrapper.TYPE_GROUP) {
                    item.description
                } else {
                    seen = if (isSeenMediaMarkerVisible) item.seen else 0L
                    /* Time / Duration */
                    if (item.length > 0) {
                        val lastTime = item.displayTime
                        if (lastTime > 0) {
                            max = (item.length / 1000).toInt()
                            progress = (lastTime / 1000).toInt()
                        }
                        if (isListMode && resolution !== null) {
                            "${Tools.millisToText(item.length)} | $resolution"
                        } else Tools.millisToText(item.length)
                    } else null
                }
                holder.binding.setVariable(BR.time, text)
                holder.binding.setVariable(BR.max, max)
                holder.binding.setVariable(BR.progress, progress)
                holder.binding.setVariable(BR.seen, seen)
                if (!isListMode) holder.binding.setVariable(BR.resolution, resolution)
            }
        }
    }


    fun setGridCardWidth(gridCardWidth: Int) {
        this.gridCardWidth = gridCardWidth
    }

    override fun getItemId(position: Int) = 0L

    @TargetApi(Build.VERSION_CODES.M)
    inner class ViewHolder(binding: ViewDataBinding) : SelectorViewHolder<ViewDataBinding>(binding) {
        val overlay: ImageView = itemView.findViewById(R.id.ml_item_overlay)
        val title : TextView = itemView.findViewById(R.id.ml_item_title)

        init {
            binding.setVariable(BR.holder, this)
            binding.setVariable(BR.cover, UiTools.getDefaultVideoDrawable(itemView.context))
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }
        }

        fun onImageClick(v: View) {
            val position = layoutPosition
            if (isPositionValid(position)) getItem(position)?.let { eventsChannel.safeOffer(VideoImageClick(layoutPosition, it)) }
        }

        fun onClick(v: View) {
            val position = layoutPosition
            if (isPositionValid(position)) getItem(position)?.let { eventsChannel.safeOffer(VideoClick(layoutPosition, it)) }
        }

        fun onMoreClick(v: View) {
            val position = layoutPosition
            if (isPositionValid(position)) getItem(position)?.let { eventsChannel.safeOffer(VideoCtxClick(layoutPosition, it)) }
        }

        fun onLongClick(v: View): Boolean {
            val position = layoutPosition
            return isPositionValid(position) && getItem(position)?.let { eventsChannel.safeOffer(VideoLongClick(layoutPosition, it)) } == true
        }

        override fun selectView(selected: Boolean) {
            overlay.setImageResource(if (selected) R.drawable.ic_action_mode_select_1610 else if (isListMode) 0 else R.drawable.black_gradient)
            if (isListMode) overlay.visibility = if (selected) View.VISIBLE else View.GONE
            super.selectView(selected)
        }

        override fun isSelected() = multiSelectHelper.isSelected(layoutPosition)
    }

    private object VideoItemDiffCallback : DiffUtil.ItemCallback<MediaLibraryItem>() {
        override fun areItemsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = when {
            oldItem is MediaWrapper && newItem is MediaWrapper -> {
                oldItem === newItem || oldItem.type == newItem.type && oldItem.equals(newItem)
            }
            else -> oldItem === newItem || oldItem.itemType == newItem.itemType && oldItem.equals(newItem)
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Boolean {
            return if (oldItem is MediaWrapper && newItem is MediaWrapper) {
                oldItem === newItem || (oldItem.displayTime == newItem.displayTime
                        && TextUtils.equals(oldItem.artworkMrl, newItem.artworkMrl)
                        && oldItem.seen == newItem.seen)
            } //else if (oldItem is FolderImpl && newItem is FolderImpl) return oldItem === newItem || (oldItem.title == newItem.title && oldItem.artworkMrl == newItem.artworkMrl)
            else if (oldItem is VideoGroup && newItem is VideoGroup) {
                oldItem === newItem || (oldItem.title == newItem.title
                        && oldItem.tracksCount == newItem.tracksCount)
            }
            else oldItem.itemType == MediaLibraryItem.TYPE_FOLDER || oldItem.itemType == MediaLibraryItem.TYPE_VIDEO_GROUP
        }

        override fun getChangePayload(oldItem: MediaLibraryItem, newItem: MediaLibraryItem) = when {
            (oldItem is MediaWrapper && newItem is MediaWrapper) && oldItem.displayTime != newItem.displayTime -> UPDATE_TIME
            (oldItem is VideoGroup && newItem is VideoGroup) -> UPDATE_VIDEO_GROUP
            !TextUtils.equals(oldItem.artworkMrl, newItem.artworkMrl) -> UPDATE_THUMB
            else -> UPDATE_SEEN
        }
    }

    fun setSeenMediaMarkerVisible(seenMediaMarkerVisible: Boolean) {
        isSeenMediaMarkerVisible = seenMediaMarkerVisible
    }
}

@BindingAdapter("time", "resolution")
fun setLayoutHeight(view: View, time: String, resolution: String) {
    val layoutParams = view.layoutParams
    layoutParams.height = if (TextUtils.isEmpty(time) && TextUtils.isEmpty(resolution))
        ViewGroup.LayoutParams.MATCH_PARENT
    else
        ViewGroup.LayoutParams.WRAP_CONTENT
    view.layoutParams = layoutParams
}
