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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.DialogFragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.actor

import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlPanelBinding
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.viewmodels.MRLPanelModel

const val TAG = "VLC/MrlPanelFragment"

class MRLPanelFragment : DialogFragment(), View.OnKeyListener, TextView.OnEditorActionListener, View.OnClickListener {
    private lateinit var adapter: MRLAdapter
    private lateinit var editText: TextInputLayout
    private lateinit var viewModel: MRLPanelModel

    private val listEventActor = actor<MediaWrapper>(UI) {
        for (event in channel) playMedia(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_FRAME, 0)
        viewModel = ViewModelProviders.of(this).get(MRLPanelModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = MrlPanelBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
        editText = binding.mrlEdit
        editText.editText?.setOnKeyListener(this)
        editText.editText?.setOnEditorActionListener(this)

        adapter = MRLAdapter(listEventActor)
        val recyclerView = binding.mrlList
        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(activity)
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
        activity?.supportInvalidateOptionsMenu()
        UiTools.setKeyboardVisibility(editText, false)
        dismiss()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent) = false

    override fun onClick(v: View) {
        processUri()
    }

    override fun onDestroy() {
        super.onDestroy()
        // TV
        (activity as? DialogActivity)?.finish()
    }
}
