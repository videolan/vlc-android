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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ContextItemBinding
import org.videolan.vlc.util.*

const val CTX_TITLE_KEY = "CTX_TITLE_KEY"
const val CTX_POSITION_KEY = "CTX_POSITION_KEY"
const val CTX_FLAGS_KEY = "CTX_FLAGS_KEY"

class ContextSheet : com.google.android.material.bottomsheet.BottomSheetDialogFragment() {

    private lateinit var options : List<CtxOption>
    lateinit var receiver : CtxActionReceiver
    private var itemPosition = -1

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
        view.findViewById<TextView>(R.id.ctx_title).text = arguments?.getString(CTX_TITLE_KEY) ?: ""
        val list = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.ctx_list)
        list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        list.adapter = ContextAdapter()
        val flags = arguments?.getInt(CTX_FLAGS_KEY) ?: 0
        options = populateOptions(flags)
        AppScope.launch(Dispatchers.Main) {
            dialog.window?.setLayout(resources.getDimensionPixelSize(R.dimen.default_context_width), ViewGroup.LayoutParams.MATCH_PARENT)
            (dialog as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                val bsb = BottomSheetBehavior.from(it)
                if (bsb.state == BottomSheetBehavior.STATE_COLLAPSED) bsb.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun populateOptions(flags: Int) = mutableListOf<CtxOption>().apply {
        if (flags and CTX_PLAY != 0) add(Simple(CTX_PLAY, getString(R.string.play), R.drawable.ic_ctx_play_normal))
        if (flags and CTX_PLAY_FROM_START != 0) add(Simple(CTX_PLAY_FROM_START, getString(R.string.play_from_start), R.drawable.ic_ctx_play_from_start_normal))
        if (flags and CTX_PLAY_ALL != 0) add(Simple(CTX_PLAY_ALL, getString(R.string.play_all), R.drawable.ic_ctx_play_all_normal))
        if (flags and CTX_PLAY_AS_AUDIO != 0) add(Simple(CTX_PLAY_AS_AUDIO, getString(R.string.play_as_audio), R.drawable.ic_ctx_play_as_audio_normal))
        if (flags and CTX_PLAY_GROUP != 0) add(Simple(CTX_PLAY_GROUP, getString(R.string.play), R.drawable.ic_ctx_play_normal))
        if (flags and CTX_VIDEO_TRACK != 0) add(Simple(CTX_VIDEO_TRACK, getString(R.string.ctx_player_video_track), R.drawable.ic_ctx_information_normal))
        if (flags and CTX_AUDIO_TRACK != 0) add(Simple(CTX_AUDIO_TRACK, getString(R.string.ctx_player_audio_track), R.drawable.ic_audiotrack_normal))
        if (flags and CTX_SUBS_TRACK != 0) add(Simple(CTX_SUBS_TRACK, getString(R.string.ctx_player_subs_track), R.drawable.ic_subtitle_normal_w))
        if (flags and CTX_PICK_SUBS != 0) add(Simple(CTX_PICK_SUBS, getString(R.string.subtitle_select), R.drawable.ic_subtitle_open_w))
        if (flags and CTX_APPEND != 0) add(Simple(CTX_APPEND, getString(R.string.append), R.drawable.ic_ctx_append_normal))
        if (flags and CTX_INFORMATION != 0) add(Simple(CTX_INFORMATION, getString(R.string.info), R.drawable.ic_ctx_information_normal))
        if (flags and CTX_DOWNLOAD_SUBTITLES != 0) add(Simple(CTX_DOWNLOAD_SUBTITLES, getString(R.string.download_subtitles), R.drawable.ic_ctx_download_subtitles_normal))
        if (flags and CTX_DOWNLOAD_SUBTITLES_PLAYER != 0) add(Simple(CTX_DOWNLOAD_SUBTITLES_PLAYER, getString(R.string.download_subtitles), R.drawable.ic_downsub_normal))
        if (flags and CTX_PLAY_NEXT != 0) add(Simple(CTX_PLAY_NEXT, getString(R.string.insert_next), R.drawable.ic_ctx_play_next_normal))
        if (flags and CTX_ADD_TO_PLAYLIST != 0) add(Simple(CTX_ADD_TO_PLAYLIST, getString(R.string.add_to_playlist), R.drawable.ic_ctx_add_to_playlist_normal))
        if (flags and CTX_SET_RINGTONE != 0 && AndroidDevices.isPhone) add(Simple(CTX_SET_RINGTONE, getString(R.string.set_song), R.drawable.ic_ctx_set_ringtone_normal))
        if (flags and CTX_FAV_ADD != 0) add(Simple(CTX_FAV_ADD, getString(R.string.favorites_add), R.drawable.ic_ctx_fav_add_normal))
        if (flags and CTX_FAV_EDIT != 0) add(Simple(CTX_FAV_EDIT, getString(R.string.favorites_edit), R.drawable.ic_ctx_fav_edit_normal))
        if (flags and CTX_FAV_REMOVE != 0) add(Simple(CTX_FAV_REMOVE, getString(R.string.favorites_remove), R.drawable.ic_ctx_fav_remove_normal))
        if (flags and CTX_REMOVE_FROM_PLAYLIST != 0) add(Simple(CTX_REMOVE_FROM_PLAYLIST, getString(R.string.remove), R.drawable.ic_ctx_remove_from_playlist_normal))
        if (flags and CTX_STOP_AFTER_THIS != 0) add(Simple(CTX_STOP_AFTER_THIS, getString(R.string.stop_after_this), R.drawable.ic_ctx_stop_after_this))
        if (flags and CTX_RENAME != 0) add(Simple(CTX_RENAME, getString(R.string.rename), R.drawable.ic_ctx_edit_normal))
        if (flags and CTX_DELETE != 0) add(Simple(CTX_DELETE, getString(R.string.delete), R.drawable.ic_ctx_delete_normal))
    }

    inner class ContextAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<ContextAdapter.ViewHolder>() {

        private val inflater: LayoutInflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(requireContext()) }

        inner class ViewHolder(val binding : ContextItemBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
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

sealed class CtxOption(val id: Int, val title : String, val icon : Int)
class Simple(id: Int, title : String, icon : Int = 0) : CtxOption(id, title, icon)

interface CtxActionReceiver {
    fun onCtxAction(position: Int, option: Int)
}

fun showContext(activity: androidx.fragment.app.FragmentActivity, receiver: CtxActionReceiver, position: Int, title: String, flags: Int) {
    val ctxDialog = ContextSheet()
    ctxDialog.arguments = Bundle(3).apply {
        putString(CTX_TITLE_KEY, title)
        putInt(CTX_POSITION_KEY, position)
        putInt(CTX_FLAGS_KEY, flags)
    }
    ctxDialog.receiver = receiver
    ctxDialog.show(activity.supportFragmentManager, "context")
}