/*****************************************************************************
 * MRLPanelFragment.java
 *
 * Copyright © 2014-2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
package org.videolan.vlc.gui.network

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.tools.coroutineScope
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlPanelBinding
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.CTX_ADD_TO_PLAYLIST
import org.videolan.vlc.util.CTX_RENAME
import org.videolan.vlc.viewmodels.StreamsModel

const val TAG = "VLC/MrlPanelFragment"

class MRLPanelFragment : androidx.fragment.app.DialogFragment(), View.OnKeyListener, TextView.OnEditorActionListener, View.OnClickListener, CtxActionReceiver {

    private lateinit var adapter: MRLAdapter
    private lateinit var editText: com.google.android.material.textfield.TextInputLayout
    private lateinit var viewModel: StreamsModel

    private val listEventActor = coroutineScope.actor<MrlAction> {
        for (event in channel) when(event) {
            is Playmedia -> playMedia(event.media)
            is ShowContext -> showContext(event.position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(androidx.fragment.app.DialogFragment.STYLE_NO_FRAME, 0)
        viewModel = ViewModelProviders.of(this).get(StreamsModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = MrlPanelBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
        editText = binding.mrlEdit
        editText.editText?.setOnKeyListener(this)
        editText.editText?.setOnEditorActionListener(this)

        adapter = MRLAdapter(listEventActor)
        val recyclerView = binding.mrlList
        recyclerView.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(activity, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(activity)
        recyclerView.adapter = adapter
        viewModel.observableHistory.observe(this, Observer { adapter.setList(it) })
        binding.play.setOnClickListener(this)

        dialog.setTitle(R.string.open_mrl_dialog_title)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        viewModel.updateHistory()
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        return (keyCode == EditorInfo.IME_ACTION_DONE ||
                keyCode == EditorInfo.IME_ACTION_GO ||
                event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) && processUri()
    }

    private fun processUri(): Boolean {
        if (!TextUtils.isEmpty(viewModel.observableSearchText.get())) {
            val mw = MediaWrapper(Uri.parse(viewModel.observableSearchText.get()))
            playMedia(mw)
            viewModel.observableSearchText.set("")
            return true
        }
        return false
    }

    private fun playMedia(mw: MediaWrapper) {
        mw.type = MediaWrapper.TYPE_STREAM
        MediaUtils.openMedia(activity, mw)
        viewModel.updateHistory()
        activity?.invalidateOptionsMenu()
        UiTools.setKeyboardVisibility(editText, false)
        dismiss()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?) = false

    override fun onClick(v: View) {
        processUri()
    }

    private fun showContext(position: Int) {
        val flags = CTX_RENAME or CTX_ADD_TO_PLAYLIST
        val media = viewModel.observableHistory.value?.get(position) ?: return
        showContext(requireActivity(), this, position, media.title, flags)
    }

    override fun onCtxAction(position: Int, option: Int) {
        when (option) {
            CTX_RENAME -> renameStream(position)
            CTX_ADD_TO_PLAYLIST -> {
                val media = viewModel.observableHistory.value?.get(position) ?: return
                UiTools.addToPlaylist(requireActivity(), media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            }
        }
    }

    private fun renameStream(position: Int) {
        val media = viewModel.observableHistory.value?.get(position) ?: return
        val edit = EditText(requireActivity())
        AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.rename_media, media.title))
                .setView(edit)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.rename(position, edit.text.toString())
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // TV
        (activity as? DialogActivity)?.finish()
    }
}
