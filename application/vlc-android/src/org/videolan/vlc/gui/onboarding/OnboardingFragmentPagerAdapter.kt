package org.videolan.vlc.gui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingFragmentPagerAdapter(fragmentActivity: FragmentActivity, lifecycle: Lifecycle, private var count: Int) : FragmentStateAdapter(fragmentActivity.supportFragmentManager, lifecycle) {

    private var forceRefresh: Boolean = false
    var permissionGranted = true
    private var fragmentList: MutableList<FragmentName> = mutableListOf(
            FragmentName.WELCOME,
            FragmentName.SCAN,
            FragmentName.THEME
    )

    fun onCustomizedChanged(customizeEnabled: Boolean) {
        count = if (customizeEnabled) {
            fragmentList.add(2, FragmentName.FOLDERS)
            4
        } else {
            fragmentList.remove(FragmentName.FOLDERS)
            3
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = count

    override fun getItemId(position: Int): Long {
        if (forceRefresh && position == 1) {
            return RecyclerView.NO_ID
        }
        return fragmentList[position].ordinal.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        val fragment = FragmentName.values()[itemId.toInt()]
        return fragmentList.contains(fragment)
    }

    override fun createFragment(position: Int): Fragment {
        return when (fragmentList[position]) {
            FragmentName.WELCOME -> OnboardingWelcomeFragment.newInstance()
            FragmentName.SCAN -> {
                forceRefresh = false
                if(permissionGranted) OnboardingScanningFragment.newInstance() else OnboardingNoPermissionFragment.newInstance()
            }
            FragmentName.FOLDERS -> OnboardingFoldersFragment.newInstance()
            FragmentName.THEME -> OnboardingThemeFragment.newInstance()
        }
    }

    fun forceRefreshSecond() {
        forceRefresh = true
    }

    enum class FragmentName {
        WELCOME,
        SCAN,
        FOLDERS,
        THEME
    }
}
