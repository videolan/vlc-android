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

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import org.videolan.vlc.R


class OnboardingPermissionFragment : OnboardingFragment(), View.OnClickListener {

    private val viewModel: OnboardingViewModel by activityViewModels()
    private lateinit var permissionTitle: TextView
    private lateinit var permDescription: TextView
    private lateinit var permNone: FrameLayout
    private lateinit var permMedia: FrameLayout
    private lateinit var permAll: FrameLayout
    private lateinit var permNoneImage: ImageView
    private lateinit var permMediaImage: ImageView
    private lateinit var permAllImage: ImageView

    private lateinit var oldSelected: ImageView
    private lateinit var currentlySelected: ImageView
    override fun getDefaultViewForTalkback() = permissionTitle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.onboarding_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        permissionTitle = view.findViewById(R.id.permission_title)
        permNone = view.findViewById(R.id.permNone)
        permMedia = view.findViewById(R.id.permMedia)
        permAll = view.findViewById(R.id.permAll)
        permNoneImage = view.findViewById(R.id.permNoneImage)
        permMediaImage = view.findViewById(R.id.permMediaImage)
        permAllImage = view.findViewById(R.id.permAllImage)
        permDescription = view.findViewById(R.id.permDescription)
        permNone.setOnClickListener(this)
        permMedia.setOnClickListener(this)
        permAll.setOnClickListener(this)
        currentlySelected = permAllImage

        currentlySelected.setColorFilter(ContextCompat.getColor(requireActivity(), R.color.orange500))
    }

    override fun onResume() {
        super.onResume()
        when(viewModel.permissionType) {
            PermissionType.NONE -> permNone
            PermissionType.ALL -> permAll
            PermissionType.MEDIA -> permMedia
        }.performClick()

    }

    override fun onClick(view: View) {
        view.background = ContextCompat.getDrawable(requireActivity(), R.drawable.theme_selection_rounded)
        view.animate().scaleX(1F).scaleY(1F)
        oldSelected = currentlySelected
        when (view) {
            permNone -> {
                permMedia.background = null
                permAll.background = null
                permMedia.animate().scaleX(0.8F).scaleY(0.8F)
                permAll.animate().scaleX(0.8F).scaleY(0.8F)
                permDescription.setText(R.string.permission_onboarding_no_perm)
                currentlySelected = permNoneImage
                viewModel.permissionType = PermissionType.NONE
            }
            permMedia -> {
                permDescription.setText(R.string.permission_onboarding_perm_media)
                permNone.background = null
                permAll.background = null
                permNone.animate().scaleX(0.8F).scaleY(0.8F)
                permAll.animate().scaleX(0.8F).scaleY(0.8F)
                currentlySelected = permMediaImage
                viewModel.permissionType = PermissionType.MEDIA
            }
            permAll -> {
                permDescription.setText(R.string.permission_onboarding_perm_all)
                permNone.background = null
                permMedia.background = null
                permNone.animate().scaleX(0.8F).scaleY(0.8F)
                permMedia.animate().scaleX(0.8F).scaleY(0.8F)
                currentlySelected = permAllImage
                viewModel.permissionType = PermissionType.ALL
            }
        }
        animateColor()
    }

    private fun animateColor() {

        val colorFrom = ContextCompat.getColor(requireActivity(), R.color.orange500)
        val colorTo = ContextCompat.getColor(requireActivity(), R.color.white)
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = 250 // milliseconds

        colorAnimation.addUpdateListener { animator ->

            oldSelected.setColorFilter(animator.animatedValue as Int)
        }
        colorAnimation.start()

        val colorFrom2 = ContextCompat.getColor(requireActivity(), R.color.white)
        val colorTo2 = ContextCompat.getColor(requireActivity(), R.color.orange500)
        val colorAnimation2 = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom2, colorTo2)
        colorAnimation2.duration = 250 // milliseconds

        colorAnimation2.addUpdateListener { animator ->

            currentlySelected.setColorFilter(animator.animatedValue as Int)
        }
        colorAnimation2.start()
    }

    companion object {
        fun newInstance(): OnboardingPermissionFragment {
            return OnboardingPermissionFragment()
        }
    }
}