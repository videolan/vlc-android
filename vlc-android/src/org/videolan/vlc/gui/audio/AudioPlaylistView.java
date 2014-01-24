/*****************************************************************************
 * AudioPlaylistView.java
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

package org.videolan.vlc.gui.audio;

import org.videolan.vlc.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class AudioPlaylistView extends ListView {

    private View mDragShadow;
    private boolean mIsDragging;
    private int mPositionDragStart;

    private float mTouchY;

    private OnItemDraggedListener mOnItemDraggedListener;
    private OnItemRemovedListener mOnItemRemovedListener;

    public AudioPlaylistView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mIsDragging = false;

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDragShadow = inflater.inflate(R.layout.audio_playlist_item_drag_shadow, this, false);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDragShadow.layout(l, t, l + mDragShadow.getMeasuredWidth(), t + mDragShadow.getMeasuredHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mDragShadow.measure(widthMeasureSpec, MeasureSpec.UNSPECIFIED);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event)
    {
        switch (event.getAction())
        {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            mTouchY = event.getY();
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            if (mIsDragging) {
                dragAborted();
            }
            break;
        default:
            break;
        }

        return mIsDragging || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        boolean handleEvent = false;

        // Save the position of the touch event.
        mTouchY = event.getY();

        if (mIsDragging) {
            handleEvent = true;
            switch (event.getAction())
            {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                dragging();
                break;
            case MotionEvent.ACTION_UP:
                dragDropped();
                break;
            case MotionEvent.ACTION_CANCEL:
                dragAborted();
                break;
            default:
                handleEvent = false;
                break;
            }
            invalidate();
        }

        return handleEvent || super.onTouchEvent(event);
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mIsDragging) {
            canvas.save();
            // Position the drag shadow.
            float posY = mTouchY - mDragShadow.getMeasuredHeight() / 2;
            canvas.translate(0, posY);
            mDragShadow.draw(canvas);
            canvas.restore();
        }
    }

    public void startDrag(int positionDragStart, String title, String artist) {
        mPositionDragStart = positionDragStart;
        if (mDragShadow != null) {
            TextView titleView = (TextView)mDragShadow.findViewById(R.id.title);
            TextView artistView = (TextView)mDragShadow.findViewById(R.id.artist);
            titleView.setText(title);
            artistView.setText(artist);
            mIsDragging = true;
        }
    }

    private void dragging() {
        // Find the child view that is currently touched (perform a hit test)
        Rect rect = new Rect();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LinearLayout expansion = (LinearLayout)child.findViewById(R.id.item_expansion);
            LinearLayout layout_item = (LinearLayout)child.findViewById(R.id.layout_item);
            View layout_footer = (View)child.findViewById(R.id.layout_footer);
            AudioListAdapter.ViewHolder holder = (AudioListAdapter.ViewHolder)child.getTag();

            if (holder.position == mPositionDragStart) {
                layout_item.setVisibility(LinearLayout.GONE);
                layout_footer.setVisibility(LinearLayout.GONE);
            }
            else {
                child.getHitRect(rect);
                if (rect.contains(getWidth() / 2, (int)mTouchY))
                    expansion.setVisibility(LinearLayout.VISIBLE);
                else
                    expansion.setVisibility(LinearLayout.GONE);
            }
        }
    }

    public void dragDropped() {
        mIsDragging = false;

        // Find the child view that was touched (perform a hit test)
        Rect rect = new Rect();
        boolean b_foundHitChild =  false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.getHitRect(rect);
            if (rect.contains(getWidth() / 2, (int)mTouchY)) {
                // Send back the performed change thanks to the listener.
                AudioListAdapter.ViewHolder holder = (AudioListAdapter.ViewHolder)child.getTag();
                if (mOnItemDraggedListener != null)
                    mOnItemDraggedListener.OnItemDradded(mPositionDragStart, holder.position);
                b_foundHitChild = true;
                break;
            }
        }
        if (!b_foundHitChild)
            mOnItemDraggedListener.OnItemDradded(mPositionDragStart, this.getCount());
    }

    public void dragAborted() {
        mIsDragging = false;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LinearLayout expansion = (LinearLayout)child.findViewById(R.id.item_expansion);
            LinearLayout layout_item = (LinearLayout)child.findViewById(R.id.layout_item);
            View layout_footer = (View)child.findViewById(R.id.layout_footer);
            layout_item.setVisibility(LinearLayout.VISIBLE);
            layout_footer.setVisibility(LinearLayout.VISIBLE);
            expansion.setVisibility(LinearLayout.GONE);
        }
    }

    public interface OnItemDraggedListener {
        public void OnItemDradded(int positionStart, int positionEnd);
    };

    public void setOnItemDraggedListener(OnItemDraggedListener l) {
        mOnItemDraggedListener = l;
    }

    public interface OnItemRemovedListener {
        public void onItemRemoved(int position);
    }

    public void setOnItemRemovedListener(OnItemRemovedListener l) {
        mOnItemRemovedListener = l;
    }

    public void removeItem(int position) {
        if (mOnItemRemovedListener != null)
            mOnItemRemovedListener.onItemRemoved(position);
    }
}
