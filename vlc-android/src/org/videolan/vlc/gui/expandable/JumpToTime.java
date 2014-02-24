/*****************************************************************************
 * JumpToTime.java
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
import java.util.TimeZone;

import kankan.wheel.widget.WheelView;
import kankan.wheel.widget.adapters.NumericWheelAdapter;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.widget.ExpandableLayout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

public class JumpToTime extends ExpandableLayout {
    public final static String TAG = "VLC/JumpToTime";

    private final WheelView mHourWheel;
    private final WheelView mMinWheel;
    private final WheelView mSecWheel;

    public JumpToTime(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTitle(R.string.jump_to_time);
        setIcon(Util.getResourceFromAttribute(context, R.attr.ic_jumpto_normal_style));
        setContent(context, R.layout.expandable_jump_to_time);

        mHourWheel = (WheelView) findViewById(R.id.hour);
        mMinWheel = (WheelView) findViewById(R.id.min);
        mSecWheel = (WheelView) findViewById(R.id.sec);
        final View colon = findViewById(R.id.colon);
        final Button okButton = (Button) findViewById(R.id.ok);
        okButton.setOnClickListener(mOnOkListener);

        long currentTime = !isInEditMode() ? AudioServiceController.getInstance().getTime() : 0;
        int length = !isInEditMode() ? AudioServiceController.getInstance().getLength() : 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

        // Limit wheel values
        c.setTimeInMillis(length);
        int hours = c.get(Calendar.HOUR_OF_DAY);
        int minutes = c.get(Calendar.MINUTE);
        int seconds = c.get(Calendar.SECOND);
        boolean isCyclic = false;
        mHourWheel.setCyclic(isCyclic);
        mHourWheel.setViewAdapter(new NumericWheelAdapter(context, 0, isCyclic ? 23 : hours, "%02d"));
        mMinWheel.setCyclic(isCyclic = isCyclic || hours > 0);
        mMinWheel.setViewAdapter(new NumericWheelAdapter(context, 0, isCyclic ? 59 : minutes, "%02d"));
        mSecWheel.setCyclic(isCyclic = isCyclic || minutes > 0);
        mSecWheel.setViewAdapter(new NumericWheelAdapter(context, 0, isCyclic ? 59 : seconds, "%02d"));
        if (hours == 0) {
            mHourWheel.setVisibility(View.GONE);
            colon.setVisibility(View.GONE);
        }

        // Set current time
        c.setTimeInMillis(currentTime);
        mHourWheel.setCurrentItem(c.get(Calendar.HOUR_OF_DAY));
        mMinWheel.setCurrentItem(c.get(Calendar.MINUTE));
        mSecWheel.setCurrentItem(c.get(Calendar.SECOND));
    }

    private OnClickListener mOnOkListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            LibVLC.getExistingInstance().setTime(
                    1000 * (mHourWheel.getCurrentItem() * 60 * 60 +
                            mMinWheel.getCurrentItem() * 60 +
                            mSecWheel.getCurrentItem()));
            dismiss();
        }
    };
}
