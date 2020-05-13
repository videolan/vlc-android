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
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
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
import org.videolan.vlc.gui.helpers.MarqueeViewHolder
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.enableMarqueeEffect
import org.videolan.vlc.gui.helpers.getBitmapFromDrawable
import org.videolan.vlc.util.getDescriptionSpan
import java.util.*


@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
open class BaseBrowserAdapter(val browserContainer: BrowserContainer<MediaLibraryItem>) : DiffUtilAdapter<MediaLibraryItem, BaseBrowserAdapter.ViewHolder<ViewDataBinding>>(), MultiSelectAdapter<MediaLibraryItem> {

    protected val TAG = "VLC/BaseBrowserAdapter"

    val multiSelectHelper: MultiSelectHelper<MediaLibraryItem> = MultiSelectHelper(this, UPDATE_SELECTION)

    private val folderDrawable: BitmapDrawable
    private val audioDrawable: BitmapDrawable
    private val videoDrawable: BitmapDrawable
    private val subtitleDrawable: BitmapDrawable
    private val unknownDrawable: BitmapDrawable
    private val qaMoviesDrawable: BitmapDrawable
    private val qaMusicDrawable: BitmapDrawable
    private val qaPodcastsDrawable: BitmapDrawable
    private val qaDownloadDrawable: BitmapDrawable

    internal var mediaCount = 0
    private var networkRoot = false
    private var specialIcons = false
    private val handler by lazy(LazyThreadSafetyMode.NONE) { Handler() }

    init {
        val root = browserContainer.isRootDirectory
        val fileBrowser = browserContainer.isFile
        val filesRoot = root && fileBrowser
        networkRoot = root && browserContainer.isNetwork
        val mrl = browserContainer.mrl
        specialIcons = filesRoot || fileBrowser && mrl != null && mrl.endsWith(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY)
        // Setup resources
        val res = browserContainer.containerActivity().resources
        folderDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_menu_folder))
        audioDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_audio_normal))
        videoDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_video_normal))
        subtitleDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_subtitle_normal))
        unknownDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_unknown_normal))
        qaMoviesDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_movies_normal))
        qaMusicDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_music_normal))
        qaPodcastsDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_podcasts_normal))
        qaDownloadDrawable = BitmapDrawable(res, browserContainer.containerActivity().getBitmapFromDrawable(R.drawable.ic_browser_download_normal))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<ViewDataBinding> {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_MEDIA || viewType == TYPE_STORAGE)
            MediaViewHolder(if (browserContainer.inCards) BrowserItemBindingContainer(CardBrowserItemBinding.inflate(inflater, parent, false)) else BrowserItemBindingContainer(BrowserItemBinding.inflate(inflater, parent, false)))
        else
            SeparatorViewHolder(BrowserItemSeparatorBinding.inflate(inflater, parent, false)) as ViewHolder<ViewDataBinding>
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (Settings.listTitleEllipsize == 0 || Settings.listTitleEllipsize == 4) enableMarqueeEffect(recyclerView, handler)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (Settings.listTitleEllipsize == 0 || Settings.listTitleEllipsize == 4) handler.removeCallbacksAndMessages(null)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int) {
        val viewType = getItemViewType(position)
        if (viewType == TYPE_MEDIA) {
            onBindMediaViewHolder(holder as MediaViewHolder, position)
        } else {
            val vh = holder as SeparatorViewHolder
            vh.binding.title = dataset[position].title
        }
    }

    override fun onBindViewHolder(holder: ViewHolder<ViewDataBinding>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty())
            onBindViewHolder(holder, position)
        else if (payloads[0] is CharSequence) {
            (holder as MediaViewHolder).bindingContainer.text.visibility = View.VISIBLE
            holder.bindingContainer.text.text = (payloads[0] as CharSequence).getDescriptionSpan(holder.bindingContainer.text.context)
        } else if (payloads[0] is Int) {
            val value = payloads[0] as Int
            if (value == UPDATE_SELECTION) holder.selectView(multiSelectHelper.isSelected(position))
        }
    }

    private fun onBindMediaViewHolder(vh: MediaViewHolder, position: Int) {
        val media = getItem(position) as MediaWrapper
        val isFavorite = media.hasStateFlags(MediaLibraryItem.FLAG_FAVORITE)
        vh.bindingContainer.setItem(media)
        val scheme = media.uri.scheme
        vh.bindingContainer.setHasContextMenu(((!networkRoot || isFavorite)
                && "content" != scheme
                && "otg" != scheme))
        vh.bindingContainer.setFileName(if (media.type != MediaWrapper.TYPE_DIR && "file" == scheme) media.fileName else null)
        if (networkRoot) vh.bindingContainer.setProtocol(getProtocol(media))
        vh.bindingContainer.setCover(getIcon(media, specialIcons))
        vh.selectView(multiSelectHelper.isSelected(position))
    }

    override fun onViewRecycled(holder: ViewHolder<ViewDataBinding>) {
        if (Settings.listTitleEllipsize == 0 || Settings.listTitleEllipsize == 4) handler.removeCallbacksAndMessages(null)
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

    }

    @TargetApi(Build.VERSION_CODES.M)
    internal inner class MediaViewHolder(val bindingContainer: BrowserItemBindingContainer) : ViewHolder<ViewDataBinding>(bindingContainer.binding), MarqueeViewHolder {
        override val titleView: TextView? = bindingContainer.title
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
        }


        override fun onCheckBoxClick(v: View) {
            if (getItem(layoutPosition).itemType == TYPE_STORAGE)
                checkBoxAction(v, (getItem(layoutPosition) as Storage).uri.toString())
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
            MediaWrapper.TYPE_AUDIO -> return audioDrawable
            MediaWrapper.TYPE_DIR -> {
                if (specialFolders) {
                    val uri = media.uri
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MOVIES_DIRECTORY_URI == uri || AndroidDevices.MediaFolders.WHATSAPP_VIDEOS_FILE_URI == uri)
                        return qaMoviesDrawable
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_MUSIC_DIRECTORY_URI == uri)
                        return qaMusicDrawable
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_PODCAST_DIRECTORY_URI == uri)
                        return qaPodcastsDrawable
                    if (AndroidDevices.MediaFolders.EXTERNAL_PUBLIC_DOWNLOAD_DIRECTORY_URI == uri)
                        return qaDownloadDrawable
                }
                return folderDrawable
            }
            MediaWrapper.TYPE_VIDEO -> return videoDrawable
            MediaWrapper.TYPE_SUBTITLE -> return subtitleDrawable
            else -> return unknownDrawable
        }
    }

    private fun getProtocol(media: MediaWrapper): String? {
        return if (media.type != MediaWrapper.TYPE_DIR) null else media.uri.scheme
    }

    open fun checkBoxAction(v: View, mrl: String) {}

    override fun prepareList(list: List<MediaLibraryItem>): List<MediaLibraryItem> {
        val internalList = ArrayList(list)
        mediaCount = 0
        for (item in internalList) {
            if (item.itemType == TYPE_MEDIA && ((item as MediaWrapper).type == MediaWrapper.TYPE_AUDIO || item.type == MediaWrapper.TYPE_VIDEO))
                ++mediaCount
        }
        return internalList
    }

    override fun onUpdateFinished() {
        browserContainer.onUpdateFinished(this)
    }
}