package org.videolan.vlc.gui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ExtensionItemViewBinding
import org.videolan.vlc.extensions.api.VLCExtensionItem
import org.videolan.vlc.media.MediaUtils
import java.util.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class ExtensionAdapter(internal var fragment: ExtensionBrowser?) : RecyclerView.Adapter<ExtensionAdapter.ViewHolder>() {
    private var itemsList: MutableList<VLCExtensionItem> = ArrayList()

    val all: List<VLCExtensionItem>
        get() = itemsList

    inner class ViewHolder(var binding: ExtensionItemViewBinding) : RecyclerView.ViewHolder(binding.root), View.OnLongClickListener {

        init {
            binding.holder = this
        }

        fun onClick(v: View) {
            val item = getItem(layoutPosition)
            if (item.type == VLCExtensionItem.TYPE_DIRECTORY) {
                fragment!!.browseItem(item)
            } else if (item.type == VLCExtensionItem.TYPE_AUDIO || item.type == VLCExtensionItem.TYPE_VIDEO) {
                val mw = MLServiceLocator.getAbstractMediaWrapper(item.link.toUri())
                mw.setDisplayTitle(item.getTitle())
                mw.description = item.getSubTitle()
                mw.type = getTypeAccordingToItem(item.type)
                MediaUtils.openMedia(v.context, mw)
            }
        }

        fun onMoreClick(v: View) {
            openContextMenu()
        }

        override fun onLongClick(v: View): Boolean {
            return openContextMenu()
        }

        private fun openContextMenu(): Boolean {
            if (fragment == null)
                return false
            fragment!!.openContextMenu(layoutPosition)
            return true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(DataBindingUtil.inflate<ViewDataBinding>(LayoutInflater.from(parent.context), R.layout.extension_item_view, parent, false) as ExtensionItemViewBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.item = item
        holder.binding.executePendingBindings()
    }

    private fun getIconResId(item: VLCExtensionItem): Int {
        return when (item.type) {
            VLCExtensionItem.TYPE_AUDIO -> R.drawable.ic_browser_audio_normal
            VLCExtensionItem.TYPE_DIRECTORY -> R.drawable.ic_menu_folder
            VLCExtensionItem.TYPE_VIDEO -> R.drawable.ic_browser_video_normal
            VLCExtensionItem.TYPE_SUBTITLE -> R.drawable.ic_browser_subtitle_normal
            else -> R.drawable.ic_browser_unknown_normal
        }
    }

    fun getItem(position: Int): VLCExtensionItem {
        return itemsList[position]
    }

    override fun getItemCount(): Int {
        return itemsList.size
    }

    fun addAll(list: List<VLCExtensionItem>) {
        itemsList.clear()
        itemsList.addAll(list)
        notifyDataSetChanged()
    }

    fun clear() {
        itemsList.clear()
    }

    private fun getTypeAccordingToItem(type: Int): Int {
        return when (type) {
            VLCExtensionItem.TYPE_DIRECTORY -> MediaWrapper.TYPE_DIR
            VLCExtensionItem.TYPE_VIDEO -> MediaWrapper.TYPE_VIDEO
            VLCExtensionItem.TYPE_AUDIO -> MediaWrapper.TYPE_AUDIO
            VLCExtensionItem.TYPE_PLAYLIST -> MediaWrapper.TYPE_PLAYLIST
            VLCExtensionItem.TYPE_SUBTITLE -> MediaWrapper.TYPE_SUBTITLE
            else -> MediaWrapper.TYPE_ALL
        }
    }

    companion object {
        private val TAG = "VLC/ExtensionAdapter"
    }
}
