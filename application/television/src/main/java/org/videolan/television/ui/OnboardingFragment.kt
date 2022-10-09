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

package org.videolan.television.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.app.OnboardingSupportFragment
import org.videolan.resources.TV_MAIN_ACTIVITY
import org.videolan.resources.util.canReadStorage
import org.videolan.television.R
import org.videolan.tools.KEY_TV_ONBOARDING_DONE
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import org.videolan.vlc.util.Permissions

class OnboardingFragment : OnboardingSupportFragment() {
    override fun getPageCount() = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startButtonText = getString(R.string.start_vlc)
        arrowColor = ContextCompat.getColor(requireActivity(), R.color.white)
    }

    override fun getPageTitle(pageIndex: Int) = when (pageIndex) {
        0 -> getString(R.string.welcome_title)
        1 -> getString(R.string.onboarding_scan_title)
        else -> getString(R.string.onboarding_all_set)
    }

    override fun getPageDescription(pageIndex: Int) = when (pageIndex) {
        0 -> getString(R.string.welcome_subtitle)
        1 -> getString(R.string.permission_media)
        else -> if (canReadStorage(requireActivity()))getString(R.string.onboarding_permission_given) else "${getString(R.string.permission_expanation_no_allow)}\n${getString(R.string.permission_expanation_allow)}"
    }


    override fun onCreateBackgroundView(inflater: LayoutInflater?, container: ViewGroup?) =
        View(activity).apply { setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.onboarding_grey)) }

    override fun onCreateContentView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        val view = ImageView(requireActivity())
        view.setImageResource(R.drawable.ic_launcher_foreground)
        return view
    }

    override fun onPageChanged(newPage: Int, previousPage: Int) {
        if (newPage == 1) {
            if (!Permissions.canReadStorage(requireActivity())) Permissions.checkReadStoragePermission(requireActivity())
        }
        super.onPageChanged(newPage, previousPage)
    }

    override fun onFinishFragment() {
        super.onFinishFragment()
        Settings.getInstance(requireActivity()).putSingle(KEY_TV_ONBOARDING_DONE, true)
        requireActivity().finish()
        val intent = Intent(Intent.ACTION_VIEW).setClassName(requireActivity(), TV_MAIN_ACTIVITY)
        startActivity(intent)
    }

    override fun onCreateForegroundView(inflater: LayoutInflater?, container: ViewGroup?) = null
}
