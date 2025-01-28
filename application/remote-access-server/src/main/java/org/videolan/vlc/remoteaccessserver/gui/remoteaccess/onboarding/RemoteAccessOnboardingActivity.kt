/*
 * ************************************************************************
 *  RemoteAccessOnboardingActivity.kt
 * *************************************************************************
 * Copyright © 2025 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.remoteaccessserver.gui.remoteaccess.onboarding

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.videolan.tools.setGone
import org.videolan.vlc.R
import org.videolan.vlc.remoteaccessserver.viewmodels.RemoteAccessOnboardingViewModel


class RemoteAccessOnboardingActivity : AppCompatActivity(), OnboardingFragmentListener {
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    private val viewModel: RemoteAccessOnboardingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        showFragment(viewModel.currentFragment)
    }

    fun showFragment(fragmentName:FragmentName, backward:Boolean = false) {
        val fragment = supportFragmentManager.getFragment(Bundle(), fragmentName.name) ?:
        when (fragmentName) {
            FragmentName.WELCOME -> RemoteAccessOnboardingWelcomeFragment.newInstance()
            FragmentName.HOW -> RemoteAccessOnboardingHowFragment.newInstance()
            FragmentName.SSL -> RemoteAccessOnboardingSslFragment.newInstance()
            FragmentName.OTP -> RemoteAccessOnboardingOtpFragment.newInstance()
            FragmentName.CONTENT -> RemoteAccessOnboardingContentFragment.newInstance()
        }
        (fragment as RemoteAccessOnboardingFragment).onboardingFragmentListener = this
        supportFragmentManager.commit {
            if (!backward) setCustomAnimations(
                 R.anim.anim_enter_right,
                 R.anim.anim_leave_left,
                 android.R.anim.fade_in,
                 android.R.anim.fade_out
            ) else setCustomAnimations(
                    R.anim.anim_enter_left,
                    R.anim.anim_leave_right,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            )
            replace(R.id.fragment_onboarding_placeholder, fragment, fragmentName.name)
        }
        viewModel.currentFragment = fragmentName
        skipButton = findViewById(R.id.skip_button)
        skipButton.setOnClickListener { onDone() }
        nextButton = findViewById(R.id.next_button)
        nextButton.setOnClickListener { onNext() }
    }


    override fun onDone() {
        finish()
    }

    override fun onNext() {
        when(viewModel.currentFragment) {
            FragmentName.WELCOME -> showFragment(FragmentName.HOW)
            FragmentName.HOW -> showFragment(FragmentName.SSL)
            FragmentName.SSL -> showFragment(FragmentName.OTP)
            FragmentName.OTP -> showFragment(FragmentName.CONTENT)
            else ->  onDone()
        }
        if (viewModel.currentFragment == FragmentName.CONTENT) {
            nextButton.text = getString(R.string.done)
            skipButton.setGone()
        }
    }

    fun manageNextVisibility(visible: Boolean) {
        nextButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

}

enum class FragmentName {
    WELCOME,
    HOW,
    SSL,
    OTP,
    CONTENT
}