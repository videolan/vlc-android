package org.videolan.vlc.gui.audio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.util.findCurrentFragment

class HomeFragment : BaseFragment(), TabLayout.OnTabSelectedListener, Filterable {
    override val hasTabs = true
    private var tabLayout: TabLayout? = null
    private lateinit var viewPager: ViewPager2

    override fun getTitle() = getString(R.string.music)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        inflater.inflate(R.layout.source_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabLayout = requireActivity().findViewById(R.id.sliding_tabs)
        viewPager = view.findViewById(R.id.pager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int) = if (position == LOCAL_TAB) AudioBrowserFragment() else Fragment()
        }
        viewPager.isUserInputEnabled = false
        if (savedInstanceState == null) viewPager.setCurrentItem(LOCAL_TAB, false)
    }

    override fun onStart() {
        super.onStart()
        tabLayout?.apply {
            removeAllTabs()
            addTab(newTab().setText(R.string.all))
            addTab(newTab().setText(R.string.discourse))
            addTab(newTab().setText(R.string.local))
            addOnTabSelectedListener(this@HomeFragment)
            selectTab(getTabAt(viewPager.currentItem))
        }
    }

    override fun onStop() {
        tabLayout?.removeOnTabSelectedListener(this)
        currentFragment<BaseFragment>()?.stopActionMode()
        super.onStop()
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        viewPager.setCurrentItem(tab.position, false)
        activity?.invalidateOptionsMenu()
    }

    override fun onTabUnselected(tab: TabLayout.Tab) = currentFragment<BaseFragment>()?.stopActionMode() ?: Unit
    override fun onTabReselected(tab: TabLayout.Tab) = Unit

    private inline fun <reified T> currentFragment() =
        viewPager.findCurrentFragment(childFragmentManager) as? T

    override fun onCreateActionMode(mode: ActionMode, menu: Menu) = false
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
    override fun onDestroyActionMode(mode: ActionMode) = Unit

    override fun getFilterQuery() = currentFragment<Filterable>()?.getFilterQuery()
    override fun enableSearchOption() = currentFragment<Filterable>()?.enableSearchOption() == true
    override fun filter(query: String) = currentFragment<Filterable>()?.filter(query) ?: Unit
    override fun restoreList() = currentFragment<Filterable>()?.restoreList() ?: Unit
    override fun setSearchVisibility(visible: Boolean) = currentFragment<Filterable>()?.setSearchVisibility(visible) ?: Unit
    override fun allowedToExpand() = currentFragment<Filterable>()?.allowedToExpand() == true

    private companion object {
        const val LOCAL_TAB = 2
    }
}
