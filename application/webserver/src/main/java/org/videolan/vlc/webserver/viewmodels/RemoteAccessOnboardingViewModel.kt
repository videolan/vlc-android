package org.videolan.vlc.webserver.viewmodels

import androidx.lifecycle.ViewModel
import org.videolan.vlc.webserver.gui.remoteaccess.onboarding.FragmentName

class RemoteAccessOnboardingViewModel : ViewModel() {

    var currentFragment = FragmentName.WELCOME
}

