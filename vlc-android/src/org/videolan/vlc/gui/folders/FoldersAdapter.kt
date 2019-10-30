package org.videolan.vlc.gui.folders

import android.annotation.TargetApi
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.AbstractFolder
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.FolderItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.util.UPDATE_SELECTION

class FoldersAdapter(val actor: SendChannel<FolderAction>) : PagedListAdapter<AbstractFolder, FoldersAdapter.ViewHolder>(DIFF_CALLBACK), MultiSelectAdapter<AbstractFolder>, CoroutineScope {
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()
    private lateinit var inflater: LayoutInflater

    val multiSelectHelper = MultiSelectHelper(this, UPDATE_SELECTION)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(FolderItemBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = getItem(position)
        holder.binding.folder = folder
        holder.job = launch(start = CoroutineStart.UNDISPATCHED) {
            val count = withContext(Dispatchers.IO) { folder?.mediaCount(AbstractFolder.TYPE_FOLDER_VIDEO) ?: 0 }
            holder.binding.folderDesc.visibility = if (count == 0) View.GONE else View.VISIBLE
            if (count > 0) holder.binding.folderDesc.text = holder.itemView.context.resources.getQuantityString(R.plurals.videos_quantity, count, count)
            holder.job = null
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

    override fun onViewRecycled(holder: ViewHolder) {
        holder.job?.cancel()
        holder.job = null
        super.onViewRecycled(holder)
    }

    override fun getItem(position: Int): AbstractFolder? = super.getItem(position)

    @TargetApi(Build.VERSION_CODES.M)
    inner class ViewHolder(binding: FolderItemBinding) : SelectorViewHolder<FolderItemBinding>(binding) {
        var job : Job? = null
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
private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AbstractFolder>() {
    override fun areItemsTheSame(oldItem: AbstractFolder, newItem: AbstractFolder) = oldItem == newItem

    override fun areContentsTheSame(oldItem: AbstractFolder, newItem: AbstractFolder) = true

    override fun getChangePayload(oldItem: AbstractFolder, newItem: AbstractFolder) = UPDATE_PAYLOAD
}