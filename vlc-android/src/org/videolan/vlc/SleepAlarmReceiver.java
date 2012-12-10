package org.videolan.vlc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class SleepAlarmReceiver extends BroadcastReceiver {

    public final static String TAG = "VLC/SleepAlarmReceiver";

    public final static String SLEEP_INTENT = "org.videolan.vlc.SleepIntent";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null)
            return;
        Log.i(TAG,"VLC is about to sleep");
        Intent intentSleep = new Intent();
        intentSleep.setAction(SLEEP_INTENT);
        context.getApplicationContext().sendBroadcast(intentSleep);
    }
}

