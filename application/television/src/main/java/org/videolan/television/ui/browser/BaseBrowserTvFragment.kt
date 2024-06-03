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

package org.videolan.television.ui.browser

import android.annotation.TargetApi
import android.app.Activity
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.yield
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.util.HeadersIndex
import org.videolan.television.R
import org.videolan.television.databinding.SongBrowserBinding
import org.videolan.television.ui.*
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.RecyclerSectionItemGridDecoration
import org.videolan.vlc.interfaces.BrowserFragmentInterface
import org.videolan.vlc.interfaces.IEventsHandler
import org.videolan.vlc.util.RefreshModel
import org.videolan.vlc.util.getScreenWidth
import org.videolan.vlc.viewmodels.SortableModel
import org.videolan.vlc.viewmodels.browser.TYPE_FILE
import org.videolan.vlc.viewmodels.browser.TYPE_NETWORK
import org.videolan.vlc.viewmodels.tv.TvBrowserModel

private const val TAG = "MediaBrowserTvFragment"

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
abstract class BaseBrowserTvFragment<T> : Fragment(), BrowserFragmentInterface, IEventsHandler<T>,
        PopupMenu.OnMenuItemClickListener, MediaHeaderAdapter.OnHeaderSelected,
        VerticalGridActivity.OnKeyPressedListener {

    abstract fun getTitle(): String
    abstract fun getCategory(): Long
    abstract fun getColumnNumber(): Int
    abstract fun provideAdapter(eventsHandler: IEventsHandler<T>, itemSize: Int): TvItemAdapter
    abstract fun getDisplayPrefId(): String

    private var recyclerSectionItemGridDecoration: RecyclerSectionItemGridDecoration? = null
    lateinit var binding: SongBrowserBinding
    lateinit var viewModel: TvBrowserModel<T>
    private var spacing: Int = 0
    abstract var adapter: TvItemAdapter
    lateinit var headerAdapter: MediaHeaderAdapter
    private lateinit var gridLayoutManager: LinearLayoutManager
    private var currentArt: String? = null
    private lateinit var backgroundManager: BackgroundManager
    internal lateinit var animationDelegate: MediaBrowserAnimatorDelegate
    private var setFocus = true
    private var inGrid = true
    protected var restarted = false
        private set
    private var previouslySelectedItem:Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = SongBrowserBinding.inflate(inflater, container, false)
        binding.emptyLoading.state = EmptyLoadingState.NONE
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
        binding.headerList.setPadding(binding.list.paddingLeft + hp, binding.list.paddingTop + vp, binding.list.paddingRight + hp, binding.list.paddingBottom + vp)

        val lp = (binding.imageButtonSettings.layoutParams as ConstraintLayout.LayoutParams)
        lp.leftMargin += hp
        lp.rightMargin += hp
        lp.topMargin += vp
        lp.bottomMargin += vp

        calculateNbColumns()

        binding.title.text = viewModel.currentItem?.let {
            when (it) {
                is MediaLibraryItem -> if (getCategory() == TYPE_NETWORK || getCategory() == TYPE_FILE) {
                    ""
                } else it.title
                else -> ""
            }

        } ?: getTitle()

        val searchHeaderClick: (View) -> Unit = { animationDelegate.hideFAB() }
        val displayClick: (View) -> Unit = {
            changeDisplayMode()
        }

        val sortClick: (View) -> Unit = { v ->
            sort(v)
        }

        binding.headerButton.setOnClickListener(searchHeaderClick)
        binding.imageButtonHeader.setOnClickListener(searchHeaderClick)

        binding.sortButton.setOnClickListener(sortClick)
        binding.imageButtonSort.setOnClickListener(sortClick)

        binding.displayButton.setOnClickListener(displayClick)
        binding.imageButtonDisplay.setOnClickListener(displayClick)

        spacing = resources.getDimensionPixelSize(R.dimen.kl_small)
        recyclerSectionItemGridDecoration = RecyclerSectionItemGridDecoration(resources.getDimensionPixelSize(R.dimen.recycler_section_header_tv_height), spacing, spacing, true, viewModel.nbColumns, viewModel.provider)
        inGrid = Settings.getInstance(requireActivity()).getBoolean(getDisplayPrefId(), true)
        setupDisplayIcon()
        setupLayoutManager()


        //size of an item
        val itemSize = RecyclerSectionItemGridDecoration.getItemSize(requireActivity().getScreenWidth() - binding.list.paddingLeft - binding.list.paddingRight, viewModel.nbColumns, spacing, spacing)

        adapter = provideAdapter(this, itemSize)
        adapter.displaySwitch(inGrid)

        recyclerSectionItemGridDecoration?.let { binding.list.addItemDecoration(it)}

        //header list
        binding.headerListContainer.visibility = View.GONE
        headerAdapter = MediaHeaderAdapter(this)
        binding.headerList.adapter = headerAdapter
        binding.headerList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.bottom = 2
                outRect.top = 2
                outRect.left = 2
                outRect.right = 2
            }
        })
        setAnimator(view as ConstraintLayout)
        binding.list.adapter = adapter as RecyclerView.Adapter<*>
        if (!backgroundManager.isAttached) {
            backgroundManager.attachToView(view)
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun setupLayoutManager() {
        if (inGrid) {
            gridLayoutManager = object : GridLayoutManager(requireActivity(), viewModel.nbColumns) {
                override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean) = false

                override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean) = false
                override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
                    super.onLayoutChildren(recycler, state)
                    if (previouslySelectedItem != -1 && binding.list.adapter?.itemCount ?: 0 > previouslySelectedItem) {
                        scrollToPosition(previouslySelectedItem)
                        binding.list.findViewHolderForLayoutPosition(previouslySelectedItem)?.let { holder ->
                            (holder as MediaTvItemAdapter.AbstractMediaItemViewHolder<*>).getView().requestFocus()
                            previouslySelectedItem = -1
                        }
                    }
                }
            }
            (gridLayoutManager as? GridLayoutManager)?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {

                    if (position == (adapter as RecyclerView.Adapter<*>).itemCount - 1) {
                        return 1
                    }
                    if (viewModel.provider.isFirstInSection(position + 1) && Settings.showHeaders) {

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
        } else {
            gridLayoutManager = object : LinearLayoutManager(requireActivity()) {
                override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
                    super.onLayoutChildren(recycler, state)
                    if (previouslySelectedItem != -1 && binding.list.adapter?.itemCount ?: 0 > previouslySelectedItem) {
                        scrollToPosition(previouslySelectedItem)
                        binding.list.findViewHolderForLayoutPosition(previouslySelectedItem)?.let { holder ->
                            (holder as MediaTvItemAdapter.AbstractMediaItemViewHolder<*>).getView().requestFocus()
                            previouslySelectedItem = -1
                        }
                    }
                }
            }
        }
        recyclerSectionItemGridDecoration?.isList = !inGrid
        binding.list.layoutManager = gridLayoutManager
    }

    override fun onPause() {
        val lm = binding.list.layoutManager as LinearLayoutManager
        previouslySelectedItem = lm.focusedChild?.let { lm.getPosition(it) } ?: 0
        super.onPause()
    }


    fun updateHeaders(it: HeadersIndex) {
        val headerItems = ArrayList<String>()
        it.run {
            for (i in 0 until size()) {
                headerItems.add(valueAt(i))
            }
        }
        headerAdapter.items = headerItems
        headerAdapter.notifyDataSetChanged()
    }

    private fun changeDisplayMode() {
        inGrid = !inGrid
        Settings.getInstance(requireActivity()).putSingle(getDisplayPrefId(), inGrid)
        adapter.displaySwitch(inGrid)
        setupDisplayIcon()
        setupLayoutManager()
    }

    private fun setupDisplayIcon() {
        binding.imageButtonDisplay.setImageResource(if (inGrid) R.drawable.ic_fabtvmini_list else R.drawable.ic_fabtvmini_grid)
        binding.displayButton.setImageResource(if (inGrid) R.drawable.ic_tv_browser_list else R.drawable.ic_tv_browser_grid)
        binding.displayDescription.setText(if (inGrid) R.string.display_in_list else R.string.display_in_grid)
        binding.displayButton.contentDescription = getString(if (inGrid) R.string.display_in_list else R.string.display_in_grid)
        binding.imageButtonDisplay.contentDescription = getString(if (inGrid) R.string.display_in_list else R.string.display_in_grid)
    }

    override fun onStart() {
        clearBackground(requireContext(), backgroundManager)
        super.onStart()
        setFocus = true
    }

    override fun onStop() {
        super.onStop()
        restarted = true
    }

    private fun calculateNbColumns() {
        viewModel.nbColumns = getColumnNumber()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        calculateNbColumns()
        (gridLayoutManager as? GridLayoutManager)?.spanCount = viewModel.nbColumns
        if (BuildConfig.DEBUG) Log.d(TAG, "${viewModel.nbColumns}")
        binding.list.layoutManager = gridLayoutManager
    }

    override fun refresh() = (viewModel as RefreshModel).refresh()

    override fun onLongClick(v: View, position: Int, item: T): Boolean {
        if (item is MediaWrapper) {
            TvUtil.showMediaDetail(requireActivity(), item)
        }
        return true
    }

    override fun onCtxClick(v: View, position: Int, item: T) {}

    override fun onUpdateFinished(adapter: RecyclerView.Adapter<*>) {}

    override fun onImageClick(v: View, position: Int, item: T) {}

    override fun onItemFocused(v: View, item: T) {
        (item as? MediaLibraryItem)?.run {
            if (currentArt == artworkMrl) return@run
            currentArt = artworkMrl
            lifecycleScope.updateBackground(v.context as Activity, backgroundManager, this)
        }
    }

    override fun onMainActionClick(v: View, position: Int, item: T) {}

    private fun sort(v: View) {
        val menu = PopupMenu(v.context, v)
        menu.inflate(R.menu.sort_options)
        val canSortByFileNameName = (viewModel as SortableModel).canSortByFileNameName()
        menu.menu.findItem(R.id.ml_menu_sortby_filename).isVisible = canSortByFileNameName
        menu.menu.findItem(R.id.ml_menu_sortby_length).isVisible = (viewModel as SortableModel).canSortByDuration()
        menu.menu.findItem(R.id.ml_menu_sortby_insertion_date).isVisible = (viewModel as SortableModel).canSortByInsertionDate()
        menu.menu.findItem(R.id.ml_menu_sortby_date).isVisible = (viewModel as SortableModel).canSortByReleaseDate()
        menu.menu.findItem(R.id.ml_menu_sortby_last_modified).isVisible = (viewModel as SortableModel).canSortByLastModified()
        menu.menu.findItem(R.id.ml_menu_sortby_number).isVisible = false
        menu.setOnMenuItemClickListener(this)
        menu.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (animationDelegate.isFABExpanded()) animationDelegate.collapseExtendedFAB()
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

    private fun sortBy(sort: Int) = (viewModel as SortableModel).sort(sort)

    override fun onHeaderSelected(header: String) {
        hideHeaderSelectionScreen()

        val positionForSectionByName = viewModel.provider.getPositionForSectionByName(header)
        val linearLayoutManager: LinearLayoutManager = binding.list.layoutManager as LinearLayoutManager

        val view = linearLayoutManager.findViewByPosition(positionForSectionByName)

        if (view == null) {
            adapter.focusNext = positionForSectionByName
        } else {
            binding.list.getChildAt(positionForSectionByName).requestFocus()
        }
        binding.list.scrollToPosition(positionForSectionByName)
    }

    private fun hideHeaderSelectionScreen() {
        binding.headerListContainer.visibility = View.GONE
        binding.list.visibility = View.VISIBLE
        animationDelegate.showFAB()
    }

    private var lastDpadEventTime = 0L
    override fun onKeyPressed(keyCode: Int) = when (keyCode) {
        KEYCODE_MENU -> {
            binding.imageButtonSettings.requestFocusFromTouch()
            animationDelegate.expandExtendedFAB()
            true
        }
        KEYCODE_BACK -> {
            if (binding.headerListContainer.visibility == View.VISIBLE) {
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
        KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT -> {
            if (!inGrid && binding.list.hasFocus() && animationDelegate.isScrolled()) {
                binding.imageButtonSettings.requestFocusFromTouch()
                true
            } else false
        }
        else -> false
    }

    fun submitList(pagedList: Any) {
        adapter.submitList(pagedList)
        if (setFocus) {
            setFocus = false
            lifecycleScope.launchWhenStarted {
                yield()
                // If there is a previous selection, no need to request focus on the list here
                // as it is requested for the specific item in the onLayoutChildren override above.
                // This stops a flicker when coming back from playback.
                if (previouslySelectedItem == -1) {
                    binding.list.requestFocus()
                }
            }
        }
        animationDelegate.setVisibility(binding.imageButtonHeader, if (viewModel.provider.headers.isEmpty) View.GONE else View.VISIBLE)
        animationDelegate.setVisibility(binding.headerButton, if (viewModel.provider.headers.isEmpty) View.GONE else View.VISIBLE)
        animationDelegate.setVisibility(binding.headerDescription, if (viewModel.provider.headers.isEmpty) View.GONE else View.VISIBLE)
    }
}
