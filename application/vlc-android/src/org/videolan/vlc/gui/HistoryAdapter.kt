/*****************************************************************************
 * HistoryAdapter.java
 *
 * Copyright © 2012-2015 VLC authors and VideoLAN
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
 */
package org.videolan.vlc.gui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.UPDATE_SELECTION
import org.videolan.tools.MultiSelectAdapter
import org.videolan.tools.MultiSelectHelper
import org.videolan.tools.Settings
import org.videolan.vlc.BR
import org.videolan.vlc.databinding.HistoryItemBinding
import org.videolan.vlc.databinding.HistoryItemCardBinding
import org.videolan.vlc.gui.helpers.*
import org.videolan.vlc.interfaces.IListEventsHandler
import org.videolan.vlc.interfaces.SwipeDragHelperAdapter
import org.videolan.vlc.util.LifecycleAwareScheduler
import org.videolan.vlc.util.isOTG
import org.videolan.vlc.util.isSD
import org.videolan.vlc.util.isSchemeNetwork


class HistoryAdapter(private val inCards: Boolean = false, private val listEventsHandler: IListEventsHandler? = null) : DiffUtilAdapter<MediaWrapper, HistoryAdapter.ViewHolder>(),
        MultiSelectAdapter<MediaWrapper>, IEventsSource<Click> by EventsSource(), SwipeDragHelperAdapter {

    val updateEvt : LiveData<Unit> = MutableLiveData()
    private lateinit var layoutInflater: LayoutInflater
    var multiSelectHelper: MultiSelectHelper<MediaWrapper> = MultiSelectHelper(this, UPDATE_SELECTION)
    var scheduler: LifecycleAwareScheduler? = null

    inner class ViewHolder(binding: ViewDataBinding) : SelectorViewHolder<ViewDataBinding>(binding), MarqueeViewHolder {

        override val titleView = when (binding) {
            is HistoryItemBinding -> binding.title
            is HistoryItemCardBinding -> binding.title
            else -> null
        }

        init {
            this.binding = binding
            when (binding) {
                is HistoryItemBinding -> binding.holder = this
                is HistoryItemCardBinding -> binding.holder = this
            }

        }

        fun onClick(@Suppress("UNUSED_PARAMETER") v: View) {
            eventsChannel.trySend(SimpleClick(layoutPosition))
        }

        fun onLongClick(@Suppress("UNUSED_PARAMETER") v: View) = eventsChannel.trySend(LongClick(layoutPosition)).isSuccess

        fun onImageClick(@Suppress("UNUSED_PARAMETER") v: View) {
            if (inCards)
                eventsChannel.trySend(SimpleClick(layoutPosition))
            else
                eventsChannel.trySend(ImageClick(layoutPosition))
        }

        override fun isSelected() = getItem(layoutPosition).hasStateFlags(MediaLibraryItem.FLAG_SELECTED)
        fun recycle() {
            when (binding) {
                is HistoryItemBinding -> (binding as HistoryItemBinding).title.isSelected = false
                is HistoryItemCardBinding -> (binding as HistoryItemCardBinding).title.isSelected = false
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (inCards && Settings.listTitleEllipsize == 4) scheduler = enableMarqueeEffect(recyclerView)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        scheduler?.cancelAction(MARQUEE_ACTION)
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (!::layoutInflater.isInitialized) layoutInflater = LayoutInflater.from(parent.context)
        return ViewHolder(if (inCards) HistoryItemCardBinding.inflate(layoutInflater, parent, false) else HistoryItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = getItem(position)
        when (holder.binding) {
            is HistoryItemBinding -> {
                (holder.binding as HistoryItemBinding).media = media
                holder.binding.setVariable(BR.isNetwork, media.uri.scheme.isSchemeNetwork())
                holder.binding.setVariable(BR.isSD, media.uri.isSD())
                holder.binding.setVariable(BR.isOTG, media.uri.isOTG())
                (holder.binding as HistoryItemBinding).cover = getMediaIconDrawable(holder.itemView.context, media.type)
                ((holder.binding as HistoryItemBinding).icon.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = if (media.type == MediaWrapper.TYPE_VIDEO) "16:10" else "1"
            }
            is HistoryItemCardBinding -> {
                (holder.binding as HistoryItemCardBinding).media = media
                holder.binding.setVariable(BR.isNetwork, media.uri.scheme.isSchemeNetwork())
                holder.binding.setVariable(BR.isSD, media.uri.isSD())
                holder.binding.setVariable(BR.isOTG, media.uri.isOTG())
                (holder.binding as HistoryItemCardBinding).cover = getMediaIconDrawable(holder.itemView.context, media.type)
                ((holder.binding as HistoryItemCardBinding).icon.layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = if (media.type == MediaWrapper.TYPE_VIDEO) "16:10" else "1"
            }
        }


        holder.selectView(multiSelectHelper.isSelected(position))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNullOrEmpty())
            super.onBindViewHolder(holder, position, payloads)
        else
            holder.selectView(multiSelectHelper.isSelected(position))
    }

    override fun getItemId(arg0: Int): Long {
        return 0
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    override fun onUpdateFinished() {
        (updateEvt as MutableLiveData).value = Unit
    }

    companion object {

        const val TAG = "VLC/HistoryAdapter"
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {    }

    override fun onItemDismiss(position: Int) {
        val item = getItem(position)
        listEventsHandler?.onRemove(position, item)
    }

    override fun onItemMoved(dragFrom: Int, dragTo: Int) {    }

    override fun createCB(): DiffCallback<MediaWrapper> = object : DiffCallback<MediaWrapper>() {
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition].title == newList[newItemPosition].title &&
                        oldList[oldItemPosition].description == newList[newItemPosition].description
    }
}