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
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.databinding.ContextItemBinding
import org.videolan.vlc.databinding.ContextualSheetBinding
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.*
import org.videolan.vlc.util.FlagSet

const val CTX_TITLE_KEY = "CTX_TITLE_KEY"
const val CTX_POSITION_KEY = "CTX_POSITION_KEY"
const val CTX_FLAGS_KEY = "CTX_FLAGS_KEY"
const val CTX_MEDIA_KEY = "CTX_MEDIA_KEY"

class ContextSheet : VLCBottomSheetDialogFragment() {
    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private lateinit var binding: ContextualSheetBinding
    private lateinit var menuItems: List<CtxMenuItem>
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
        val flags = FlagSet(ContextOption::class.java).apply {
            setCapabilities(arguments?.getLong(CTX_FLAGS_KEY) ?: 0)
        }
        menuItems = populateMenuItems(flags)
    }

    private fun populateMenuItems(flags: FlagSet<ContextOption>) = mutableListOf<CtxMenuItem>().apply {
        if (flags.contains(CTX_PLAY)) add(Simple(CTX_PLAY, getString(R.string.play), R.drawable.ic_play))
        if (flags.contains(CTX_PLAY_FROM_START)) add(Simple(CTX_PLAY_FROM_START, getString(R.string.play_from_start), R.drawable.ic_play_from_start))
        if (flags.contains(CTX_PLAY_ALL)) add(Simple(CTX_PLAY_ALL, getString(R.string.play_all), R.drawable.ic_play_all))
        if (flags.contains(CTX_PLAY_AS_AUDIO)) add(Simple(CTX_PLAY_AS_AUDIO, getString(R.string.play_as_audio), R.drawable.ic_play_as_audio))
        if (flags.contains(CTX_APPEND)) add(Simple(CTX_APPEND, getString(R.string.append), R.drawable.ic_play_append))
        if (flags.contains(CTX_PLAY_SHUFFLE)) add(Simple(CTX_PLAY_SHUFFLE, getString(R.string.shuffle_play), R.drawable.ic_shuffle))
        if (flags.contains(CTX_PLAY_NEXT)) add(Simple(CTX_PLAY_NEXT, getString(R.string.insert_next), R.drawable.ic_play_next))
        if (flags.contains(CTX_DOWNLOAD_SUBTITLES)) add(Simple(CTX_DOWNLOAD_SUBTITLES, getString(R.string.download_subtitles), R.drawable.ic_download_subtitles))
        if (flags.contains(CTX_INFORMATION)) add(Simple(CTX_INFORMATION, getString(R.string.info), R.drawable.ic_information))
        if (flags.contains(CTX_ADD_TO_PLAYLIST)) add(Simple(CTX_ADD_TO_PLAYLIST, getString(R.string.add_to_playlist), R.drawable.ic_add_to_playlist))
        if (flags.contains(CTX_SET_RINGTONE) && AndroidDevices.isPhone) add(Simple(CTX_SET_RINGTONE, getString(R.string.set_song), R.drawable.ic_set_ringtone))
        if (flags.contains(CTX_FAV_ADD)) add(Simple(CTX_FAV_ADD, getString(R.string.favorites_add), R.drawable.ic_fav_add))
        if (flags.contains(CTX_ADD_SCANNED)) add(Simple(CTX_ADD_SCANNED, getString(R.string.add_to_scanned), R.drawable.ic_add_to_scan))
        if (flags.contains(CTX_FAV_EDIT)) add(Simple(CTX_FAV_EDIT, getString(R.string.favorites_edit), R.drawable.ic_edit))
        if (flags.contains(CTX_FAV_REMOVE)) add(Simple(CTX_FAV_REMOVE, getString(R.string.favorites_remove), R.drawable.ic_fav_remove))
        if (flags.contains(CTX_REMOVE_FROM_PLAYLIST)) add(Simple(CTX_REMOVE_FROM_PLAYLIST, getString(R.string.remove), R.drawable.ic_remove_from_playlist))
        if (flags.contains(CTX_STOP_AFTER_THIS)) add(Simple(CTX_STOP_AFTER_THIS, getString(R.string.stop_after_this), R.drawable.ic_stop_after_this))
        if (flags.contains(CTX_RENAME)) add(Simple(CTX_RENAME, getString(R.string.rename), R.drawable.ic_edit))
        if (flags.contains(CTX_COPY)) add(Simple(CTX_COPY, getString(R.string.copy_to_clipboard), R.drawable.ic_link))
        if (flags.contains(CTX_DELETE)) add(Simple(CTX_DELETE, getString(R.string.delete), R.drawable.ic_delete))
        if (flags.contains(CTX_SHARE)) add(Simple(CTX_SHARE, getString(R.string.share), R.drawable.ic_share))
        if (flags.contains(CTX_ADD_SHORTCUT) && ShortcutManagerCompat.isRequestPinShortcutSupported(requireActivity())) add(Simple(CTX_ADD_SHORTCUT, getString(R.string.create_shortcut), R.drawable.ic_app_shortcut))
        if (flags.contains(CTX_FIND_METADATA)) add(Simple(CTX_FIND_METADATA, getString(R.string.find_metadata), R.drawable.ic_delete))
        if (flags.contains(CTX_ADD_FOLDER_PLAYLIST)) add(Simple(CTX_ADD_FOLDER_PLAYLIST, getString(R.string.this_folder), R.drawable.ic_add_to_playlist))
        if (flags.contains(CTX_ADD_FOLDER_AND_SUB_PLAYLIST)) add(Simple(CTX_ADD_FOLDER_AND_SUB_PLAYLIST, getString(R.string.all_subfolders), R.drawable.ic_add_to_playlist))
        if (flags.contains(CTX_ADD_GROUP)) add(Simple(CTX_ADD_GROUP, getString(R.string.add_to_group), R.drawable.ic_add_to_group))
        if (flags.contains(CTX_REMOVE_GROUP)) add(Simple(CTX_REMOVE_GROUP, getString(R.string.remove_from_group), R.drawable.ic_remove_from_group))
        if (flags.contains(CTX_RENAME_GROUP)) add(Simple(CTX_RENAME_GROUP, getString(R.string.rename_group), R.drawable.ic_edit))
        if (flags.contains(CTX_UNGROUP)) add(Simple(CTX_UNGROUP, getString(R.string.ungroup), R.drawable.ic_delete))
        if (flags.contains(CTX_GROUP_SIMILAR)) add(Simple(CTX_GROUP_SIMILAR, getString(R.string.group_similar), R.drawable.ic_group_auto))
        if (flags.contains(CTX_MARK_AS_PLAYED)) add(Simple(CTX_MARK_AS_PLAYED, getString(R.string.mark_as_played), R.drawable.ic_mark_as_played))
        if (flags.contains(CTX_MARK_AS_UNPLAYED)) add(Simple(CTX_MARK_AS_UNPLAYED, getString(R.string.mark_as_not_played), R.drawable.ic_mark_as_not_played))
        if (flags.contains(CTX_MARK_ALL_AS_PLAYED)) add(Simple(CTX_MARK_ALL_AS_PLAYED, getString(R.string.mark_all_as_played), R.drawable.ic_mark_all_as_played))
        if (flags.contains(CTX_MARK_ALL_AS_UNPLAYED)) add(Simple(CTX_MARK_ALL_AS_UNPLAYED, getString(R.string.mark_all_as_not_played), R.drawable.ic_mark_all_as_not_played))
        if (flags.contains(CTX_GO_TO_FOLDER)) add(Simple(CTX_GO_TO_FOLDER, getString(R.string.go_to_folder), R.drawable.ic_browse_parent))
        if (flags.contains(CTX_CUSTOM_REMOVE)) add(Simple(CTX_CUSTOM_REMOVE, getString(R.string.remove_custom_path), R.drawable.ic_delete))
        if (flags.contains(CTX_BAN_FOLDER)) add(Simple(CTX_BAN_FOLDER, getString(R.string.group_ban_folder), R.drawable.ic_hide_source))
    }

    inner class ContextAdapter : RecyclerView.Adapter<ContextAdapter.ViewHolder>() {

        private val inflater: LayoutInflater by lazy(LazyThreadSafetyMode.NONE) { LayoutInflater.from(requireContext()) }

        inner class ViewHolder(val binding: ContextItemBinding) : RecyclerView.ViewHolder(binding.root) {
            private val textColor = binding.contextOptionTitle.currentTextColor
            private val focusedColor by lazy(LazyThreadSafetyMode.NONE) { ContextCompat.getColor(itemView.context, R.color.orange500transparent) }

            init {
                itemView.setOnClickListener {
                    receiver.onCtxAction(itemPosition, menuItems[layoutPosition].id)
                    dismiss()
                }
                itemView.setOnFocusChangeListener { _, hasFocus -> binding.contextOptionTitle.setTextColor(if (hasFocus) focusedColor else textColor) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ContextItemBinding.inflate(inflater, parent, false))

        override fun getItemCount() = menuItems.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.menuItem = menuItems[position]
            holder.binding.contextOptionIcon.setImageResource(menuItems[position].icon)
        }
    }
}

sealed class CtxMenuItem(val id: ContextOption, val title: String, val icon: Int)
class Simple(id: ContextOption, title: String, icon: Int = 0) : CtxMenuItem(id, title, icon)

interface CtxActionReceiver {
    fun onCtxAction(position: Int, option: ContextOption)
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
fun showContext(activity: FragmentActivity, receiver: CtxActionReceiver, position: Int, media: MediaLibraryItem?, flags: FlagSet<ContextOption>) {
    if (!activity.isStarted()) return
    val arguments = when (media) {
        is MediaLibraryItem -> {
            bundleOf(CTX_MEDIA_KEY to media, CTX_POSITION_KEY to position,
                    CTX_FLAGS_KEY to flags.getCapabilities())
        }
        else -> bundleOf(CTX_TITLE_KEY to (media?.title ?: ""), CTX_POSITION_KEY to position,
                CTX_FLAGS_KEY to flags.getCapabilities())
    }
    showContext(activity, receiver, arguments)
}