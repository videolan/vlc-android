package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.videolan.vlc.R

class OnboardingWelcomeFragment : OnboardingFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.startButton).setOnClickListener {
            onboardingFragmentListener.onNext()
        }
    }

    companion object {
        fun newInstance(): OnboardingWelcomeFragment {
            return OnboardingWelcomeFragment()
        }
    }
}