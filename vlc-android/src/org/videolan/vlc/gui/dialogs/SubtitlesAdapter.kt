package org.videolan.vlc.gui.dialogs

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.experimental.channels.SendChannel
import org.videolan.vlc.api.OpenSubtitle
import org.videolan.vlc.databinding.SubtitleDownloadItemBinding

internal class SubtitlesAdapter(private val eventActor: SendChannel<SubtitleItem>) : RecyclerView.Adapter<SubtitlesAdapter.ViewHolder>() {
    private var dataset: List<SubtitleItem>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtitlesAdapter.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = SubtitleDownloadItemBinding.inflate(inflater, parent, false )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataset?.get(position)
        holder.bind(item)
    }

    fun setList(list: List<SubtitleItem>?) {
        dataset = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = dataset?.size ?: 0

    inner class ViewHolder(val binding: SubtitleDownloadItemBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            dataset?.get(layoutPosition)?.let { eventActor.offer(it) }
        }

        fun bind(subtitleItem: SubtitleItem?) {
            binding.subtitleItem = subtitleItem
            binding.executePendingBindings()
        }
    }
}
