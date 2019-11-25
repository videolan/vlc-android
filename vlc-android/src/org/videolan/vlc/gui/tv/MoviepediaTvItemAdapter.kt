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

package org.videolan.vlc.gui.tv

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.database.models.MediaMetadataWithImages
import org.videolan.vlc.databinding.MovieBrowserTvItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getMoviepediaIconDrawable
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.UPDATE_PAYLOAD
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.generateResolutionClass

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MoviepediaTvItemAdapter(type: Long, private val eventsHandler: IEventsHandler<MediaMetadataWithImages>, var itemSize: Int) : PagedListAdapter<MediaMetadataWithImages, MoviepediaTvItemAdapter.AbstractMoviepediaItemViewHolder<ViewDataBinding>>(DIFF_CALLBACK), FastScroller.SeparatedAdapter, TvItemAdapter {
    override var focusNext = -1
    private val defaultCover: BitmapDrawable?
    private var focusListener: FocusableRecyclerView.FocusListener? = null

    init {
        val ctx: Context? = when (eventsHandler) {
            is Context -> eventsHandler
            is Fragment -> (eventsHandler as Fragment).context
            else -> null
        }
        defaultCover = ctx?.let { getMoviepediaIconDrawable(it, type, true) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractMoviepediaItemViewHolder<ViewDataBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = MovieBrowserTvItemBinding.inflate(inflater, parent, false)
        @Suppress("UNCHECKED_CAST")
        return MovieItemTVViewHolder(binding, eventsHandler) as AbstractMoviepediaItemViewHolder<ViewDataBinding>
    }

    override fun onBindViewHolder(holder: AbstractMoviepediaItemViewHolder<ViewDataBinding>, position: Int) {
        if (position >= itemCount) return
        val item = getItem(position)
        holder.setItem(item)
        holder.binding.executePendingBindings()
        if (position == focusNext) {
            holder.binding.root.requestFocus()
            focusNext = -1
        }
    }

    override fun onBindViewHolder(holder: AbstractMoviepediaItemViewHolder<ViewDataBinding>, position: Int, payloads: List<Any>) {
        if (Util.isListEmpty(payloads))
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

    override fun onViewRecycled(holder: AbstractMoviepediaItemViewHolder<ViewDataBinding>) {
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

    override fun setOnFocusChangeListener(focusListener: FocusableRecyclerView.FocusListener?) {
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

    abstract class AbstractMoviepediaItemViewHolder<T : ViewDataBinding> @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: T) : SelectorViewHolder<T>(binding), View.OnFocusChangeListener {

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

    inner class MovieItemTVViewHolder @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: MovieBrowserTvItemBinding, override val eventsHandler: IEventsHandler<MediaMetadataWithImages>) : AbstractMoviepediaItemViewHolder<MovieBrowserTvItemBinding>(binding), View.OnFocusChangeListener {
        override fun getItem(layoutPosition: Int) = this@MoviepediaTvItemAdapter.getItem(layoutPosition)

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
                if (hasFocus) {
                    var newWidth = (itemSize * 1.1).toInt()
                    if (newWidth % 2 == 1) {
                        newWidth--
                    }
                    val scale = newWidth.toFloat() / itemSize
                    binding.container.animate().scaleX(scale).scaleY(scale).translationZ(scale)

                    eventsHandler.onItemFocused(binding.root, getItem(layoutPosition)!!)
                    if (focusListener != null) {
                        focusListener!!.onFocusChanged(layoutPosition)
                    }
                } else {
                    binding.container.animate().scaleX(1f).scaleY(1f).translationZ(1f)
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
                if (media.type == AbstractMediaWrapper.TYPE_VIDEO) {
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
            if (seen == 0L) binding.mlItemSeen.visibility = View.GONE
            if (progress <= 0L) binding.progressBar.visibility = View.GONE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        @ObsoleteCoroutinesApi
        override fun setCoverlay(selected: Boolean) {
        }
    }
}
