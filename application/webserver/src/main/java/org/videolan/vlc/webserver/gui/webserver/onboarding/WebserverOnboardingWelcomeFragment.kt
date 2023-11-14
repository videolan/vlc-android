package org.videolan.vlc.webserver.gui.webserver.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.videolan.vlc.webserver.R

class WebserverOnboardingWelcomeFragment : WebserverOnboardingFragment() {
    private lateinit var titleView: TextView

    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.webserver_onboarding_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
    }

    companion object {
        fun newInstance(): WebserverOnboardingWelcomeFragment {
            return WebserverOnboardingWelcomeFragment()
        }
    }
}