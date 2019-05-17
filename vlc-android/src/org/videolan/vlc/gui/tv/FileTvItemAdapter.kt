package org.videolan.vlc.gui.tv

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.databinding.MediaBrowserTvItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.getAudioIconDrawable
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.generateResolutionClass

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class FileTvItemAdapter(type: Int, private val eventsHandler: IEventsHandler, var itemSize: Int) : DiffUtilAdapter<MediaLibraryItem, MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>>(), FastScroller.SeparatedAdapter, TvItemAdapter {

    override fun submitList(pagedList: Any?) {

        if (pagedList is List<*>) {
            update(pagedList as List<MediaLibraryItem>)
        }


    }

    override var focusNext = -1
    private val mDefaultCover: BitmapDrawable?
    private var focusListener: FocusableRecyclerView.FocusListener? = null


    init {
        var ctx: Context? = null
        if (eventsHandler is Context)
            ctx = eventsHandler
        else if (eventsHandler is Fragment) ctx = (eventsHandler as Fragment).context
        mDefaultCover = if (ctx != null) getAudioIconDrawable(ctx, type) else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding> {
        val inflater = parent.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = MediaBrowserTvItemBinding.inflate(inflater, parent, false)
        return MediaItemTVViewHolder(binding, eventsHandler)
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


    override fun hasSections(): Boolean {
        return true
    }


    override fun setOnFocusChangeListener(focusListener: FocusableRecyclerView.FocusListener?) {
        this.focusListener = focusListener
    }

    override fun createCB(): DiffCallback<MediaLibraryItem> {
        return object : DiffCallback<MediaLibraryItem>() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return try {
                    dataset[oldItemPosition] === dataset[newItemPosition]
                } catch (e: IndexOutOfBoundsException) {
                    false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return dataset[oldItemPosition].description == dataset[newItemPosition].description
                        && dataset[oldItemPosition].title == dataset[newItemPosition].title
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                return arrayListOf(UPDATE_PAYLOAD)
            }
        }
    }


    companion object {

        private val TAG = "VLC/AudioBrowserAdapter"
        private const val UPDATE_PAYLOAD = 1

    }


    inner class MediaItemTVViewHolder @TargetApi(Build.VERSION_CODES.M)
    internal constructor(binding: MediaBrowserTvItemBinding, override val eventsHandler: IEventsHandler) : MediaTvItemAdapter.AbstractMediaItemViewHolder<MediaBrowserTvItemBinding>(binding), View.OnFocusChangeListener {
        override fun getItem(layoutPosition: Int): MediaLibraryItem? {
            return this@FileTvItemAdapter.getItem(layoutPosition)
        }

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