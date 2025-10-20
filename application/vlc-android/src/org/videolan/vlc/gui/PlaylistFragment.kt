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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.parcelable
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlaylistsFragmentBinding
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.audio.BaseAudioBrowser
import org.videolan.vlc.gui.dialogs.CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT
import org.videolan.vlc.gui.dialogs.CURRENT_SORT
import org.videolan.vlc.gui.dialogs.DISPLAY_IN_CARDS
import org.videolan.vlc.gui.dialogs.DisplaySettingsDialog
import org.videolan.vlc.gui.dialogs.ONLY_FAVS
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_MEDIA
import org.videolan.vlc.gui.dialogs.RENAME_DIALOG_NEW_NAME
import org.videolan.vlc.gui.dialogs.RenameDialog
import org.videolan.vlc.gui.helpers.DefaultPlaybackActionMediaType
import org.videolan.vlc.gui.helpers.INavigator
import org.videolan.vlc.gui.video.VideoBrowserFragment
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.FastScroller
import org.videolan.vlc.gui.view.RecyclerSectionItemDecoration
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.providers.medialibrary.MedialibraryProvider
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.ContextOption
import org.videolan.vlc.util.ContextOption.CTX_PLAY_ALL
import org.videolan.vlc.util.ContextOption.CTX_RENAME
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.util.onAnyChange
import org.videolan.vlc.viewmodels.mobile.PlaylistsViewModel
import org.videolan.vlc.viewmodels.mobile.getViewModel
import kotlin.math.min

class PlaylistFragment : BaseAudioBrowser<PlaylistsViewModel>(), SwipeRefreshLayout.OnRefreshListener {

    private lateinit var binding: PlaylistsFragmentBinding
    private lateinit var playlists: RecyclerView
    private lateinit var playlistAdapter: AudioBrowserAdapter
    private lateinit var fastScroller: FastScroller
    override val isMainNavigationPoint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = arguments?.getInt(PLAYLIST_TYPE, 0) ?: 0
        viewModel = getViewModel(Playlist.Type.entries[type])
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = PlaylistsFragmentBinding.inflate(inflater, container, false)
        playlists = binding.swipeLayout.findViewById(R.id.audio_list)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeLayout.setOnRefreshListener(this)


        //size of an item
        val spacing = resources.getDimension(R.dimen.kl_half).toInt()

        val dimension = resources.getDimension(R.dimen.default_content_width)
        val totalWidth = if (dimension > 0) min(requireActivity().getScreenWidth(), dimension.toInt()) else requireActivity().getScreenWidth()

        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(totalWidth - spacing * 2, nbColumns, spacing, 16.dp)

        playlistAdapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_PLAYLIST, this, cardSize = itemSize)
        playlistAdapter.onAnyChange { updateEmptyView() }
        adapter = playlistAdapter

        setupLayoutManager()

        playlists.adapter = playlistAdapter
        fastScroller = view.rootView.findViewById<FastScroller>(R.id.songs_fast_scroller_playlist)!!
        fastScroller.attachToCoordinator(
            requireActivity().findViewById<AppBarLayout>(R.id.appbar)!!,
            requireActivity().findViewById<CoordinatorLayout>(R.id.coordinator)!!,
            requireActivity().findViewById<FloatingActionButton>(R.id.fab)!!
        )
        viewModel.provider.pagedList.observe(viewLifecycleOwner) {
            @Suppress("UNCHECKED_CAST")
            playlistAdapter.submitList(it as PagedList<MediaLibraryItem>)
            updateEmptyView()
        }
        viewModel.provider.loading.observe(viewLifecycleOwner) { loading ->
            if (isResumed) setRefreshing(loading) { }
        }

        viewModel.provider.liveHeaders.observe(viewLifecycleOwner) {
            playlists.invalidateItemDecorations()
        }

        fastScroller.setRecyclerView(getCurrentRV(), viewModel.provider)
        (parentFragment as? VideoBrowserFragment)?.playlistOnlyFavorites = viewModel.provider.onlyFavorites
        requireActivity().supportFragmentManager.setFragmentResultListener(CONFIRM_PLAYLIST_RENAME_DIALOG_RESULT, viewLifecycleOwner) { requestKey, bundle ->
            val media = bundle.parcelable<MediaLibraryItem>(RENAME_DIALOG_MEDIA) ?: return@setFragmentResultListener
            val name = bundle.getString(RENAME_DIALOG_NEW_NAME) ?: return@setFragmentResultListener
            lifecycleScope.launch {
                viewModel.rename(media, name)
            }
        }
    }

    override fun onDisplaySettingChanged(key: String, value: Any) {
        when (key) {
            DISPLAY_IN_CARDS -> {
                viewModel.providerInCard = value as Boolean
                setupLayoutManager()
                playlists.adapter = adapter
                activity?.invalidateOptionsMenu()
                Settings.getInstance(requireActivity()).putSingle(viewModel.displayModeKey, value)
            }
            ONLY_FAVS -> {
                viewModel.providers[currentTab].showOnlyFavs(value as Boolean)
                viewModel.refresh()
                (parentFragment as? VideoBrowserFragment)?.playlistOnlyFavorites = value
            }
            CURRENT_SORT -> {
                @Suppress("UNCHECKED_CAST") val sort = value as Pair<Int, Boolean>
                viewModel.providers[currentTab].sort = sort.first
                viewModel.providers[currentTab].desc = sort.second
                viewModel.providers[currentTab].saveSort()
                viewModel.refresh()
            }
        }
    }

    private fun updateEmptyView() {
        if (!isAdded) return
        swipeRefreshLayout.visibility = if (Medialibrary.getInstance().isInitiated) View.VISIBLE else View.GONE
        binding.emptyLoading.emptyText = viewModel.filterQuery?.let {  getString(R.string.empty_search, it) } ?: if (viewModel.provider.onlyFavorites) getString(R.string.nofav) else getString(R.string.noplaylist)
        binding.emptyLoading.state =
                when {
                    viewModel.provider.loading.value == true && empty -> EmptyLoadingState.LOADING
                    empty && viewModel.provider.onlyFavorites -> EmptyLoadingState.EMPTY_FAVORITES
                    empty && viewModel.filterQuery != null -> EmptyLoadingState.EMPTY_SEARCH
                    empty -> EmptyLoadingState.EMPTY_SEARCH
                    else -> EmptyLoadingState.NONE
                }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        adapter?.itemCount?.let { getMultiHelper()?.toggleActionMode(true, it)}
        mode.menuInflater.inflate(R.menu.action_mode_audio_browser, menu)
        menu.findItem(R.id.action_mode_audio_add_playlist).isVisible = false
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.ml_menu_sortby).isVisible = false
        menu.findItem(R.id.ml_menu_display_options).isVisible = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ml_menu_display_options -> {
                //filter all sorts and keep only applicable ones
                val sorts = arrayListOf(Medialibrary.SORT_ALPHA, Medialibrary.SORT_FILENAME, Medialibrary.SORT_ARTIST, Medialibrary.SORT_ALBUM, Medialibrary.SORT_DURATION, Medialibrary.SORT_RELEASEDATE, Medialibrary.SORT_LASTMODIFICATIONDATE, Medialibrary.SORT_FILESIZE, Medialibrary.NbMedia).filter {
                    viewModel.provider.canSortBy(it)
                }
                //Open the display settings Bottom sheet
                DisplaySettingsDialog.newInstance(
                    displayInCards = viewModel.providerInCard,
                    onlyFavs = viewModel.provider.onlyFavorites,
                    sorts = sorts,
                    currentSort = viewModel.provider.sort,
                    currentSortDesc = viewModel.provider.desc,
                    defaultPlaybackActions = getDefaultActionMediaType().getDefaultPlaybackActions(Settings.getInstance(requireActivity())),
                    defaultActionType = getString(getDefaultActionMediaType().title)
                )
                        .show(requireActivity().supportFragmentManager, "DisplaySettingsDialog")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupLayoutManager() {
        val spacing = resources.getDimension(R.dimen.kl_half).toInt()

        if (playlists.itemDecorationCount > 0) {
            playlists.removeItemDecorationAt(0)
        }
        when (viewModel.providerInCard) {
            true -> {
                val screenWidth = (requireActivity() as? INavigator)?.getFragmentWidth(requireActivity()) ?: requireActivity().getScreenWidth()
                adapter?.cardSize = RecyclerSectionItemGridDecoration.getItemSize(screenWidth, nbColumns, spacing, 16.dp)
                adapter?.let { adapter ->
                    @Suppress("UNCHECKED_CAST")
                    displayListInGrid(playlists, adapter, viewModel.provider as MedialibraryProvider<MediaLibraryItem>, spacing)
                }
            }
            else -> {
                adapter?.cardSize = -1
                playlists.addItemDecoration(
                    RecyclerSectionItemDecoration(
                        resources.getDimensionPixelSize(R.dimen.recycler_section_header_height),
                        true,
                        viewModel.provider
                    )
                )
                playlists.layoutManager = LinearLayoutManager(activity)
            }
        }

        val lp = playlists.layoutParams
        val dimension = requireActivity().resources.getDimension(R.dimen.default_content_width)
        lp.width = if (viewModel.providerInCard) ViewGroup.LayoutParams.MATCH_PARENT else {
            dimension.toInt()
        }
        (playlists.parent as View).setBackgroundColor(if (!viewModel.providerInCard && dimension != -1F) backgroundColor else ContextCompat.getColor(requireContext(), R.color.transparent))
        playlists.setBackgroundColor(if (!viewModel.providerInCard && dimension != -1F) listColor else ContextCompat.getColor(requireContext(), R.color.transparent))
    }

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        if (actionMode == null) {
            val i = Intent(activity, HeaderMediaListActivity::class.java)
            i.putExtra(AudioBrowserFragment.TAG_ITEM, item)
            startActivity(i)
        } else super.onClick(v, position, item)
    }

    override fun onCtxAction(position: Int, option: ContextOption) {
        @Suppress("UNCHECKED_CAST")
        when (option) {
            CTX_PLAY_ALL -> MediaUtils.playAll(activity, viewModel.provider as MedialibraryProvider<MediaWrapper>, position, false)
            CTX_RENAME -> {
                val media = getCurrentAdapter()?.getItemByPosition(position) ?: return
                val dialog = RenameDialog.newInstance(media)
                dialog.show(requireActivity().supportFragmentManager, RenameDialog::class.simpleName)
            }
            else -> super.onCtxAction(position, option)
        }


    }

    override fun onRefresh() {
        activity?.reloadLibrary()
    }

    override fun getTitle(): String = getString(R.string.playlists)

    override fun getCurrentRV(): RecyclerView = playlists

    override fun getDefaultActionMediaType() = DefaultPlaybackActionMediaType.PLAYLIST

    override fun hasFAB() = false

    companion object {
        private const val PLAYLIST_TYPE = "PLAYLIST_TYPE"
        fun newInstance(type: Playlist.Type) = PlaylistFragment().apply {
            arguments = Bundle().apply {
                putInt(PLAYLIST_TYPE, type.ordinal)
            }
        }
    }
}
