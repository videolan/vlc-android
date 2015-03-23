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

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.R;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.VLCInstance;

public abstract class PickTimeFragment extends DialogFragment implements DialogInterface.OnKeyListener, View.OnClickListener, View.OnFocusChangeListener, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/PickTimeFragment";

    public static final int ACTION_JUMP_TO_TIME = 0;
    public static final int ACTION_SPU_DELAY = 1;
    public static final int ACTION_AUDIO_DELAY = 2;
    protected int mTextColor;
    protected boolean mLiveAction = true;

    protected static long MILLIS_IN_MICROS = 1000;
    protected static long SECONDS_IN_MICROS = 1000 * MILLIS_IN_MICROS;
    protected static long MINUTES_IN_MICROS = 60*SECONDS_IN_MICROS;
    protected static long HOURS_IN_MICROS = 60*MINUTES_IN_MICROS;

    protected LibVLC mLibVLC = null;
    protected EditText mHours, mMinutes, mSeconds, mMillis;
    protected TextView mSign;
    protected Button mActionButton;
    protected long max = -1;

    public PickTimeFragment(){
        mLibVLC = VLCInstance.get();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.jump_to_time, container);
        ((TextView)view.findViewById(R.id.jump_dialog_title)).setText(getTitle());
        mHours = (EditText) view.findViewById(R.id.jump_hours);
        mMinutes = (EditText) view.findViewById(R.id.jump_minutes);
        mSeconds = (EditText) view.findViewById(R.id.jump_seconds);
        mMillis = (EditText) view.findViewById(R.id.jump_millis);
        mActionButton = (Button) view.findViewById(R.id.jump_go);
        mSign = (TextView) view.findViewById(R.id.jump_sign);

        mMinutes.setOnFocusChangeListener(this);
        mSeconds.setOnFocusChangeListener(this);

        mMinutes.setOnEditorActionListener(this);
        mSeconds.setOnEditorActionListener(this);

        mActionButton.setOnClickListener(this);
        mActionButton.setOnFocusChangeListener(this);

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
        ((TextView)v).setTextColor(hasFocus ? getResources().getColor(R.color.orange500) : mTextColor);
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
        long delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
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

    private void updateValue(long delta, int resId) {
        long slide = 0l;
        switch(resId) {
            case R.id.jump_hours:
                slide = delta * HOURS_IN_MICROS;
                break;
            case R.id.jump_minutes:
                slide = delta * MINUTES_IN_MICROS;
                break;
            case R.id.jump_seconds:
                slide = delta * SECONDS_IN_MICROS;
                break;
            case R.id.jump_millis:
                slide = delta * MILLIS_IN_MICROS;
        }
        slide += getTime();
        if (max == -1 || slide <= max)
            initTime(slide);
        if (mLiveAction)
            executeAction();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        executeAction();
        return true;
    }

    protected void initTime(long delay) {
        if (delay < 0l){
            if ( mSign.getVisibility() == View.VISIBLE) {
                delay = -delay;
                mSign.setText("-");
            } else {
                delay = 0l;
            }
        } else {
            mSign.setText("+");
        }
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

    protected long getTime(){
        long sign = mSign.getText().equals("-") ? -1 : 1;
        long minutes = Long.parseLong(mMinutes.getText().toString());
        long seconds = Long.parseLong(mSeconds.getText().toString());
        long millis = Long.parseLong(mMillis.getText().toString());
        return sign * (minutes * MINUTES_IN_MICROS + seconds * SECONDS_IN_MICROS + millis * MILLIS_IN_MICROS);
    }

    abstract protected int getTitle();
    abstract protected void executeAction();
    abstract protected void buttonAction();
}
