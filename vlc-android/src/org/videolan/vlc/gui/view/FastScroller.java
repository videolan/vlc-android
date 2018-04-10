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
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.DiffUtilAdapter;
import org.videolan.vlc.util.WeakHandler;

import java.util.TreeMap;


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
    private int mHeight, mItemCount, mRecyclerviewTotalHeight;
    private boolean mFastScrolling, mShowBubble;
    private int mCurrentPosition;

    private AnimatorSet mCurrentAnimator = null;
    private final ScrollListener scrollListener = new ScrollListener();
    private RecyclerView mRecyclerView;
    private ImageView handle;
    private TextView bubble;
    private final TreeMap<Integer, String> sections = new TreeMap<>();

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
        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.fastscroller, this);
        handle = findViewById(R.id.fastscroller_handle);
        bubble = findViewById(R.id.fastscroller_bubble);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mHeight = h;
    }

    private void showBubble() {
        final AnimatorSet animatorSet = new AnimatorSet();
        bubble.setPivotX(bubble.getWidth());
        bubble.setPivotY(bubble.getHeight());
        scrollListener.onScrolled(mRecyclerView, 0, 0);
        bubble.setVisibility(VISIBLE);
        final Animator growerX = ObjectAnimator.ofFloat(bubble, SCALE_X, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION);
        final Animator growerY = ObjectAnimator.ofFloat(bubble, SCALE_Y, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION);
        final Animator alpha = ObjectAnimator.ofFloat(bubble, ALPHA, 0f, 1f).setDuration(HANDLE_ANIMATION_DURATION);
        animatorSet.playTogether(growerX, growerY, alpha);
        animatorSet.start();
    }

    private void hideBubble() {
        mCurrentAnimator = new AnimatorSet();
        bubble.setPivotX(bubble.getWidth());
        bubble.setPivotY(bubble.getHeight());
        final Animator shrinkerX = ObjectAnimator.ofFloat(bubble, SCALE_X, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION);
        final Animator shrinkerY = ObjectAnimator.ofFloat(bubble, SCALE_Y, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION);
        final Animator alpha = ObjectAnimator.ofFloat(bubble, ALPHA, 1f, 0f).setDuration(HANDLE_ANIMATION_DURATION);
        mCurrentAnimator.playTogether(shrinkerX, shrinkerY, alpha);
        mCurrentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                bubble.setVisibility(GONE);
                mCurrentAnimator = null;
                mHandler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                bubble.setVisibility(INVISIBLE);
                mCurrentAnimator = null;
                mHandler.sendEmptyMessageDelayed(HIDE_SCROLLER, SCROLLER_HIDE_DELAY);
            }
        });
        mCurrentAnimator.start();
    }

    private void setPosition(float y) {
        final float position = y / mHeight;
        final int handleHeight = handle.getHeight();
        handle.setY(getValueInRange(0, mHeight - handleHeight, (int) ((mHeight - handleHeight) * position)));
        final int bubbleHeight = bubble.getHeight();
        bubble.setY(getValueInRange(0, mHeight - bubbleHeight, (int) ((mHeight - bubbleHeight) * position) - handleHeight));
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        if (mRecyclerView != null) mRecyclerView.removeOnScrollListener(scrollListener);
        setVisibility(INVISIBLE);
        mItemCount = recyclerView.getAdapter().getItemCount();
        this.mRecyclerView = recyclerView;
        mRecyclerviewTotalHeight = 0;
        prepareSectionsMap();
        recyclerView.addOnScrollListener(scrollListener);
        mShowBubble = !sections.isEmpty();
    }

    private void prepareSectionsMap() {
        sections.clear();
        final DiffUtilAdapter adapter = (DiffUtilAdapter) mRecyclerView.getAdapter();
        final int count = adapter.getItemCount();
        for (int i = 0; i < count; ++i) {
            if (adapter.getItemViewType(i) == MediaLibraryItem.TYPE_DUMMY)
                sections.put(i, ((MediaLibraryItem) adapter.getItem(i)).getTitle());
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            mFastScrolling = true;
            mCurrentPosition = -1;
            if (mCurrentAnimator != null) mCurrentAnimator.cancel();
            mHandler.removeMessages(HIDE_SCROLLER);
            mHandler.removeMessages(HIDE_HANDLE);
            if (mShowBubble && bubble.getVisibility() == GONE) showBubble();
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

    private void setRecyclerViewPosition(float y) {
        if (mRecyclerView != null) {
            float proportion;
            if (handle.getY() == 0) {
                proportion = 0f;
            } else if (handle.getY() + handle.getHeight() >= mHeight - TRACK_SNAP_RANGE) {
                proportion = 1f;
            } else {
                proportion = y / (float) mHeight;
            }
            final int targetPos = getValueInRange(0, mItemCount - 1, Math.round(proportion * (float) mItemCount));
            if (targetPos == mCurrentPosition) return;
            mCurrentPosition = targetPos;
            mRecyclerView.scrollToPosition(targetPos);
        }
    }

    private int getValueInRange(int min, int max, int value) {
        return Math.min(Math.max(min, value), max);
    }

    private class ScrollListener extends RecyclerView.OnScrollListener {
        final StringBuilder sb = new StringBuilder();
        @Override
        public void onScrolled(RecyclerView rv, int dx, int dy) {
            final float proportion = mRecyclerviewTotalHeight == 0 ? 0f : rv.computeVerticalScrollOffset() / (float) mRecyclerviewTotalHeight;
            setPosition(mHeight * proportion);
            if (mFastScrolling) {
                sb.setLength(0);
                int position = mCurrentPosition != -1 ? mCurrentPosition
                        : ((LinearLayoutManager)mRecyclerView.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                sb.append(' ')
                        .append(sections.floorEntry(position).getValue())
                        .append(' ');
                bubble.setText(sb.toString());
                return;
            }
            if (mRecyclerviewTotalHeight == 0)
                mRecyclerviewTotalHeight = mRecyclerView.computeVerticalScrollRange()-mRecyclerView.computeVerticalScrollExtent();
            if (FastScroller.this.getVisibility() == INVISIBLE) mHandler.sendEmptyMessage(SHOW_SCROLLER);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }
    }

    private static class FastScrollerHandler extends WeakHandler<FastScroller> {
        FastScrollerHandler(FastScroller owner) {
            super(owner);
        }
    }

    private final Handler mHandler = new FastScrollerHandler(this) {
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
}
