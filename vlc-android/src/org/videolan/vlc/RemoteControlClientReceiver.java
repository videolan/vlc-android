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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * Small class to receive events passed out by the lockscreen control.
 */
public class RemoteControlClientReceiver extends BroadcastReceiver {
    @SuppressWarnings("unused")
    private static final String TAG = "VLC/RemoteControlClientReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        LibVLC mLibVLC;
        try {
            mLibVLC = LibVLC.getInstance();
        } catch (LibVlcException e) {
            return;
        }
        if(mLibVLC == null)
            return;

        if(action.equalsIgnoreCase(Intent.ACTION_MEDIA_BUTTON)) {
            KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
            if (keyEvent.getAction() != KeyEvent.ACTION_DOWN)
                return;

            Intent i = null;
            switch (keyEvent.getKeyCode()) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    i = new Intent(AudioService.ACTION_WIDGET_PLAY);
                    break;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    i = new Intent(AudioService.ACTION_WIDGET_STOP);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    i = new Intent(AudioService.ACTION_WIDGET_FORWARD);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    i = new Intent(AudioService.ACTION_WIDGET_BACKWARD);
                    break;
            }
            if(i != null)
                context.sendBroadcast(i);
        }
    }
}
