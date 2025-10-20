package org.videolan.television.ui.details

import android.annotation.TargetApi
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.interfaces.FocusListener
import org.videolan.television.R
import org.videolan.television.databinding.ActivityMediaListTvItemBinding
import org.videolan.television.ui.TvFocusableAdapter
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.helpers.SelectorViewHolder
import org.videolan.vlc.interfaces.ITVEventsHandler
import org.videolan.vlc.util.ModelsHelper.getDiscNumberString

class MediaListAdapter(private val type: Int, private val listener: ITVEventsHandler) : DiffUtilAdapter<MediaWrapper, MediaListAdapter.MediaListViewHolder>(), TvFocusableAdapter {

    private var focusListener: FocusListener? = null


    override fun setOnFocusChangeListener(focusListener: FocusListener?) {
        this.focusListener = focusListener
    }


    var lastMovedItemFrom = -1
    var lastMovedItemTo = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ActivityMediaListTvItemBinding.inflate(inflater, parent, false)
        return MediaListViewHolder(binding, type)
    }

    override fun onBindViewHolder(holder: MediaListViewHolder, position: Int) {
        val item = getItemByPosition(position)
        holder.binding.item = item
        holder.binding.holder = holder
        holder.binding.subtitle = if (item.getDiscNumberString() != null) "${item.artistName} · ${item.getDiscNumberString()}" else item.artistName

        val moveVisibility = if (type == MediaLibraryItem.TYPE_ALBUM) View.GONE else View.VISIBLE
        holder.binding.itemMoveDown.visibility = if (moveVisibility == View.VISIBLE && position == itemCount - 1) View.INVISIBLE else moveVisibility
        holder.binding.itemMoveUp.visibility = if (moveVisibility == View.VISIBLE && position == 0) View.INVISIBLE else moveVisibility
        holder.binding.itemRemove.visibility = moveVisibility
        holder.binding.itemSelector.contentDescription = holder.binding.itemSelector.context.getString(R.string.play_media, item.title)
        if (type == MediaLibraryItem.TYPE_ALBUM) (holder.binding.itemAddPlaylist.layoutParams as ConstraintLayout.LayoutParams).marginEnd = 0

    }

    override fun detectMoves() = true

    override fun createCB(): DiffCallback<MediaWrapper> {
        return object : DiffCallback<MediaWrapper>() {
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                if (newItemPosition == lastMovedItemFrom) {
                    lastMovedItemFrom = -1
                    return false
                }
                if (newItemPosition == lastMovedItemTo) {
                    lastMovedItemTo = -1
                    return false
                }
                return super.areContentsTheSame(oldItemPosition, newItemPosition)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    inner class MediaListViewHolder(binding: ActivityMediaListTvItemBinding, @Suppress("UNUSED_PARAMETER") type: Int) : SelectorViewHolder<ActivityMediaListTvItemBinding>(binding) {

        init {
            val fadableViews = arrayOf(binding.itemMoveDown, binding.itemMoveUp, binding.itemAddPlaylist, binding.itemInsertNext, binding.itemAppend, binding.itemSelector, binding.itemRemove)
            val focusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                val alpha = if (!hasFocus) 0f else 1f
                fadableViews.forEach { it.animate().alpha(alpha) }

                val playAlpha = if (v == binding.itemSelector && hasFocus) 1f else 0f
                binding.itemPlay.animate().alpha(playAlpha)
                if (hasFocus) {
                    listener.onFocusChanged(getItemByPosition(layoutPosition))
                    focusListener?.onFocusChanged(layoutPosition)
                }

            }

            fadableViews.forEach {
                it.onFocusChangeListener = focusChangeListener
            }
            binding.itemPlay.onFocusChangeListener = focusChangeListener


        }

        fun onClickPlay(v: View) {
            listener.onClickPlay(v, layoutPosition)
        }

        fun onClickMoveDown(v: View) {
            lastMovedItemFrom = layoutPosition
            lastMovedItemTo = layoutPosition + 1
            if (BuildConfig.DEBUG) Log.d("MediaListAdapter", "From $lastMovedItemFrom to $lastMovedItemTo")
            listener.onClickMoveDown(v, layoutPosition)
        }

        fun onClickMoveUp(v: View) {
            lastMovedItemFrom = layoutPosition
            lastMovedItemTo = layoutPosition - 1
            if (BuildConfig.DEBUG) Log.d("MediaListAdapter", "From $lastMovedItemFrom to $lastMovedItemTo")
            listener.onClickMoveUp(v, layoutPosition)
        }

        fun onClickAppend(v: View) {
            listener.onClickAppend(v, layoutPosition)
        }

        fun onClickAddToPlaylist(v: View) {
            listener.onClickAddToPlaylist(v, layoutPosition)
        }

        fun onClickRemove(v: View) {
            listener.onClickRemove(v, layoutPosition)
        }

        fun onClickPlayNext(v: View) {
            listener.onClickPlayNext(v, layoutPosition)
        }
    }
}