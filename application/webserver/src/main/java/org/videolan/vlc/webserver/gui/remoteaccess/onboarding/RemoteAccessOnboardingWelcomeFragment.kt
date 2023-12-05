package org.videolan.vlc.webserver.gui.remoteaccess.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.videolan.vlc.webserver.R

class RemoteAccessOnboardingWelcomeFragment : RemoteAccessOnboardingFragment() {
    private lateinit var titleView: TextView

    override fun getDefaultViewForTalkback() = titleView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.remote_access_onboarding_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        titleView = view.findViewById(R.id.welcome_title)
    }

    companion object {
        fun newInstance(): RemoteAccessOnboardingWelcomeFragment {
            return RemoteAccessOnboardingWelcomeFragment()
        }
    }
}