/*****************************************************************************
 * FastScroller.java
 *****************************************************************************
 * Copyright © 2016 VLC authors and VideoLAN
 * Inspired by Mark Allison blog post:
 * https://blog.stylingandroid.com/recyclerview-fastscroll-part-1/
 * https://blog.stylingandroid.com/recyclerview-fastscroll-part-2/
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

package org.videolan.vlc.gui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.R;


@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class FastScroller extends LinearLayout {
    
    private static final String TAG = "FastScroller";

    private static final int HANDLE_ANIMATION_DURATION = 100;
    private static final int HANDLE_HIDE_DELAY = 1000;
    private static final int SCROLLER_HIDE_DELAY = 3000;
    private static final int TRACK_SNAP_RANGE = 5;

    private static final int HIDE_HANDLE = 0;
    private static final int HIDE_SCROLLER = 1;
    private static final int SHOW_SCROLLER = 2;

    private static final String SCALE_X = "scaleX";
    private static final String SCALE_Y = "scaleY";
    private static final String ALPHA = "alpha";
    private int mHeight, mItemCount;
    boolean mFastScrolling, mShowBubble;

    private AnimatorSet currentAnimator = null;
    private final ScrollListener scrollListener = new ScrollListener();
    private RecyclerView mRecyclerView;
    private ImageView handle;
    private TextView bubble;

    public FastScroller(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public FastScroller(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        setOrientation(HORIZONTAL);
        setClipChildren(false);
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.fastscroller, this);
        handle = (ImageView) findViewById(R.id.fastscroller_handle);
        bubble = (TextView) findViewById(R.id.fastscroller_bubble);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mHeight = h;
    }

    private void showBubble() {
        AnimatorSet animatorSet = new AnimatorSet();
        bubble.setPivotX(bubble.getWidth());
        bubble.setPivotY(bubble.getHeight());
        bubble.setVisibility(VISIBLE);
        Animator growerX = ObjectAnimator.ofFloat(bubble, SCALE_X, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION);
        Animator growerY = ObjectAnimator.ofFloat(bubble, SCALE_Y, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION);
        Animator alpha = ObjectAnimator.ofFloat(bubble, ALPHA, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION);
        animatorSet.playTogether(growerX, growerY, alpha);
        animatorSet.start();
    }

    private void hideBubble() {
        currentAnimator = new AnimatorSet();
        bubble.setPivotX(bubble.getWidth());
        bubble.setPivotY(bubble.getHeight());
        Animator shrinkerX = ObjectAnimator.ofFloat(bubble, SCALE_X, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION);
        Animator shrinkerY = ObjectAnimator.ofFloat(bubble, SCALE_Y, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION);
        Animator alpha = ObjectAnimator.ofFloat(bubble, ALPHA, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION);
        currentAnimator.playTogether(shrinkerX, shrinkerY, alpha);
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                bubble.setVisibility(GONE);
                currentAnimator = null;
                mHandler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                bubble.setVisibility(INVISIBLE);
                currentAnimator = null;
                mHandler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY);
            }
        });
        currentAnimator.start();
    }

    private void setPosition(float y) {
        float position = y / mHeight;
        int handleHeight = handle.getHeight();
        ViewCompat.setY(handle, getValueInRange(0, mHeight - handleHeight, (int) ((mHeight - handleHeight) * position)));
        int bubbleHeight = bubble.getHeight();
        ViewCompat.setY(bubble, getValueInRange(0, mHeight - bubbleHeight, (int) ((mHeight - bubbleHeight) * position) - handleHeight));
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        if (!AndroidUtil.isHoneycombOrLater())
            return;
        if (mRecyclerView != null)
            mRecyclerView.removeOnScrollListener(scrollListener);
        setVisibility(INVISIBLE);
        mItemCount = recyclerView.getAdapter().getItemCount();
        this.mRecyclerView = recyclerView;
        recyclerView.addOnScrollListener(scrollListener);
        mShowBubble = ((SeparatedAdapter)recyclerView.getAdapter()).hasSections();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            mFastScrolling = true;
            setPosition(event.getY());
            if (currentAnimator != null)
                currentAnimator.cancel();
            mHandler.removeMessages(HIDE_SCROLLER);
            mHandler.removeMessages(HIDE_HANDLE);
            if (mShowBubble && bubble.getVisibility() == GONE)
                showBubble();
            setRecyclerViewPosition(event.getY());
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            mFastScrolling = false;
            mHandler.sendEmptyMessageDelayed(HIDE_HANDLE, HANDLE_HIDE_DELAY);
            mHandler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY);
            return true;
        }
        return super.onTouchEvent(event);
    }

    int formerPosition;
    private void setRecyclerViewPosition(float y) {
        if (mRecyclerView != null) {
            float proportion;
            if (ViewCompat.getY(handle) == 0) {
                proportion = 0f;
            } else if (ViewCompat.getY(handle) + handle.getHeight() >= mHeight - TRACK_SNAP_RANGE) {
                proportion = 1f;
            } else {
                proportion = y / (float) mHeight;
            }
            int targetPos = getValueInRange(0, mItemCount - 1, Math.round(proportion * (float) mItemCount));
            if (targetPos == formerPosition)
                return;
            mRecyclerView.scrollToPosition(targetPos);
        }
    }

    private int getValueInRange(int min, int max, int value) {
        int minimum = Math.max(min, value);
        return Math.min(minimum, max);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HIDE_HANDLE:
                    hideBubble();
                    break;
                case HIDE_SCROLLER:
                    FastScroller.this.setVisibility(INVISIBLE);
                    break;
                case SHOW_SCROLLER:
                    FastScroller.this.setVisibility(VISIBLE);
                    mHandler.removeMessages(HIDE_SCROLLER);
                    mHandler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY);
                    break;
            }
        }
    };

    private class ScrollListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrolled(RecyclerView rv, int dx, int dy) {
            int firstVisiblePosition = ((LinearLayoutManager)mRecyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            if (mFastScrolling) {
                String letter = ((SeparatedAdapter)mRecyclerView.getAdapter()).getSectionforPosition(firstVisiblePosition);
                bubble.setText(letter);
                return;
            }
            if (FastScroller.this.getVisibility() == INVISIBLE)
                mHandler.sendEmptyMessage(SHOW_SCROLLER);
            int lastVisiblePosition = ((LinearLayoutManager)mRecyclerView.getLayoutManager()).findLastVisibleItemPosition();
            int position;
            if (firstVisiblePosition == 0) {
                position = 0;
            } else if (lastVisiblePosition == mItemCount - 1) {
                position = mItemCount - 1;
            } else {
                position = firstVisiblePosition;
            }
            float proportion = (float) position / (float) mItemCount;
            setPosition(mHeight * proportion);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }
    }

    public interface SeparatedAdapter {
        boolean hasSections();
        String getSectionforPosition(int position);
    }
}
