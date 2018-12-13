package org.videolan.vlc.gui.folders

import android.annotation.TargetApi
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.media.Folder
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.vlc.databinding.FolderItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.util.UPDATE_SELECTION

class FoldersAdapter(val actor: SendChannel<FolderAction>) : PagedListAdapter<Folder, FoldersAdapter.ViewHolder>(DIFF_CALLBACK), MultiSelectAdapter<Folder>, CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate
    private lateinit var inflater: LayoutInflater

    val multiSelectHelper = MultiSelectHelper(this, UPDATE_SELECTION)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(FolderItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = getItem(position)
        holder.binding.folder = folder
        launch {
            val count = withContext(Dispatchers.IO) { folder?.mediaCount(Folder.TYPE_FOLDER_VIDEO) ?: 0 }
            holder.binding.folderDesc.visibility = if (count == 0) View.GONE else View.VISIBLE
            if (count > 0) holder.binding.folderDesc.text = "$count videos"
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNullOrEmpty()) super.onBindViewHolder(holder, position, payloads)
        else {
            val folder = getItem(position) ?: return
            for (payload in payloads) {
                when (payload as? Int) {
                    UPDATE_SELECTION -> holder.selectView(multiSelectHelper.isSelected(position))
                }
            }
        }
    }

    override fun getItem(position: Int): Folder? = super.getItem(position)

    @TargetApi(Build.VERSION_CODES.M)
    inner class ViewHolder(binding: FolderItemBinding) : SelectorViewHolder<FolderItemBinding>(binding) {
        init {
            binding.holder = this
            itemView.setOnLongClickListener {
                getItem(layoutPosition)?.let { folder ->  actor.offer(FolderLongClick(layoutPosition, folder)) }
                true
            }
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener {
                onCtxClick(itemView)
                true
            }
        }

        fun onClick(v: View) {
            getItem(layoutPosition)?.let { folder ->  actor.offer(FolderClick(layoutPosition, folder)) }
        }

        fun onCtxClick(v: View) {
            getItem(layoutPosition)?.let { folder ->  actor.offer(FolderCtxClick(layoutPosition, folder)) }
        }
    }
}

private const val UPDATE_PAYLOAD = 1
private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Folder>() {
    override fun areItemsTheSame( oldItem: Folder, newItem: Folder) = oldItem == newItem

    override fun areContentsTheSame(oldItem: Folder, newItem: Folder) = true

    override fun getChangePayload(oldItem: Folder, newItem: Folder) = UPDATE_PAYLOAD
}