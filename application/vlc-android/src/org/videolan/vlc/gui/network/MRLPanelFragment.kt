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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.*
import org.videolan.tools.Settings
import org.videolan.tools.copy
import org.videolan.tools.isValidUrl
import org.videolan.vlc.R
import org.videolan.vlc.databinding.MrlPanelBinding
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.MainActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.interfaces.BrowserFragmentInterface
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.viewmodels.StreamsModel

const val TAG = "VLC/MrlPanelFragment"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MRLPanelFragment : Fragment(), View.OnKeyListener, TextView.OnEditorActionListener,
        View.OnClickListener, CtxActionReceiver, BrowserFragmentInterface {

    private lateinit var adapter: MRLAdapter
    private lateinit var editText: com.google.android.material.textfield.TextInputLayout
    private lateinit var viewModel: StreamsModel

    private val listEventActor = lifecycleScope.actor<MrlAction> {
        for (event in channel) when (event) {
            is Playmedia -> playMedia(event.media)
            is ShowContext -> showContext(event.position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), StreamsModel.Factory(requireContext())).get(StreamsModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = MrlPanelBinding.inflate(inflater, container, false)
        binding.viewmodel = viewModel
        editText = binding.mrlEdit
        editText.editText?.setOnKeyListener(this)
        editText.editText?.setOnEditorActionListener(this)

        adapter = MRLAdapter(listEventActor)
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

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
        (activity as? ContentActivity)?.setTabLayoutVisibility(false)
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.open_mrl)
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

    private fun playMedia(mw: MediaWrapper) {
        mw.type = MediaWrapper.TYPE_STREAM
        if (mw.uri.scheme?.startsWith("rtsp") == true) VideoPlayerActivity.start(requireContext(), mw.uri)
        else MediaUtils.openMedia(activity, mw)
        activity?.invalidateOptionsMenu()
        UiTools.setKeyboardVisibility(editText, false)
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?) = false

    override fun onClick(v: View) {
        processUri()
    }

    private fun showContext(position: Int) {
        val flags = CTX_RENAME or CTX_APPEND or CTX_ADD_TO_PLAYLIST or CTX_COPY or CTX_DELETE
        val media = viewModel.dataset.get(position)
        showContext(requireActivity(), this, position, media.title, flags)
    }

    override fun onCtxAction(position: Int, option: Int) {
        when (option) {
            CTX_RENAME -> renameStream(position)
            CTX_APPEND -> {
                val media = viewModel.dataset.get(position)
                MediaUtils.appendMedia(requireContext(), media)
            }
            CTX_ADD_TO_PLAYLIST -> {
                val media = viewModel.dataset.get(position)
                requireActivity().addToPlaylist(media.tracks, SavePlaylistDialog.KEY_NEW_TRACKS)
            }
            CTX_COPY -> {
                val media = viewModel.dataset.get(position)
                requireContext().copy(media.title, media.location)
                Snackbar.make(requireActivity().window.decorView.findViewById<View>(android.R.id.content), R.string.url_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
            }
            CTX_DELETE -> {
                val media = viewModel.dataset.get(position)
                viewModel.deletingMedia = media
                UiTools.snackerWithCancel(editText, getString(R.string.stream_deleted), Runnable { viewModel.delete() }, Runnable {
                    viewModel.deletingMedia = null
                    viewModel.refresh()
                })
                viewModel.refresh()
            }
        }
    }

    private fun renameStream(position: Int) {
        RenameDialog.newInstance(viewModel.dataset.get(position)) { newName ->
            viewModel.rename(position, newName)
        }.show(requireActivity().supportFragmentManager, RenameDialog::class.simpleName)
    }

    override fun refresh() {
        viewModel.refresh()
    }
}
