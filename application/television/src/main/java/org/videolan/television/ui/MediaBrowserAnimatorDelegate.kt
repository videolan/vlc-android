/*
 * ************************************************************************
 *  MediaBrowserTvFragment.kt
 * *************************************************************************
 *  Copyright © 2019 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.television.ui

import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import org.videolan.television.R
import org.videolan.television.databinding.SongBrowserBinding
import org.videolan.television.ui.browser.BaseBrowserTvFragment
import org.videolan.vlc.BuildConfig

internal class MediaBrowserAnimatorDelegate(val binding: SongBrowserBinding, private val cl: ConstraintLayout) : RecyclerView.OnScrollListener(), View.OnFocusChangeListener {

    private val scrolledUpConstraintSet = ConstraintSet()
    private val scrolledDownFABCollapsedConstraintSet = ConstraintSet()
    private val scrolledDownFABExpandedConstraintSet = ConstraintSet()
    private val headerVisibleConstraintSet = ConstraintSet()

    private val constraintSets = arrayOf(scrolledUpConstraintSet, scrolledDownFABCollapsedConstraintSet, scrolledDownFABExpandedConstraintSet, headerVisibleConstraintSet)

    private val transition = ChangeBounds().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private val fakeToolbar = binding.toolbar
    private val fabSettings = binding.imageButtonSettings
    private val fabHeader = binding.imageButtonHeader
    private val fabFavorite = binding.imageButtonFavorite
    private val fabSort = binding.imageButtonSort
    private val fabDisplay = binding.imageButtonDisplay

    private var currenstate = MediaBrowserState.SCROLLED_UP
        set(value) {
            //avoid playing the transition again
            if (value == field) {
                return
            }
            TransitionManager.beginDelayedTransition(cl, transition)
            when (value) {
                MediaBrowserState.SCROLLED_UP -> scrolledUpConstraintSet
                MediaBrowserState.SCROLLED_DOWN_FAB_COLLAPSED -> scrolledDownFABCollapsedConstraintSet
                MediaBrowserState.SCROLLED_DOWN_FAB_EXPANDED -> scrolledDownFABExpandedConstraintSet
                MediaBrowserState.HEADER_VISIBLE -> headerVisibleConstraintSet

            }.applyTo(cl)
            field = value
        }

    enum class MediaBrowserState {
        /**
         * Initial state : Visible fake toolbar + no fab
         */
        SCROLLED_UP,
        /**
         * Scrolled state with collapsed FAB
         */
        SCROLLED_DOWN_FAB_COLLAPSED,
        /**
         * Scrolled state with expanded FAB
         */
        SCROLLED_DOWN_FAB_EXPANDED,
        /**
         * Header visible : no toolbar no FAB
         */
        HEADER_VISIBLE
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        currenstate = if (recyclerView.computeVerticalScrollOffset() > 0) {
            MediaBrowserState.SCROLLED_DOWN_FAB_COLLAPSED
        } else {
            MediaBrowserState.SCROLLED_UP
        }
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        //Show action labels when needed
        val view = when (v) {
            binding.displayButton -> binding.displayDescription
            binding.headerButton -> binding.headerDescription
            binding.sortButton -> binding.sortDescription
            binding.favoriteButton -> binding.favoriteDescription
            else -> null
        }

        if (BuildConfig.DEBUG) Log.d(this::class.java.simpleName, "Focusing: $hasFocus $view")

        view?.animate()?.cancel()
        view?.animate()?.alpha(if (hasFocus) 1f else 0f)

        // FAB has to be expanded / collapsed when its focus changes
        if (currenstate != MediaBrowserState.SCROLLED_UP) {
            if (!fabSettings.hasFocus() && !fabSort.hasFocus() && !fabDisplay.hasFocus() && !fabDisplay.hasFocus() && !fabHeader.hasFocus() && !fabFavorite.hasFocus() && currenstate != MediaBrowserState.HEADER_VISIBLE) {
                collapseExtendedFAB()
            }
            if (v == fabSettings && hasFocus) {
                expandExtendedFAB()
            }
        }
    }

    fun isFABExpanded() = currenstate == MediaBrowserState.SCROLLED_DOWN_FAB_EXPANDED

    internal fun expandExtendedFAB() {
        currenstate = MediaBrowserState.SCROLLED_DOWN_FAB_EXPANDED
    }

    internal fun collapseExtendedFAB() {
        currenstate = MediaBrowserState.SCROLLED_DOWN_FAB_COLLAPSED
    }

    internal fun hideFAB() {
        currenstate = MediaBrowserState.HEADER_VISIBLE
    }

    internal fun showFAB() {
        currenstate = MediaBrowserState.SCROLLED_DOWN_FAB_COLLAPSED
    }

    //FIXME it doesn't work. WHY???
    fun setVisibility(view: View, visibility: Int) {

        constraintSets.forEach {
            it.setVisibility(view.id, visibility)
        }

        view.visibility = visibility
    }

    fun isScrolled() = currenstate == MediaBrowserState.SCROLLED_DOWN_FAB_COLLAPSED

    init {

        // Scrolled up is the state already described in the XML. We clone it to be able to reuse it.
        scrolledUpConstraintSet.clone(cl)

        /* See MediaBrowserState.SCROLLED_DOWN_FAB_COLLAPSED */
        scrolledDownFABCollapsedConstraintSet.clone(cl)

        //Reset margins
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.sortButton, ConstraintSet.START, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.sortButton, ConstraintSet.END, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.sortButton, ConstraintSet.TOP, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.sortButton, ConstraintSet.BOTTOM, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.displayButton, ConstraintSet.START, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.displayButton, ConstraintSet.END, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.displayButton, ConstraintSet.TOP, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.displayButton, ConstraintSet.BOTTOM, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.headerButton, ConstraintSet.START, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.headerButton, ConstraintSet.END, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.headerButton, ConstraintSet.TOP, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.headerButton, ConstraintSet.BOTTOM, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.favoriteButton, ConstraintSet.START, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.favoriteButton, ConstraintSet.END, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.favoriteButton, ConstraintSet.TOP, 0)
        scrolledDownFABCollapsedConstraintSet.setMargin(R.id.favoriteButton, ConstraintSet.BOTTOM, 0)

        //New constraints for toolbar buttons to make them move to the FAB
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortButton, ConstraintSet.START, R.id.imageButtonSettings, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortButton, ConstraintSet.END, R.id.imageButtonSettings, ConstraintSet.END)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortButton, ConstraintSet.TOP, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortButton, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.BOTTOM)

        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayButton, ConstraintSet.START, R.id.imageButtonSettings, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayButton, ConstraintSet.END, R.id.imageButtonSettings, ConstraintSet.END)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayButton, ConstraintSet.TOP, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayButton, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.BOTTOM)

        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerButton, ConstraintSet.START, R.id.imageButtonSettings, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerButton, ConstraintSet.END, R.id.imageButtonSettings, ConstraintSet.END)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerButton, ConstraintSet.TOP, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerButton, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.BOTTOM)

        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteButton, ConstraintSet.START, R.id.imageButtonSettings, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteButton, ConstraintSet.END, R.id.imageButtonSettings, ConstraintSet.END)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteButton, ConstraintSet.TOP, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteButton, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.BOTTOM)

        //New constraints for the action description labels (they will be reused when expanding the FAB)
        scrolledDownFABCollapsedConstraintSet.clear(R.id.sortDescription, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortDescription, ConstraintSet.END, R.id.imageButtonSort, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortDescription, ConstraintSet.TOP, R.id.imageButtonSort, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.sortDescription, ConstraintSet.BOTTOM, R.id.imageButtonSort, ConstraintSet.BOTTOM)

        scrolledDownFABCollapsedConstraintSet.clear(R.id.displayDescription, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayDescription, ConstraintSet.END, R.id.imageButtonDisplay, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayDescription, ConstraintSet.TOP, R.id.imageButtonDisplay, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.displayDescription, ConstraintSet.BOTTOM, R.id.imageButtonDisplay, ConstraintSet.BOTTOM)

        scrolledDownFABCollapsedConstraintSet.clear(R.id.headerDescription, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerDescription, ConstraintSet.END, R.id.imageButtonHeader, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerDescription, ConstraintSet.TOP, R.id.imageButtonHeader, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.headerDescription, ConstraintSet.BOTTOM, R.id.imageButtonHeader, ConstraintSet.BOTTOM)

        scrolledDownFABCollapsedConstraintSet.clear(R.id.favoriteDescription, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteDescription, ConstraintSet.END, R.id.imageButtonFavorite, ConstraintSet.START)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteDescription, ConstraintSet.TOP, R.id.imageButtonFavorite, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.favoriteDescription, ConstraintSet.BOTTOM, R.id.imageButtonFavorite, ConstraintSet.BOTTOM)

        // Title escapes by the top of the screen
        scrolledDownFABCollapsedConstraintSet.connect(R.id.title, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.connect(R.id.ariane, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        scrolledDownFABCollapsedConstraintSet.clear(R.id.ariane, ConstraintSet.TOP)

        //FAB showing
        scrolledDownFABCollapsedConstraintSet.connect(R.id.imageButtonSettings, ConstraintSet.BOTTOM, 0, ConstraintSet.BOTTOM)
        scrolledDownFABCollapsedConstraintSet.clear(R.id.imageButtonSettings, ConstraintSet.TOP)

        /* See MediaBrowserState.SCROLLED_DOWN_FAB_EXPANDED */
        //We clone the scrolledDownFABCollapsedConstraintSet as it's really close to this state
        scrolledDownFABExpandedConstraintSet.clone(scrolledDownFABCollapsedConstraintSet)

        // show the FAB children
        scrolledDownFABExpandedConstraintSet.clear(R.id.imageButtonHeader, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.clear(R.id.imageButtonSort, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.clear(R.id.imageButtonDisplay, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.clear(R.id.imageButtonFavorite, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.connect(R.id.imageButtonHeader, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.connect(R.id.imageButtonSort, ConstraintSet.BOTTOM, R.id.imageButtonHeader, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.connect(R.id.imageButtonFavorite, ConstraintSet.BOTTOM, R.id.imageButtonSort, ConstraintSet.TOP)
        scrolledDownFABExpandedConstraintSet.connect(R.id.imageButtonDisplay, ConstraintSet.BOTTOM, R.id.imageButtonFavorite, ConstraintSet.TOP)

        scrolledDownFABExpandedConstraintSet.setAlpha(R.id.displayDescription, 1f)
        scrolledDownFABExpandedConstraintSet.setAlpha(R.id.sortDescription, 1f)
        scrolledDownFABExpandedConstraintSet.setAlpha(R.id.headerDescription, 1f)
        scrolledDownFABExpandedConstraintSet.setAlpha(R.id.favoriteDescription, 1f)

        /* See MediaBrowserState.HEADER_VISIBLE */
        //We clone the scrolledDownFABCollapsedConstraintSet state
        headerVisibleConstraintSet.clone(scrolledDownFABCollapsedConstraintSet)

        //Hide the list and show the header list as their visibility is embbeded in the ConstraintSets
        headerVisibleConstraintSet.setVisibility(R.id.headerListContainer, View.VISIBLE)
        headerVisibleConstraintSet.setVisibility(R.id.list, View.GONE)

        // Hide the FAB
        headerVisibleConstraintSet.clear(R.id.imageButtonSettings, ConstraintSet.BOTTOM)
        headerVisibleConstraintSet.connect(R.id.imageButtonSettings, ConstraintSet.TOP, R.id.headerListContainer, ConstraintSet.BOTTOM)

        transition.excludeTarget(ProgressBar::class.java, true)

        // The fake toolbar has to be View.GONE to avoid focus on its elements when it's not shown
        // We also want to give the focus to the header list when it's shown
        transition.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                if (currenstate == MediaBrowserState.SCROLLED_UP) {
                    fakeToolbar.visibility = View.VISIBLE
                } else {
                    fakeToolbar.visibility = View.GONE
                }
                if (currenstate == MediaBrowserState.HEADER_VISIBLE) {
                    binding.headerList.requestFocus()
                }
            }

            override fun onTransitionResume(transition: Transition) {}

            override fun onTransitionPause(transition: Transition) {}

            override fun onTransitionCancel(transition: Transition) {}

            override fun onTransitionStart(transition: Transition) {}
        })

        fabSettings.setOnClickListener { expandExtendedFAB() }
    }
}

fun BaseBrowserTvFragment<*>.setAnimator(cl: ConstraintLayout) {
    animationDelegate = MediaBrowserAnimatorDelegate(binding, cl)
    binding.headerButton.onFocusChangeListener = animationDelegate
    binding.displayButton.onFocusChangeListener = animationDelegate
    binding.sortButton.onFocusChangeListener = animationDelegate
    binding.favoriteButton.onFocusChangeListener = animationDelegate
    binding.imageButtonSort.onFocusChangeListener = animationDelegate
    binding.imageButtonDisplay.onFocusChangeListener = animationDelegate
    binding.imageButtonHeader.onFocusChangeListener = animationDelegate
    binding.imageButtonSettings.onFocusChangeListener = animationDelegate
    binding.list.addOnScrollListener(animationDelegate)
}
