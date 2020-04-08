/*
 * ************************************************************************
 *  BottomNavigationBehavior.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.helpers

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.snackbar.Snackbar
import org.videolan.vlc.R
import kotlin.math.max
import kotlin.math.min

class BottomNavigationBehavior<V : View>(context: Context, attrs: AttributeSet) :
        CoordinatorLayout.Behavior<V>(context, attrs) {

    private val listeners = ArrayList<(translation: Float) -> Unit>()
    @ViewCompat.NestedScrollType
    private var lastStartedType: Int = 0
    private var offsetAnimator: ValueAnimator? = null
    private var isSnappingEnabled = true
    var isPlayerHidden = false
    private var player: FrameLayout? = null

    override fun onNestedPreScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
        child.translationY = max(0f, min(child.height.toFloat(), child.translationY + dy))
        listeners.forEach { it(child.translationY) }
        player?.let { updatePlayer(child, it) }
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency is Snackbar.SnackbarLayout) {
            updateSnackbar(child, dependency)
        }
        if (dependency is FrameLayout && dependency.id == R.id.audio_player_container) {
            player = dependency
            updatePlayer(child, dependency)
        }
        return super.layoutDependsOn(parent, child, dependency)
    }

    override fun onStartNestedScroll(
            coordinatorLayout: CoordinatorLayout, child: V, directTargetChild: View, target: View, axes: Int, type: Int
    ): Boolean {
        if (isPlayerHidden) return false
        if (axes != ViewCompat.SCROLL_AXIS_VERTICAL)
            return false

        lastStartedType = type
        offsetAnimator?.cancel()

        return true
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, type: Int) {
        if (!isSnappingEnabled)
            return

        // add snap behaviour
        // Logic here borrowed from AppBarLayout onStopNestedScroll code
        if (lastStartedType == ViewCompat.TYPE_TOUCH || type == ViewCompat.TYPE_NON_TOUCH) {
            // find nearest seam
            val currTranslation = child.translationY
            val childHalfHeight = child.height * 0.5f

            // translate down
            if (currTranslation >= childHalfHeight) {
                animateBarVisibility(child, isVisible = false)
            }
            // translate up
            else {
                animateBarVisibility(child, isVisible = true)
            }
        }
    }

    fun addScrollListener(listener: (translation: Float) -> Unit) {
        listeners.add(listener)
    }

    private fun updateSnackbar(child: View, snackbarLayout: Snackbar.SnackbarLayout) {
        if (snackbarLayout.layoutParams is CoordinatorLayout.LayoutParams) {
            val params = snackbarLayout.layoutParams as CoordinatorLayout.LayoutParams

            params.anchorId = child.id
            params.anchorGravity = Gravity.TOP
            params.gravity = Gravity.TOP
            snackbarLayout.layoutParams = params
        }
    }

    private fun updatePlayer(child: View, player: FrameLayout) {
        if (player.layoutParams is CoordinatorLayout.LayoutParams) {
            val params = player.layoutParams as CoordinatorLayout.LayoutParams
            val playerBehavior = params.behavior as PlayerBehavior<*>
            playerBehavior.peekHeight = child.context.resources.getDimensionPixelSize(R.dimen.player_peek_height) + child.height - child.translationY.toInt()
        }
    }

    fun animateBarVisibility(child: View, isVisible: Boolean) {
        val targetTranslation = if (isVisible) 0f else child.height.toFloat()
        if (child.translationY == targetTranslation) return
        if (offsetAnimator == null) {
            offsetAnimator = ValueAnimator().apply {
                interpolator = DecelerateInterpolator()
                duration = 150L
            }

            offsetAnimator?.addUpdateListener {
                child.translationY = it.animatedValue as Float
                listeners.forEach { listener -> listener(it.animatedValue as Float) }
                player?.let { updatePlayer(child, it) }
            }
        } else {
            offsetAnimator?.cancel()
        }

        offsetAnimator?.setFloatValues(child.translationY, targetTranslation)
        offsetAnimator?.start()
    }

    companion object {
        fun <V : View> from(view: V): BottomNavigationBehavior<V>? {
            val params = view.layoutParams
            require(params is CoordinatorLayout.LayoutParams) { "The view is not a child of CoordinatorLayout" }
            val behavior = params.behavior
            require(behavior is BottomNavigationBehavior<*>) { "The view is not associated with BottomNavigationBehavior" }
            return behavior as BottomNavigationBehavior<V>?
        }
    }
}