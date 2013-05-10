/*****************************************************************************
 * RemoteControlClientReceiver.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Small class to receive events passed out by the remote controls (wired, bluetooth, lock screen, ...)
 */
public class RemoteControlClientReceiver extends BroadcastReceiver {
    @SuppressWarnings("unused")
    private static final String TAG = "VLC/RemoteControlClientReceiver";

    /* It should be safe to use static variables here once registered via the AudioManager */
    private static long mHeadsetDownTime = 0;
    private static long mHeadsetUpTime = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        LibVLC mLibVLC;
        try {
            mLibVLC = Util.getLibVlcInstance();
        } catch (LibVlcException e) {
            return;
        }
        if(mLibVLC == null)
            return;

        if(action.equalsIgnoreCase(Intent.ACTION_MEDIA_BUTTON)) {

            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null)
                return;

            if (event.getKeyCode() != KeyEvent.KEYCODE_HEADSETHOOK &&
                event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                event.getAction() != KeyEvent.ACTION_DOWN)
                return;

            Intent i = null;
            switch (event.getKeyCode())
            {
            /*
             * one click => play/pause
             * long click => previous
             * double click => next
             */
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    long time = SystemClock.uptimeMillis();
                    switch (event.getAction())
                    {
                        case KeyEvent.ACTION_DOWN:
                            if (event.getRepeatCount() > 0)
                                break;
                            mHeadsetDownTime = time;
                            break;
                        case KeyEvent.ACTION_UP:
                            // long click
                            if (time - mHeadsetDownTime >= 1000) {
                                i = new Intent(AudioService.ACTION_REMOTE_BACKWARD);
                                time = 0;
                                // double click
                            } else if (time - mHeadsetUpTime <= 500) {
                                i = new Intent(AudioService.ACTION_REMOTE_FORWARD);
                            }
                            // one click
                            else {
                                if (mLibVLC.isPlaying())
                                    i = new Intent(AudioService.ACTION_REMOTE_PAUSE);
                                else
                                    i = new Intent(AudioService.ACTION_REMOTE_PLAY);
                            }
                            mHeadsetUpTime = time;
                            break;
                    }
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    i = new Intent(AudioService.ACTION_REMOTE_PLAY);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    i = new Intent(AudioService.ACTION_REMOTE_PAUSE);
                    break;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    i = new Intent(AudioService.ACTION_REMOTE_STOP);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    i = new Intent(AudioService.ACTION_REMOTE_FORWARD);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    i = new Intent(AudioService.ACTION_REMOTE_BACKWARD);
                    break;
            }

            if (isOrderedBroadcast())
                abortBroadcast();
            if(i != null)
                context.sendBroadcast(i);
        }
    }
}
