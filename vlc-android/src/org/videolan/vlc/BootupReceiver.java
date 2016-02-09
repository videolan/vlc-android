/*****************************************************************************
 * BootupReceiver.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
 * Author: Geoffrey Métais
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.videolan.vlc.util.AndroidDevices;

public class BootupReceiver extends BroadcastReceiver {
    public BootupReceiver() {
    }
    private static final String TAG = "VLC/BootupReceiver";

    private static final long INITIAL_DELAY = 5000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AndroidDevices.isAndroidTv() && intent.getAction().endsWith(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "ACTION_BOOT_COMPLETED ");
            scheduleRecommendationUpdate(context);
        }
    }

    private void scheduleRecommendationUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) VLCApplication.getAppContext().getSystemService(
                Context.ALARM_SERVICE);
        Intent recommendationIntent = new Intent(context,
                RecommendationsService.class);
        PendingIntent alarmIntent = PendingIntent.getService(context, 0,
                recommendationIntent, 0);

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                INITIAL_DELAY,
                AlarmManager.INTERVAL_HOUR,
                alarmIntent);
    }
}
