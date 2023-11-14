package org.videolan.vlc.webserver.viewmodels

import androidx.lifecycle.ViewModel
import org.videolan.vlc.webserver.gui.webserver.onboarding.FragmentName

class WebServerOnboardingViewModel : ViewModel() {

    var currentFragment = FragmentName.WELCOME
}

