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

package org.videolan.vlc.gui.helpers;

import android.content.Context;
import androidx.annotation.Keep;
import com.google.android.material.appbar.AppBarLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;

import org.videolan.vlc.R;

@Keep
public class FloatingActionButtonBehavior extends FloatingActionButton.Behavior {

    private static final String TAG = "VLC/FloatingActionButtonBehavior";

    // Listener to workaroud AppCompat 25.x bug
    // FAB doesn't receive any callback when set to GONE.
    private final FloatingActionButton.OnVisibilityChangedListener mOnVisibilityChangedListener;

    public FloatingActionButtonBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOnVisibilityChangedListener = new FloatingActionButton.OnVisibilityChangedListener() {
            @Override
            public void onHidden(FloatingActionButton fab) {
                fab.setVisibility(View.INVISIBLE);
            }

        };
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        return dependency.getId() == R.id.audio_player_container || dependency instanceof Snackbar.SnackbarLayout || dependency instanceof AppBarLayout;
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionButton child, View directTargetChild, View target, int nestedScrollAxes) {
        return true;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, FloatingActionButton child, View dependency) {
        if (dependency.getId() == R.id.audio_player_container && dependency.getVisibility() == View.VISIBLE) {
            int childHeight = ((CoordinatorLayout.LayoutParams)child.getLayoutParams()).bottomMargin + child.getHeight();
            ViewCompat.setY(child, ViewCompat.getY(dependency) - childHeight);
            return true;
        } else
            return super.onDependentViewChanged(parent, child, dependency);
    }

    @Override
    public void onNestedScroll(CoordinatorLayout coordinatorLayout, FloatingActionButton child, View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        if (dyConsumed > 0 && child.getVisibility() == View.VISIBLE)
            child.hide(mOnVisibilityChangedListener);
        else if (dyConsumed < 0 && child.getVisibility() == View.INVISIBLE)
            child.show();
    }
}
