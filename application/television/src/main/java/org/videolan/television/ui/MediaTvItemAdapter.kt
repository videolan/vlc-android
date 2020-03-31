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
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.UPDATE_PAYLOAD
import org.videolan.resources.interfaces.FocusListener
import org.videolan.television.databinding.MediaBrowserTvItemBinding
import org.videolan.television.databinding.MediaBrowserTvItemListBinding
import org.videolan.television.ui.browser.TvAdapterUtils
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getMediaIconDrawable
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.generateResolutionClass

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MediaTvItemAdapter(type: Int, private val eventsHandler: IEventsHandler<MediaLibraryItem>, var itemSize: Int, private var inGrid: Boolean = true) : PagedListAdapter<MediaLibraryItem, MediaTvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>>(DIFF_CALLBACK), FastScroller.SeparatedAdapter, TvItemAdapter {
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
        defaultCover = ctx?.let { getMediaIconDrawable(it, type, true) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractMediaItemViewHolder<ViewDataBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        @Suppress("UNCHECKED_CAST")
        return if (inGrid)
            MediaItemTVViewHolder(MediaBrowserTvItemBinding.inflate(inflater, parent, false), eventsHandler) as AbstractMediaItemViewHolder<ViewDataBinding>
        else
            MediaItemTVListViewHolder(MediaBrowserTvItemListBinding.inflate(inflater, parent, false), eventsHandler) as AbstractMediaItemViewHolder<ViewDataBinding>
    }

    override fun getItemViewType(position: Int): Int {
        return if (inGrid) 0 else 1
    }

    override fun onBindViewHolder(holder: AbstractMediaItemViewHolder<ViewDataBinding>, position: Int) {
        if (position >= itemCount) return
        val item = getItem(position)
        holder.setItem(item)
        holder.binding.executePendingBindings()
        if (position == focusNext) {
            holder.binding.root.requestFocus()
            focusNext = -1
        }
    }

    override fun onBindViewHolder(holder: AbstractMediaItemViewHolder<ViewDataBinding>, position: Int, payloads: List<Any>) {
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

    override fun onViewRecycled(holder: AbstractMediaItemViewHolder<ViewDataBinding>) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    override fun hasSections() = true

    override fun isEmpty() = currentList.isNullOrEmpty()

    override fun submitList(pagedList: Any?) {
        if (pagedList == null) {
            this.submitList(null)
        }
        if (pagedList is PagedList<*>) {
            @Suppress("UNCHECKED_CAST")
            this.submitList(pagedList as PagedList<MediaLibraryItem>)
        }
    }

    override fun setOnFocusChangeListener(focusListener: FocusListener?) {
        this.focusListener = focusListener
    }

    companion object {

        private const val TAG = "VLC/MediaTvItemAdapter"
        /**
         * Awful hack to workaround the [PagedListAdapter] not keeping track of notifyItemMoved operations
         */
        private var preventNextAnim: Boolean = false

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MediaLibraryItem>() {
            override fun areItemsTheSame(
                    oldMedia: MediaLibraryItem, newMedia: MediaLibraryItem) = if (preventNextAnim)  true
            else oldMedia === newMedia || oldMedia.itemType == newMedia.itemType && oldMedia.equals(newMedia)

            override fun areContentsTheSame(oldMedia: MediaLibraryItem, newMedia: MediaLibraryItem) = false

            override fun getChangePayload(oldItem: MediaLibraryItem, newItem: MediaLibraryItem): Any? {
                preventNextAnim = false
                return UPDATE_PAYLOAD
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    abstract class AbstractMediaItemViewHolder<T : ViewDataBinding>(binding: T) : SelectorViewHolder<T>(binding) {

        fun onClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onClick(v, layoutPosition, it) }
        }

        fun onMoreClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onCtxClick(v, layoutPosition, it) }
        }

        fun onLongClick(view: View): Boolean {
            return getItem(layoutPosition)?.let { eventsHandler.onLongClick(view, layoutPosition, it) } ?: false
        }

        fun onImageClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onImageClick(v, layoutPosition, it) }
        }

        fun onMainActionClick(v: View) {
            getItem(layoutPosition)?.let { eventsHandler.onMainActionClick(v, layoutPosition, it) }
        }

        abstract fun getItem(layoutPosition: Int): MediaLibraryItem?

        abstract val eventsHandler: IEventsHandler<MediaLibraryItem>

        abstract fun setItem(item: MediaLibraryItem?)

        abstract fun recycle()

        abstract fun setCoverlay(selected: Boolean)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    inner class MediaItemTVViewHolder(
            binding: MediaBrowserTvItemBinding,
            override val eventsHandler: IEventsHandler<MediaLibraryItem>
    ) : AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>(binding)
    {
        override fun getItem(layoutPosition: Int) =  this@MediaTvItemAdapter.getItem(layoutPosition)

        init {
            binding.holder = this
            if (defaultCover != null) binding.cover = defaultCover
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
            if (AndroidUtil.isLolliPopOrLater) binding.container.clipToOutline = true
        }

        override fun recycle() {
            if (defaultCover != null) binding.cover = defaultCover
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
            binding.scaleType = ImageView.ScaleType.CENTER_INSIDE
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
            override val eventsHandler: IEventsHandler<MediaLibraryItem>
    ) : AbstractMediaItemViewHolder<MediaBrowserTvItemListBinding>(binding) {
        override fun getItem(layoutPosition: Int) = this@MediaTvItemAdapter.getItem(layoutPosition)

        init {
            binding.holder = this
            if (defaultCover != null) binding.cover = defaultCover
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
            binding.scaleType = ImageView.ScaleType.CENTER_INSIDE
            if (seen == 0L) binding.mlItemSeen.visibility = View.GONE
            if (progress <= 0L) binding.progressBar.visibility = View.GONE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        @ObsoleteCoroutinesApi
        override fun setCoverlay(selected: Boolean) {}
    }
}
