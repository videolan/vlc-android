/**
 * **************************************************************************
 * JumpToTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * <p/>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * ***************************************************************************
 */
package org.videolan.vlc.gui.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.R;

public abstract class PickTimeFragment extends DialogFragment implements DialogInterface.OnKeyListener, View.OnClickListener, View.OnFocusChangeListener, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/PickTimeFragment";

    public static final int ACTION_JUMP_TO_TIME = 0;
    public static final int ACTION_SPU_DELAY = 1;
    public static final int ACTION_AUDIO_DELAY = 2;
    protected int mTextColor;

    protected static long MILLIS_IN_MICROS = 1000;
    protected static long SECONDS_IN_MICROS = 1000 * MILLIS_IN_MICROS;
    protected static long MINUTES_IN_MICROS = 60*SECONDS_IN_MICROS;
    protected static long HOURS_IN_MICROS = 60*MINUTES_IN_MICROS;

    protected LibVLC mLibVLC = null;
    protected TextView mHours, mMinutes, mSeconds, mMillis;
    protected Button mActionButton, mSign;

    public PickTimeFragment(){}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        try {
            mLibVLC = LibVLC.getInstance();
        } catch (LibVlcException e) {
            getDialog().dismiss();
        }
        View view = inflater.inflate(R.layout.jump_to_time, container);
        ((TextView)view.findViewById(R.id.jump_dialog_title)).setText(getTitle());
        mHours = (TextView) view.findViewById(R.id.jump_hours);
        mMinutes = (TextView) view.findViewById(R.id.jump_minutes);
        mSeconds = (TextView) view.findViewById(R.id.jump_seconds);
        mMillis = (TextView) view.findViewById(R.id.jump_millis);
        mActionButton = (Button) view.findViewById(R.id.jump_go);
        mSign = (Button) view.findViewById(R.id.jump_sign);

        mMinutes.setOnFocusChangeListener(this);
        mSeconds.setOnFocusChangeListener(this);

        mMinutes.setOnEditorActionListener(this);
        mSeconds.setOnEditorActionListener(this);

        mActionButton.setOnClickListener(this);

        mTextColor = mMinutes.getCurrentTextColor();

        view.findViewById(R.id.jump_minutes_up).setOnClickListener(this);
        view.findViewById(R.id.jump_minutes_down).setOnClickListener(this);
        view.findViewById(R.id.jump_seconds_up).setOnClickListener(this);
        view.findViewById(R.id.jump_seconds_down).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().setOnKeyListener(this);
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        getDialog().getWindow().setBackgroundDrawableResource(R.drawable.rounded_corners);
        return view;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        ((TextView)v).setTextColor(hasFocus ? getResources().getColor(R.color.darkorange) : mTextColor);
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN)
            return false;
        switch (keyCode){
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                updateViews(keyCode);
                return true;
//            case KeyEvent.KEYCODE_DPAD_CENTER:
//            case KeyEvent.KEYCODE_ENTER:
//            case KeyEvent.KEYCODE_NUMPAD_ENTER:
//                executeAction();
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.jump_hours_up:
                updateValue(1, R.id.jump_hours);
                break;
            case R.id.jump_hours_down:
                updateValue(-1, R.id.jump_hours);
                break;
            case R.id.jump_minutes_up:
                updateValue(1, R.id.jump_minutes);
                break;
            case R.id.jump_minutes_down:
                updateValue(-1, R.id.jump_minutes);
                break;
            case R.id.jump_seconds_up:
                updateValue(1, R.id.jump_seconds);
                break;
            case R.id.jump_seconds_down:
                updateValue(-1, R.id.jump_seconds);
                break;
            case R.id.jump_millis_up:
                updateValue(50, R.id.jump_millis);
                break;
            case R.id.jump_millis_down:
                updateValue(-50, R.id.jump_millis);
                break;
            case R.id.jump_go:
                buttonAction();
                break;
            case R.id.jump_sign:
                toggleSign();
                executeAction();
                break;
        }
    }

    private void toggleSign() {
        if (mSign.getText().equals("+"))
            mSign.setText("-");
        else
            mSign.setText("+");
    }

    private void updateViews(int keyCode){
        int delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
        int id = 0;
        if (mMillis.hasFocus()) {
            id = mMillis.getId();
            delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 50 : -50;
        } else if (mSeconds.hasFocus())
            id = mSeconds.getId();
        else if  (mMinutes.hasFocus())
            id = mMinutes.getId();
        else if (mHours.hasFocus())
            id = mHours.getId();
        updateValue(delta, id);
    }

    private void updateValue(int delta, int resId) {
        int max = 59;
        String format = "%02d";
        long length = mLibVLC.getLength() * 1000; //work in µSeconds
        TextView edit = null;
        switch(resId){
            case R.id.jump_hours:
                edit = mHours;
                if (length < 59 * HOURS_IN_MICROS)
                    max = (int) (length/ HOURS_IN_MICROS);
                break;
            case R.id.jump_minutes:
                edit = mMinutes;
                if (this instanceof JumpToTimeDialog) {
                    if (mHours != null)
                        length -= Long.decode(mHours.getText().toString()).longValue() * HOURS_IN_MICROS;
                    if (length < 59 * MINUTES_IN_MICROS)
                        max = (int) (length / MINUTES_IN_MICROS);
                }
                break;
            case R.id.jump_seconds:
                edit = mSeconds;
                if (this instanceof JumpToTimeDialog) {
                    if (mHours != null)
                        length -= Long.decode(mHours.getText().toString()).longValue() * HOURS_IN_MICROS;
                    length -= Long.decode(mMinutes.getText().toString()).longValue() * MINUTES_IN_MICROS;
                    if (length < 59000)
                        max = (int) (length / SECONDS_IN_MICROS);
                }
                break;
            case R.id.jump_millis:
                edit = mMillis;
                max = 999;
                format = "%03d";
                break;
        }
        if (edit != null) {
            int value = Integer.parseInt(edit.getText().toString()) + delta;
            if (value < 0)
                value = max;
            else if (value > max)
                value = 0;
            edit.setText(String.format(format, value));

            if (this instanceof AudioDelayDialog || this instanceof SubsDelayDialog)
                executeAction();
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        executeAction();
        return true;
    }

    protected void initTime(long delay) {
        if (delay < 0l){
            delay = -delay;
            mSign.setText("-");
        } else
            mSign.setText("+");
        long minutes = 0;
        long seconds = 0;
        long millis = 0;
        if (delay != 0) {
            minutes = delay / MINUTES_IN_MICROS;
            seconds = (delay - minutes * MINUTES_IN_MICROS)/ SECONDS_IN_MICROS;
            millis = (delay - minutes * MINUTES_IN_MICROS - seconds * SECONDS_IN_MICROS)/ MILLIS_IN_MICROS;
        }
        mMinutes.setText(String.format("%02d", minutes));
        mSeconds.setText(String.format("%02d", seconds));
        mMillis.setText(String.format("%03d", millis));
    }

    abstract protected int getTitle();
    abstract protected void executeAction();
    abstract protected void buttonAction();
}
