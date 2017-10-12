/*****************************************************************************
 * RemoteControlClientReceiver.java
 * ****************************************************************************
 * Copyright © 2012-2017 VLC authors and VideoLAN
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
package org.videolan.vlc;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.media.session.MediaButtonReceiver;
import android.view.KeyEvent;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Util;

/**
 * Small class to receive events passed out by the remote controls (wired, bluetooth, lock screen, ...)
 */
public class RemoteControlClientReceiver extends MediaButtonReceiver {
    @SuppressWarnings("unused")
    private static final String TAG = "VLC/RemoteControlClientReceiver";

    /* It should be safe to use static variables here once registered via the AudioManager */
    private static long mHeadsetDownTime = 0;
    private static long mHeadsetUpTime = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event != null && action.equalsIgnoreCase(Intent.ACTION_MEDIA_BUTTON)) {

            if (event.getKeyCode() != KeyEvent.KEYCODE_HEADSETHOOK &&
                    event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                    event.getAction() != KeyEvent.ACTION_DOWN) {
                super.onReceive(context, intent);
                return;
            }

            Intent i = null;
            switch (event.getKeyCode()) {
            /*
             * one click => play/pause
             * long click => previous
             * double click => next
             */
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    long time = SystemClock.uptimeMillis();
                    switch (event.getAction()) {
                        case KeyEvent.ACTION_DOWN:
                            if (event.getRepeatCount() <= 0)
                                mHeadsetDownTime = time;
                            break;
                        case KeyEvent.ACTION_UP:
                            if (AndroidDevices.hasTsp) { //no backward/forward on TV
                                if (time - mHeadsetDownTime >= 1000) { // long click
                                    i = new Intent(Constants.ACTION_REMOTE_BACKWARD, null, context, PlaybackService.class);
                                    break;
                                } else if (time - mHeadsetUpTime <= 500) { // double click
                                    i = new Intent(Constants.ACTION_REMOTE_FORWARD, null, context, PlaybackService.class);
                                    break;
                                }
                            }
                            // one click
                            i = new Intent(Constants.ACTION_REMOTE_PLAYPAUSE, null, context, PlaybackService.class);
                            mHeadsetUpTime = time;
                            break;
                    }
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    i = new Intent(Constants.ACTION_REMOTE_PLAY, null, context, PlaybackService.class);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    i = new Intent(Constants.ACTION_REMOTE_PAUSE, null, context, PlaybackService.class);
                    break;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    i = new Intent(Constants.ACTION_REMOTE_STOP, null, context, PlaybackService.class);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    i = new Intent(Constants.ACTION_REMOTE_FORWARD, null, context, PlaybackService.class);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    i = new Intent(Constants.ACTION_REMOTE_BACKWARD, null, context, PlaybackService.class);
                    break;
            }

            if (isOrderedBroadcast())
                abortBroadcast();
            if (i != null) {
                Util.startService(context, i);
                return;
            }
        } else if (action.equals(Constants.ACTION_REMOTE_PLAYPAUSE)) {
            intent = new Intent(context, PlaybackService.class);
            intent.setAction(Constants.ACTION_REMOTE_PLAYPAUSE);
            Util.startService(context, intent);
            return;
        }
        if (!AndroidUtil.isOOrLater) //We need AppCompat 26+ for Oreo service management
            super.onReceive(context, intent);
    }
}
