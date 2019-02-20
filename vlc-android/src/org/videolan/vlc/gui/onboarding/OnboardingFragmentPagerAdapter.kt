package org.videolan.vlc.gui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter

class OnboardingFragmentPagerAdapter(private val fragmentManager: FragmentManager, private var count: Int) : FragmentStatePagerAdapter(fragmentManager) {


    private var fragments = ArrayList<Fragment>()
    private var folderFragment: OnboardingFoldersFragment = OnboardingFoldersFragment.newInstance()

    init {
        fragments.add(OnboardingWelcomeFragment.newInstance())
        val onboardingScanningFragment = OnboardingScanningFragment.newInstance()
        fragments.add(onboardingScanningFragment)
        fragments.add(OnboardingThemeFragment.newInstance())


    }

    fun onCustomizedChanged(customizeEnabled: Boolean) {
        if (customizeEnabled) {
            fragments.add(2, folderFragment)
            count = 4
        } else {
            fragments.remove(folderFragment)
            count = 3
        }

        notifyDataSetChanged()


    }


    override fun getItemPosition(obj: Any): Int {
        return PagerAdapter.POSITION_NONE
    }


    override fun getItem(position: Int): Fragment {
        return fragments[position]
    }

    override fun getCount(): Int {
        return count
    }


}