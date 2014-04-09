/*****************************************************************************
 * AnimatedCoverImageView.java
 *****************************************************************************
 * Copyright © 2011-2013 VLC authors and VideoLAN
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;


public class AnimatedCoverView extends View {

    private Bitmap mImage;

    private TranslateAnimation mCurrentAnim = null;
    private final static int ANIMATION_MOVE_1 = 0;
    private final static int ANIMATION_MOVE_2 = 1;
    private int mCurrentMove = ANIMATION_MOVE_2;

    private Rect mSrc = new Rect();
    private Rect mDst = new Rect();
    float[] mStartPos = new float[2];
    private Transformation mTrans = new Transformation();
    private Paint mPaint = new Paint();
    private int mScaledImageWidth;
    private int mScaledImageHeight;

    public AnimatedCoverView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public AnimatedCoverView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedCoverView(Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Reinitialize the current animation.
        mCurrentAnim = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mImage != null) {
            // Switch the current animation if needed.
            if (mCurrentAnim == null || mCurrentAnim.hasEnded())
                switchAnimation(canvas);

            // Animate and draw
            mCurrentAnim.getTransformation(AnimationUtils.currentAnimationTimeMillis(), mTrans);
            mStartPos[0] = 0; mStartPos[1] = 0;
            mTrans.getMatrix().mapPoints(mStartPos);

            mSrc.set(0, 0, mImage.getWidth(), mImage.getHeight());
            mDst.set((int)mStartPos[0], (int)mStartPos[1],
                    (int)mStartPos[0] + mScaledImageWidth, (int)mStartPos[1] + mScaledImageHeight);
            mPaint.setFilterBitmap(true);
            canvas.drawBitmap(mImage, mSrc, mDst, mPaint);
            super.onDraw(canvas);

            // Request another draw operation until time is up.
            invalidate();
        }
    }

    private void switchAnimation(Canvas canvas) {
        // Determine the animation parameters.
        Rect rect = new Rect();
        canvas.getClipBounds(rect);

        float ARview = (float)rect.width() / rect.height();
        float ARimage = (float)mImage.getWidth() / mImage.getHeight();

        if (ARimage > ARview) {
            mScaledImageWidth = (int)((float)mImage.getWidth() * rect.bottom / mImage.getHeight());
            mScaledImageHeight = rect.bottom;
        }
        else {
            mScaledImageWidth = rect.right;
            mScaledImageHeight = (int)((float)mImage.getHeight() * rect.right / mImage.getWidth());
        }

        mCurrentMove = mCurrentMove == ANIMATION_MOVE_1 ? ANIMATION_MOVE_2 : ANIMATION_MOVE_1;

        mCurrentAnim = new TranslateAnimation(
                mCurrentMove == ANIMATION_MOVE_1 ? 0 : rect.right - mScaledImageWidth,
                mCurrentMove == ANIMATION_MOVE_1 ? rect.right - mScaledImageWidth : 0,
                mCurrentMove == ANIMATION_MOVE_1 ? 0 : rect.bottom - mScaledImageHeight,
                mCurrentMove == ANIMATION_MOVE_1 ? rect.bottom - mScaledImageHeight : 0);

        int animationDuration = mScaledImageHeight == rect.bottom ?
                (mScaledImageWidth - rect.right) * 60 : (mScaledImageHeight - rect.bottom) * 60;
        if (animationDuration <= 10)
            animationDuration = 10;
        mCurrentAnim.setDuration(animationDuration);
        mCurrentAnim.setInterpolator(new LinearInterpolator());
        mCurrentAnim.initialize(mImage.getWidth(), mImage.getHeight(), rect.right, rect.bottom);
    }

    public void setImageBitmap(Bitmap b) {
        mImage = b;
        invalidate();
    }
}
