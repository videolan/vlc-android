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
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.R
import org.videolan.vlc.discourse.Discourse
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.util.findCurrentFragment

class HomeFragment : BaseFragment(), TabLayout.OnTabSelectedListener, Filterable {
    override val hasTabs = true
    private var tabLayout: TabLayout? = null
    private lateinit var viewPager: ViewPager2
    private var pendingDiscourse: Discourse? = null
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(requireContext()) }
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (position in 0 until TAB_COUNT) settings.putSingle(KEY_HOME_TAB, position)
            showPendingDiscourse()
        }
    }

    override fun getTitle() = getString(R.string.music)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?) =
        inflater.inflate(R.layout.source_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabLayout = requireActivity().findViewById(R.id.sliding_tabs)
        viewPager = view.findViewById(R.id.pager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int) = when (position) {
                ALL_TAB -> RecentlyPlayedDiscoursesFragment()
                DISCOURSE_TAB -> DiscourseFragment()
                LOCAL_TAB -> AudioBrowserFragment()
                else -> Fragment()
            }
        }
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(pageChangeCallback)
        viewPager.setCurrentItem(settings.getInt(KEY_HOME_TAB, ALL_TAB).coerceIn(0, TAB_COUNT - 1), false)
    }

    fun openDiscourse(discourse: Discourse) {
        pendingDiscourse = discourse
        viewPager.setCurrentItem(DISCOURSE_TAB, false)
        tabLayout?.getTabAt(DISCOURSE_TAB)?.let { tabLayout?.selectTab(it) }
        viewPager.post { showPendingDiscourse() }
    }

    private fun showPendingDiscourse() {
        val discourse = pendingDiscourse ?: return
        if (viewPager.currentItem != DISCOURSE_TAB) return
        val fragment = currentFragment<DiscourseFragment>() ?: return
        pendingDiscourse = null
        fragment.openDiscourse(discourse)
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

    override fun onDestroyView() {
        viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        pendingDiscourse = null
        super.onDestroyView()
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
        const val ALL_TAB = 0
        const val DISCOURSE_TAB = 1
        const val LOCAL_TAB = 2
        const val TAB_COUNT = 3
        const val KEY_HOME_TAB = "osho_home_current_tab"
    }
}
