/*****************************************************************************
 * ContextSheet.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.resources.*
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ContextItemBinding

const val CTX_TITLE_KEY = "CTX_TITLE_KEY"
const val CTX_POSITION_KEY = "CTX_POSITION_KEY"
const val CTX_FLAGS_KEY = "CTX_FLAGS_KEY"

class ContextSheet : VLCBottomSheetDialogFragment() {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private lateinit var options : List<CtxOption>
    lateinit var receiver : CtxActionReceiver
    private var itemPosition = -1
    private lateinit var list: RecyclerView

    override fun initialFocusedView(): View = list

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemPosition = arguments?.getInt(CTX_POSITION_KEY) ?: -1
        if (!this::receiver.isInitialized) restoreReceiver(savedInstanceState)
    }

    private fun restoreReceiver(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            val fragments = requireActivity().supportFragmentManager.fragments
            for ((index, fragment) in fragments.withIndex()) {
                if (fragment is CtxActionReceiver) {
                    receiver = fragment
                    return
                } else if (index > 1) break
            }
        }
        dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contextual_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.ctx_title).text = arguments?.getString(CTX_TITLE_KEY) ?: ""
        list = view.findViewById<RecyclerView>(R.id.ctx_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = ContextAdapter()
        val flags = arguments?.getLong(CTX_FLAGS_KEY) ?: 0
        options = populateOptions(flags)
    }

    private fun populateOptions(flags: Long) = mutableListOf<CtxOption>().apply {
        if (flags and CTX_PLAY != 0L) add(Simple(CTX_PLAY, getString(R.string.play), R.drawable.ic_ctx_play_normal))
        if (flags and CTX_PLAY_FROM_START != 0L) add(Simple(CTX_PLAY_FROM_START, getString(R.string.play_from_start), R.drawable.ic_ctx_play_from_start_normal))
        if (flags and CTX_PLAY_ALL != 0L) add(Simple(CTX_PLAY_ALL, getString(R.string.play_all), R.drawable.ic_ctx_play_all_normal))
        if (flags and CTX_PLAY_AS_AUDIO != 0L) add(Simple(CTX_PLAY_AS_AUDIO, getString(R.string.play_as_audio), R.drawable.ic_ctx_play_as_audio_normal))
        if (flags and CTX_PLAY_GROUP != 0L) add(Simple(CTX_PLAY_GROUP, getString(R.string.play), R.drawable.ic_ctx_play_normal))
        if (flags and CTX_VIDEO_TRACK != 0L) add(Simple(CTX_VIDEO_TRACK, getString(R.string.ctx_player_video_track), R.drawable.ic_ctx_information_normal))
        if (flags and CTX_AUDIO_TRACK != 0L) add(Simple(CTX_AUDIO_TRACK, getString(R.string.ctx_player_audio_track), R.drawable.ic_audiotrack_normal))
        if (flags and CTX_SUBS_TRACK != 0L) add(Simple(CTX_SUBS_TRACK, getString(R.string.ctx_player_subs_track), R.drawable.ic_subtitle_w))
        if (flags and CTX_PICK_SUBS != 0L) add(Simple(CTX_PICK_SUBS, getString(R.string.subtitle_select), R.drawable.ic_subtitle_open_w))
        if (flags and CTX_APPEND != 0L) add(Simple(CTX_APPEND, getString(R.string.append), R.drawable.ic_ctx_append_normal))
        if (flags and CTX_INFORMATION != 0L) add(Simple(CTX_INFORMATION, getString(R.string.info), R.drawable.ic_ctx_information_normal))
        if (flags and CTX_DOWNLOAD_SUBTITLES != 0L) add(Simple(CTX_DOWNLOAD_SUBTITLES, getString(R.string.download_subtitles), R.drawable.ic_ctx_download_subtitles_normal))
        if (flags and CTX_DOWNLOAD_SUBTITLES_PLAYER != 0L) add(Simple(CTX_DOWNLOAD_SUBTITLES_PLAYER, getString(R.string.download_subtitles), R.drawable.ic_downsub_normal))
        if (flags and CTX_PLAY_NEXT != 0L) add(Simple(CTX_PLAY_NEXT, getString(R.string.insert_next), R.drawable.ic_ctx_play_next_normal))
        if (flags and CTX_ADD_TO_PLAYLIST != 0L) add(Simple(CTX_ADD_TO_PLAYLIST, getString(R.string.add_to_playlist), R.drawable.ic_ctx_add_to_playlist_normal))
        if (flags and CTX_SET_RINGTONE != 0L && AndroidDevices.isPhone) add(Simple(CTX_SET_RINGTONE, getString(R.string.set_song), R.drawable.ic_ctx_set_ringtone_normal))
        if (flags and CTX_FAV_ADD != 0L) add(Simple(CTX_FAV_ADD, getString(R.string.favorites_add), R.drawable.ic_ctx_fav_add_normal))
        if (flags and CTX_ADD_SCANNED != 0L) add(Simple(CTX_ADD_SCANNED, getString(R.string.add_to_scanned), R.drawable.ic_ctx_addtoscan_normal))
        if (flags and CTX_FAV_EDIT != 0L) add(Simple(CTX_FAV_EDIT, getString(R.string.favorites_edit), R.drawable.ic_ctx_fav_edit_normal))
        if (flags and CTX_FAV_REMOVE != 0L) add(Simple(CTX_FAV_REMOVE, getString(R.string.favorites_remove), R.drawable.ic_ctx_fav_remove_normal))
        if (flags and CTX_REMOVE_FROM_PLAYLIST != 0L) add(Simple(CTX_REMOVE_FROM_PLAYLIST, getString(R.string.remove), R.drawable.ic_ctx_remove_from_playlist_normal))
        if (flags and CTX_STOP_AFTER_THIS != 0L) add(Simple(CTX_STOP_AFTER_THIS, getString(R.string.stop_after_this), R.drawable.ic_ctx_stop_after_this))
        if (flags and CTX_RENAME != 0L) add(Simple(CTX_RENAME, getString(R.string.rename), R.drawable.ic_ctx_edit_normal))
        if (flags and CTX_COPY != 0L) add(Simple(CTX_COPY, getString(R.string.copy_to_clipboard), R.drawable.ic_ctx_link_normal))
        if (flags and CTX_DELETE != 0L) add(Simple(CTX_DELETE, getString(R.string.delete), R.drawable.ic_ctx_delete_normal))
        if (flags and CTX_SHARE != 0L) add(Simple(CTX_SHARE, getString(R.string.share), R.drawable.ic_ctx_share_normal))
        if (flags and CTX_FIND_METADATA != 0L) add(Simple(CTX_FIND_METADATA, getString(R.string.find_metadata), R.drawable.ic_ctx_delete_normal))
        if (flags and CTX_ADD_FOLDER_PLAYLIST != 0L) add(Simple(CTX_ADD_FOLDER_PLAYLIST, getString(R.string.this_folder), R.drawable.ic_ctx_add_to_playlist_normal))
        if (flags and CTX_ADD_FOLDER_AND_SUB_PLAYLIST != 0L) add(Simple(CTX_ADD_FOLDER_AND_SUB_PLAYLIST, getString(R.string.all_subfolders), R.drawable.ic_ctx_add_to_playlist_normal))
        if (flags and CTX_ADD_GROUP != 0L) add(Simple(CTX_ADD_GROUP, getString(R.string.add_to_group), R.drawable.ic_ctx_add_to_group))
        if (flags and CTX_REMOVE_GROUP != 0L) add(Simple(CTX_REMOVE_GROUP, getString(R.string.remove_from_group), R.drawable.ic_ctx_remove_from_group))
        if (flags and CTX_RENAME_GROUP != 0L) add(Simple(CTX_RENAME_GROUP, getString(R.string.rename_group), R.drawable.ic_ctx_edit))
        if (flags and CTX_UNGROUP != 0L) add(Simple(CTX_UNGROUP, getString(R.string.ungroup), R.drawable.ic_ctx_delete))
        if (flags and CTX_GROUP_SIMILAR != 0L) add(Simple(CTX_GROUP_SIMILAR, getString(R.string.group_similar), R.drawable.ic_ctx_group_auto))
        if (flags and CTX_MARK_AS_PLAYED != 0L) add(Simple(CTX_MARK_AS_PLAYED, getString(R.string.mark_as_played), R.drawable.ic_ctx_mark_as_played))
        if (flags and CTX_MARK_ALL_AS_PLAYED != 0L) add(Simple(CTX_MARK_ALL_AS_PLAYED, getString(R.string.mark_all_as_played), R.drawable.ic_ctx_mark_all_as_played))
    }

    inner class ContextAdapter : RecyclerView.Adapter<ContextAdapter.ViewHolder>() {

        private val inflater: LayoutInflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(requireContext()) }

        inner class ViewHolder(val binding : ContextItemBinding) : RecyclerView.ViewHolder(binding.root) {
            private val textColor = binding.contextOptionTitle.currentTextColor
            private val focusedColor by lazy(LazyThreadSafetyMode.NONE) { ContextCompat.getColor(itemView.context, R.color.orange500transparent) }
            init {
                itemView.setOnClickListener {
                    receiver.onCtxAction(itemPosition, options[layoutPosition].id)
                    dismiss()
                }
                itemView.setOnFocusChangeListener { v, hasFocus -> binding.contextOptionTitle.setTextColor( if (hasFocus) focusedColor else textColor) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ContextItemBinding.inflate(inflater, parent, false))

        override fun getItemCount() = options.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.option = options[position]
            holder.binding.contextOptionIcon.setImageResource(options[position].icon)
        }
    }
}

sealed class CtxOption(val id: Long, val title: String, val icon: Int)
class Simple(id: Long, title: String, icon: Int = 0) : CtxOption(id, title, icon)

interface CtxActionReceiver {
    fun onCtxAction(position: Int, option: Long)
}

fun showContext(activity: FragmentActivity, receiver: CtxActionReceiver, position: Int, title: String, flags: Long) {
    if (!activity.isStarted()) return
    val ctxDialog = ContextSheet()
    ctxDialog.arguments = bundleOf(CTX_TITLE_KEY to title, CTX_POSITION_KEY to position,
        CTX_FLAGS_KEY to flags)
    ctxDialog.receiver = receiver
    ctxDialog.show(activity.supportFragmentManager, "context")
}