package org.videolan.vlc.gui.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import org.videolan.vlc.util.AndroidDevices


class OnboardingViewModel() : ViewModel() {
    var scanStorages = true
    var customizeMediaFolders = false
    var permissionGranted = false
    var adapterCount = 3
    var theme = if (AndroidDevices.canUseSystemNightMode()) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO
}

fun FragmentActivity.getOnboardingModel() = ViewModelProviders.of(this).get(OnboardingViewModel::class.java)
fun Fragment.getOnboardingModel() = requireActivity().getOnboardingModel()