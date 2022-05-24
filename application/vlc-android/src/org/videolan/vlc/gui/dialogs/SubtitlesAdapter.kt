package org.videolan.vlc.gui.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SubtitleDownloadItemBinding
import java.util.*

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

    @OptIn(ExperimentalCoroutinesApi::class)
    inner class ViewHolder(val binding: SubtitleDownloadItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                dataset?.get(layoutPosition)?.let {
                    if(!eventActor.isClosedForSend)
                        eventActor.trySend(SubtitleClick(it)) }
            }
            itemView.setOnLongClickListener {
                dataset?.get(layoutPosition)?.let {
                    if(!eventActor.isClosedForSend)
                        eventActor.trySend(SubtitleLongClick(it))
                }
                true
            }
        }

        fun bind(subtitleItem: SubtitleItem?) {
            binding.subtitleItem = subtitleItem
            binding.downloadSub.setOnClickListener { itemView.performClick() }
            binding.executePendingBindings()

            val context = binding.root.context
            val downloadString = context.getString(when(subtitleItem?.state) {
                State.Downloaded ->R.string.downloaded
                State.NotDownloaded ->R.string.not_downloaded
                else -> R.string.downloading
            })
            itemView.contentDescription = context.getString(R.string.talkback_subtitle_dowload_item, Locale(subtitleItem?.subLanguageID ?: "").displayLanguage, downloadString, subtitleItem?.movieReleaseName ?: "")
        }
    }
}

sealed class SubtitleEvent
class SubtitleClick(val item: SubtitleItem) : SubtitleEvent()
class SubtitleLongClick(val item: SubtitleItem) : SubtitleEvent()
