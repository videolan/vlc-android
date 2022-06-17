/*
 * ************************************************************************
 *  OnboardingFragment.kt
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

import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.fragment.app.Fragment
import org.videolan.vlc.util.isTalkbackIsEnabled

abstract class OnboardingFragment: Fragment() {
    lateinit var onboardingFragmentListener: OnboardingFragmentListener
    abstract fun getDefaultViewForTalkback(): View

    override fun onResume() {
        super.onResume()
        if (requireActivity().isTalkbackIsEnabled()) getDefaultViewForTalkback().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }
}

interface OnboardingFragmentListener {
    fun onNext()
    fun onDone()
}