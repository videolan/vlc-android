/*****************************************************************************
 * TimeSleepDialog.java
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
package org.videolan.vlc.gui;

import java.util.Calendar;

import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.widget.TimePicker;

public class TimeSleepDialog extends TimePickerDialog {
    public final static String TAG = "VLC/TimeSleepDialog";

    private int hourOfDay;
    protected int minute;

    private TimeSleepDialog(Context context,
            OnTimeSetListener callBack, int hourOfDay, int minute,
            boolean is24HourView) {
        super(context, callBack, hourOfDay, minute, is24HourView);
    }

    public TimeSleepDialog(Context context, int hourOfDay, int minute) {
        this(context, null, hourOfDay, minute, true);
        setCanceledOnTouchOutside(true);
        setTitle(R.string.sleep_title);
        setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.sleep_title), mSleepListener);
        setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.sleep_cancel), mSleepCancelListener);
        show();
    }

    @Override
    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        super.onTimeChanged(view, hourOfDay, minute);
        this.hourOfDay = hourOfDay;
        this.minute = minute;
    }

    private final DialogInterface.OnClickListener mSleepListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlarmManager alarmMgr = (AlarmManager) TimeSleepDialog.this.getContext().getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(VLCApplication.SLEEP_INTENT);
            PendingIntent sleepPendingIntent = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            Calendar currentTime = Calendar.getInstance();
            currentTime.setTimeInMillis(System.currentTimeMillis());
            Calendar sleepTime = Calendar.getInstance();
            sleepTime.setTimeInMillis(System.currentTimeMillis());
            sleepTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            sleepTime.set(Calendar.MINUTE, minute);
            sleepTime.set(Calendar.SECOND, 0);
            if (sleepTime.before(currentTime))
                sleepTime.roll(Calendar.DATE, true);

            Log.i(TAG, "VLC will sleep at " + sleepTime.getTime().toString());
            alarmMgr.set(AlarmManager.RTC_WAKEUP, sleepTime.getTimeInMillis(), sleepPendingIntent);
        }
    };

    private final DialogInterface.OnClickListener mSleepCancelListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.i(TAG, "Sleep cancelled");
            AlarmManager alarmMgr = (AlarmManager) TimeSleepDialog.this.getContext().getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(VLCApplication.SLEEP_INTENT);
            PendingIntent sleepPendingIntent = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmMgr.cancel(sleepPendingIntent);
        }
    };
}
