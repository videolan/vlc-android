package org.videolan.television.ui.preferences

import androidx.leanback.preference.LeanbackPreferenceFragment
import androidx.leanback.preference.LeanbackSettingsFragment
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragment
import androidx.preference.PreferenceFragment
import androidx.preference.PreferenceScreen


class TvSettings : LeanbackSettingsFragment() {

    override fun onPreferenceStartInitialScreen() {
        startPreferenceFragment(PreferencesFragment())
    }

    override fun onPreferenceStartScreen(caller: PreferenceFragment, pref: PreferenceScreen) = false

    override fun onPreferenceStartFragment(caller: PreferenceFragment, pref: Preference): Boolean {
        val f = LeanbackPreferenceFragment.instantiate(activity, pref.fragment, pref.extras)
        f.setTargetFragment(caller, 0)
        if (f is PreferenceFragment || f is PreferenceDialogFragment) startPreferenceFragment(f)
        else startImmersiveFragment(f)
        return true
    }
}