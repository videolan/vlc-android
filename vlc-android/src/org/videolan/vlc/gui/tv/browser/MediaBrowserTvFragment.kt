/*
 * ************************************************************************
 *  MediaBrowserTvFragment.kt
 * *************************************************************************
 *  Copyright © 2016-2019 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.gui.tv.browser

import android.annotation.TargetApi
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.KeyEvent.*
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

import kotlinx.android.synthetic.main.song_browser.*
import kotlinx.coroutines.*
import org.videolan.medialibrary.Medialibrary
import org.videolan.medialibrary.media.Folder
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.audio.AudioBrowserAdapter
import org.videolan.vlc.gui.tv.MediaBrowserAnimatorDelegate
import org.videolan.vlc.gui.tv.MediaHeaderAdapter
import org.videolan.vlc.gui.tv.TvUtil
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.gui.tv.setAnimator
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.*
import org.videolan.vlc.viewmodels.paged.*
import java.util.*

private const val TAG = "MediaBrowserTvFragment"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class MediaBrowserTvFragment : Fragment(), BrowserFragmentInterface, IEventsHandler,
        PopupMenu.OnMenuItemClickListener, MediaHeaderAdapter.OnHeaderSelected,
        VerticalGridActivity.OnKeyPressedListener, CoroutineScope by MainScope() {

    private lateinit var viewModel: MLPagedModel<MediaLibraryItem>
    private lateinit var adapter: AudioBrowserAdapter
    //    private lateinit var headerList: RecyclerView
    private lateinit var headerAdapter: MediaHeaderAdapter
    private var nbColumns: Int = 0
    private lateinit var gridLayoutManager: GridLayoutManager
    private var currentItem: MediaLibraryItem? = null
    private var currentArt: String? = null
    private lateinit var backgroundManager: BackgroundManager
    internal lateinit var animationDelegate : MediaBrowserAnimatorDelegate

    companion object {
        fun newInstance(type: Long, item: MediaLibraryItem?) =
                MediaBrowserTvFragment().apply {
                    arguments = Bundle().apply {
                        this.putLong(AUDIO_CATEGORY, type)
                        this.putParcelable(AUDIO_ITEM, item)
                    }
                }
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.song_browser, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backgroundManager = BackgroundManager.getInstance(requireActivity())

        currentItem = if (savedInstanceState != null) savedInstanceState.getParcelable<Parcelable>(AUDIO_ITEM) as? MediaLibraryItem
        else requireActivity().intent.getParcelableExtra<Parcelable>(AUDIO_ITEM) as? MediaLibraryItem

        when (arguments?.getLong(AUDIO_CATEGORY, CATEGORY_SONGS)) {
            CATEGORY_SONGS ->
                viewModel = ViewModelProviders.of(this, PagedTracksModel.Factory(requireContext(), currentItem)).get(PagedTracksModel::class.java) as MLPagedModel<MediaLibraryItem>
            CATEGORY_ALBUMS ->
                viewModel = ViewModelProviders.of(this, PagedAlbumsModel.Factory(requireContext(), currentItem)).get(PagedAlbumsModel::class.java) as MLPagedModel<MediaLibraryItem>
            CATEGORY_ARTISTS ->
                viewModel = ViewModelProviders.of(this, PagedArtistsModel.Factory(requireContext(), Settings.getInstance(requireContext()).getBoolean(KEY_ARTISTS_SHOW_ALL, false))).get(PagedArtistsModel::class.java) as MLPagedModel<MediaLibraryItem>
            CATEGORY_GENRES ->
                viewModel = ViewModelProviders.of(this, PagedGenresModel.Factory(requireContext())).get(PagedGenresModel::class.java) as MLPagedModel<MediaLibraryItem>
            CATEGORY_VIDEOS ->
                viewModel = ViewModelProviders.of(this, PagedVideosModel.Factory(requireContext(), currentItem as? Folder)).get(PagedVideosModel::class.java) as MLPagedModel<MediaLibraryItem>

        }

        viewModel.pagedList.observe(this, Observer { items ->
            if (items != null) adapter.submitList(items)

            //headers
            var nbColumns = 1

            when (viewModel.sort) {
                Medialibrary.SORT_ALPHA -> nbColumns = 9
            }

            headerList.layoutManager = GridLayoutManager(requireActivity(), nbColumns)
            headerAdapter.sortType = viewModel.sort
            val headerItems = ArrayList<String>()
            viewModel.liveHeaders.value?.run {
                for (i in 0 until size()) {
                    headerItems.add(valueAt(i))
                }
            }
            headerAdapter.items = headerItems
            headerAdapter.notifyDataSetChanged()
        })

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        //overscan
        val hp = TvUtil.getOverscanHorizontal(requireContext())
        val vp = TvUtil.getOverscanVertical(requireContext())
        list.setPadding(list.paddingLeft + hp, list.paddingTop + vp, list.paddingRight + hp, list.paddingBottom + vp)
        headerList.setPadding(list.paddingLeft + hp, list.paddingTop + vp, list.paddingRight + hp, list.paddingBottom + vp)


        val lp = (imageButtonSettings.layoutParams as ConstraintLayout.LayoutParams)
        lp.leftMargin += hp
        lp.rightMargin += hp
        lp.topMargin += vp
        lp.bottomMargin += vp

        if (currentItem != null) {
            title.text = currentItem!!.title
        } else when (arguments?.getLong(AUDIO_CATEGORY, CATEGORY_SONGS)) {
            CATEGORY_SONGS -> title.setText(R.string.tracks)
            CATEGORY_ALBUMS -> title.setText(R.string.albums)
            CATEGORY_ARTISTS -> title.setText(R.string.artists)
            CATEGORY_GENRES -> title.setText(R.string.genres)
            CATEGORY_VIDEOS -> title.setText(R.string.videos)
        }

        val searchHeaderClick: (View) -> Unit = {
            headerListContainer.visibility = View.VISIBLE
            headerList.requestFocus()
            list.visibility = View.GONE
            animationDelegate.hideFAB()
        }

        val sortClick: (View) -> Unit = { v -> sort(v) }

        headerButton.setOnClickListener(searchHeaderClick)
        imageButtonHeader.setOnClickListener(searchHeaderClick)

        sortButton.setOnClickListener(sortClick)
        imageButtonSort.setOnClickListener(sortClick)

        nbColumns = resources.getInteger(R.integer.tv_songs_col_count)

        gridLayoutManager = object : GridLayoutManager(requireActivity(), nbColumns) {
            override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean) = false

            override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean) = false
        }

        val spacing = resources.getDimensionPixelSize(R.dimen.recycler_section_header_spacing)

        //size of an item
        val itemSize = requireActivity().getScreenWidth() / nbColumns - spacing * 2

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {

                if (position == adapter.itemCount - 1) {
                    return 1
                }
                if (viewModel.isFirstInSection(position + 1)) {

                    //calculate how many cell it must take
                    val firstSection = viewModel.getPositionForSection(position)
                    val nbItems = position - firstSection
                    if (BuildConfig.DEBUG)
                        Log.d("SongsBrowserFragment", "Position: " + position + " nb items: " + nbItems + " span: " + nbItems % nbColumns)

                    return nbColumns - nbItems % nbColumns
                }
                return 1
            }
        }

        list.layoutManager = gridLayoutManager

        adapter = AudioBrowserAdapter(MediaLibraryItem.TYPE_MEDIA, this, itemSize).apply { setTV(true) }

        list.addItemDecoration(RecyclerSectionItemGridDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_tv_height), spacing, true, nbColumns, viewModel))

        //header list
        headerListContainer.visibility = View.GONE
        headerAdapter = MediaHeaderAdapter(this)
        headerList.adapter = headerAdapter
        headerList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.bottom = 2
                outRect.top = 2
                outRect.left = 2
                outRect.right = 2
            }
        })
        setAnimator(view as ConstraintLayout)
        super.onViewCreated(view, savedInstanceState)
    }


    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        nbColumns = resources.getInteger(R.integer.tv_songs_col_count)
        gridLayoutManager.spanCount = nbColumns
        list.layoutManager = gridLayoutManager
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        list.adapter = adapter
        backgroundManager.attachToView(view)
        super.onActivityCreated(savedInstanceState)
    }


    override fun refresh() = viewModel.refresh()

    override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
        launch { TvUtil.openMediaFromPaged(requireActivity(), item, viewModel.provider) }
    }

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem) = false

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {}


    override fun onItemFocused(v: View, item: MediaLibraryItem) {
        (item as? MediaLibraryItem)?.run {
            if (currentArt == artworkMrl) return@run
            currentArt = artworkMrl
            TvUtil.updateBackground(backgroundManager, this)
        }
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    fun sort(v: View) {
        val menu = PopupMenu(v.context, v)
        menu.inflate(R.menu.sort_options)
        menu.menu.findItem(R.id.ml_menu_sortby_filename).isVisible = viewModel.canSortByFileNameName()
        menu.menu.findItem(R.id.ml_menu_sortby_length).isVisible = viewModel.canSortByDuration()
        menu.menu.findItem(R.id.ml_menu_sortby_date).isVisible = viewModel.canSortByInsertionDate() || viewModel.canSortByReleaseDate() || viewModel.canSortByLastModified()
        menu.menu.findItem(R.id.ml_menu_sortby_date).isVisible = viewModel.canSortByReleaseDate()
        menu.menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = viewModel.canSortByLastModified()
        menu.menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
        menu.setOnMenuItemClickListener(this)
        menu.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        animationDelegate.collapseExtendedFAB()
        when (item.itemId) {
            R.id.ml_menu_sortby_name -> {
                sortBy(Medialibrary.SORT_ALPHA)
                return true
            }
            R.id.ml_menu_sortby_filename -> {
                sortBy(Medialibrary.SORT_FILENAME)
                return true
            }
            R.id.ml_menu_sortby_length -> {
                sortBy(Medialibrary.SORT_DURATION)
                return true
            }
            R.id.ml_menu_sortby_date -> {
                sortBy(Medialibrary.SORT_RELEASEDATE)
                return true
            }
            R.id.ml_menu_sortby_last_modified -> {
                sortBy(Medialibrary.SORT_LASTMODIFICATIONDATE)
                return true
            }
            R.id.ml_menu_sortby_artist_name -> {
                sortBy(Medialibrary.SORT_ARTIST)
                return true
            }
            R.id.ml_menu_sortby_album_name -> {
                sortBy(Medialibrary.SORT_ALBUM)
                return true
            }
            R.id.ml_menu_sortby_number -> {
                sortBy(Medialibrary.SORT_FILESIZE)
                return super.onOptionsItemSelected(item)
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun sortBy(sort: Int) = viewModel.sort(sort)

    override fun onHeaderSelected(header: String) {
        hideHeaderSelectionScreen()

        val positionForSectionByName = viewModel.getPositionForSectionByName(header)
        if (list.getChildAt(positionForSectionByName) == null) {
            adapter.focusNext = positionForSectionByName
        } else {
            list.getChildAt(positionForSectionByName).requestFocus()
        }
        list.scrollToPosition(positionForSectionByName)
    }

    private fun hideHeaderSelectionScreen() {
        headerListContainer.visibility = View.GONE
        list.visibility = View.VISIBLE
        animationDelegate.showFAB()
    }

    private var lastDpadEventTime = 0L
    override fun onKeyPressed(keyCode: Int)= when (keyCode) {
        KEYCODE_MENU -> {
            imageButtonSettings.requestFocusFromTouch()
            animationDelegate.expandExtendedFAB()
            true
        }
        KEYCODE_BACK -> {
            if (headerListContainer.visibility == View.VISIBLE) {
                hideHeaderSelectionScreen()
                true
            } else false
        }
        /**
         * mitigate the perf issue when scrolling fast with d-pad
         */
        KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_UP -> {
            val now = System.currentTimeMillis()
            if (now - lastDpadEventTime > 200) {
                lastDpadEventTime = now
                if (BuildConfig.DEBUG) Log.d("keydown", "Keydown propagated");
                false
            } else true
        }
        else -> false
    }
}
