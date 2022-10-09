package org.videolan.vlc.gui.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import org.videolan.resources.AndroidDevices

class OnboardingViewModel : ViewModel() {
    var permissionAlreadyAsked: Boolean = false
    var notificationPermissionAlreadyAsked: Boolean = false
    var scanStorages = true
    var permissionType: PermissionType = PermissionType.ALL

    var theme = if (AndroidDevices.canUseSystemNightMode()) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO
    var currentFragment = FragmentName.WELCOME
}

enum class PermissionType {
    NONE, MEDIA, ALL
}
