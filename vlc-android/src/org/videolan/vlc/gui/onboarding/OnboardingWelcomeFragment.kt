package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.videolan.vlc.R
import org.videolan.vlc.util.Permissions

class OnboardingWelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Permissions.canReadStorage(view.context)) view.findViewById<View>(R.id.textView3).visibility = View.GONE
    }

    companion object {
        fun newInstance(): OnboardingWelcomeFragment {
            return OnboardingWelcomeFragment()
        }
    }
}