/*
 * ************************************************************************
 *  PopupManager.java
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

package org.videolan.vlc.gui.video;

import android.content.Context;
import android.graphics.PixelFormat;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

public class PopupManager implements PlaybackService.Callback, GestureDetector.OnDoubleTapListener {

    private static final String TAG ="VLC/PopupManager";
    private static final int FLING_STOP_VELOCITY = 3000;

    private PlaybackService mService;
    private GestureDetectorCompat mGestureDetector = null;

    private WindowManager windowManager;
    private SurfaceView mPopupView;

    public PopupManager(PlaybackService service) {
        mService = service;
        windowManager = (WindowManager) VLCApplication.getAppContext().getSystemService(Context.WINDOW_SERVICE);
    }

    public void removePopup() {
        if (mPopupView == null)
            return;
        mService.removeCallback(this);
        final IVLCVout vlcVout = mService.getVLCVout();
        windowManager.removeView(mPopupView);
        vlcVout.detachViews();
        vlcVout.removeCallback(mVoutCallBack);
        mPopupView = null;
    }

    public void showPopup() {
        mService.addCallback(this);
        mPopupView = new SurfaceView(mService);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.video_pip_width),
                VLCApplication.getAppResources().getDimensionPixelSize(R.dimen.video_pip_heigth),
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);

        params.gravity = Gravity.BOTTOM | Gravity.LEFT;
        params.x = 50;
        params.y = 50;

        mGestureDetector = new GestureDetectorCompat(mService, mGestureListener);
        mGestureDetector.setOnDoubleTapListener(this);
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mPopupView == null)
                    return false;
                if (mGestureDetector != null && mGestureDetector.onTouchEvent(event))
                    return true;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY - (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(mPopupView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(mPopupView, params);

        mService.setVideoTrackEnabled(true);
        final IVLCVout vlcVout = mService.getVLCVout();
        vlcVout.setVideoView(mPopupView);
        vlcVout.attachViews();
        vlcVout.addCallback(mVoutCallBack);
        if (!mService.isPlaying())
            mService.playIndex(mService.getCurrentMediaPosition());
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (mService.hasMedia()) {
            if (mService.isPlaying())
                mService.pause();
            else
                mService.play();
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        mService.removePopup();
        mService.switchToVideo();
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            Log.d(TAG, "onDown: ");
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            Log.d(TAG, "onShowPress: ");
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.d(TAG, "onSingleTapUp: ");
            //TODO switch to VideoplayerActivity
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.d(TAG, "onScroll: X "+distanceX+", Y "+distanceY);
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.d(TAG, "onLongPress: ");
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (Math.abs(velocityX) > FLING_STOP_VELOCITY || velocityY > FLING_STOP_VELOCITY) {
                mService.stop();
                return true;
            }
            return false;
        }
    };

    IVLCVout.Callback mVoutCallBack = new IVLCVout.Callback() {
        @Override
        public void onNewLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
            Log.d(TAG, "onNewLayout: "+(width * height));
            if (width * height == 0)
                return;
            int sw = mPopupView.getWidth();
            int sh = mPopupView.getHeight();
            vlcVout.setWindowSize(sw, sh);

            double dw = sw, dh = sh;

            // sanity check
            if (dw * dh == 0) {
                Log.e(TAG, "Invalid surface size");
                return;
            }

            // compute the aspect ratio
            double ar, vw;
            if (sarDen == sarNum) {
            /* No indication about the density, assuming 1:1 */
                vw = visibleWidth;
                ar = (double)visibleWidth / (double)visibleHeight;
            } else {
            /* Use the specified aspect ratio */
                vw = visibleWidth * (double)sarNum / sarDen;
                ar = vw / visibleHeight;
            }

            // compute the display aspect ratio
            double dar = dw / dh;
            if (dar < ar)
                dh = dw / ar;
            else
                dw = dh * ar;

            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) mPopupView.getLayoutParams();
            lp.width = (int) Math.floor(dw);
            lp.height = (int) Math.floor(dh);
            mPopupView.setLayoutParams(lp);
            mPopupView.invalidate();
        }

        @Override public void onSurfacesCreated(IVLCVout vlcVout) {}

        @Override public void onSurfacesDestroyed(IVLCVout vlcVout) {}

        @Override public void onHardwareAccelerationError(IVLCVout vlcVout) {}
    };

    @Override
    public void update() {

    }

    @Override
    public void updateProgress() {

    }

    @Override
    public void onMediaEvent(Media.Event event) {
    }

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Stopped:
            case MediaPlayer.Event.EndReached:
                mService.removePopup();
                break;
        }
    }
}
