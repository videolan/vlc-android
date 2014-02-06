/*****************************************************************************
 * VerticalSeekBar.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
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

package org.videolan.vlc.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

public class VerticalSeekBar extends SeekBar {

    private boolean mIsMovingThumb = false;
    static private float THUMB_SLOP = 25;

    public VerticalSeekBar(Context context) {
        super(context);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(h, w, oldh, oldw);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    protected void onDraw(Canvas c) {
        c.rotate(-90);
        c.translate(-getHeight(), 0);

        super.onDraw(c);
    }

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress);
        onSizeChanged(getWidth(), getHeight(), 0, 0);
    }

    private boolean isWithinThumb(MotionEvent event) {
        final float progress = getProgress();
        final float density = this.getResources().getDisplayMetrics().density;
        final float height = getHeight();
        final float y = event.getY();
        final float max = getMax();
        if (progress >= max - (int)(max * (y + THUMB_SLOP * density) / height)
            && progress <= max - (int)(max * (y - THUMB_SLOP * density) / height))
            return true;
        else
            return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        boolean handled = false;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isWithinThumb(event)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    mIsMovingThumb = true;
                    handled = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsMovingThumb) {
                    setProgress(getMax() - (int) (getMax() * event.getY() / getHeight()));
                    onSizeChanged(getWidth(), getHeight(), 0, 0);
                    handled = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                mIsMovingThumb = false;
                handled = true;
                break;
        }
        return handled;
    }
}
