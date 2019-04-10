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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import org.jetbrains.annotations.NotNull;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.view.PopupLayout;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.KextensionsKt;
import org.videolan.vlc.util.Settings;

import androidx.core.app.NotificationCompat;
import androidx.core.view.GestureDetectorCompat;

public class PopupManager implements PlaybackService.Callback, GestureDetector.OnDoubleTapListener,
        View.OnClickListener, GestureDetector.OnGestureListener, IVLCVout.OnNewVideoLayoutListener, IVLCVout.Callback {

    private static final String TAG ="VLC/PopupManager";

    private static final int FLING_STOP_VELOCITY = 3000;
    private static final int MSG_DELAY = 3000;

    private static final int SHOW_BUTTONS = 0;
    private static final int HIDE_BUTTONS = 1;

    private PlaybackService mService;

    private PopupLayout mRootView;
    private ImageView mExpandButton;
    private ImageView mCloseButton;
    private ImageView mPlayPauseButton;
    private final boolean mAlwaysOn;

    public PopupManager(PlaybackService service) {
        mService = service;
        mAlwaysOn = Settings.INSTANCE.getInstance(service).getBoolean("popup_keepscreen", false);
    }

    public void removePopup() {
        hideNotification();
        if (mRootView == null) return;
        mService.removeCallback(this);
        final IVLCVout vlcVout = mService.getVout();
        if (vlcVout != null) vlcVout.detachViews();
        mRootView.close();
        mRootView = null;
    }

    public void showPopup() {
        mService.addCallback(this);
        final LayoutInflater li = (LayoutInflater) mService.getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (li == null) return;
        mRootView = (PopupLayout) li.inflate(R.layout.video_popup, null);
        if (mAlwaysOn) mRootView.setKeepScreenOn(true);
        mPlayPauseButton = mRootView.findViewById(R.id.video_play_pause);
        mCloseButton = mRootView.findViewById(R.id.popup_close);
        mExpandButton = mRootView.findViewById(R.id.popup_expand);
        mPlayPauseButton.setOnClickListener(this);
        mCloseButton.setOnClickListener(this);
        mExpandButton.setOnClickListener(this);

        GestureDetectorCompat gestureDetector = new GestureDetectorCompat(mService, this);
        gestureDetector.setOnDoubleTapListener(this);
        mRootView.setGestureDetector(gestureDetector);

        final IVLCVout vlcVout = mService.getVout();
        if (vlcVout == null) return;
        vlcVout.setVideoView((SurfaceView) mRootView.findViewById(R.id.player_surface));
        vlcVout.addCallback(this);
        vlcVout.attachViews(this);
        mRootView.setVLCVOut(vlcVout);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (mPlayPauseButton.getVisibility() == View.VISIBLE) return false;
        mHandler.sendEmptyMessage(SHOW_BUTTONS);
        mHandler.sendEmptyMessageDelayed(HIDE_BUTTONS, MSG_DELAY);
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        expandToVideoPlayer();
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityX) > FLING_STOP_VELOCITY || velocityY > FLING_STOP_VELOCITY) {
            stopPlayback();
            return true;
        }
        return false;
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height,
                                 int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (mRootView == null) return;
        int displayW = mRootView.getWidth(), displayH = mRootView.getHeight();

        // sanity check
        if (displayW * displayH == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        if (width == 0 || height == 0) {
            mRootView.setViewSize(displayW, displayH);
            return;
        }

        // compute the aspect ratio
        double dw = displayW, dh = displayH;
        double ar;
        if (sarDen == sarNum) {
            /* No indication about the density, assuming 1:1 */
            ar = (double)visibleWidth / (double)visibleHeight;
        } else {
            /* Use the specified aspect ratio */
            double vw = visibleWidth * (double)sarNum / sarDen;
            ar = vw / visibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;
        if (dar < ar) dh = dw / ar;
        else dw = dh * ar;

        width = (int) Math.floor(dw);
        height = (int) Math.floor(dh);
        mRootView.setViewSize(width, height);
    }

    @Override
    public void update() {}

    @Override
    public void onMediaEvent(@NotNull Media.Event event) {}

    @Override
    public void onMediaPlayerEvent(@NotNull MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                if (mRootView != null) {
                    if (!mAlwaysOn) mRootView.setKeepScreenOn(true);
                    mPlayPauseButton.setImageResource(R.drawable.ic_popup_pause);
                }
                showNotification();
                break;
            case MediaPlayer.Event.Paused:
                if (mRootView != null) {
                    if (!mAlwaysOn) mRootView.setKeepScreenOn(false);
                    mPlayPauseButton.setImageResource(R.drawable.ic_popup_play);
                }
                showNotification();
                break;
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            int visibility = msg.what == SHOW_BUTTONS ? View.VISIBLE : View.GONE;
            mPlayPauseButton.setVisibility(visibility);
            mCloseButton.setVisibility(visibility);
            mExpandButton.setVisibility(visibility);
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.video_play_pause:
                if (mService.hasMedia()) {
                    if (mService.isPlaying())
                        mService.pause();
                    else
                        mService.play();
                }
                break;
            case R.id.popup_close:
                stopPlayback();
                break;
            case R.id.popup_expand:
                expandToVideoPlayer();
                break;
        }
    }

    private void expandToVideoPlayer() {
        removePopup();
        if (mService.hasMedia() && !mService.isPlaying())
            mService.getCurrentMediaWrapper().setFlags(MediaWrapper.MEDIA_PAUSED);
        mService.switchToVideo();
    }

    private void stopPlayback() {
        long time = mService.getTime();
        if (time != -1) {
            // remove saved position if in the last 5 seconds
            // else, go back 2 seconds, to compensate loading time
            time = mService.getLength() - time < 5000 ? 0 :  2000;
            // Save position
            if (mService.isSeekable())
                Settings.INSTANCE.getInstance(mService).edit()
                        .putLong(PreferencesActivity.VIDEO_RESUME_TIME, time).apply();
        }
        mService.stop();
    }

    @SuppressWarnings("deprecation")
    private void showNotification() {
        final PendingIntent piStop = KextensionsKt.getPendingIntent(mService, new Intent(Constants.ACTION_REMOTE_STOP));
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mService, "misc")
                .setSmallIcon(R.drawable.ic_notif_video)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(mService.getTitle())
                .setContentText(mService.getString(R.string.popup_playback))
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(mService.getSessionPendingIntent())
                .setDeleteIntent(piStop);

        //Switch
        final Intent notificationIntent = new Intent(Constants.ACTION_REMOTE_SWITCH_VIDEO);
        final PendingIntent piExpand = KextensionsKt.getPendingIntent(mService, notificationIntent);
        //PLay Pause
        final PendingIntent piPlay = KextensionsKt.getPendingIntent(mService, new Intent(Constants.ACTION_REMOTE_PLAYPAUSE));

        if (mService.isPlaying())
            builder.addAction(R.drawable.ic_popup_pause, mService.getString(R.string.pause), piPlay);
        else
            builder.addAction(R.drawable.ic_popup_play, mService.getString(R.string.play), piPlay);
        builder.addAction(R.drawable.ic_popup_expand_w, mService.getString(R.string.popup_expand), piExpand);
        mService.startForeground(42, builder.build());
    }

    private void hideNotification() {
        mService.stopForeground(true);
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
        mService.setVideoAspectRatio(null);
        mService.setVideoScale(0);
        mService.setVideoTrackEnabled(true);
        if (mService.hasMedia()) {
            mService.flush();
            mPlayPauseButton.setImageResource(mService.isPlaying() ? R.drawable.ic_popup_pause : R.drawable.ic_popup_play);
        } else
            mService.playIndex(mService.getCurrentMediaPosition());
        showNotification();
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
        vlcVout.removeCallback(this);
    }
}
