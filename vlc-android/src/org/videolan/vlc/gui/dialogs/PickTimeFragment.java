/**
 * **************************************************************************
 * JumpToTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
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
package org.videolan.vlc.gui.dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.videolan.vlc.BuildConfig;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.util.Util;

public abstract class PickTimeFragment extends DialogFragment implements DialogInterface.OnKeyListener,
        View.OnClickListener, View.OnFocusChangeListener, TextView.OnEditorActionListener,
        PlaybackService.Client.Callback {

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

    protected EditText mHours, mMinutes, mSeconds, mMillis;
    protected TextView mSign;
    protected Button mActionButton;
    private long mMax = -1;
    protected PlaybackService mService;

    public PickTimeFragment(){
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.attr.advanced_options_style);
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
        if (BuildConfig.tv){
            mHours.setInputType(InputType.TYPE_NULL);
            mMinutes.setInputType(InputType.TYPE_NULL);
            mSeconds.setInputType(InputType.TYPE_NULL);
            mMillis.setInputType(InputType.TYPE_NULL);
            mHours.setOnClickListener(this);
            mMinutes.setOnClickListener(this);
            mSeconds.setOnClickListener(this);
            mMillis.setOnClickListener(this);
        }

        getDialog().setOnKeyListener(this);
        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        Window window = getDialog().getWindow();
        window.setBackgroundDrawableResource(Util.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
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
            case R.id.jump_sign:
                if (mService != null) {
                    toggleSign();
                    executeAction();
                }
                break;
            case R.id.jump_go:
            case R.id.jump_hours:
            case R.id.jump_minutes:
            case R.id.jump_seconds:
            case R.id.jump_millis:
                buttonAction();
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
        if (mMax == -1 || slide <= mMax)
            initTime(slide);
        if (mLiveAction && mService != null)
            executeAction();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (mService != null)
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
        long minutes = TextUtils.isEmpty(mMinutes.getText().toString()) ? 0l : Long.parseLong(mMinutes.getText().toString());
        long seconds = TextUtils.isEmpty(mSeconds.getText().toString()) ? 0l : Long.parseLong(mSeconds.getText().toString());
        long millis = TextUtils.isEmpty(mMillis.getText().toString()) ? 0l : Long.parseLong(mMillis.getText().toString());
        return sign * (minutes * MINUTES_IN_MICROS + seconds * SECONDS_IN_MICROS + millis * MILLIS_IN_MICROS);
    }

    @Override
    public void onStart() {
        super.onStart();
        PlaybackServiceFragment.registerPlaybackService(this, this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PlaybackServiceFragment.unregisterPlaybackService(this, this);
    }

    @Override
    public void onConnected(PlaybackService service) {
        mService = service;
        mMax = getMax();
        initTime(getInitTime());
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    abstract protected int getTitle();
    abstract protected void executeAction();
    abstract protected void buttonAction();
    abstract protected long getMax();
    abstract protected long getInitTime();
}
