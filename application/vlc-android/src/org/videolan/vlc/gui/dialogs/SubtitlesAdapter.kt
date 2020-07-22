package org.videolan.vlc.gui.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import org.videolan.vlc.databinding.SubtitleDownloadItemBinding

internal class SubtitlesAdapter(private val eventActor: SendChannel<SubtitleEvent>) : RecyclerView.Adapter<SubtitlesAdapter.ViewHolder>() {
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

    @ExperimentalCoroutinesApi
    inner class ViewHolder(val binding: SubtitleDownloadItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                dataset?.get(layoutPosition)?.let {
                    if(!eventActor.isClosedForSend)
                        eventActor.offer(SubtitleClick(it)) }
            }
            itemView.setOnLongClickListener {
                dataset?.get(layoutPosition)?.let {
                    if(!eventActor.isClosedForSend)
                        eventActor.offer(SubtitleLongClick(it))
                }
                true
            }
        }

        fun bind(subtitleItem: SubtitleItem?) {
            binding.subtitleItem = subtitleItem
            binding.executePendingBindings()
        }
    }
}

sealed class SubtitleEvent
class SubtitleClick(val item: SubtitleItem) : SubtitleEvent()
class SubtitleLongClick(val item: SubtitleItem) : SubtitleEvent()
