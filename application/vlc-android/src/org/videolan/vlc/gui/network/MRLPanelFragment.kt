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
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.tools.Settings
import org.videolan.tools.isValidUrl
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlPanelBinding
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.interfaces.BrowserFragmentInterface
import org.videolan.vlc.viewmodels.StreamsModel

const val TAG = "VLC/MrlPanelFragment"

class MRLPanelFragment : BaseFragment(), View.OnKeyListener, TextView.OnEditorActionListener,
        View.OnClickListener, BrowserFragmentInterface,
        IStreamsFragmentDelegate by StreamsFragmentDelegate(), KeyboardListener {

    private lateinit var binding: MrlPanelBinding
    private lateinit var adapter: MRLAdapter
    private lateinit var viewModel: StreamsModel
    override fun getTitle() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), StreamsModel.Factory(requireContext())).get(StreamsModel::class.java)
        setup(this, viewModel, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = MrlPanelBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
        binding.mrlEdit.editText?.setOnKeyListener(this)
        binding.mrlEdit.editText?.setOnEditorActionListener(this)
        binding.mrlEdit.editText?.requestFocus()

        binding.play.setOnClickListener(this)

        return binding.root
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

    override fun onDestroyActionMode(mode: ActionMode?) {}

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
            val horizontalOverscan = resources.getDimension(R.dimen.tv_overscan_horizontal).toInt()
            val verticalOverscan = resources.getDimension(R.dimen.tv_overscan_vertical).toInt()
            binding.mrlRoot.setPadding(horizontalOverscan, verticalOverscan, horizontalOverscan, verticalOverscan)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
            recyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
        }
        recyclerView.adapter = adapter
        viewModel.dataset.observe(requireActivity()) { adapter.update(it) }
        viewModel.loading.observe(requireActivity()) { (activity as? MainActivity)?.refreshing = it }
    }

    override fun onResume() {
        super.onResume()
        //Needed after privacy changes made in Android 10
        binding.mrlEdit.doOnLayout {
            try {
                val clipBoardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipBoardManager?.primaryClip?.getItemAt(0)?.text?.toString()
                if (text.isValidUrl()) {
                    viewModel.observableSearchText.set(text)
                    binding.clipboardIndicator.setVisible()
                }
            } catch (e: Exception) {
            }
        }
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
        if (!viewModel.observableSearchText.get().isNullOrEmpty()) {
            val mw = MLServiceLocator.getAbstractMediaWrapper(viewModel.observableSearchText.get()?.trim()?.toUri())
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
        UiTools.setKeyboardVisibility(binding.mrlEdit, false)
    }
}
