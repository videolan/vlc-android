/*
 * *************************************************************************
 *  BaseAdapter.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
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
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MotionEventCompat
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.media.Artist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaLibraryItem.FLAG_SELECTED
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.AudioBrowserCardItemBinding
import org.videolan.vlc.databinding.AudioBrowserItemBinding
import org.videolan.vlc.gui.helpers.KeyHelper
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getAudioIconDrawable
import org.videolan.vlc.gui.tv.FocusableRecyclerView
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter
import org.videolan.vlc.util.UPDATE_SELECTION
import org.videolan.vlc.util.Util

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AudioBrowserAdapter @JvmOverloads constructor(private val type: Int, private val mIEventsHandler: IEventsHandler, private val mListEventsHandler: IListEventsHandler? = null, private val mReorder: Boolean = false) : PagedListAdapter<MediaLibraryItem, AudioBrowserAdapter.AbstractMediaItemViewHolder<ViewDataBinding>>(DIFF_CALLBACK), FastScroller.SeparatedAdapter, MultiSelectAdapter<MediaLibraryItem>, SwipeDragHelperAdapter {
    var itemSize = -1
    val multiSelectHelper: MultiSelectHelper<MediaLibraryItem> = MultiSelectHelper(this, UPDATE_SELECTION)
    private val mDefaultCover: BitmapDrawable?
    private var focusNext = -1
    private var focusListener: FocusableRecyclerView.FocusListener? = null

    val isEmpty: Boolean
        get() {
            val currentList = currentList
            return currentList == null || currentList.isEmpty()
        }

    init {
        var ctx: Context? = null
        if (mIEventsHandler is Context)
            ctx = mIEventsHandler
        else if (mIEventsHandler is Fragment) ctx = (mIEventsHandler as Fragment).context
        mDefaultCover = if (ctx != null) getAudioIconDrawable(ctx, type) else null
    }

    constructor(typeMedia: Int, eventsHandler: IEventsHandler, itemSize: Int) : this(typeMedia, eventsHandler) {
        this.itemSize = itemSize
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractMediaItemViewHolder<ViewDataBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return if (type == MediaLibraryItem.TYPE_PLAYLIST || type == MediaLibraryItem.TYPE_ARTIST || type == MediaLibraryItem.TYPE_ALBUM) {
            val binding = AudioBrowserCardItemBinding.inflate(inflater, parent, false)
            MediaItemCardViewHolder(binding) as AbstractMediaItemViewHolder<ViewDataBinding>
        } else {
            val binding = AudioBrowserItemBinding.inflate(inflater, parent, false)
            MediaItemViewHolder(binding) as AbstractMediaItemViewHolder<ViewDataBinding>
        }
    }

    override fun onBindViewHolder(holder: AbstractMediaItemViewHolder<ViewDataBinding>, position: Int) {
        if (position >= itemCount) return
        val item = getItem(position)
        holder.setItem(item)
        if (item is Artist) {
            item.description = holder.binding.root.context.resources.getQuantityString(R.plurals.albums_quantity, item.albumsCount, item.albumsCount)
        }
        val isSelected = multiSelectHelper.isSelected(position)
        holder.setCoverlay(isSelected)
        holder.selectView(isSelected)
        holder.binding.executePendingBindings()
        if (position == focusNext) {
            holder.binding.root.requestFocus()
            focusNext = -1
        }
    }

    override fun onBindViewHolder(holder: AbstractMediaItemViewHolder<ViewDataBinding>, position: Int, payloads: List<Any>) {
        if (Util.isListEmpty(payloads))
            onBindViewHolder(holder, position)
        else {
            val payload = payloads[0]
            if (payload is MediaLibraryItem) {
                val isSelected = payload.hasStateFlags(FLAG_SELECTED)
                holder.setCoverlay(isSelected)
                holder.selectView(isSelected)
            } else if (payload is Int) {
                if (payload == UPDATE_SELECTION) {
                    val isSelected = multiSelectHelper.isSelected(position)
                    holder.setCoverlay(isSelected)
                    holder.selectView(isSelected)
                }
            }
        }
    }

    override fun onViewRecycled(h: AbstractMediaItemViewHolder<ViewDataBinding>) {
        h.recycle()
        super.onViewRecycled(h)
    }

    private fun isPositionValid(position: Int): Boolean {
        return position in 0 until itemCount
    }

    override fun getItemId(position: Int): Long {
        if (!isPositionValid(position)) return -1
        val item = getItem(position)
        return item?.id ?: -1
    }

    override fun getItem(position: Int): MediaLibraryItem? {
        return super.getItem(position)
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return item?.itemType ?: MediaLibraryItem.TYPE_MEDIA
    }

    fun clear() {
        //        getDataset().clear();
    }


    override fun onCurrentListChanged(previousList: PagedList<MediaLibraryItem>?, currentList: PagedList<MediaLibraryItem>?) {
        mIEventsHandler.onUpdateFinished(this@AudioBrowserAdapter)
    }

    override fun hasSections(): Boolean {
        return true
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onItemMoved(dragFrom: Int, dragTo: Int) {
        mListEventsHandler!!.onMove(dragFrom, dragTo)
        preventNextAnim = true
    }

    override fun onItemDismiss(position: Int) {
        val item = getItem(position)
        mListEventsHandler!!.onRemove(position, item!!)
    }


    fun setOnFocusChangeListener(focusListener: FocusableRecyclerView.FocusListener?) {
        this.focusListener = focusListener
    }

    inner class MediaItemViewHolder @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: AudioBrowserItemBinding) : AbstractMediaItemViewHolder<AudioBrowserItemBinding>(binding), View.OnFocusChangeListener {
        private var coverlayResource = 0
        var onTouchListener: View.OnTouchListener

        init {
            binding.holder = this
            if (mDefaultCover != null) binding.cover = mDefaultCover
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }

            onTouchListener = object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    if (mListEventsHandler == null) {
                        return false
                    }
                    if (multiSelectHelper.getSelectionCount() != 0) {
                        return false
                    }
                    if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                        mListEventsHandler.onStartDrag(this@MediaItemViewHolder)
                        return true
                    }
                    return false
                }
            }
        }

        override fun setItem(item: MediaLibraryItem?) {
            binding.item = item
        }

        override fun recycle() {
            if (mDefaultCover != null) binding.cover = mDefaultCover
            binding.title.text = ""
            binding.subtitle.text = ""
        }


        override fun setCoverlay(selected: Boolean) {
            val resId = if (selected) R.drawable.ic_action_mode_select else 0
            if (resId != coverlayResource) {
                binding.mediaCover.setImageResource(if (selected) R.drawable.ic_action_mode_select else 0)
                coverlayResource = resId
            }
        }


    }

    inner class MediaItemCardViewHolder @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: AudioBrowserCardItemBinding) : AbstractMediaItemViewHolder<AudioBrowserCardItemBinding>(binding), View.OnFocusChangeListener {
        private var coverlayResource = 0

        init {
            binding.holder = this
            if (mDefaultCover != null) binding.cover = mDefaultCover
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }
            binding.imageWidth = itemSize
            binding.container.layoutParams.width = itemSize

        }

        override fun setItem(item: MediaLibraryItem?) {
            binding.item = item
        }

        override fun recycle() {
            if (mDefaultCover != null) binding.cover = mDefaultCover
            binding.title.text = ""
            binding.subtitle.text = ""
        }


        override fun setCoverlay(selected: Boolean) {
            val resId = if (selected) R.drawable.ic_action_mode_select else 0
            if (resId != coverlayResource) {
                binding.mediaCover.setImageResource(if (selected) R.drawable.ic_action_mode_select else 0)
                coverlayResource = resId
            }
        }


    }


    abstract inner class AbstractMediaItemViewHolder<T : ViewDataBinding> @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: T) : SelectorViewHolder<T>(binding), View.OnFocusChangeListener {

        val canBeReordered: Boolean
            get() = mReorder


        fun onClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null)
                if (KeyHelper.isShiftPressed)
                    mIEventsHandler.onShiftClick(v, layoutPosition, item)
                else
                    mIEventsHandler.onClick(v, layoutPosition, item)
        }

        fun onMoreClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) mIEventsHandler.onCtxClick(v, layoutPosition, item)
        }

        fun onLongClick(view: View): Boolean {
            val item = getItem(layoutPosition)
            return item != null && mIEventsHandler.onLongClick(view, layoutPosition, item)
        }

        fun onImageClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) mIEventsHandler.onImageClick(v, layoutPosition, item)
        }

        fun onMainActionClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) mIEventsHandler.onMainActionClick(v, layoutPosition, item)
        }


        override fun isSelected(): Boolean {
            return multiSelectHelper.isSelected(layoutPosition)
        }

        abstract fun setItem(item: MediaLibraryItem?)

        abstract fun recycle()

        abstract fun setCoverlay(selected: Boolean)
    }

    companion object {

        private val TAG = "VLC/AudioBrowserAdapter"
        private const val UPDATE_PAYLOAD = 1
        /**
         * Awful hack to workaround the [PagedListAdapter] not keeping track of notifyItemMoved operations
         */
        private var preventNextAnim: Boolean = false

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MediaLibraryItem>() {
            override fun areItemsTheSame(
                    oldMedia: MediaLibraryItem, newMedia: MediaLibraryItem): Boolean {
                return if (preventNextAnim) {
                    true
                } else oldMedia === newMedia || oldMedia.itemType == newMedia.itemType && oldMedia.equals(newMedia)
            }

            override fun areContentsTheSame(
                    oldMedia: MediaLibraryItem, newMedia: MediaLibraryItem): Boolean {
                return false
            }

            override fun getChangePayload(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Any? {
                preventNextAnim = false
                return UPDATE_PAYLOAD
            }
        }
    }
}

