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
import android.util.Log
import android.view.KeyEvent.*
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.song_browser.*
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.AbstractMedialibrary
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.databinding.SongBrowserBinding
import org.videolan.vlc.gui.tv.*
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserFragmentInterface
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.RefreshModel
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.SortableModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK
import org.videolan.vlc.viewmodels.tv.TvBrowserModel

private const val TAG = "MediaBrowserTvFragment"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class BaseBrowserTvFragment : Fragment(), BrowserFragmentInterface, IEventsHandler,
        PopupMenu.OnMenuItemClickListener, MediaHeaderAdapter.OnHeaderSelected,
        VerticalGridActivity.OnKeyPressedListener, CoroutineScope by MainScope() {

    abstract fun getTitle(): String
    abstract fun getCategory(): Long
    abstract fun getColumnNumber(): Int
    abstract fun provideAdapter(eventsHandler: IEventsHandler, itemSize: Int): TvItemAdapter

    lateinit var binding: SongBrowserBinding
    lateinit var viewModel: TvBrowserModel
    private var spacing: Int = 0
    abstract var adapter: TvItemAdapter
    lateinit var headerAdapter: MediaHeaderAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private var currentArt: String? = null
    private lateinit var backgroundManager: BackgroundManager
    internal lateinit var animationDelegate: MediaBrowserAnimatorDelegate
    private var setFocus = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = SongBrowserBinding.inflate(inflater, container, false)
        binding.empty = false
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundManager = BackgroundManager.getInstance(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        //overscan
        val hp = TvUtil.getOverscanHorizontal(requireContext())
        val vp = TvUtil.getOverscanVertical(requireContext())
        headerList.setPadding(list.paddingLeft + hp, list.paddingTop + vp, list.paddingRight + hp, list.paddingBottom + vp)

        val lp = (imageButtonSettings.layoutParams as ConstraintLayout.LayoutParams)
        lp.leftMargin += hp
        lp.rightMargin += hp
        lp.topMargin += vp
        lp.bottomMargin += vp

        calculateNbColumns()

        title.text = viewModel.currentItem?.let {
            if (getCategory() == TYPE_NETWORK || getCategory() == TYPE_FILE) {
                ""
            } else it.title
        } ?: getTitle()

        val searchHeaderClick: (View) -> Unit = { animationDelegate.hideFAB() }

        val sortClick: (View) -> Unit = { v ->
            animationDelegate.setVisibility(headerButton, View.GONE)
            sort(v)
        }

        headerButton.setOnClickListener(searchHeaderClick)
        imageButtonHeader.setOnClickListener(searchHeaderClick)

        sortButton.setOnClickListener(sortClick)
        imageButtonSort.setOnClickListener(sortClick)

        gridLayoutManager = object : GridLayoutManager(requireActivity(), viewModel.nbColumns) {
            override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean) = false

            override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean) = false
        }

        spacing = resources.getDimensionPixelSize(R.dimen.kl_small)

        //size of an item
        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth() - list.paddingLeft - list.paddingRight, viewModel.nbColumns, spacing)

        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {

                if (position == (adapter as RecyclerView.Adapter<*>).itemCount - 1) {
                    return 1
                }
                if (viewModel.provider.isFirstInSection(position + 1)) {

                    //calculate how many cell it must take
                    val firstSection = viewModel.provider.getPositionForSection(position)
                    val nbItems = position - firstSection
                    if (BuildConfig.DEBUG)
                        Log.d("SongsBrowserFragment", "Position: " + position + " nb items: " + nbItems + " span: " + (viewModel.nbColumns - nbItems % viewModel.nbColumns))

                    return viewModel.nbColumns - nbItems % viewModel.nbColumns
                }
                return 1
            }
        }

        list.layoutManager = gridLayoutManager

        adapter = provideAdapter(this, itemSize)

        list.addItemDecoration(RecyclerSectionItemGridDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_tv_height), spacing, true, viewModel.nbColumns, viewModel.provider))

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

    override fun onStart() {
        clearBackground(requireContext(), backgroundManager)
        super.onStart()
        setFocus = true
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }

    private fun calculateNbColumns() {
        viewModel.nbColumns = getColumnNumber()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        calculateNbColumns()
        gridLayoutManager.spanCount = viewModel.nbColumns
        if (BuildConfig.DEBUG) Log.d(TAG, "${viewModel.nbColumns}")
        list.layoutManager = gridLayoutManager
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        list.adapter = adapter as RecyclerView.Adapter<*>
        if (!backgroundManager.isAttached) {
            backgroundManager.attachToView(view)
        }
        super.onActivityCreated(savedInstanceState)
    }

    override fun refresh() = (viewModel as RefreshModel).refresh()

    override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
        if (item is AbstractMediaWrapper) {
            TvUtil.showMediaDetail(requireActivity(), item)
        }
        return true
    }

    override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}

    override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {}

    override fun onItemFocused(v: View, item: MediaLibraryItem) {
        (item as? MediaLibraryItem)?.run {
            if (currentArt == artworkMrl) return@run
            currentArt = artworkMrl
            updateBackground(v.context, backgroundManager, this)
        }
    }

    override fun onMainActionClick(v: View, position: Int, item: MediaLibraryItem) {}

    fun sort(v: View) {
        val menu = PopupMenu(v.context, v)
        menu.inflate(R.menu.sort_options)
        val canSortByFileNameName = (viewModel as SortableModel).canSortByFileNameName()
        menu.menu.findItem(R.id.ml_menu_sortby_filename).isVisible = canSortByFileNameName
        menu.menu.findItem(R.id.ml_menu_sortby_length).isVisible = (viewModel as SortableModel).canSortByDuration()
        menu.menu.findItem(R.id.ml_menu_sortby_date).isVisible = (viewModel as SortableModel).canSortByInsertionDate() || (viewModel as SortableModel).canSortByReleaseDate() || (viewModel as SortableModel).canSortByLastModified()
        menu.menu.findItem(R.id.ml_menu_sortby_date).isVisible = (viewModel as SortableModel).canSortByReleaseDate()
        menu.menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = (viewModel as SortableModel).canSortByLastModified()
        menu.menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
        menu.setOnMenuItemClickListener(this)
        menu.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        animationDelegate.collapseExtendedFAB()
        when (item.itemId) {
            R.id.ml_menu_sortby_name -> {
                sortBy(AbstractMedialibrary.SORT_ALPHA)
                return true
            }
            R.id.ml_menu_sortby_filename -> {
                sortBy(AbstractMedialibrary.SORT_FILENAME)
                return true
            }
            R.id.ml_menu_sortby_length -> {
                sortBy(AbstractMedialibrary.SORT_DURATION)
                return true
            }
            R.id.ml_menu_sortby_date -> {
                sortBy(AbstractMedialibrary.SORT_RELEASEDATE)
                return true
            }
            R.id.ml_menu_sortby_last_modified -> {
                sortBy(AbstractMedialibrary.SORT_LASTMODIFICATIONDATE)
                return true
            }
            R.id.ml_menu_sortby_artist_name -> {
                sortBy(AbstractMedialibrary.SORT_ARTIST)
                return true
            }
            R.id.ml_menu_sortby_album_name -> {
                sortBy(AbstractMedialibrary.SORT_ALBUM)
                return true
            }
            R.id.ml_menu_sortby_number -> {
                sortBy(AbstractMedialibrary.SORT_FILESIZE)
                return super.onOptionsItemSelected(item)
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun sortBy(sort: Int) = (viewModel as SortableModel).sort(sort)

    override fun onHeaderSelected(header: String) {
        hideHeaderSelectionScreen()

        val positionForSectionByName = viewModel.provider.getPositionForSectionByName(header)
        val linearLayoutManager: LinearLayoutManager = list.layoutManager as LinearLayoutManager

        val view = linearLayoutManager.findViewByPosition(positionForSectionByName)

        if (view == null) {
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
    override fun onKeyPressed(keyCode: Int) = when (keyCode) {
        KEYCODE_MENU -> {
            imageButtonSettings.requestFocusFromTouch()
            animationDelegate.expandExtendedFAB()
            true
        }
        KEYCODE_BACK -> {
            if (headerListContainer != null && headerListContainer.visibility == View.VISIBLE) {
                hideHeaderSelectionScreen()
                true
            } else false
        }
        /**
         * mitigate the perf issue when scrolling fast with d-pad
         */
        KEYCODE_DPAD_DOWN, KEYCODE_DPAD_UP -> {
            val now = System.currentTimeMillis()
            if (now - lastDpadEventTime > 200) {
                lastDpadEventTime = now
                if (BuildConfig.DEBUG) Log.d("keydown", "Keydown propagated")
                false
            } else true
        }
        else -> false
    }

    fun submitList(pagedList: Any) {
        adapter.submitList(pagedList)
        if (setFocus) {
            setFocus = false
            launch { binding.list.requestFocus() }
        }
        animationDelegate.setVisibility(imageButtonHeader, if (viewModel.provider.headers.isEmpty) View.GONE else View.VISIBLE)
        animationDelegate.setVisibility(headerButton, if (viewModel.provider.headers.isEmpty) View.GONE else View.VISIBLE)
        animationDelegate.setVisibility(headerDescription, if (viewModel.provider.headers.isEmpty) View.GONE else View.VISIBLE)
    }
}

@MainThread
@BindingAdapter("constraintRatio")
fun constraintRatio(v: View, isSquare: Boolean) {
    val constraintLayout = v.parent as? ConstraintLayout
    constraintLayout?.let {
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.setDimensionRatio(v.id, if (isSquare) "1" else "16:10")
        constraintLayout.setConstraintSet(constraintSet)

    }
}
