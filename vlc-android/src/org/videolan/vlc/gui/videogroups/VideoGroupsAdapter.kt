package org.videolan.vlc.gui.videogroups

import android.annotation.TargetApi
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.SendChannel
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.AbstractVideoGroup
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.vlc.BR
import org.videolan.vlc.R
import org.videolan.vlc.databinding.VideogroupItemBinding
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.util.UPDATE_SELECTION


class VideoGroupsAdapter (val actor: SendChannel<VideoGroupAction>) : PagedListAdapter<AbstractVideoGroup,
        VideoGroupsAdapter.ViewHolder>(DIFF_CALLBACK), MultiSelectAdapter<AbstractVideoGroup>,
        CoroutineScope by MainScope() {
    private lateinit var inflater: LayoutInflater

    val multiSelectHelper = MultiSelectHelper(this, UPDATE_SELECTION)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!this::inflater.isInitialized) inflater = LayoutInflater.from(parent.context)
        return ViewHolder(VideogroupItemBinding.inflate(inflater, parent, false))
    }

    override fun getItem(position: Int) = super.getItem(position)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = getItem(position)
        holder.binding.group = group
            val count = group?.mediaCount() ?: 0
            holder.binding.groupDesc.visibility = if (count == 0) View.GONE else View.VISIBLE
            if (count > 0) holder.binding.groupDesc.text = holder.itemView.context.resources.getQuantityString(R.plurals.videos_quantity, count, count)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNullOrEmpty()) super.onBindViewHolder(holder, position, payloads)
        else {
            for (payload in payloads) {
                when (payload as? Int) {
                    UPDATE_SELECTION -> holder.selectView(multiSelectHelper.isSelected(position))
                }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.binding.setVariable(BR.cover, UiTools.getDefaultVideoDrawable(holder.itemView.context))
    }

    @TargetApi(Build.VERSION_CODES.M)
    inner class ViewHolder(binding: VideogroupItemBinding) : SelectorViewHolder<VideogroupItemBinding>(binding) {
        init {
            binding.holder = this
            itemView.setOnLongClickListener {
                getItem(layoutPosition)?.let { folder ->  actor.offer(VideoGroupLongClick(layoutPosition, folder)) }
                true
            }
            if (AndroidUtil.isMarshMallowOrLater) itemView.setOnContextClickListener {
                onCtxClick(itemView)
                true
            }
        }

        fun onClick(v: View) {
            getItem(layoutPosition)?.let { folder ->  actor.offer(VideoGroupClick(layoutPosition, folder)) }
        }

        fun onCtxClick(v: View) {
            getItem(layoutPosition)?.let { folder ->  actor.offer(VideoGroupCtxClick(layoutPosition, folder)) }
        }
    }
}

private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AbstractVideoGroup>() {
    override fun areItemsTheSame(oldItem: AbstractVideoGroup, newItem: AbstractVideoGroup) = oldItem == newItem
    override fun areContentsTheSame(oldItem: AbstractVideoGroup, newItem: AbstractVideoGroup) = true
}