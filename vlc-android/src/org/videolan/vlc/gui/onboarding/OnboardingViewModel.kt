package org.videolan.vlc.gui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders


class OnboardingViewModel() : ViewModel() {
    var scanStorages = true
    var customizeMediaFolders = false
    var permissionGranted = false
    var adapterCount = 3
}

fun FragmentActivity.getOnboardingModel() = ViewModelProviders.of(this).get(OnboardingViewModel::class.java)
fun Fragment.getOnboardingModel() = requireActivity().getOnboardingModel()