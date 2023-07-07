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
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.AndroidDevices
import org.videolan.resources.CTX_ADD_FOLDER_AND_SUB_PLAYLIST
import org.videolan.resources.CTX_ADD_FOLDER_PLAYLIST
import org.videolan.resources.CTX_ADD_GROUP
import org.videolan.resources.CTX_ADD_SCANNED
import org.videolan.resources.CTX_ADD_SHORTCUT
import org.videolan.resources.CTX_ADD_TO_PLAYLIST
import org.videolan.resources.CTX_APPEND
import org.videolan.resources.CTX_BAN_FOLDER
import org.videolan.resources.CTX_COPY
import org.videolan.resources.CTX_CUSTOM_REMOVE
import org.videolan.resources.CTX_DELETE
import org.videolan.resources.CTX_DOWNLOAD_SUBTITLES
import org.videolan.resources.CTX_FAV_ADD
import org.videolan.resources.CTX_FAV_EDIT
import org.videolan.resources.CTX_FAV_REMOVE
import org.videolan.resources.CTX_FIND_METADATA
import org.videolan.resources.CTX_GO_TO_FOLDER
import org.videolan.resources.CTX_GROUP_SIMILAR
import org.videolan.resources.CTX_INFORMATION
import org.videolan.resources.CTX_MARK_ALL_AS_PLAYED
import org.videolan.resources.CTX_MARK_AS_PLAYED
import org.videolan.resources.CTX_MARK_AS_UNPLAYED
import org.videolan.resources.CTX_PLAY
import org.videolan.resources.CTX_PLAY_ALL
import org.videolan.resources.CTX_PLAY_AS_AUDIO
import org.videolan.resources.CTX_PLAY_FROM_START
import org.videolan.resources.CTX_PLAY_NEXT
import org.videolan.resources.CTX_PLAY_SHUFFLE
import org.videolan.resources.CTX_REMOVE_FROM_PLAYLIST
import org.videolan.resources.CTX_REMOVE_GROUP
import org.videolan.resources.CTX_RENAME
import org.videolan.resources.CTX_RENAME_GROUP
import org.videolan.resources.CTX_SET_RINGTONE
import org.videolan.resources.CTX_SHARE
import org.videolan.resources.CTX_STOP_AFTER_THIS
import org.videolan.resources.CTX_UNGROUP
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ContextItemBinding
import org.videolan.vlc.databinding.ContextualSheetBinding

const val CTX_TITLE_KEY = "CTX_TITLE_KEY"
const val CTX_POSITION_KEY = "CTX_POSITION_KEY"
const val CTX_FLAGS_KEY = "CTX_FLAGS_KEY"
const val CTX_MEDIA_KEY = "CTX_MEDIA_KEY"

class ContextSheet : VLCBottomSheetDialogFragment() {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private lateinit var binding: ContextualSheetBinding
    private lateinit var options: List<CtxOption>
    lateinit var receiver: CtxActionReceiver
    private var itemPosition = -1

