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
import android.support.design.widget.TextInputLayout
import android.support.v4.app.DialogFragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch

import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.DialogActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.Constants.KEY_MRL
import org.videolan.vlc.util.VLCIO

const val TAG = "VLC/MrlPanelFragment"

class MRLPanelFragment : DialogFragment(), View.OnKeyListener, TextView.OnEditorActionListener, View.OnClickListener, MRLAdapter.MediaPlayerController {
    private lateinit var adapter: MRLAdapter
    private lateinit var editText: TextInputLayout

    val isEmpty: Boolean
        get() = adapter.isEmpty

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.mrl_panel, container, false)
        editText = v.findViewById(R.id.mrl_edit)
        editText.editText?.setOnKeyListener(this)
        editText.editText?.setOnEditorActionListener(this)
        val recyclerView = v.findViewById<RecyclerView>(R.id.mrl_list)
        recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(activity)
        adapter = MRLAdapter(this)
        recyclerView.adapter = adapter
        v.findViewById<View>(R.id.send).setOnClickListener(this)
        dialog.setTitle(R.string.open_mrl_dialog_title);
        return v
    }

    override fun onStart() {
        super.onStart()
        updateHistory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_MRL, editText.editText?.text.toString())
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        val mrl = savedInstanceState?.getString(KEY_MRL)
        mrl?.let {
            editText.editText?.setText(mrl)
        }
    }

    private fun updateHistory() {
        launch(VLCIO) {
            //        val history = VLCApplication.getMLInstance().lastStreamsPlayed()
            /***FOR TEST PURPOSE ONLY REMOVE THIS **/val history = VLCApplication.getMLInstance().lastMediaPlayed()
            launch(UI) {
                adapter.setList(history)
            }
        }
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        return (keyCode == EditorInfo.IME_ACTION_DONE ||
                keyCode == EditorInfo.IME_ACTION_GO ||
                event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) && processUri()
    }

    private fun processUri(): Boolean {
        if (!TextUtils.isEmpty(editText.editText?.text)) {
            val mw = MediaWrapper(Uri.parse(editText.editText?.text.toString().trim()))
            playMedia(mw)
            editText.editText?.text?.clear()
            return true
        }
        return false
    }

    override fun playMedia(mw: MediaWrapper) {
        mw.type = MediaWrapper.TYPE_STREAM
        MediaUtils.openMedia(activity, mw)
        updateHistory()
        activity?.supportInvalidateOptionsMenu()
        UiTools.setKeyboardVisibility(editText, false)
        dismiss()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
        return false
    }

    override fun onClick(v: View) {
        processUri()
    }

    override fun onDestroy() {
        super.onDestroy()
        val activity = activity
        (activity as? DialogActivity)?.finish()
    }

}
