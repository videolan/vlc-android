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

package org.videolan.vlc.gui.tv

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import kotlinx.android.synthetic.main.song_browser.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.vlc.R
import org.videolan.vlc.gui.tv.browser.MediaBrowserTvFragment


@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
internal class MediaBrowserAnimatorDelegate(val browser: MediaBrowserTvFragment, private val cl: ConstraintLayout) : RecyclerView.OnScrollListener(), View.OnFocusChangeListener {

    private val scrolledUpConstraintSet = ConstraintSet()
    private val scrolledNoFABConstraintSet = ConstraintSet()
    private val scrolledFABConstraintSet = ConstraintSet()
    private val transition = ChangeBounds().apply {
        interpolator = AccelerateDecelerateInterpolator()
        duration = 300
    }
    private val fakeToolbar = browser.toolbar
    private val fabSettings = browser.imageButtonSettings
    private val fabHeader = browser.imageButtonHeader
    private val fabSort = browser.imageButtonSort
    var menuHidden = false

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)

        if (recyclerView.computeVerticalScrollOffset() > 0) {
            if (!menuHidden) {
                TransitionManager.beginDelayedTransition(cl, transition)
                scrolledNoFABConstraintSet.applyTo(cl)
                menuHidden = true
            }
        } else if (menuHidden) {
            TransitionManager.beginDelayedTransition(cl, transition)
            scrolledUpConstraintSet.applyTo(cl)
            menuHidden = false
        }
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        when(v) {
            browser.headerButton ->  browser.headerDescription
            browser.sortButton -> browser.sortDescription
            else -> null
        }?.animate()?.alpha(if (hasFocus) 1f else 0f)
    }

    internal fun expandExtendedFAB() {
        browser.sortDescription.animate().alpha(1f)
        browser.headerDescription.animate().alpha(1f)
        TransitionManager.beginDelayedTransition(cl, transition)
        scrolledFABConstraintSet.applyTo(cl)

    }

    internal fun collapseExtendedFAB() {
        browser.sortDescription.animate().alpha(0f)
        browser.headerDescription.animate().alpha(0f)
        TransitionManager.beginDelayedTransition(cl, transition)
        scrolledNoFABConstraintSet.applyTo(cl)
    }

    internal fun hideFAB() {
        val marginBottom = (fabSettings.layoutParams as ConstraintLayout.LayoutParams).bottomMargin.toFloat()
        fabSettings.animate().translationY(fabSettings.height + marginBottom)
        fabHeader.animate().translationY(fabSettings.height + marginBottom)
        fabSort.animate().translationY(fabSettings.height + marginBottom)
        fabSettings.isFocusable = false
        fabHeader.isFocusable = false
        fabSort.isFocusable = false
        fakeToolbar.visibility = View.GONE
    }

    internal fun showFAB() {
        fabSettings.animate().translationY(0f).setListener(null)
        fabHeader.animate().translationY(0f)
        fabSort.animate().translationY(0f)
        fabSettings.isFocusable = true
        fabHeader.isFocusable = true
        fabSort.isFocusable = true
        fakeToolbar.visibility = View.VISIBLE
    }

    init {
        scrolledUpConstraintSet.clone(cl)
        scrolledNoFABConstraintSet.clone(cl)

        //Buttons
        scrolledNoFABConstraintSet.setMargin(R.id.sortButton, ConstraintSet.START, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.sortButton, ConstraintSet.END, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.sortButton, ConstraintSet.TOP, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.sortButton, ConstraintSet.BOTTOM, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.headerButton, ConstraintSet.START, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.headerButton, ConstraintSet.END, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.headerButton, ConstraintSet.TOP, 0)
        scrolledNoFABConstraintSet.setMargin(R.id.headerButton, ConstraintSet.BOTTOM, 0)

        scrolledNoFABConstraintSet.connect(R.id.sortButton, ConstraintSet.START, R.id.imageButtonSettings, ConstraintSet.START)
        scrolledNoFABConstraintSet.connect(R.id.sortButton, ConstraintSet.END, R.id.imageButtonSettings, ConstraintSet.END)
        scrolledNoFABConstraintSet.connect(R.id.sortButton, ConstraintSet.TOP, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledNoFABConstraintSet.connect(R.id.sortButton, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.BOTTOM)

        scrolledNoFABConstraintSet.connect(R.id.headerButton, ConstraintSet.START, R.id.imageButtonSettings, ConstraintSet.START)
        scrolledNoFABConstraintSet.connect(R.id.headerButton, ConstraintSet.END, R.id.imageButtonSettings, ConstraintSet.END)
        scrolledNoFABConstraintSet.connect(R.id.headerButton, ConstraintSet.TOP, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledNoFABConstraintSet.connect(R.id.headerButton, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.BOTTOM)

        //descriptions
        scrolledNoFABConstraintSet.clear(R.id.sortDescription, ConstraintSet.START)
        scrolledNoFABConstraintSet.connect(R.id.sortDescription, ConstraintSet.END, R.id.imageButtonSort, ConstraintSet.START)
        scrolledNoFABConstraintSet.connect(R.id.sortDescription, ConstraintSet.TOP, R.id.imageButtonSort, ConstraintSet.TOP)
        scrolledNoFABConstraintSet.connect(R.id.sortDescription, ConstraintSet.BOTTOM, R.id.imageButtonSort, ConstraintSet.BOTTOM)

        scrolledNoFABConstraintSet.clear(R.id.headerDescription, ConstraintSet.START)
        scrolledNoFABConstraintSet.connect(R.id.headerDescription, ConstraintSet.END, R.id.imageButtonHeader, ConstraintSet.START)
        scrolledNoFABConstraintSet.connect(R.id.headerDescription, ConstraintSet.TOP, R.id.imageButtonHeader, ConstraintSet.TOP)
        scrolledNoFABConstraintSet.connect(R.id.headerDescription, ConstraintSet.BOTTOM, R.id.imageButtonHeader, ConstraintSet.BOTTOM)

        scrolledNoFABConstraintSet.constrainMaxHeight(R.id.title, ConstraintSet.TOP)
        scrolledNoFABConstraintSet.connect(R.id.title, ConstraintSet.BOTTOM, 0, ConstraintSet.TOP)

        //FAB hiding
        scrolledNoFABConstraintSet.connect(R.id.imageButtonSettings, ConstraintSet.BOTTOM, 0, ConstraintSet.BOTTOM)
        scrolledNoFABConstraintSet.clear(R.id.imageButtonSettings, ConstraintSet.TOP)


        scrolledFABConstraintSet.clone(scrolledNoFABConstraintSet)

        scrolledFABConstraintSet.clear(R.id.imageButtonHeader, ConstraintSet.TOP)
        scrolledFABConstraintSet.clear(R.id.imageButtonSort, ConstraintSet.TOP)
        scrolledFABConstraintSet.connect(R.id.imageButtonHeader, ConstraintSet.BOTTOM, R.id.imageButtonSettings, ConstraintSet.TOP)
        scrolledFABConstraintSet.connect(R.id.imageButtonSort, ConstraintSet.BOTTOM, R.id.imageButtonHeader, ConstraintSet.TOP)


        transition.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                if (menuHidden) {
                    fakeToolbar.visibility = View.GONE
                } else {
                    fakeToolbar.visibility = View.VISIBLE
                }
            }

            override fun onTransitionResume(transition: Transition) {}

            override fun onTransitionPause(transition: Transition) {}

            override fun onTransitionCancel(transition: Transition) {}

            override fun onTransitionStart(transition: Transition) {}

        })

        fabSettings.setOnClickListener { expandExtendedFAB() }
        val fabOnFocusChangedListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!fabSettings.hasFocus() && !fabSort.hasFocus() && !fabHeader.hasFocus()) {
                collapseExtendedFAB()
            }
            if (v == fabSettings && hasFocus) {
                expandExtendedFAB()
            }
        }
        fabSettings.onFocusChangeListener = fabOnFocusChangedListener
        fabSort.onFocusChangeListener = fabOnFocusChangedListener
        fabHeader.onFocusChangeListener = fabOnFocusChangedListener
    }
}

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
fun MediaBrowserTvFragment.setAnimator(cl: ConstraintLayout) {
    animationDelegate = MediaBrowserAnimatorDelegate(this, cl)
    headerButton.onFocusChangeListener = animationDelegate
    sortButton.onFocusChangeListener = animationDelegate
    list.addOnScrollListener(animationDelegate)
}
