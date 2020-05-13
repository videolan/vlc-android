package org.videolan.television.ui

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.UPDATE_PAYLOAD
import org.videolan.resources.interfaces.FocusListener
import org.videolan.television.R
import org.videolan.television.databinding.MediaBrowserTvItemBinding
import org.videolan.television.databinding.MediaBrowserTvItemListBinding
import org.videolan.television.ui.browser.TvAdapterUtils
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.helpers.getMediaIconDrawable
import org.videolan.vlc.gui.helpers.getTvIconRes
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.generateResolutionClass
import org.videolan.vlc.util.getDescriptionSpan

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class FileTvItemAdapter(private val eventsHandler: IEventsHandler<MediaLibraryItem>, var itemSize: Int, private val showProtocol: Boolean, private var inGrid: Boolean = true) : DiffUtilAdapter<MediaWrapper, MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>>(), FastScroller.SeparatedAdapter, TvItemAdapter {

    override fun submitList(pagedList: Any?) {
        if (pagedList is List<*>) {
            @Suppress("UNCHECKED_CAST")
            update(pagedList as List<MediaWrapper>)
        }
    }

    override var focusNext = -1
    override fun displaySwitch(inGrid: Boolean) {
        this.inGrid = inGrid
    }

    private val defaultCover: BitmapDrawable?
    private var focusListener: FocusListener? = null

    init {
        val ctx = when (eventsHandler) {
            is Context -> eventsHandler
            is Fragment -> eventsHandler.context
            else -> null
        }
        defaultCover = ctx?.let { BitmapDrawable(it.resources, getBitmapFromDrawable(it, R.drawable.ic_browser_unknown_big_normal)) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return if (inGrid)
            MediaItemTVViewHolder(MediaBrowserTvItemBinding.inflate(inflater, parent, false), eventsHandler, showProtocol) as MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>
        else
            MediaItemTVListViewHolder(MediaBrowserTvItemListBinding.inflate(inflater, parent, false), eventsHandler, showProtocol) as MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>
    }

    override fun getItemViewType(position: Int): Int {
        return if (inGrid) 0 else 1
    }

    override fun onBindViewHolder(holder: MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>, position: Int) {
        if (position >= itemCount) return
        val item = getItem(position)
        holder.setItem(item)
        holder.binding.executePendingBindings()
        if (position == focusNext) {
            holder.binding.root.requestFocus()
            focusNext = -1
        }
    }

    override fun onBindViewHolder(holder: MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNullOrEmpty()) onBindViewHolder(holder, position)
        else for (payload in payloads) {
            when (holder.binding) {
                is MediaBrowserTvItemBinding -> (holder.binding as MediaBrowserTvItemBinding).description = if (payload is String) payload else getItem(position).description
                is MediaBrowserTvItemListBinding -> (holder.binding as MediaBrowserTvItemListBinding).description = if (payload is String) payload else getItem(position).description
            }

        }
    }

    override fun onViewRecycled(holder: MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    override fun hasSections() = true

    override fun setOnFocusChangeListener(focusListener: FocusListener?) {
        this.focusListener = focusListener
    }

    override fun createCB(): DiffCallback<MediaWrapper> = object : DiffCallback<MediaWrapper>() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) = try {
            oldList[oldItemPosition] == newList[newItemPosition]
        } catch (e: IndexOutOfBoundsException) {
            false
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].description == newList[newItemPosition].description
                    && oldList[oldItemPosition].title == newList[newItemPosition].title
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int) = arrayListOf(UPDATE_PAYLOAD)
    }

    private fun getProtocol(media: MediaWrapper) = if (media.type != MediaWrapper.TYPE_DIR) null else media.uri.scheme

    @RequiresApi(Build.VERSION_CODES.M)
    inner class MediaItemTVViewHolder(
            binding: MediaBrowserTvItemBinding,
            override val eventsHandler: IEventsHandler<MediaLibraryItem>,
            private val showProtocol: Boolean
    ) : MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>(binding)
    {

        override fun getItem(layoutPosition: Int) = this@FileTvItemAdapter.getItem(layoutPosition)

        init {
            binding.holder = this
            binding.scaleType = ImageView.ScaleType.CENTER_INSIDE
            defaultCover?.let { binding.cover = it }
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }
            binding.container.layoutParams.width = itemSize
            binding.container.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                TvAdapterUtils.itemFocusChange(hasFocus, itemSize, binding.container, false) {
                    if (layoutPosition in dataset.indices) {
                        eventsHandler.onItemFocused(binding.root, getItem(layoutPosition))
                        focusListener?.onFocusChanged(layoutPosition)
                    }
                }
            }
            if (AndroidUtil.isLolliPopOrLater) binding.container.clipToOutline = true
        }

        override fun recycle() {
            defaultCover?.let { binding.cover = it }
            binding.title.text = ""
            binding.subtitle.text = ""
            binding.mediaCover.resetFade()
        }

        override fun setItem(item: MediaLibraryItem?) {
            binding.item = item
            var progress = 0
            var seen = 0L
            var description = item?.description
            var resolution = ""
            if (item is MediaWrapper) {
                if (item.type == MediaWrapper.TYPE_VIDEO) {
                    resolution = generateResolutionClass(item.width, item.height) ?: ""
                    description = if (item.time == 0L) Tools.millisToString(item.length) else Tools.getProgressText(item)
                    binding.badge = resolution
                    seen = item.seen
                    var max = 0

                    if (item.length > 0) {
                        val lastTime = item.displayTime
                        if (lastTime > 0) {
                            max = (item.length / 1000).toInt()
                            progress = (lastTime / 1000).toInt()
                        }
                    }
                    binding.max = max
                }
            }

            binding.progress = progress
            binding.isSquare = true
            binding.seen = seen
            binding.description = description
            if (showProtocol && item is MediaWrapper) binding.protocol = getProtocol(item)
            val cover = if (item is MediaWrapper) getMediaIconDrawable(binding.root.context, item.type, true) else defaultCover
            cover?.let { binding.cover = it }
            if (seen == 0L) binding.mlItemSeen.visibility = View.GONE
            if (progress <= 0L) binding.progressBar.visibility = View.GONE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        @ObsoleteCoroutinesApi
        override fun setCoverlay(selected: Boolean) {
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    inner class MediaItemTVListViewHolder(
            binding: MediaBrowserTvItemListBinding,
            override val eventsHandler: IEventsHandler<MediaLibraryItem>,
            private val showProtocol: Boolean
    ) : MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemListBinding>(binding) {

        override fun getItem(layoutPosition: Int) = this@FileTvItemAdapter.getItem(layoutPosition)

        init {
            binding.holder = this
            binding.scaleType = ImageView.ScaleType.CENTER_INSIDE
            defaultCover?.let { binding.cover = it }
            if (AndroidUtil.isMarshMallowOrLater)
                itemView.setOnContextClickListener { v ->
                    onMoreClick(v)
                    true
                }
//            binding.container.layoutParams.width = itemSize
            binding.container.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                TvAdapterUtils.itemFocusChange(hasFocus, itemSize, binding.container, true) {
                    if (layoutPosition in dataset.indices) {
                        eventsHandler.onItemFocused(binding.root, getItem(layoutPosition))
                        focusListener?.onFocusChanged(layoutPosition)
                    }
                }
            }
            binding.container.clipToOutline = true
        }

        override fun recycle() {
            defaultCover?.let { binding.cover = it }
            binding.title.text = ""
            binding.subtitle.text = ""
            binding.mediaCover.resetFade()
        }

        override fun setItem(item: MediaLibraryItem?) {
            binding.item = item
            var isSquare = true
            var progress = 0
            var seen = 0L
            var description = item?.description
            var resolution = ""
            if (item is MediaWrapper) {
                if (item.type == MediaWrapper.TYPE_VIDEO) {
                    resolution = generateResolutionClass(item.width, item.height) ?: ""
                    isSquare = false
                    description = if (item.time == 0L) Tools.millisToString(item.length) else Tools.getProgressText(item)
                    binding.badge = resolution
                    seen = item.seen
                    var max = 0

                    if (item.length > 0) {
                        val lastTime = item.displayTime
                        if (lastTime > 0) {
                            max = (item.length / 1000).toInt()
                            progress = (lastTime / 1000).toInt()
                        }
                    }
                    binding.max = max
                }
            }

            binding.progress = progress
            binding.isSquare = isSquare
            binding.seen = seen
            binding.description = description
            if (showProtocol && item is MediaWrapper) binding.protocol = getProtocol(item)
            val cover = if (item is MediaWrapper) getMediaIconDrawable(binding.root.context, item.type, true) else defaultCover
            cover?.let { binding.cover = it }
            if (seen == 0L) binding.mlItemSeen.visibility = View.GONE
            if (progress <= 0L) binding.progressBar.visibility = View.GONE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        @ObsoleteCoroutinesApi
        override fun setCoverlay(selected: Boolean) {
        }
    }
}
