/**
 * **************************************************************************
 * ConfirmDeleteDialog.kt
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.interfaces.media.Album
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import java.lang.IllegalStateException

const val CONFIRM_DELETE_DIALOG_MEDIALIST = "CONFIRM_DELETE_DIALOG_MEDIALIST"

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class ConfirmDeleteDialog : VLCBottomSheetDialogFragment() {

    private lateinit var listener: () -> Unit
    private lateinit var deleteAnimation: ImageView
    private lateinit var title: TextView
    private lateinit var mediaList: List<MediaLibraryItem>

    companion object {

        fun newInstance(medias: ArrayList<MediaLibraryItem>): ConfirmDeleteDialog {

            return ConfirmDeleteDialog().apply {
                arguments = bundleOf(CONFIRM_DELETE_DIALOG_MEDIALIST to medias)
            }
        }
    }

    fun setListener(listener: () -> Unit) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mediaList = arguments?.getParcelableArrayList(CONFIRM_DELETE_DIALOG_MEDIALIST) ?: throw IllegalStateException("List cannot be empty")
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_confirm_delete, container)
        deleteAnimation = view.findViewById(R.id.delete_animation)
        title = view.findViewById(R.id.title)
        view.findViewById<Button>(R.id.delete_button).setOnClickListener {
            listener.invoke()
            dismiss()
        }
        view.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dismiss()
        }

        title.text = when {
            mediaList.size > 1 && mediaList.filterIsInstance<MediaWrapper>().size == mediaList.size -> {
                //folders and files
                val nbFiles = mediaList.filter { it is MediaWrapper && it.type != MediaWrapper.TYPE_DIR }.size
                val nbFolders = mediaList.filter { it is MediaWrapper && it.type == MediaWrapper.TYPE_DIR }.size
                when {
                    nbFiles == 0 -> getString(R.string.confirm_delete_folders, nbFolders)
                    nbFolders == 0 -> getString(R.string.confirm_delete_files, nbFiles)
                    else -> getString(R.string.confirm_delete_folders_and_files, nbFolders, nbFiles)
                }

            }
            mediaList[0] is MediaWrapper -> getString(if ((mediaList[0] as MediaWrapper).type == MediaWrapper.TYPE_DIR) R.string.confirm_delete_folder else R.string.confirm_delete, mediaList[0].title)
            mediaList[0] is Album -> getString(R.string.confirm_delete_album, mediaList[0].title)
            mediaList[0] is Playlist -> getString(R.string.confirm_delete_playlist, mediaList[0].title)
            else -> getString(R.string.confirm_delete_several_media, mediaList.size)
        }


        val anim = AnimatedVectorDrawableCompat.create(requireActivity(), R.drawable.anim_delete)!!
        deleteAnimation.setImageDrawable(anim)
        anim.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                anim.start()
                super.onAnimationEnd(drawable)
            }
        })
        anim.start()
        return view
    }

    override fun getDefaultState(): Int {
        return STATE_EXPANDED
    }

    override fun initialFocusedView(): View = deleteAnimation

    override fun needToManageOrientation(): Boolean {
        return true
    }
}