    override fun initialFocusedView(): View = binding.ctxList

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.contextual_sheet, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (arguments?.containsKey(CTX_MEDIA_KEY) == true) {
            val media: MediaLibraryItem = arguments?.get(CTX_MEDIA_KEY) as MediaLibraryItem

            binding.item = media
            val artwork = when (media) {
                is MediaWrapper -> media.artworkURL
                else -> media.artworkMrl
            }
            binding.showCover = !artwork.isNullOrBlank()
            binding.ctxCoverTitle.text = media.title
            binding.ctxTitle.text = media.title
        } else if (arguments?.containsKey(CTX_TITLE_KEY) == true) {
            binding.ctxCoverTitle.text = arguments?.getString(CTX_TITLE_KEY)
                    ?: ""
        }
        binding.ctxList.layoutManager = LinearLayoutManager(requireContext())
        binding.ctxList.adapter = ContextAdapter()
        val flags = arguments?.getLong(CTX_FLAGS_KEY) ?: 0
        options = populateOptions(flags)
    }

    private fun populateOptions(flags: Long) = mutableListOf<CtxOption>().apply {
        if (flags and CTX_PLAY != 0L) add(Simple(CTX_PLAY, getString(R.string.play), R.drawable.ic_ctx_play))
        if (flags and CTX_PLAY_FROM_START != 0L) add(Simple(CTX_PLAY_FROM_START, getString(R.string.play_from_start), R.drawable.ic_ctx_play_from_start))
        if (flags and CTX_PLAY_ALL != 0L) add(Simple(CTX_PLAY_ALL, getString(R.string.play_all), R.drawable.ic_ctx_play_all))
        if (flags and CTX_PLAY_AS_AUDIO != 0L) add(Simple(CTX_PLAY_AS_AUDIO, getString(R.string.play_as_audio), R.drawable.ic_ctx_play_as_audio))
        if (flags and CTX_APPEND != 0L) add(Simple(CTX_APPEND, getString(R.string.append), R.drawable.ic_ctx_append))
        if (flags and CTX_PLAY_SHUFFLE != 0L) add(Simple(CTX_PLAY_SHUFFLE, getString(R.string.shuffle_play), R.drawable.ic_ctx_shuffle))
        if (flags and CTX_PLAY_NEXT != 0L) add(Simple(CTX_PLAY_NEXT, getString(R.string.insert_next), R.drawable.ic_ctx_play_next))
        if (flags and CTX_DOWNLOAD_SUBTITLES != 0L) add(Simple(CTX_DOWNLOAD_SUBTITLES, getString(R.string.download_subtitles), R.drawable.ic_ctx_download))
        if (flags and CTX_INFORMATION != 0L) add(Simple(CTX_INFORMATION, getString(R.string.info), R.drawable.ic_ctx_information))
        if (flags and CTX_ADD_TO_PLAYLIST != 0L) add(Simple(CTX_ADD_TO_PLAYLIST, getString(R.string.add_to_playlist), R.drawable.ic_ctx_add_to_playlist))
        if (flags and CTX_SET_RINGTONE != 0L && AndroidDevices.isPhone) add(Simple(CTX_SET_RINGTONE, getString(R.string.set_song), R.drawable.ic_ctx_set_ringtone))
        if (flags and CTX_FAV_ADD != 0L) add(Simple(CTX_FAV_ADD, getString(R.string.favorites_add), R.drawable.ic_ctx_fav_add))
        if (flags and CTX_ADD_SCANNED != 0L) add(Simple(CTX_ADD_SCANNED, getString(R.string.add_to_scanned), R.drawable.ic_ctx_addtoscan))
        if (flags and CTX_FAV_EDIT != 0L) add(Simple(CTX_FAV_EDIT, getString(R.string.favorites_edit), R.drawable.ic_ctx_edit))
        if (flags and CTX_FAV_REMOVE != 0L) add(Simple(CTX_FAV_REMOVE, getString(R.string.favorites_remove), R.drawable.ic_ctx_fav_remove))
        if (flags and CTX_REMOVE_FROM_PLAYLIST != 0L) add(Simple(CTX_REMOVE_FROM_PLAYLIST, getString(R.string.remove), R.drawable.ic_ctx_remove_from_playlist))
        if (flags and CTX_STOP_AFTER_THIS != 0L) add(Simple(CTX_STOP_AFTER_THIS, getString(R.string.stop_after_this), R.drawable.ic_ctx_stop_after_this))
        if (flags and CTX_RENAME != 0L) add(Simple(CTX_RENAME, getString(R.string.rename), R.drawable.ic_ctx_edit))
        if (flags and CTX_COPY != 0L) add(Simple(CTX_COPY, getString(R.string.copy_to_clipboard), R.drawable.ic_ctx_link))
        if (flags and CTX_DELETE != 0L) add(Simple(CTX_DELETE, getString(R.string.delete), R.drawable.ic_ctx_delete))
        if (flags and CTX_SHARE != 0L) add(Simple(CTX_SHARE, getString(R.string.share), R.drawable.ic_ctx_share))
        if (flags and CTX_ADD_SHORTCUT != 0L && ShortcutManagerCompat.isRequestPinShortcutSupported(requireActivity())) add(Simple(CTX_ADD_SHORTCUT, getString(R.string.create_shortcut), R.drawable.ic_ctx_app_shortcut))
        if (flags and CTX_FIND_METADATA != 0L) add(Simple(CTX_FIND_METADATA, getString(R.string.find_metadata), R.drawable.ic_ctx_delete))
        if (flags and CTX_ADD_FOLDER_PLAYLIST != 0L) add(Simple(CTX_ADD_FOLDER_PLAYLIST, getString(R.string.this_folder), R.drawable.ic_ctx_add_to_playlist))
        if (flags and CTX_ADD_FOLDER_AND_SUB_PLAYLIST != 0L) add(Simple(CTX_ADD_FOLDER_AND_SUB_PLAYLIST, getString(R.string.all_subfolders), R.drawable.ic_ctx_add_to_playlist))
        if (flags and CTX_ADD_GROUP != 0L) add(Simple(CTX_ADD_GROUP, getString(R.string.add_to_group), R.drawable.ic_ctx_add_to_group))
        if (flags and CTX_REMOVE_GROUP != 0L) add(Simple(CTX_REMOVE_GROUP, getString(R.string.remove_from_group), R.drawable.ic_ctx_remove_from_group))
        if (flags and CTX_RENAME_GROUP != 0L) add(Simple(CTX_RENAME_GROUP, getString(R.string.rename_group), R.drawable.ic_ctx_edit))
        if (flags and CTX_UNGROUP != 0L) add(Simple(CTX_UNGROUP, getString(R.string.ungroup), R.drawable.ic_ctx_delete))
        if (flags and CTX_GROUP_SIMILAR != 0L) add(Simple(CTX_GROUP_SIMILAR, getString(R.string.group_similar), R.drawable.ic_ctx_group_auto))
        if (flags and CTX_MARK_AS_PLAYED != 0L) add(Simple(CTX_MARK_AS_PLAYED, getString(R.string.mark_as_played), R.drawable.ic_ctx_mark_as_played))
        if (flags and CTX_MARK_AS_UNPLAYED != 0L) add(Simple(CTX_MARK_AS_UNPLAYED, getString(R.string.mark_as_not_played), R.drawable.ic_ctx_mark_as_not_played))
        if (flags and CTX_MARK_ALL_AS_PLAYED != 0L) add(Simple(CTX_MARK_ALL_AS_PLAYED, getString(R.string.mark_all_as_played), R.drawable.ic_ctx_mark_all_as_played))
        if (flags and CTX_GO_TO_FOLDER != 0L) add(Simple(CTX_GO_TO_FOLDER, getString(R.string.go_to_folder), R.drawable.ic_ctx_folder))
        if (flags and CTX_CUSTOM_REMOVE != 0L) add(Simple(CTX_CUSTOM_REMOVE, getString(R.string.remove_custom_path), R.drawable.ic_ctx_delete))
        if (flags and CTX_BAN_FOLDER != 0L) add(Simple(CTX_BAN_FOLDER, getString(R.string.group_ban_folder), R.drawable.ic_ctx_hide_source))
    }

    inner class ContextAdapter : RecyclerView.Adapter<ContextAdapter.ViewHolder>() {

        private val inflater: LayoutInflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(requireContext()) }

        inner class ViewHolder(val binding: ContextItemBinding) : RecyclerView.ViewHolder(binding.root) {
            private val textColor = binding.contextOptionTitle.currentTextColor
            private val focusedColor by lazy(LazyThreadSafetyMode.NONE) { ContextCompat.getColor(itemView.context, R.color.orange500transparent) }

            init {
                itemView.setOnClickListener {
                    receiver.onCtxAction(itemPosition, options[layoutPosition].id)
                    dismiss()
                }
                itemView.setOnFocusChangeListener { _, hasFocus -> binding.contextOptionTitle.setTextColor(if (hasFocus) focusedColor else textColor) }
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

/**
 * Show the bottom sheet containing the context actions
 *
 * @param activity the activity to use to launch the BottomSheet
 * @param receiver the `CtxActionReceiver` managing the result
 * @param arguments the arguments to send to the [VLCBottomSheetDialogFragment]
 */
private fun showContext(activity: FragmentActivity, receiver: CtxActionReceiver, arguments:Bundle) {
    if (!activity.isStarted()) return
    val ctxDialog = ContextSheet()
    ctxDialog.arguments = arguments
    ctxDialog.receiver = receiver
    ctxDialog.show(activity.supportFragmentManager, "context")
}

/**
 * Show the bottom sheet containing the context actions. Depending on [media] type, it generate the right arguments
 *
 * @param activity the activity to use to launch the BottomSheet
 * @param receiver the `CtxActionReceiver` managing the result
 * @param position the position that the caller may need to manage the result
 * @param media the media used to display the title
 * @param flags the flags describing the actions to be displayed
 */
fun showContext(activity: FragmentActivity, receiver: CtxActionReceiver, position: Int, media: MediaLibraryItem?, flags: Long) {
    if (!activity.isStarted()) return
    val arguments = when (media) {
        is MediaLibraryItem -> {
            bundleOf(CTX_MEDIA_KEY to media, CTX_POSITION_KEY to position,
                    CTX_FLAGS_KEY to flags)
        }
        else -> bundleOf(CTX_TITLE_KEY to (media?.title ?: ""), CTX_POSITION_KEY to position,
                CTX_FLAGS_KEY to flags)
    }
    showContext(activity, receiver, arguments)
}