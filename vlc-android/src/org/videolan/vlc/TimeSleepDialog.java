package org.videolan.vlc;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.widget.TimePicker;

public class TimeSleepDialog extends TimePickerDialog{

    public final static String TAG = "VLC/TimeSleepDialog";

    private static PendingIntent mSleepPendingIntent = null;
    private static Context mContext;

    private TimeSleepDialog(Context context,
            OnTimeSetListener callBack, int hourOfDay, int minute,
            boolean is24HourView) {
        super(context, callBack, hourOfDay, minute, is24HourView);

    }

    public TimeSleepDialog (Context context, int hourOfDay, int minute) {
        this (context, onTimeSetListener (context), hourOfDay, minute, true);
        setCanceledOnTouchOutside(true);
        setTitle(R.string.sleep_title);
        setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.sleep_cancel), mSleepCancelListener);
        show();
    }

    private static OnTimeSetListener onTimeSetListener (Context context) {
        mContext = context;
        return new OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                AlarmManager alarmMgr = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(VLCApplication.getAppContext(), SleepAlarmReceiver.class);
                mSleepPendingIntent = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, intent, 0);
                Calendar currentTime = Calendar.getInstance();
                currentTime.setTimeInMillis(System.currentTimeMillis());
                Calendar sleepTime = Calendar.getInstance();
                sleepTime.setTimeInMillis(System.currentTimeMillis());
                sleepTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                sleepTime.set(Calendar.MINUTE, minute);
                sleepTime.set(Calendar.SECOND, 0);
                if (sleepTime.before(currentTime))
                    sleepTime.roll(Calendar.DATE, true);
                Log.i(TAG, "Vlc will sleep at " + sleepTime.getTime().toString());
                alarmMgr.set(AlarmManager.RTC_WAKEUP, sleepTime.getTimeInMillis(), mSleepPendingIntent);
            }
        };
    }

    private final DialogInterface.OnClickListener mSleepCancelListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (mSleepPendingIntent!=null) {
                Log.i(TAG,"Cancel Sleep");
                AlarmManager alarmMgr = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
                alarmMgr.cancel(mSleepPendingIntent);
            }
        }
    };
}
