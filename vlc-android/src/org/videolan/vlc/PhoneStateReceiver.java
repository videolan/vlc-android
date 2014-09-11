/*****************************************************************************
 * PhoneStateReceiver.java
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

package org.videolan.vlc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class PhoneStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (state.equals(TelephonyManager.EXTRA_STATE_RINGING) ||
                state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
            Intent newIntent = new Intent(VLCApplication.INCOMING_CALL_INTENT);
            VLCApplication.getAppContext().sendBroadcast(newIntent);
        }

        if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
            Intent newIntent = new Intent(VLCApplication.CALL_ENDED_INTENT);
            VLCApplication.getAppContext().sendBroadcast(newIntent);
        }
    }

}
