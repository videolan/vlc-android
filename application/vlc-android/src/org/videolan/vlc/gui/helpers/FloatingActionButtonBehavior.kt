/*
 * ************************************************************************
 *  FloatingActionButtonBehavior.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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

package org.videolan.vlc.gui.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.videolan.tools.setInvisible
import org.videolan.vlc.R

@Keep
class FloatingActionButtonBehavior(context: Context, attrs: AttributeSet?) : FloatingActionButton.Behavior(context, attrs) {

    // Listener to workaroud AppCompat 25.x bug
    // FAB doesn't receive any callback when set to GONE.
    private val onVisibilityChangedListener: FloatingActionButton.OnVisibilityChangedListener

    init {
        onVisibilityChangedListener = object : FloatingActionButton.OnVisibilityChangedListener() {
            override fun onHidden(fab: FloatingActionButton?) {
                fab.setInvisible()
            }

        }
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: FloatingActionButton, dependency: View): Boolean {
        return (dependency.id == R.id.audio_player_container
                || dependency is Snackbar.SnackbarLayout
                || dependency is RecyclerView
                || dependency is NestedScrollView)
    }

    override fun onStartNestedScroll(coordinatorLayout: CoordinatorLayout, child: FloatingActionButton, directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
        return true
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, child: FloatingActionButton, dependency: View): Boolean {
        return if (dependency.id == R.id.audio_player_container && dependency.visibility == View.VISIBLE) {
            val childHeight = (child.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin + child.height
            child.y = dependency.y - childHeight
            true
        } else
            super.onDependentViewChanged(parent, child, dependency)
    }

    override fun onNestedScroll(coordinatorLayout: CoordinatorLayout, child: FloatingActionButton, target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)
        // When target is a NestedScrollView, use dyUnconsumed as dyConsumed is always 0
        val dy = if (target is NestedScrollView) dyUnconsumed else dyConsumed
        if (dy > 0 && child.visibility == View.VISIBLE)
            child.hide(onVisibilityChangedListener)
        else if (dy < 0 && child.visibility == View.INVISIBLE)
            child.show()
    }

    companion object {

        private const val TAG = "VLC/FloatingActionButtonBehavior"
    }
}
