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
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.customview.view.AbsSavedState
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import org.videolan.vlc.R

private const val STATE_SCROLLED_DOWN = 1
private const val STATE_SCROLLED_UP = 2

class BottomNavigationBehavior<V : View>(context: Context, attrs: AttributeSet) :
        CoordinatorLayout.Behavior<V>(context, attrs) {

    private var stateIsScrolling: Boolean = false
    private var height = 0
    private var currentState = STATE_SCROLLED_UP

    private var offsetAnimator: ValueAnimator? = null
    private var player: FrameLayout? = null
    private val playerBehavior: PlayerBehavior<*>?
        get() {
            return ((player?.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? PlayerBehavior)
        }
    private var forceTranslation: Float = -1F

    override fun onSaveInstanceState(parent: CoordinatorLayout, child: V): Parcelable? {
        val superState = super.onSaveInstanceState(parent, child)
        superState?.let {
            return BottomNavigationBehaviorState(superState, child.translationY)
        }
        return superState
    }

    override fun onRestoreInstanceState(parent: CoordinatorLayout, child: V, state: Parcelable) {
        val ss = state as BottomNavigationBehaviorState
        super.onRestoreInstanceState(parent, child, ss.superState!!)
        this.forceTranslation = ss.translation
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (forceTranslation != -1F && child.translationY != forceTranslation) {
            child.translationY = forceTranslation
            forceTranslation = -1F
        }
        val paramsCompat = child.layoutParams as ViewGroup.MarginLayoutParams
        height = child.measuredHeight + paramsCompat.bottomMargin
        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency is Snackbar.SnackbarLayout) {
            updateSnackbar(child, dependency)
        }
        if (dependency.id == R.id.audio_player_container) return true
        return super.layoutDependsOn(parent, child, dependency)
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency is FrameLayout && dependency.id == R.id.audio_player_container) {
            player = dependency
            updatePlayer(child)
        }
        return super.onDependentViewChanged(parent, child, dependency)
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
        if (playerBehavior?.state == BottomSheetBehavior.STATE_EXPANDED) return false
        updatePlayer(child)
        return true
    }

    override fun onNestedFling(coordinatorLayout: CoordinatorLayout, child: V, target: View, velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        updatePlayer(child)
        return super.onNestedFling(coordinatorLayout, child, target, velocityX, velocityY, consumed)
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int, consumed: IntArray) {
        updatePlayer(child)
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
    }

    private fun updateSnackbar(child: View, snackbarLayout: Snackbar.SnackbarLayout) {
        if (player?.visibility != View.GONE && playerBehavior?.state ?: BottomSheetBehavior.STATE_HIDDEN != BottomSheetBehavior.STATE_HIDDEN) return
        if (snackbarLayout.layoutParams is CoordinatorLayout.LayoutParams) {
            val params = snackbarLayout.layoutParams as CoordinatorLayout.LayoutParams

            if (params.anchorId != child.id) {
                params.anchorId = child.id
                params.anchorGravity = Gravity.TOP
                params.gravity = Gravity.TOP
                snackbarLayout.layoutParams = params
            }
        }
    }

    private fun updatePlayer(child: V) {
        player?.let { player ->
            if (player.layoutParams is CoordinatorLayout.LayoutParams) {
                val params = player.layoutParams as CoordinatorLayout.LayoutParams
                val playerBehavior = params.behavior as PlayerBehavior<*>
                playerBehavior.peekHeight = child.context.resources.getDimensionPixelSize(R.dimen.player_peek_height) + child.height - if (stateIsScrolling || currentState == STATE_SCROLLED_DOWN) child.translationY.toInt() else 0
            }
        }
    }

    fun translate(child: V, fl: Float) {
        if (currentState == STATE_SCROLLED_DOWN) return
        child.translationY = fl
        updatePlayer(child)
    }

    fun setCollapsed() {
        currentState = STATE_SCROLLED_DOWN
    }

    companion object {
        fun <V : View> from(view: V): BottomNavigationBehavior<V>? {
            val params = view.layoutParams
            require(params is CoordinatorLayout.LayoutParams) { "The view is not a child of CoordinatorLayout" }
            val behavior = params.behavior
            require(behavior is BottomNavigationBehavior<*>) { "The view is not associated with BottomNavigationBehavior" }
            @Suppress("UNCHECKED_CAST")
            return behavior as BottomNavigationBehavior<V>?
        }
    }
}

/** State persisted across instances  */
class BottomNavigationBehaviorState : AbsSavedState {
    var translation: Float

    @JvmOverloads
    constructor(source: Parcel, loader: ClassLoader? = null) : super(source, loader) {
        translation = source.readFloat()
    }

    constructor(superState: Parcelable?, translation: Float) : super(superState!!) {
        this.translation = translation
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        super.writeToParcel(out, flags)
        out.writeFloat(translation)
    }

    companion object CREATOR : Parcelable.Creator<BottomNavigationBehaviorState> {
        override fun createFromParcel(parcel: Parcel): BottomNavigationBehaviorState {
            return BottomNavigationBehaviorState(parcel)
        }

        override fun newArray(size: Int): Array<BottomNavigationBehaviorState?> {
            return arrayOfNulls(size)
        }
    }
}