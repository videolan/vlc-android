package org.videolan.vlc.gui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.Genre
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AppContextProvider
import org.videolan.tools.dp
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SearchItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.generateResolutionClass
import java.lang.StringBuilder


class SearchResultAdapter internal constructor(private val mLayoutInflater: LayoutInflater) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

    private var mDataList: Array<MediaLibraryItem>? = null
    internal lateinit var mClickHandler: SearchActivity.ClickHandler

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(SearchItemBinding.inflate(mLayoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mDataList!![position]
        if (item.artworkMrl.isNullOrEmpty())
            holder.binding.cover = UiTools.getDefaultCover(holder.itemView.context, item)
        holder.binding.item = item
        val isNotVideo = item !is MediaWrapper || item.type != MediaWrapper.TYPE_VIDEO
        holder.binding.isSquare = isNotVideo
        holder.binding.coverWidth = if (isNotVideo) 48.dp else 100.dp
        holder.binding.description = when {
            (item as? MediaWrapper)?.type == MediaWrapper.TYPE_VIDEO -> {
                if (item.length > 0) {
                    val resolution = generateResolutionClass(item.width, item.height)
                    if (resolution !== null) {
                        "${Tools.millisToString(item.length)}  •  $resolution"
                    } else Tools.millisToString(item.length)
                } else null
            }
            item is Playlist || item is Genre -> holder.itemView.context.getString(R.string.track_number, item.tracksCount)
            else -> item.description
        }
    }

    fun add(newList: Array<MediaLibraryItem>) {
        mDataList = newList
        notifyDataSetChanged()
    }

    internal fun setClickHandler(clickHandler: SearchActivity.ClickHandler) {
        mClickHandler = clickHandler
    }

    override fun getItemCount(): Int {
        return if (mDataList == null) 0 else mDataList!!.size
    }

    inner class ViewHolder(binding: SearchItemBinding) : SelectorViewHolder<SearchItemBinding>(binding) {

        init {
            binding.holder = this
            binding.handler = mClickHandler
        }
    }
}
