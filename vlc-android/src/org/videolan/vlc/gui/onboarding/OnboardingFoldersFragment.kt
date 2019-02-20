package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.StorageBrowserFragment

class OnboardingFoldersFragment : Fragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_folders, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val storageBrowserFragment = StorageBrowserFragment()
        //todo : when opening an empty directory, the root is shown again + ariane is not clickable
        childFragmentManager.beginTransaction()
                .replace(R.id.fragment_placeholder, storageBrowserFragment)
                .commit()

    }

    companion object {
        fun newInstance(): OnboardingFoldersFragment {
            return OnboardingFoldersFragment()
        }
    }
}