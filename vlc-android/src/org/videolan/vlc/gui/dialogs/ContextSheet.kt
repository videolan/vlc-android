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
import android.support.design.widget.BottomSheetDialogFragment
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ContextItemBinding
import org.videolan.vlc.util.AndroidDevices
import org.videolan.vlc.util.Constants

const val CTX_TITLE_KEY = "CTX_TITLE_KEY"
const val CTX_POSITION_KEY = "CTX_POSITION_KEY"
const val CTX_FLAGS_KEY = "CTX_FLAGS_KEY"

class ContextSheet : BottomSheetDialogFragment() {

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
        val list = view.findViewById<RecyclerView>(R.id.ctx_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = ContextAdapter()
        val flags = arguments?.getInt(CTX_FLAGS_KEY) ?: 0
        options = populateOptions(flags)
        if (!AndroidDevices.isPhone) launch(UI) { dialog.window.setLayout(resources.getDimensionPixelSize(R.dimen.default_context_width), ViewGroup.LayoutParams.MATCH_PARENT) }
    }

    private fun populateOptions(flags: Int) = mutableListOf<CtxOption>().apply {
        if (flags and Constants.CTX_PLAY != 0) add(Simple(Constants.CTX_PLAY, getString(R.string.play), R.drawable.ic_play))
        if (flags and Constants.CTX_PLAY_FROM_START != 0) add(Simple(Constants.CTX_PLAY_FROM_START, getString(R.string.play_from_start), R.drawable.ic_play))
        if (flags and Constants.CTX_PLAY_ALL != 0) add(Simple(Constants.CTX_PLAY_ALL, getString(R.string.play_all), R.drawable.ic_play))
        if (flags and Constants.CTX_PLAY_AS_AUDIO != 0) add(Simple(Constants.CTX_PLAY_AS_AUDIO, getString(R.string.play_as_audio), R.drawable.ic_am_playasaudio_normal))
        if (flags and Constants.CTX_PLAY_GROUP != 0) add(Simple(Constants.CTX_PLAY_GROUP, getString(R.string.play), R.drawable.ic_play))
        if (flags and Constants.CTX_APPEND != 0) add(Simple(Constants.CTX_APPEND, getString(R.string.append), R.drawable.ic_am_append_normal))
        if (flags and Constants.CTX_INFORMATION != 0) add(Simple(Constants.CTX_INFORMATION, getString(R.string.info), R.drawable.ic_am_information_normal))
        if (flags and Constants.CTX_DELETE != 0) add(Simple(Constants.CTX_DELETE, getString(R.string.delete), R.drawable.ic_trash))
        if (flags and Constants.CTX_DOWNLOAD_SUBTITLES != 0) add(Simple(Constants.CTX_DOWNLOAD_SUBTITLES, getString(R.string.download_subtitles), R.drawable.ic_am_downsub_normal))
        if (flags and Constants.CTX_PLAY_NEXT != 0) add(Simple(Constants.CTX_PLAY_NEXT, getString(R.string.insert_next), R.drawable.ic_am_append_normal))
        if (flags and Constants.CTX_ADD_TO_PLAYLIST != 0) add(Simple(Constants.CTX_ADD_TO_PLAYLIST, getString(R.string.add_to_playlist), R.drawable.ic_am_addtoplaylist_normal))
        if (flags and Constants.CTX_SET_RINGTONE != 0 && AndroidDevices.isPhone) add(Simple(Constants.CTX_SET_RINGTONE, getString(R.string.set_song), R.drawable.ic_am_ringtone_normal))
        if (flags and Constants.CTX_NETWORK_ADD != 0) add(Simple(Constants.CTX_NETWORK_ADD, getString(R.string.favorites_add), R.drawable.ic_menu_network))
        if (flags and Constants.CTX_NETWORK_EDIT != 0) add(Simple(Constants.CTX_NETWORK_EDIT, getString(R.string.favorites_edit), R.drawable.ic_menu_network))
        if (flags and Constants.CTX_NETWORK_REMOVE != 0) add(Simple(Constants.CTX_NETWORK_REMOVE, getString(R.string.favorites_remove), R.drawable.ic_menu_network))
    }

    inner class ContextAdapter : RecyclerView.Adapter<ContextAdapter.ViewHolder>() {

        private val inflater: LayoutInflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(requireContext()) }

        inner class ViewHolder(val binding : ContextItemBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                itemView.setOnClickListener {
                    receiver.onCtxAction(itemPosition, options[layoutPosition].id)
                    dismiss()
                }
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

fun showContext(activity: FragmentActivity, receiver: CtxActionReceiver, position: Int, title: String, flags: Int) {
    val ctxDialog = ContextSheet()
    ctxDialog.arguments = Bundle(3).apply {
        putString(CTX_TITLE_KEY, title)
        putInt(CTX_POSITION_KEY, position)
        putInt(CTX_FLAGS_KEY, flags)
    }
    ctxDialog.receiver = receiver
    ctxDialog.show(activity.supportFragmentManager, "context")
}