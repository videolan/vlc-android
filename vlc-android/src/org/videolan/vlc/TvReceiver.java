/*****************************************************************************
 * TvReceiver.java
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

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Build;
import android.util.Log;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.TvChannelsKt;

public class TvReceiver extends BroadcastReceiver {

    private static final String TAG = "VLC/TvReceiver";

    private static final long INITIAL_DELAY = 5000;

    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "onReceive: "+action);
        if (action == null || !AndroidDevices.isAndroidTv) return;
        switch (action) {
            case TvContract.ACTION_INITIALIZE_PROGRAMS:
                Log.d(TAG, "onReceive: ACTION_INITIALIZE_PROGRAMS ");
                TvChannelsKt.setChannel(context);
                break;
            case TvContract.ACTION_PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT:
                final long preview_id = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L   );
                final long next_id = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L);
                Log.d(TAG, "onReceive: ACTION_PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT"+preview_id+", "+next_id);
                break;
            case TvContract.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED:
                final long preview_program_id = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L   );
                final long preview_internal_id = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L);
                Log.d(TAG, "onReceive: ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED, "+preview_program_id+", "+preview_internal_id);
                break;
            case TvContract.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED:
                final long watch_next_program_id = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L   );
                final long watch_next_internal_id = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L);
                Log.d(TAG, "onReceive: ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED, "+watch_next_program_id+", "+watch_next_internal_id);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                Log.d(TAG, "onReceive: ACTION_BOOT_COMPLETED ");
                if (!AndroidUtil.isOOrLater) scheduleRecommendationUpdate(context);
                if (AndroidDevices.watchDevices) ExternalMonitor.INSTANCE.register();
                break;
        }
    }

    private void scheduleRecommendationUpdate(Context context) {
        final AlarmManager alarmManager = (AlarmManager) context.getApplicationContext().getSystemService(
                Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        final Intent ri = new Intent(context, RecommendationsService.class);
        final PendingIntent pi = PendingIntent.getService(context, 0, ri, 0);

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, INITIAL_DELAY, AlarmManager.INTERVAL_HOUR, pi);
    }
}
