/*
 * ************************************************************************
 *  VideoBrowserFragment.kt
 * *************************************************************************
 * Copyright © 2022 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.video

import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.PlaylistFragment
import org.videolan.vlc.interfaces.Filterable


/**
 * Fragment containing the video viewpager
 *
 */
class VideoBrowserFragment : BaseFragment(), TabLayout.OnTabSelectedListener, ViewPager.OnPageChangeListener, Filterable {
    override fun getTitle() = getString(R.string.videos)

    private lateinit var videoPagerAdapter: VideoPagerAdapter
    private lateinit var layoutOnPageChangeListener: TabLayout.TabLayoutOnPageChangeListener
    override val hasTabs = true
    private var tabLayout: TabLayout? = null
    private lateinit var viewPager: ViewPager

    private var needToReopenSearch = false
    private var lastQuery = ""

    private val tcl = TabLayout.TabLayoutOnPageChangeListener(tabLayout)

    var videoGridOnlyFavorites: Boolean = false
        set(value) {
            field = value
            updateTabs()
        }
    var playlistOnlyFavorites: Boolean = false
        set(value) {
            field = value
            updateTabs()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.video_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabLayout = requireActivity().findViewById(R.id.sliding_tabs)
        viewPager = view.findViewById(R.id.pager)
        videoPagerAdapter = VideoPagerAdapter(fragmentManager = childFragmentManager)
        viewPager.adapter = videoPagerAdapter
    }

    override fun onStart() {
        setupTabLayout()
        super.onStart()
    }

    override fun onStop() {
        unSetTabLayout()
        super.onStop()
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

    override fun onDestroyActionMode(mode: ActionMode?) {}

    override fun onTabSelected(tab: TabLayout.Tab) { }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        stopActionMode()
        needToReopenSearch = (activity as? ContentActivity)?.isSearchViewVisible() ?: false
        lastQuery = (activity as? ContentActivity)?.getCurrentQuery() ?: ""
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        reopenSearchIfNeeded()
    }

    /**
     * Reopens the search if the tab is changed
     *
     */
    private fun reopenSearchIfNeeded() {
        if (needToReopenSearch) {
            (activity as? ContentActivity)?.openSearchView()
            (activity as? ContentActivity)?.setCurrentQuery(lastQuery)
            lastQuery = ""
            needToReopenSearch = false
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        tcl.onPageScrolled(position, positionOffset, positionOffsetPixels)
    }

    override fun onPageScrollStateChanged(state: Int) {
        tcl.onPageScrollStateChanged(state)
    }

    override fun onPageSelected(position: Int) {
        if (position == 0) setFabPlayVisibility(true)
        manageFabNeverShow()
    }

    private fun setupTabLayout() {
        if (tabLayout == null || !::viewPager.isInitialized) return
        tabLayout?.setupWithViewPager(viewPager)
        if (!::layoutOnPageChangeListener.isInitialized) layoutOnPageChangeListener = TabLayout.TabLayoutOnPageChangeListener(tabLayout)
        viewPager.addOnPageChangeListener(layoutOnPageChangeListener)
        tabLayout?.addOnTabSelectedListener(this)
        viewPager.addOnPageChangeListener(this)
    }

    override fun hasFAB(): Boolean {
        return !::viewPager.isInitialized || viewPager.currentItem == 0
    }

    private fun unSetTabLayout() {
        if (tabLayout != null || !::viewPager.isInitialized) return
        viewPager.removeOnPageChangeListener(layoutOnPageChangeListener)
        tabLayout?.removeOnTabSelectedListener(this)
        viewPager.removeOnPageChangeListener(this)
    }

    /**
     * View pager adapter hosting the video an playlist fragments
     *
     * @property fragmentManager the [FragmentManager] to be used
     */
    inner class VideoPagerAdapter(val fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        // Returns the fragment to display for that page
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> VideoGridFragment.newInstance()
                1 -> PlaylistFragment.newInstance(Playlist.Type.VideoOnly)
                else -> throw IllegalStateException("Invalid fragment index")
            }
        }

        override fun getCount() = 2

        // Returns the page title for the top indicator
        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> getString(R.string.videos)
                else -> getString(R.string.playlists)
            }
        }
    }

    /**
     * Finds current resumed fragment
     *
     * @return the current shown fragment
     */
    private fun getCurrentFragment() = childFragmentManager.fragments.find{ it.isResumed }

    override fun getFilterQuery() = try {
        (getCurrentFragment() as? Filterable)?.getFilterQuery()
    } catch (e: Exception) {
        null
    }

    override fun enableSearchOption() = (getCurrentFragment() as? Filterable)?.enableSearchOption() ?: false

    override fun filter(query: String) {
        (getCurrentFragment() as? Filterable)?.filter(query)
    }

    override fun restoreList() {
        (getCurrentFragment() as? Filterable)?.restoreList()
    }

    override fun setSearchVisibility(visible: Boolean) {
        (getCurrentFragment() as? Filterable)?.setSearchVisibility(visible)
    }

    override fun allowedToExpand() = (getCurrentFragment() as? Filterable)?.allowedToExpand() ?: false
    private fun updateTabs() {
        for (i in 0 until tabLayout!!.tabCount) {
            val tab = tabLayout!!.getTabAt(i)
            val view = tab?.customView ?: View.inflate(requireActivity(), R.layout.audio_tab, null)
            val title = view.findViewById<TextView>(R.id.tab_title)
            val icon = view.findViewById<ImageView>(R.id.tab_icon)
            title.text = videoPagerAdapter.getPageTitle(i)
            when (i) {
                0 -> if (videoGridOnlyFavorites) icon.setVisible() else icon.setGone()
                1 -> if (playlistOnlyFavorites) icon.setVisible() else icon.setGone()
            }
            tab?.customView = view
        }
    }


}

