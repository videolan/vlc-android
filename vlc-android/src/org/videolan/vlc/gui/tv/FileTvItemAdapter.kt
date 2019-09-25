package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MediaBrowserTvItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.gui.helpers.getMediaIconDrawable
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.UPDATE_PAYLOAD
import org.videolan.vlc.util.generateResolutionClass

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class FileTvItemAdapter(private val type: Long, private val eventsHandler: IEventsHandler, var itemSize: Int, val showProtocol: Boolean) : DiffUtilAdapter<AbstractMediaWrapper, MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>>(), FastScroller.SeparatedAdapter, TvItemAdapter {

    override fun submitList(pagedList: Any?) {
        if (pagedList is List<*>) {
            @Suppress("UNCHECKED_CAST")
            update(pagedList as List<AbstractMediaWrapper>)
        }
    }

    override var focusNext = -1
    private val defaultCover: BitmapDrawable?
    private var focusListener: FocusableRecyclerView.FocusListener? = null

    init {
        val ctx = when (eventsHandler) {
            is Context -> eventsHandler
            is Fragment -> eventsHandler.context
            else -> null
        }
        defaultCover = ctx?.let { BitmapDrawable(it.resources, getBitmapFromDrawable(it, R.drawable.ic_browser_unknown_big_normal)) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = MediaBrowserTvItemBinding.inflate(inflater, parent, false)
        return MediaItemTVViewHolder(binding, eventsHandler, showProtocol)
    }

    override fun onBindViewHolder(holder: MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>, position: Int) {
        if (position >= itemCount) return
        val item = getItem(position)
        holder.setItem(item)
        holder.binding.executePendingBindings()
        if (position == focusNext) {
            holder.binding.root.requestFocus()
            focusNext = -1
        }
    }

    override fun onViewRecycled(holder: MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    override fun hasSections() = true

    override fun setOnFocusChangeListener(focusListener: FocusableRecyclerView.FocusListener?) {
        this.focusListener = focusListener
    }

    override fun createCB(): DiffCallback<AbstractMediaWrapper> {
        return object : DiffCallback<AbstractMediaWrapper>() {
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
    }

    private fun getProtocol(media: AbstractMediaWrapper): String? {
        return if (media.type != AbstractMediaWrapper.TYPE_DIR) null else media.uri.scheme
    }

    inner class MediaItemTVViewHolder @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: MediaBrowserTvItemBinding, override val eventsHandler: IEventsHandler, private val showProtocol: Boolean) : MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>(binding), View.OnFocusChangeListener {

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
                if (hasFocus) {
                    var newWidth = (itemSize * 1.1).toInt()
                    if (newWidth % 2 == 1) {
                        newWidth--
                    }
                    val scale = newWidth.toFloat() / itemSize
                    binding.container.animate().scaleX(scale).scaleY(scale).translationZ(scale)

                    if (layoutPosition in dataset.indices) {
                        eventsHandler.onItemFocused(binding.root, getItem(layoutPosition))
                        focusListener?.onFocusChanged(layoutPosition)
                    }
                } else {
                    binding.container.animate().scaleX(1f).scaleY(1f).translationZ(1f)
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
            if (item is AbstractMediaWrapper) {
                if (item.type == AbstractMediaWrapper.TYPE_VIDEO) {
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
            if (showProtocol && item is AbstractMediaWrapper) binding.protocol = getProtocol(item)
            val cover = if (item is AbstractMediaWrapper) getMediaIconDrawable(binding.root.context, item.type, true) else defaultCover
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
