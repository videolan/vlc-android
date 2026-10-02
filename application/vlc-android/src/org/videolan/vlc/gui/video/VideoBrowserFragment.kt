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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.util.findCurrentFragment


/**
 * Fragment containing the video viewpager
 *
 */
class VideoBrowserFragment : BaseFragment(), TabLayout.OnTabSelectedListener, Filterable {
    override fun getTitle() = getString(R.string.videos)

    private lateinit var videoPagerAdapter: VideoPagerAdapter
    override val hasTabs = true
    private var tabLayout: TabLayout? = null
    private lateinit var viewPager: ViewPager2

    private var needToReopenSearch = false
    private var lastQuery = ""


    var videoGridOnlyFavorites: Boolean = false
        set(value) { field = value }
    var playlistOnlyFavorites: Boolean = false
        set(value) { field = value }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.video_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabLayout = requireActivity().findViewById(R.id.sliding_tabs)
        viewPager = view.findViewById(R.id.pager)
        videoPagerAdapter = VideoPagerAdapter(this)
        viewPager.adapter = videoPagerAdapter
        viewPager.isUserInputEnabled = false
        if (savedInstanceState == null) viewPager.setCurrentItem(LOCAL_TAB, false)
    }

    override fun onStart() {
        setupTabLayout()
        super.onStart()
    }

    override fun onStop() {
        unSetTabLayout()
        (viewPager.findCurrentFragment(childFragmentManager) as? BaseFragment)?.stopActionMode()
        super.onStop()
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

    override fun onDestroyActionMode(mode: ActionMode?) {}

    override fun onTabSelected(tab: TabLayout.Tab) {
        viewPager.setCurrentItem(tab.position, false)
        activity?.invalidateOptionsMenu()
        setFabPlayVisibility(hasFAB())
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        stopActionMode()
        needToReopenSearch = (activity as? ContentActivity)?.isSearchViewVisible() == true
        lastQuery = (activity as? ContentActivity)?.getCurrentQuery() ?: ""
        if (isStarted()) (viewPager.findCurrentFragment(childFragmentManager) as? BaseFragment)?.stopActionMode()
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

    private fun setupTabLayout() {
        tabLayout?.apply {
            removeAllTabs()
            addTab(newTab().setText(R.string.all))
            addTab(newTab().setText(R.string.local))
            addOnTabSelectedListener(this@VideoBrowserFragment)
            selectTab(getTabAt(viewPager.currentItem))
        }
    }

    override fun hasFAB(): Boolean {
        return true
    }

    private fun unSetTabLayout() {
        if (tabLayout == null || !::viewPager.isInitialized) return
        tabLayout?.removeOnTabSelectedListener(this)
    }

    /**
     * View pager adapter hosting the video and playlist fragments
     *
     * @property fa the [FragmentActivity] to be used
     */
    inner class VideoPagerAdapter(fa: VideoBrowserFragment) : FragmentStateAdapter(fa) {

        override fun getItemCount() = 2

        // Returns the fragment to display for that page
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                LOCAL_TAB -> VideoGridFragment.newInstance()
                else -> Fragment()
            }
        }
    }

    /**
     * Finds current fragment
     *
     * @return the current shown fragment
     */
    private fun getCurrentFragment() = childFragmentManager.findFragmentByTag("f" + viewPager.currentItem)

    override fun getFilterQuery() = try {
        (getCurrentFragment() as? Filterable)?.getFilterQuery()
    } catch (e: Exception) {
        null
    }

    override fun enableSearchOption() = (getCurrentFragment() as? Filterable)?.enableSearchOption() == true

    override fun filter(query: String) {
        (getCurrentFragment() as? Filterable)?.filter(query)
    }

    override fun restoreList() {
        (getCurrentFragment() as? Filterable)?.restoreList()
    }

    override fun setSearchVisibility(visible: Boolean) {
        (getCurrentFragment() as? Filterable)?.setSearchVisibility(visible)
    }

    override fun allowedToExpand() = (getCurrentFragment() as? Filterable)?.allowedToExpand() == true

    private companion object {
        const val LOCAL_TAB = 1
    }

}
