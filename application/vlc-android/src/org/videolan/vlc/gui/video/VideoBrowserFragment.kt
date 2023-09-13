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
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.ContentActivity
import org.videolan.vlc.gui.PlaylistFragment
import org.videolan.vlc.gui.helpers.UiTools.addFavoritesIcon
import org.videolan.vlc.gui.helpers.UiTools.removeDrawables
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
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var viewPager: ViewPager2

    private var needToReopenSearch = false
    private var lastQuery = ""


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
        videoPagerAdapter = VideoPagerAdapter(this)
        viewPager.adapter = videoPagerAdapter
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

    override fun onTabSelected(tab: TabLayout.Tab) { }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        stopActionMode()
        needToReopenSearch = (activity as? ContentActivity)?.isSearchViewVisible() ?: false
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
        if (tabLayout == null || !::viewPager.isInitialized) return
        tabLayout?.addOnTabSelectedListener(this)
        tabLayout?.let {
            tabLayoutMediator = TabLayoutMediator(it, viewPager) { tab, position ->
                tab.text = getPageTitle(position)
            }
            tabLayoutMediator.attach()
        }
        updateTabs()
    }

    private fun getPageTitle(position: Int) = when (position) {
        0 -> getString(R.string.videos)
        else -> getString(R.string.playlists)
    }

    override fun hasFAB(): Boolean {
        return !::viewPager.isInitialized || viewPager.currentItem == 0
    }

    private fun unSetTabLayout() {
        if (tabLayout == null || !::viewPager.isInitialized) return
        tabLayout?.removeOnTabSelectedListener(this)
        tabLayoutMediator.detach()
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
                0 -> VideoGridFragment.newInstance()
                1 -> PlaylistFragment.newInstance(Playlist.Type.Video)
                else -> throw IllegalStateException("Invalid fragment index")
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
            title.text = getPageTitle(i)
            when (i) {
                0 -> if (videoGridOnlyFavorites) title.addFavoritesIcon() else title.removeDrawables()
                1 -> if (playlistOnlyFavorites) title.addFavoritesIcon() else title.removeDrawables()
            }
            tab?.customView = view
        }
    }


}

