/*
 * ************************************************************************
 *  MoviepediaTvItemAdapter.kt
 * *************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.television.ui

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.moviepedia.database.models.MediaMetadataWithImages
import org.videolan.resources.UPDATE_PAYLOAD
import org.videolan.resources.interfaces.FocusListener
import org.videolan.television.databinding.MovieBrowserTvItemBinding
import org.videolan.television.databinding.MovieBrowserTvItemListBinding
import org.videolan.television.ui.browser.TvAdapterUtils
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getMoviepediaIconDrawable
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.generateResolutionClass

class MediaScrapingTvItemAdapter(
        type: Long,
        private val eventsHandler: IEventsHandler<MediaMetadataWithImages>,
        var itemSize: Int,
        private var inGrid: Boolean = true
) : PagedListAdapter<MediaMetadataWithImages, MediaScrapingTvItemAdapter.AbstractMediaScrapingItemViewHolder<ViewDataBinding>>(DIFF_CALLBACK),
        FastScroller.SeparatedAdapter, TvItemAdapter
{
    override var focusNext = -1
    override fun displaySwitch(inGrid: Boolean) {
        this.inGrid = inGrid
    }

    private val defaultCover: BitmapDrawable?
    private var focusListener: FocusListener? = null

    init {
        val ctx: Context? = when (eventsHandler) {
            is Context -> eventsHandler
            is Fragment -> (eventsHandler as Fragment).context
            else -> null
        }
        defaultCover = ctx?.let { getMoviepediaIconDrawable(it, type, true) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractMediaScrapingItemViewHolder<ViewDataBinding> {
        val inflater = LayoutInflater.from(parent.context)
        @Suppress("UNCHECKED_CAST")
        return if (inGrid) MovieItemTVViewHolder(MovieBrowserTvItemBinding.inflate(inflater, parent, false), eventsHandler) as AbstractMediaScrapingItemViewHolder<ViewDataBinding>
        else MovieItemTVListViewHolder(MovieBrowserTvItemListBinding.inflate(inflater, parent, false), eventsHandler) as AbstractMediaScrapingItemViewHolder<ViewDataBinding>
    }

    override fun getItemViewType(position: Int): Int {
        return if (inGrid) 0 else 1
    }

    override fun onBindViewHolder(holder: AbstractMediaScrapingItemViewHolder<ViewDataBinding>, position: Int) {
        if (position >= itemCount) return
        val item = getItem(position)
        holder.setItem(item)
        holder.binding.executePendingBindings()
        if (position == focusNext) {
            holder.binding.root.requestFocus()
            focusNext = -1
        }
    }

    override fun onBindViewHolder(holder: AbstractMediaScrapingItemViewHolder<ViewDataBinding>, position: Int, payloads: List<Any>) {
        if (payloads.isNullOrEmpty())
            onBindViewHolder(holder, position)
        else {
            val payload = payloads[0]
            if (payload is MediaLibraryItem) {
                val isSelected = payload.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)
                holder.setCoverlay(isSelected)
                holder.selectView(isSelected)
            }
        }
    }

    override fun onViewRecycled(holder: AbstractMediaScrapingItemViewHolder<ViewDataBinding>) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    override fun hasSections() = true

    override fun submitList(pagedList: Any?) {
        if (pagedList == null) {
            this.submitList(null)
        }
        if (pagedList is PagedList<*>) {
            @Suppress("UNCHECKED_CAST")
            this.submitList(pagedList as PagedList<MediaMetadataWithImages>)
        }
    }

    override fun isEmpty() = currentList?.isEmpty() != false

    override fun setOnFocusChangeListener(focusListener: FocusListener?) {
        this.focusListener = focusListener
    }

    companion object {

        private const val TAG = "VLC/MediaTvItemAdapter"
        /**
         * Awful hack to workaround the [PagedListAdapter] not keeping track of notifyItemMoved operations
         */
        private var preventNextAnim: Boolean = false

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MediaMetadataWithImages>() {
            override fun areItemsTheSame(
                    oldMedia: MediaMetadataWithImages, newMedia: MediaMetadataWithImages) = if (preventNextAnim) true
            else oldMedia === newMedia

            override fun areContentsTheSame(oldMedia: MediaMetadataWithImages, newMedia: MediaMetadataWithImages) = false

            override fun getChangePayload(oldItem: MediaMetadataWithImages, newItem: MediaMetadataWithImages): Any? {
                preventNextAnim = false
                return UPDATE_PAYLOAD
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    abstract class AbstractMediaScrapingItemViewHolder<T : ViewDataBinding>(binding: T) : SelectorViewHolder<T>(binding) {

        fun onClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onClick(v, layoutPosition, it) }
        }

        fun onMoreClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onCtxClick(v, layoutPosition, it) }
        }

        fun onLongClick(view: View): Boolean {
            return getItem(layoutPosition)?.let { eventsHandler.onLongClick(view, layoutPosition, it) }
                    ?: false
        }

        fun onImageClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onImageClick(v, layoutPosition, it) }
        }

        fun onMainActionClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onMainActionClick(v, layoutPosition, it) }
        }

        abstract fun getItem(layoutPosition: Int): MediaMetadataWithImages?

        abstract val eventsHandler: IEventsHandler<MediaMetadataWithImages>

        abstract fun setItem(item: MediaMetadataWithImages?)

        abstract fun recycle()

        abstract fun setCoverlay(selected: Boolean)
    }

    @TargetApi(Build.VERSION_CODES.M)
    inner class MovieItemTVViewHolder(
            binding: MovieBrowserTvItemBinding,
            override val eventsHandler: IEventsHandler<MediaMetadataWithImages>
    ) : AbstractMediaScrapingItemViewHolder<MovieBrowserTvItemBinding>(binding) {
        override fun getItem(layoutPosition: Int) = this@MediaScrapingTvItemAdapter.getItem(layoutPosition)

        init {
            binding.holder = this
            if (defaultCover != null) binding.cover = defaultCover
            binding.scaleType = ImageView.ScaleType.FIT_CENTER
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }
            binding.container.layoutParams.width = itemSize
            binding.container.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                TvAdapterUtils.itemFocusChange(hasFocus, itemSize, binding.container, false) {
                    eventsHandler.onItemFocused(binding.root, getItem(layoutPosition)!!)
                    if (focusListener != null) {
                        focusListener!!.onFocusChanged(layoutPosition)
                    }
                }
            }
            binding.container.clipToOutline = true
        }

        override fun recycle() {
            if (defaultCover != null) binding.cover = defaultCover
            binding.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.title.text = ""
            binding.subtitle.text = ""
            binding.mediaCover.resetFade()
        }

        override fun setItem(item: MediaMetadataWithImages?) {
            binding.item = item
            var progress = 0
            var seen = 0L
            var description = item?.metadata?.summary
            var resolution = ""
            item?.media?.let { media ->
                if (media.type == MediaWrapper.TYPE_VIDEO) {
                    resolution = generateResolutionClass(media.width, media.height) ?: ""
                    description = if (media.time == 0L) Tools.millisToString(media.length) else Tools.getProgressText(media)
                    binding.badge = resolution
                    seen = media.seen
                    var max = 0

                    if (media.length > 0) {
                        val lastTime = media.displayTime
                        if (lastTime > 0) {
                            max = (media.length / 1000).toInt()
                            progress = (lastTime / 1000).toInt()
                        }
                    }
                    binding.max = max
                }
            }

            binding.progress = progress
            binding.isSquare = false
            binding.seen = seen
            binding.description = description
            binding.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.mlItemSeen.visibility = if (seen == 0L) View.GONE else View.VISIBLE
            binding.progressBar.visibility = if (progress <= 0L) View.GONE else View.VISIBLE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        override fun setCoverlay(selected: Boolean) {
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    inner class MovieItemTVListViewHolder(
            binding: MovieBrowserTvItemListBinding,
            override val eventsHandler: IEventsHandler<MediaMetadataWithImages>
    ) : AbstractMediaScrapingItemViewHolder<MovieBrowserTvItemListBinding>(binding) {
        override fun getItem(layoutPosition: Int) = this@MediaScrapingTvItemAdapter.getItem(layoutPosition)

        init {
            binding.holder = this
            if (defaultCover != null) binding.cover = defaultCover
            binding.scaleType = ImageView.ScaleType.FIT_CENTER
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }
            binding.container.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                TvAdapterUtils.itemFocusChange(hasFocus, itemSize, binding.container, true) {
                    eventsHandler.onItemFocused(binding.root, getItem(layoutPosition)!!)
                    if (focusListener != null) {
                        focusListener!!.onFocusChanged(layoutPosition)
                    }
                }
            }
            binding.container.clipToOutline = true
        }

        override fun recycle() {
            if (defaultCover != null) binding.cover = defaultCover
            binding.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.title.text = ""
            binding.subtitle.text = ""
            binding.mediaCover.resetFade()
        }

        override fun setItem(item: MediaMetadataWithImages?) {
            binding.item = item
            var progress = 0
            var seen = 0L
            var description = item?.metadata?.summary
            var resolution = ""
            item?.media?.let { media ->
                if (media.type == MediaWrapper.TYPE_VIDEO) {
                    resolution = generateResolutionClass(media.width, media.height) ?: ""
                    description = if (media.time == 0L) Tools.millisToString(media.length) else Tools.getProgressText(media)
                    binding.badge = resolution
                    seen = media.seen
                    var max = 0

                    if (media.length > 0) {
                        val lastTime = media.displayTime
                        if (lastTime > 0) {
                            max = (media.length / 1000).toInt()
                            progress = (lastTime / 1000).toInt()
                        }
                    }
                    binding.max = max
                }
            }

            binding.progress = progress
            binding.isSquare = false
            binding.seen = seen
            binding.description = description
            binding.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.mlItemSeen.visibility = if (seen == 0L) View.GONE else View.VISIBLE
            binding.progressBar.visibility = if (progress <= 0L) View.GONE else View.VISIBLE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        override fun setCoverlay(selected: Boolean) {
        }
    }
}
