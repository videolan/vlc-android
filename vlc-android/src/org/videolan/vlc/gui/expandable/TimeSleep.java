/*****************************************************************************
 * TimeSleep.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
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

package org.videolan.vlc.gui.expandable;

import java.util.Calendar;

import kankan.wheel.widget.WheelView;
import kankan.wheel.widget.adapters.NumericWheelAdapter;

import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.widget.ExpandableLayout;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class TimeSleep extends ExpandableLayout {
    public final static String TAG = "VLC/TimeSleep";

    private final WheelView mHourWheel;
    private final WheelView mMinWheel;
    private static Calendar mTime = null;

    public TimeSleep(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTitle(R.string.sleep_title);
        setIcon(Util.getResourceFromAttribute(context, R.attr.ic_sleep_normal_style));
        setContent(context, R.layout.expandable_time_sleep);

        mHourWheel = (WheelView) findViewById(R.id.hour);
        mMinWheel = (WheelView) findViewById(R.id.min);
        final Button okButton = (Button) findViewById(R.id.ok);
        final Button cancelButton = (Button) findViewById(R.id.cancel);

        mHourWheel.setViewAdapter(new NumericWheelAdapter(context, 0, 23, "%02d"));
        mHourWheel.setCyclic(true);
        mMinWheel.setViewAdapter(new NumericWheelAdapter(context, 0, 59, "%02d"));
        mMinWheel.setCyclic(true);
        okButton.setOnClickListener(mOnOkListener);
        cancelButton.setOnClickListener(mOnCancelListener);

        if (mTime != null && mTime.before(Calendar.getInstance()))
            mTime = null;
        Calendar c = mTime != null ? mTime : Calendar.getInstance();
        mHourWheel.setCurrentItem(c.get(Calendar.HOUR_OF_DAY));
        mMinWheel.setCurrentItem(c.get(Calendar.MINUTE));
        setText();
    }

    private OnClickListener mOnOkListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Calendar currentTime = Calendar.getInstance();
            Calendar sleepTime = Calendar.getInstance();
            sleepTime.set(Calendar.HOUR_OF_DAY, mHourWheel.getCurrentItem());
            sleepTime.set(Calendar.MINUTE, mMinWheel.getCurrentItem());
            sleepTime.set(Calendar.SECOND, 0);
            if (sleepTime.before(currentTime))
                sleepTime.roll(Calendar.DATE, true);

            setSleep(v.getContext(), sleepTime);
            setText();
        }
    };

    private OnClickListener mOnCancelListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            cancelSleep(v.getContext());
            setText();
        }
    };

    private void setText() {
        setText(mTime != null ? DateFormat.getTimeFormat(getContext()).format(mTime.getTime()) : null);
    }

    public static void setSleep(Context context, Calendar time) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(VLCApplication.SLEEP_INTENT);
        PendingIntent sleepPendingIntent = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (time != null) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), sleepPendingIntent);
            Log.i(TAG, "VLC will sleep at " + time.getTime().toString());
        }
        else {
            alarmMgr.cancel(sleepPendingIntent);
            Log.i(TAG, "Sleep cancelled");
        }
        mTime = time;
    }

    public static void cancelSleep(Context context) {
        setSleep(context, null);
    }
}
