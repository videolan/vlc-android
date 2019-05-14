package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.databinding.MediaBrowserTvItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.getAudioIconDrawable
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.UPDATE_SELECTION
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.generateResolutionClass

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class TvItemAdapter(type: Int, private val eventsHandler: IEventsHandler, var itemSize: Int) : PagedListAdapter<MediaLibraryItem, TvItemAdapter.AbstractMediaItemViewHolder<ViewDataBinding>>(TvItemAdapter.DIFF_CALLBACK), FastScroller.SeparatedAdapter {
    var focusNext = -1
    private val mDefaultCover: BitmapDrawable?
    private var focusListener: FocusableRecyclerView.FocusListener? = null


    init {
        var ctx: Context? = null
        if (eventsHandler is Context)
            ctx = eventsHandler
        else if (eventsHandler is Fragment) ctx = (eventsHandler as Fragment).context
        mDefaultCover = if (ctx != null) getAudioIconDrawable(ctx, type) else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractMediaItemViewHolder<ViewDataBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = MediaBrowserTvItemBinding.inflate(inflater, parent, false)
        return MediaItemTVViewHolder(binding) as AbstractMediaItemViewHolder<ViewDataBinding>
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
        if (Util.isListEmpty(payloads))
            onBindViewHolder(holder, position)
        else {
            val payload = payloads[0]
            if (payload is MediaLibraryItem) {
                val isSelected = payload.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)
                holder.setCoverlay(isSelected)
                holder.selectView(isSelected)
            } else if (payload is Int) {
                if (payload == UPDATE_SELECTION) {
                }
            }
        }
    }

    override fun hasSections(): Boolean {
        return true
    }

    override fun getItem(position: Int): MediaLibraryItem? {
        return super.getItem(position)
    }


    fun setOnFocusChangeListener(focusListener: FocusableRecyclerView.FocusListener?) {
        this.focusListener = focusListener
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


    abstract inner class AbstractMediaItemViewHolder<T : ViewDataBinding> @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: T) : SelectorViewHolder<T>(binding), View.OnFocusChangeListener {


        fun onClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) eventsHandler.onClick(v, layoutPosition, item)
        }

        fun onMoreClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) eventsHandler.onCtxClick(v, layoutPosition, item)
        }

        fun onLongClick(view: View): Boolean {
            val item = getItem(layoutPosition)
            return item != null && eventsHandler.onLongClick(view, layoutPosition, item)
        }

        fun onImageClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) eventsHandler.onImageClick(v, layoutPosition, item)
        }

        fun onMainActionClick(v: View) {
            val item = getItem(layoutPosition)
            if (item != null) eventsHandler.onMainActionClick(v, layoutPosition, item)
        }


        abstract fun setItem(item: MediaLibraryItem?)

        abstract fun recycle()

        abstract fun setCoverlay(selected: Boolean)
    }

    inner class MediaItemTVViewHolder @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: MediaBrowserTvItemBinding) : AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>(binding), View.OnFocusChangeListener {

        init {
            binding.holder = this
            if (mDefaultCover != null) binding.cover = mDefaultCover
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
            if (mDefaultCover != null) binding.cover = mDefaultCover
            binding.title.text = ""
            binding.subtitle.text = ""
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
            if (seen == 0L) binding.mlItemSeen.visibility = View.GONE
            if (progress <= 0L) binding.progressBar.visibility = View.GONE
            binding.badgeTV.visibility = if (resolution.isBlank()) View.GONE else View.VISIBLE
        }

        @ObsoleteCoroutinesApi
        override fun setCoverlay(selected: Boolean) {
        }


    }


}