/*****************************************************************************
 * JumpToTimeDialog.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
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

import org.videolan.vlc.LibVLC;
import org.videolan.vlc.R;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class JumpToTimeDialog extends Dialog {
    public final static String TAG = "VLC/JumpToTimeDialog";

    private EditText mHour;
    private EditText mMinute;
    private EditText mSeconds;

    public JumpToTimeDialog(Context context, long currentTime) {
        super(context);
        setTitle(R.string.jump_to_time);

        LayoutInflater inflator = LayoutInflater.from(context);
        View view = inflator.inflate(R.layout.jumptotime, null);
        setContentView(view);

        TextView hourLabel = (TextView)findViewById(R.id.hour_label);
        mHour = (EditText)findViewById(R.id.hour);
        mMinute = (EditText)findViewById(R.id.minute);
        mSeconds = (EditText)findViewById(R.id.second);

        mHour.addTextChangedListener(new JumpToTimeWatcher());
        mMinute.addTextChangedListener(new JumpToTimeWatcher());
        mSeconds.addTextChangedListener(new JumpToTimeWatcher());

        // Set current time
        long seconds_total = currentTime / 1000;
        Long seconds = seconds_total % 60;
        Long minutes = (seconds_total - seconds) / 60;
        Short hours = 0;

        // Hide hour box if the clip isn't an hour long
        // 60 minutes = 1000 ms * 60 seconds * 60 minutes
        if(currentTime < 1000*60*60) {
            mHour.setVisibility(View.GONE);
            hourLabel.setVisibility(View.GONE);
            // Compute hours
            long minutes_t = minutes;
            minutes = minutes_t % 60;
            hours = (short) ((minutes_t - minutes) / 60);
        }

        mHour.setText(hours.toString());
        mMinute.setText(minutes.toString());
        mSeconds.setText(seconds.toString());

        Button plus = (Button)findViewById(R.id.plus);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mHour.isFocused()) {
                    incrementHour();
                } else if(mMinute.isFocused()) {
                    incrementMinute();
                } else if(mSeconds.isFocused()) {
                    incrementSecond();
                }
            }
        });
        Button minus = (Button)findViewById(R.id.minus);
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mHour.isFocused()) {
                    decrementHour();
                } else if(mMinute.isFocused()) {
                    decrementMinute();
                } else if(mSeconds.isFocused()) {
                    decrementSecond();
                }
            }
        });

        Button ok = (Button)findViewById(R.id.ok);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String hour, min, sec;
                hour = mHour.getText().toString();
                min = mMinute.getText().toString();
                sec = mSeconds.getText().toString();
                if(hour.equals(""))
                    hour = "0";
                if(min.equals(""))
                    min = "0";
                if(sec.equals(""))
                    sec = "0";
                LibVLC.getExistingInstance().setTime(
                        1000 * (Integer.parseInt(hour)*60*60 +
                                Integer.parseInt(min)*60 +
                                Integer.parseInt(sec)
                                )
                        );
                JumpToTimeDialog.this.dismiss();
            }
        });
        Button cancel = (Button)findViewById(R.id.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { JumpToTimeDialog.this.dismiss(); }
        });
    }

    private class JumpToTimeWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { }
        @Override
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) { }
        @Override
        public void afterTextChanged(Editable s) {
            String text = s.toString();
            if(text.equals("")) return;

            Short value = null;
            try {
                Short text_value = Short.valueOf(text);
                if(text_value > 59)
                    value = 59;
                else if(text_value < 0)
                    value = 0;
            } catch(NumberFormatException e) {
                value = 0;
            }
            // change required
            if(value != null) {
                s.delete(0, s.length());
                s.append(value.toString());
            }
        }
    }

    private void incrementHour() {
        Integer hour;
        try {
            hour = Integer.valueOf(mHour.getText().toString()) + 1;
        } catch(NumberFormatException e) {
            hour = 1;
        }
        mHour.setText(hour.toString());
    };

    private void incrementMinute() {
        Integer newMinute;
        try {
            newMinute = Integer.valueOf((mMinute.getText().toString())) + 1;
        } catch(NumberFormatException e) {
            newMinute = 1;
        }
        if(newMinute >= 60) {
            newMinute = 0;
        }
        mMinute.setText(newMinute.toString());
        if(mHour.getVisibility() != View.GONE && newMinute >= 60) {
            incrementHour();
        }
    }

    private void incrementSecond() {
        Integer newSecond;
        try {
            newSecond = Integer.valueOf((mSeconds.getText().toString())) + 1;
        } catch(NumberFormatException e) {
            newSecond = 1;
        }
        if(newSecond >= 60) {
            newSecond = 0;
            incrementMinute();
        }
        mSeconds.setText(newSecond.toString());
    }

    private void decrementHour() {
        Integer hour;
        try {
            hour = Integer.valueOf(mHour.getText().toString()) - 1;
        } catch(NumberFormatException e) {
            hour = 0;
        }
        if(hour < 0)
            hour = 0;
        mHour.setText(hour.toString());
    }

    private void decrementMinute() {
        Integer newMinute;
        try {
            newMinute = Integer.valueOf((mMinute.getText().toString())) - 1;
        } catch(NumberFormatException e) {
            newMinute = 0;
        }
        if(newMinute < 0) {
            newMinute = 0;
        }
        mMinute.setText(newMinute.toString());
    }

    private void decrementSecond() {
        Integer newSecond;
        try {
            newSecond = Integer.valueOf((mSeconds.getText().toString())) - 1;
        } catch(NumberFormatException e) {
            newSecond = 0;
        }
        if(newSecond < 0) {
            newSecond = 0;
        }
        mSeconds.setText(newSecond.toString());
    }
}
