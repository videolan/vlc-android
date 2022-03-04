/*
 * ************************************************************************
 *  OnboardingPermissionFragment.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.videolan.tools.INITIAL_PERMISSION_ASKED
import org.videolan.tools.Settings
import org.videolan.vlc.R

class OnboardingPermissionFragment : OnboardingFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.grantPermissionButton).setOnClickListener {
            onboardingFragmentListener.askPermission()
        }
        val nextButton = view.findViewById<Button>(R.id.nextButton)
        nextButton.visibility = if (Settings.getInstance(requireActivity()).getBoolean(INITIAL_PERMISSION_ASKED, false)) View.VISIBLE else View.GONE
        nextButton.setOnClickListener { onboardingFragmentListener.onNext() }
    }

    companion object {
        fun newInstance(): OnboardingPermissionFragment {
            return OnboardingPermissionFragment()
        }
    }
}