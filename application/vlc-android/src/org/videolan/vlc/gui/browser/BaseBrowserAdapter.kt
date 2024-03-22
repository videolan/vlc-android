/**
 * **************************************************************************
 * BaseBrowserAdapter.kt
 * ****************************************************************************
 * Copyright © 2015-2017 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser

import android.annotation.TargetApi
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.MediaLibraryItem.TYPE_MEDIA
import org.videolan.medialibrary.media.MediaLibraryItem.TYPE_STORAGE
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.resources.UPDATE_SELECTION
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.Settings
import org.videolan.vlc.R
import org.videolan.vlc.databinding.BrowserItemBinding
import org.videolan.vlc.databinding.BrowserItemSeparatorBinding
import org.videolan.vlc.databinding.CardBrowserItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.MiniVisualizer
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.getDescriptionSpan
import org.videolan.vlc.viewmodels.PlaylistModel

const val UPDATE_PROGRESS = "update_progress"
open class BaseBrowserAdapter(val browserContainer: BrowserContainer<MediaLibraryItem>, var sort:Int = Medialibrary.SORT_FILENAME, var asc:Boolean = true, val forMain:Boolean = true) : DiffUtilAdapter<MediaLibraryItem, BaseBrowserAdapter.ViewHolder<ViewDataBinding>>(), MultiSelectAdapter<MediaLibraryItem>, FastScroller.SeparatedAdapter {

    protected val TAG = "VLC/BaseBrowserAdapter"

    val multiSelectHelper: MultiSelectHelper<MediaLibraryItem> = MultiSelectHelper(this, UPDATE_SELECTION)

    private val folderDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder)) }
    private val folderDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_big)) }
    private val audioDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_song)) }
    private val audioDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_song_big)) }
    private val videoDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_video)) }
    private val videoDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_video_big)) }
    private val subtitleDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_subtitles)) }
    private val subtitleDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_subtitles_big)) }
    private val unknownDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_unknown)) }
    private val qaMoviesDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_movies)) }
    private val qaMoviesDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_movies_big)) }
    private val qaMusicDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_music)) }
    private val qaMusicDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_music_big)) }
    private val qaPodcastsDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_podcasts)) }
    private val qaPodcastsDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_podcasts_big)) }
    private val qaDownloadDrawable: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_download)) }
    private val qaDownloadDrawableBig: BitmapDrawable by lazy { BitmapDrawable(browserContainer.containerActivity().resources, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_folder_download_big)) }

    internal var mediaCount = 0
    private var networkRoot = false
    private var specialIcons = false

    val diffCallback = BrowserDiffCallback()
    private var currentPlayingVisu: MiniVisualizer? = null
    private var model: PlaylistModel? = null

    var currentMedia: MediaWrapper? = null
        set(media) {
            if (media == currentMedia) return
            val former = currentMedia
            field = media
            if (former != null) notifyItemChanged(dataset.indexOf(former))
            if (media != null) {
                notifyItemChanged(dataset.indexOf(media))
            }
        }

    private var scheduler: LifecycleAwareScheduler? = null

    fun changeSort(sort:Int, asc:Boolean) {
        diffCallback.oldSort = diffCallback.newSort
        diffCallback.oldAsc = diffCallback.newAsc
        this.sort = sort
        this.asc = asc
        diffCallback.newAsc = asc
    }

    init {
        val root = browserContainer.isRootDirectory
        val fileBrowser = browserContainer.isFile
        val filesRoot = root && fileBrowser
        networkRoot = root && browserContainer.isNetwork
        val mrl = browserContainer.mrl
        specialIcons = filesRoot || fileBrowser && mrl != null && mrl.endsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
        // Setup resources
        val res = browserContainer.containerActivity().resources
        diffCallback.oldSort = sort
        diffCallback.newSort = sort
        diffCallback.oldAsc = asc
        diffCallback.newAsc = asc
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<ViewDataBinding> {
        val inflater = LayoutInflater.from(parent.context)
        @Suppress("UNCHECKED_CAST")
        return if (viewType == TYPE_MEDIA || viewType == TYPE_STORAGE)
            MediaViewHolder(if (browserContainer.inCards) BrowserItemBindingContainer(CardBrowserItemBinding.inflate(inflater, parent, false)) else BrowserItemBindingContainer(BrowserItemBinding.inflate(inflater, parent, false)))
        else
            SeparatorViewHolder(BrowserItemSeparatorBinding.inflate(inflater, parent, false)) as ViewHolder<ViewDataBinding>
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (Settings.listTitleEllipsize == 0 || Settings.listTitleEllipsize == 4) scheduler = enableMarqueeEffect(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        scheduler?.cancelAction(MARQUEE_ACTION)
        currentMedia = null
        currentPlayingVisu = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun setCurrentlyPlaying(playing: Boolean) {
        if (playing) currentPlayingVisu?.start() else currentPlayingVisu?.stop()
    }

    fun setModel(model: PlaylistModel) {
        this.model = model
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int) {
        val viewType = getItemViewType(position)
        if (viewType == TYPE_MEDIA) {
            onBindMediaViewHolder(holder as MediaViewHolder, position)
        } else {
            val vh = holder as SeparatorViewHolder
            vh.binding.title = dataset[position].title
        }
        itemFocusChanged(position, false, (holder as MediaViewHolder).bindingContainer)
        if (!forMain) holder.bindingContainer.setupGrid()
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty())
            onBindViewHolder(holder, position)
        else if (payloads[0] == UPDATE_PROGRESS) {
            val media = getItem(position) as MediaWrapper
            val max = (media.length / 1000).toInt()
            val progress = (media.displayTime / 1000).toInt()
            (holder as MediaViewHolder).bindingContainer.setProgress(holder.bindingContainer.container.context, progress, max)
            if (media.type != MediaWrapper.TYPE_AUDIO) holder.bindingContainer.setIsPlayed(holder.bindingContainer.container.context, media.playCount > 0)
        }  else if (payloads[0] is CharSequence) {
            (holder as MediaViewHolder).bindingContainer.text.visibility = View.VISIBLE
            holder.bindingContainer.text.text = (payloads[0] as CharSequence).getDescriptionSpan(holder.bindingContainer.text.context)
            val item = getItem(position) as MediaWrapper
            holder.bindingContainer.container.contentDescription = TalkbackUtil.getDir(holder.binding.root.context, item, item.hasStateFlags(MediaLibraryItem.FLAG_FAVORITE))
        } else if (payloads[0] is Int) {
            val value = payloads[0] as Int
            if (value == UPDATE_SELECTION) holder.selectView(multiSelectHelper.isSelected(position))
        }
        itemFocusChanged(position, false, (holder as MediaViewHolder).bindingContainer)
    }

    private fun onBindMediaViewHolder(vh: MediaViewHolder, position: Int) {
        val media = getItem(position) as MediaWrapper
        val isFavorite = media.hasStateFlags(MediaLibraryItem.FLAG_FAVORITE)
        val max = (media.length / 1000).toInt()
        val progress = (media.displayTime / 1000).toInt()
        vh.bindingContainer.setProgress(vh.bindingContainer.container.context, progress, max)
        if (media.type != MediaWrapper.TYPE_AUDIO) vh.bindingContainer.setIsPlayed(vh.bindingContainer.container.context, media.playCount > 0)
        vh.bindingContainer.setItem(media)
        vh.bindingContainer.setIsFavorite(isFavorite)
        val scheme = media.uri?.scheme ?: ""
        vh.bindingContainer.setHasContextMenu(((!networkRoot || isFavorite)
                && "content" != scheme
                && "otg" != scheme)
                && !multiSelectHelper.inActionMode)
        vh.bindingContainer.setFileName(if ((sort == Medialibrary.SORT_FILENAME || sort == Medialibrary.SORT_DEFAULT) && media.type != MediaWrapper.TYPE_DIR && "file" == scheme) media.fileName else null)
        if (networkRoot || (isFavorite && getProtocol(media)?.contains("file") == false)) vh.bindingContainer.setProtocol(getProtocol(media))
        vh.bindingContainer.setCover(getIcon(media, specialIcons))
        vh.selectView(multiSelectHelper.isSelected(position))
        itemFocusChanged(position, false, vh.bindingContainer)
        if (currentMedia == media) {
            if (model?.playing != false) vh.bindingContainer.startVisu() else vh.bindingContainer.stopVisu()
            vh.bindingContainer.setVisuVisibility(View.VISIBLE)
            currentPlayingVisu = vh.bindingContainer.getVisu()
        } else {
            vh.bindingContainer.stopVisu()
            vh.bindingContainer.setVisuVisibility(View.INVISIBLE)
        }
    }

    override fun onViewRecycled(holder: ViewHolder<ViewDataBinding>) {
        scheduler?.cancelAction(MARQUEE_ACTION)
        super.onViewRecycled(holder)
        holder.titleView?.isSelected = false
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    abstract inner class ViewHolder<T : ViewDataBinding>(binding: T) : SelectorViewHolder<T>(binding), MarqueeViewHolder {

        abstract fun getType(): Int

        open fun onClick(v: View) {}

        open fun onImageClick(v: View) {}

        open fun onLongClick(v: View) = false

        open fun onCheckBoxClick(v: View) {}

        open fun onMoreClick(v: View) {}

        open fun onBanClick(v: View) {}

    }

    /**
     * Listener for the item focus. For now it's only used on TV to manage the ban icon visibility
     *
     * @param position the item position
     * @param hasFocus true if the item has the focus
     * @param bindingContainer the [BrowserItemBindingContainer] to be used
     */
    open fun itemFocusChanged(position: Int, hasFocus: Boolean, bindingContainer: BrowserItemBindingContainer) {}

    @TargetApi(Build.VERSION_CODES.M)
    inner class MediaViewHolder(val bindingContainer: BrowserItemBindingContainer) : ViewHolder<ViewDataBinding>(bindingContainer.binding), MarqueeViewHolder {
        override val titleView: TextView = bindingContainer.title
        var job : Job? = null

        init {
            bindingContainer.setHolder(this)
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener { v ->
                onMoreClick(v)
                true
            }
            if (this@BaseBrowserAdapter is FilePickerAdapter) {
                bindingContainer.itemIcon.isFocusable = false
            }


            val focusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                itemFocusChanged(layoutPosition, hasFocus, bindingContainer)
            }

            bindingContainer.banIcon.onFocusChangeListener = focusChangeListener
            bindingContainer.container.onFocusChangeListener = focusChangeListener
        }

        override fun selectView(selected: Boolean) {
            super.selectView(selected)
            bindingContainer.moreIcon.visibility = if (multiSelectHelper.inActionMode) View.INVISIBLE else View.VISIBLE
        }

        override fun onCheckBoxClick(v: View) {
            when (getItem(layoutPosition)) {
                is Storage -> checkBoxAction(v, (getItem(layoutPosition) as Storage).uri.toString())
                is MediaWrapper -> checkBoxAction(v, (getItem(layoutPosition) as MediaWrapper).uri.toString())
            }
        }

        override fun getType(): Int {
            return TYPE_MEDIA
        }

        override fun onClick(v: View) {
            val position = layoutPosition
            if (position < dataset.size && position >= 0)
                browserContainer.onClick(v, position, dataset[position])
        }

        override fun onImageClick(v: View) {
            val position = layoutPosition
            if (position < dataset.size && position >= 0)
                browserContainer.onImageClick(v, position, dataset[position])
        }

        override fun onMoreClick(v: View) {
            val position = layoutPosition
            if (position < dataset.size && position >= 0)
                browserContainer.onCtxClick(v, position, dataset[position])
        }

        override fun onBanClick(v: View) {
            val position = layoutPosition
            browserContainer.onLongClick(v, position, dataset[position])
        }

        override fun onLongClick(v: View): Boolean {
            val position = layoutPosition
            if (getItem(position).itemType == TYPE_STORAGE && Settings.showTvUi) {
                bindingContainer.browserCheckbox.toggle()
                onCheckBoxClick(bindingContainer.browserCheckbox)
                return true
            }
            return (position < dataset.size && position >= 0
                    && browserContainer.onLongClick(v, position, dataset[position]))
        }

        override fun isSelected(): Boolean {
            return multiSelectHelper.isSelected(layoutPosition)
        }
    }

    private inner class SeparatorViewHolder(binding: BrowserItemSeparatorBinding) : ViewHolder<BrowserItemSeparatorBinding>(binding) {
        override val titleView: TextView? = null

        override fun getType(): Int {
            return MediaLibraryItem.TYPE_DUMMY
        }
    }

    fun clear() {
        if (!isEmpty()) update(ArrayList(0))
    }

    fun getAll(): List<MediaLibraryItem> {
        return dataset
    }

    override fun getItem(position: Int): MediaLibraryItem {
        return dataset[position]
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).itemType
    }


    fun getIcon(media: MediaWrapper, specialFolders: Boolean): BitmapDrawable {
        when (media.type) {
            MediaWrapper.TYPE_AUDIO -> return if (browserContainer.inCards) audioDrawableBig else audioDrawable
            MediaWrapper.TYPE_DIR -> {
                if (specialFolders) {
                    val uri = media.uri
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI == uri || AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI == uri)
                        return if (browserContainer.inCards) qaMoviesDrawableBig else qaMoviesDrawable
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI == uri)
                        return if (browserContainer.inCards) qaMusicDrawableBig else  qaMusicDrawable
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI == uri)
                        return if (browserContainer.inCards) qaPodcastsDrawableBig else  qaPodcastsDrawable
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI == uri)
                        return if (browserContainer.inCards) qaDownloadDrawableBig else  qaDownloadDrawable
                }
                return if (browserContainer.inCards) folderDrawableBig else folderDrawable
            }
            MediaWrapper.TYPE_VIDEO -> return if (browserContainer.inCards) videoDrawableBig else videoDrawable
            MediaWrapper.TYPE_SUBTITLE -> return  if (browserContainer.inCards) subtitleDrawableBig else subtitleDrawable
            else -> return unknownDrawable
        }
    }

    private fun getProtocol(media: MediaWrapper): String? {
        return if (media.type != MediaWrapper.TYPE_DIR) null else media.uri?.scheme
    }

    open fun checkBoxAction(v: View, mrl: String) {}

    override fun prepareList(list: List<MediaLibraryItem>): List<MediaLibraryItem> {
        val internalList = ArrayList(list)
        mediaCount = internalList.filterNotNull().map {item -> item.itemType == TYPE_MEDIA && ((item as MediaWrapper).type == MediaWrapper.TYPE_AUDIO || item.type == MediaWrapper.TYPE_VIDEO) }.size
        return internalList
    }

    override fun onUpdateFinished() {
        browserContainer.onUpdateFinished(this)
        diffCallback.oldSort = diffCallback.newSort
        diffCallback.oldAsc = diffCallback.newAsc
    }

    override fun createCB() = diffCallback

    class BrowserDiffCallback : DiffCallback<MediaLibraryItem>() {
        var oldSort = -1
        var newSort = -1
        var oldAsc = true
        var newAsc = true

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int):Boolean {
            val result = try {
                val oldItem = oldList[oldItemPosition] as MediaWrapper
                val newItem = newList[newItemPosition] as MediaWrapper
                (oldItem.displayTime == newItem.displayTime) &&
                        (oldItem.playCount == newItem.playCount) &&
                        (oldItem.fileName == newItem.fileName) &&
                        (newItem.title == oldItem.title)
            } catch (ignored: Exception) {
                true
            }
            return result
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            try {
                val oldItem = oldList[oldItemPosition] as MediaWrapper
                val newItem = newList[newItemPosition] as MediaWrapper
                if (oldItem.displayTime != newItem.displayTime || oldItem.playCount != newItem.playCount) return UPDATE_PROGRESS
            } catch (ignored: Exception) {
            }
            return super.getChangePayload(oldItemPosition, newItemPosition)
        }

        override fun areItemsTheSame(oldItemPosition : Int, newItemPosition : Int) = oldList[oldItemPosition] == newList[newItemPosition]
    }

    override fun hasSections() = false
}
