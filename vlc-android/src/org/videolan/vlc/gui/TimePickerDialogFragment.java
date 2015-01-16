/**
 * **************************************************************************
 * TimePickerDialogFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
 * ***************************************************************************
 */
package org.videolan.vlc.gui;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.video.AdvOptionsDialog;

import java.util.Calendar;

public class TimePickerDialogFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

    public static final int ACTION_SLEEP = 0;
    public static final int ACTION_JUMP = 1;

    boolean setTime = !LibVlcUtil.isICSOrLater() || LibVlcUtil.isLolliPopOrLater();
    int action =-1;

    public TimePickerDialogFragment(){}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        action = getArguments().getInt("action");
        boolean is24 = true;
        int hour = 0;
        int minute = 0;
        if (action == ACTION_SLEEP) {
            // Use the current time as the default values for the picker
            final Calendar c = VLCApplication.sPlayerSleepTime != null ?
                    VLCApplication.sPlayerSleepTime : Calendar.getInstance();
            hour = c.get(Calendar.HOUR_OF_DAY);
            minute = c.get(Calendar.MINUTE);
            is24 = DateFormat.is24HourFormat(getActivity());
        }

        // Create a new instance of TimePickerDialog and return it
        return new TimePickerDialog(getActivity(), this, hour, minute, is24);
    }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().setCancelable(true);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        if (!setTime){  //workaround for weird ICS&JB bug
            setTime = true;
            return;
        }
        Calendar currentTime = Calendar.getInstance();
        Calendar sleepTime = Calendar.getInstance();
        sleepTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
        sleepTime.set(Calendar.MINUTE, minute);
        sleepTime.set(Calendar.SECOND, 0);
        switch(action){
            case ACTION_SLEEP:
                if (sleepTime.before(currentTime))
                    sleepTime.roll(Calendar.DATE, true);

                AdvOptionsDialog.setSleep(view.getContext(), sleepTime);
                break;
            case ACTION_JUMP:
                long time = (long)((hourOfDay*60+minute)*60000);
                LibVLC.getExistingInstance().setTime(time);
                break;
        }
    }
}
