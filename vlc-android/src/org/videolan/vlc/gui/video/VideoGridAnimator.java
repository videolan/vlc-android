/*****************************************************************************
 * VideoGridAnimator.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
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
 *****************************************************************************/

package org.videolan.vlc.gui.video;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.OnHierarchyChangeListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class VideoGridAnimator {

    public final static String TAG = "VLC/VideoGridAnimator";

    private final GridView mGridView;
    private boolean isAnimating = false;
    private int mLastNItems;
    private int mAnimationsRunning = 0;

    public VideoGridAnimator(GridView gridview) {
        mGridView = gridview;
        mGridView.setOnHierarchyChangeListener(mHCL);
    }

    public void animate() {
        isAnimating = true;
        mLastNItems = -1;
        mGridView.removeCallbacks(r);
        mGridView.post(r);
    }

    /* If animation is running, hide the items as they are added to the grid
     * so they don't flicker after being laid out and before the animation
     * starts.
     */
    OnHierarchyChangeListener mHCL = new OnHierarchyChangeListener() {
        @Override
        public void onChildViewRemoved(View parent, View child) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onChildViewAdded(View parent, View child) {
            if (isAnimating && parent == mGridView)
                setAlpha(0, child);
        }
    };

    final Runnable r = new Runnable() {
        @Override
        public void run() {
            /* Ensure the number of visible items is stable between two run */
            if (mGridView.getChildCount() != mLastNItems) {
                /* List not not ready yet: reschedule */
                mLastNItems = mGridView.getChildCount();
                Log.e(TAG, "Rescheduling animation: list not ready");
                mGridView.postDelayed(this, 10);
                return;
            }

            isAnimating = false;

            for (int i = 0; i < mGridView.getChildCount(); i++) {
                AnimationSet animSet = new AnimationSet(true);
                Animation animation = new AlphaAnimation(0.0f, 1.0f);
                animation.setDuration(300);
                animation.setStartOffset(i * 80);
                animSet.addAnimation(animation);
                if (((VideoListAdapter)mGridView.getAdapter()).isListMode()) {
                    animation = new TranslateAnimation(
                            Animation.RELATIVE_TO_SELF, -1.0f,Animation.RELATIVE_TO_SELF, 0.0f,
                            Animation.RELATIVE_TO_SELF, 0.0f,Animation.RELATIVE_TO_SELF, 0.0f
                        );
                    animation.setDuration(400);
                    animation.setStartOffset(i * 80);
                    animSet.addAnimation(animation);
                }
                animSet.setAnimationListener(new AnimationListener() {

                    @Override
                    public void onAnimationStart(Animation animation) {
                        mAnimationsRunning += 1;
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mAnimationsRunning -= 1;
                    }
                });
                isAnimating = false;
                View v = mGridView.getChildAt(i);
                setAlpha(1, v);
                v.startAnimation(animSet);
            }
        }
    };

    public boolean isAnimationDone() {
        return mAnimationsRunning == 0;
    }

    /* Support pre-11 device */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setAlpha(float alpha, View view)
    {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            view.setAlpha(alpha);
        else if (view instanceof ViewGroup)
        {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            {
                setAlpha(alpha, ((ViewGroup) view).getChildAt(i));
                if (((ViewGroup) view).getBackground() != null) ((ViewGroup) view).getBackground().setAlpha((int) (alpha * 255));
            }
        }
        else if (view instanceof ImageView)
        {
            if (((ImageView) view).getDrawable() != null) ((ImageView) view).getDrawable().setAlpha((int) (alpha * 255));
            if (((ImageView) view).getBackground() != null) ((ImageView) view).getBackground().setAlpha((int) (alpha * 255));
        }
        else if (view instanceof TextView)
        {
            ((TextView) view).setTextColor(((TextView) view).getTextColors().withAlpha((int) (alpha * 255)));
            if (((TextView) view).getBackground() != null) ((TextView) view).getBackground().setAlpha((int) (alpha * 255));
        }
    }
}
