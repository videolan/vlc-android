package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb
import org.videolan.vlc.R
import org.videolan.vlc.VLCApplication
import org.videolan.vlc.gui.browser.StorageBrowserFragment

class OnboardingFoldersFragment : Fragment(), EntryPointsEventsCb {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VLCApplication.getMLInstance().addEntryPointsEventsCb(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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


    override fun onEntryPointBanned(entryPoint: String?, success: Boolean) {
    }

    override fun onDiscoveryStarted(entryPoint: String?) {
    }

    override fun onDiscoveryCompleted(entryPoint: String?) {
    }

    override fun onEntryPointUnbanned(entryPoint: String?, success: Boolean) {
    }

    override fun onDiscoveryProgress(entryPoint: String?) {
    }

    override fun onEntryPointRemoved(entryPoint: String?, success: Boolean) {
    }

}