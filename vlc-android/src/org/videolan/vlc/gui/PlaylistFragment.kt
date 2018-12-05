/**
 * **************************************************************************
 * PlaylistFragment.kt
 * ****************************************************************************
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlaylistsFragmentBinding
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.audio.BaseAudioBrowser
import org.videolan.vlc.util.AppScope
import org.videolan.vlc.viewmodels.paged.PagedPlaylistsModel

class PlaylistFragment : BaseAudioBrowser(), Observer<PagedList<MediaLibraryItem>>, SwipeRefreshLayout.OnRefreshListener {

    private lateinit var binding : PlaylistsFragmentBinding
    private lateinit var playlists : RecyclerView
    private lateinit var playlistAdapter : AudioBrowserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (viewModel == null) {
            viewModel = ViewModelProviders.of(requireActivity(), PagedPlaylistsModel.Factory(requireContext())).get(PagedPlaylistsModel::class.java)
            playlistAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_PLAYLIST, this, viewModel.sort)
            mAdapter = playlistAdapter
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = PlaylistsFragmentBinding.inflate(inflater, container, false)
        playlists = binding.swipeLayout.findViewById(R.id.playlist_list)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeLayout.setOnRefreshListener(this)
        playlists.layoutManager = LinearLayoutManager(view.context, RecyclerView.VERTICAL, false)
        playlists.adapter = playlistAdapter

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel.pagedList.observe(requireActivity(), this)
        viewModel.loading.observe(this, Observer<Boolean> { loading ->
            AppScope.launch { binding.swipeLayout.isRefreshing = loading == true }
        })
    }

    override fun onChanged(list: PagedList<MediaLibraryItem>?) {
        playlistAdapter.submitList(list)
        binding.empty.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.action_mode_audio_browser, menu)
        menu.findItem(R.id.action_mode_audio_add_playlist).isVisible = false
        return true
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (mActionMode == null) {
            val i = Intent(activity, PlaylistActivity::class.java)
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item)
            startActivity(i)
        } else super.onClick(v, position, item)
    }

    override fun onRefresh() {
        viewModel.refresh()
    }

    override fun getTitle() = getString(R.string.playlists)

    override fun getCurrentRV() : RecyclerView = playlists

    override fun hasFAB() = false
}