package org.videolan.vlc.gui.onboarding

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders


class OnboardingViewModel() : ViewModel() {
    var scanStorages = true
    var customizeMediaFolders = false
    var permissionGranted = false
    var adapterCount = 3
    var enableSystemNight = Build.VERSION.SDK_INT > Build.VERSION_CODES.P || Build.VERSION.SDK_INT == Build.VERSION_CODES.P && "samsung" == Build.MANUFACTURER.toLowerCase()
    var theme = if (enableSystemNight) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_AUTO
}

fun FragmentActivity.getOnboardingModel() = ViewModelProviders.of(this).get(OnboardingViewModel::class.java)
fun Fragment.getOnboardingModel() = requireActivity().getOnboardingModel()