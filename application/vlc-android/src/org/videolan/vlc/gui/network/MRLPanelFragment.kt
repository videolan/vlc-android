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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.Settings
import org.videolan.tools.isValidUrl
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlPanelBinding
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_REQUEST_CODE
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.BrowserFragmentInterface
import org.videolan.vlc.viewmodels.StreamsModel

const val TAG = "VLC/MrlPanelFragment"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MRLPanelFragment : Fragment(), View.OnKeyListener, TextView.OnEditorActionListener,
        View.OnClickListener, BrowserFragmentInterface,
        IStreamsFragmentDelegate by StreamsFragmentDelegate(), KeyboardListener {

    private lateinit var adapter: MRLAdapter
    private lateinit var editText: com.google.android.material.textfield.TextInputLayout
    private lateinit var viewModel: StreamsModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), StreamsModel.Factory(requireContext())).get(StreamsModel::class.java)
        setup(this, viewModel, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = MrlPanelBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
        editText = binding.mrlEdit
        editText.editText?.setOnKeyListener(this)
        editText.editText?.setOnEditorActionListener(this)

        adapter = MRLAdapter(getlistEventActor())
        val recyclerView = binding.mrlList

        if (Settings.showTvUi) {
            val gridLayoutManager = GridLayoutManager(activity, 2)
            recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.left = resources.getDimension(R.dimen.kl_half).toInt()
                    outRect.right = resources.getDimension(R.dimen.kl_half).toInt()
                    super.getItemOffsets(outRect, view, parent, state)
                }
            })
            recyclerView.layoutManager = gridLayoutManager
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
            recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        }
        recyclerView.adapter = adapter

        binding.play.setOnClickListener(this)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.dataset.observe(requireActivity(), Observer { adapter.update(it) })
        viewModel.loading.observe(requireActivity(), Observer { (activity as? MainActivity)?.refreshing = it })
    }

    override fun onResume() {
        super.onResume()
        val clipBoardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipBoardManager.primaryClip?.getItemAt(0)?.text?.toString()
        if (text.isValidUrl()) viewModel.observableSearchText.set(text)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RENAME_DIALOG_REQUEST_CODE) {
            data?.let {

                val media = it.getParcelableExtra<MediaWrapper>(RENAME_DIALOG_MEDIA)
                val newName = it.getStringExtra(RENAME_DIALOG_NEW_NAME)
                viewModel.rename(media, newName)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
        (activity as? ContentActivity)?.setTabLayoutVisibility(false)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.streams)
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent) = (keyCode == EditorInfo.IME_ACTION_DONE ||
            keyCode == EditorInfo.IME_ACTION_GO ||
            event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) && processUri()

    private fun processUri(): Boolean {
        if (!TextUtils.isEmpty(viewModel.observableSearchText.get())) {
            val mw = MLServiceLocator.getAbstractMediaWrapper(Uri.parse(viewModel.observableSearchText.get()?.trim()))
            playMedia(mw)
            viewModel.observableSearchText.set("")
            return true
        }
        return false
    }


    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?) = false

    override fun onClick(v: View) {
        processUri()
    }


    override fun refresh() {
        viewModel.refresh()
    }

    override fun hideKeyboard() {
        UiTools.setKeyboardVisibility(editText, false)
    }
}
