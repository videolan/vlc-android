/*
 * *************************************************************************
 *  SleepTimerDialog.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.dialogs;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.vlc.R;

import java.util.Calendar;

public class SleepTimerDialog extends PickTimeFragment {

    protected static long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

    public SleepTimerDialog() {
        super();
    }

    public static SleepTimerDialog newInstance(int theme) {
        SleepTimerDialog myFragment = new SleepTimerDialog();

        Bundle args = new Bundle();
        args.putInt("theme", theme);
        myFragment.setArguments(args);

        return myFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, getArguments().getInt("theme"));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mMaxTimeSize = 4;
        return view;
    }


    protected void executeAction() {
        long hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        long minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        long interval = (hours + minutes) / MILLIS_IN_MICROS; //Interval in ms

        if (interval < ONE_DAY_IN_MILLIS) {
            Calendar sleepTime = Calendar.getInstance();
            sleepTime.setTimeInMillis(Calendar.getInstance().getTimeInMillis() + interval);
            sleepTime.set(Calendar.SECOND, 0);
            AdvOptionsDialog.setSleep(sleepTime);
        }

        dismiss();
    }

    @Override
    protected int getTitle() {
        return R.string.sleep_in;
    }

    @Override
    protected int getIcon() {
        return R.attr.ic_sleep_normal_style;
    }

}