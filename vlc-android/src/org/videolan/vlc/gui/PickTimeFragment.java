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
package org.videolan.vlc.gui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.vlc.R;

public class PickTimeFragment extends DialogFragment implements DialogInterface.OnKeyListener, View.OnClickListener, View.OnFocusChangeListener, TextView.OnEditorActionListener {

    public final static String TAG = "VLC/PickTimeFragment";

    public static String ACTION = "action";
    public static int ACTION_JUMP_TO_TIME = 0;
    public static int ACTION_SPU_DELAY = 1;
    public static int ACTION_AUDIO_DELAY = 2;
    private int mAction = -1;

    private static long HOURS_IN_MILLIS = 60*60*1000;
    private static long MINUTES_IN_MILLIS = 60*1000;

    LibVLC mLibVLC = null;
    EditText mHours, mMinutes, mSeconds;

    public PickTimeFragment(){}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        try {
            mLibVLC = LibVLC.getInstance();
        } catch (LibVlcException e) {
            getDialog().dismiss();
        }
        mAction = getArguments().getInt(ACTION);
        View view = inflater.inflate(R.layout.jump_to_time, container);
        ((TextView)view.findViewById(R.id.jump_dialog_title)).setText(getTitle());
        mMinutes = (EditText) view.findViewById(R.id.jump_minutes);
        mSeconds = (EditText) view.findViewById(R.id.jump_seconds);

        mMinutes.setOnFocusChangeListener(this);
        mSeconds.setOnFocusChangeListener(this);

        mMinutes.setOnEditorActionListener(this);
        mSeconds.setOnEditorActionListener(this);

        if (mAction == ACTION_JUMP_TO_TIME) {
            if (mLibVLC.getLength() > HOURS_IN_MILLIS) {
                mHours = (EditText) view.findViewById(R.id.jump_hours);
                mHours.setOnFocusChangeListener(this);
                mHours.setOnEditorActionListener(this);
                view.findViewById(R.id.jump_hours_up).setOnClickListener(this);
                view.findViewById(R.id.jump_hours_down).setOnClickListener(this);
            } else {
                view.findViewById(R.id.jump_hours_text).setVisibility(View.GONE);
                view.findViewById(R.id.jump_hours_container).setVisibility(View.GONE);
            }
            view.findViewById(R.id.jump_go).setOnClickListener(this);
        } else {
            view.findViewById(R.id.jump_hours_text).setVisibility(View.GONE);
            view.findViewById(R.id.jump_hours_container).setVisibility(View.GONE);
            view.findViewById(R.id.jump_go).setVisibility(View.GONE);
            long delay = 0l;
            if (mAction == ACTION_AUDIO_DELAY)
                delay = mLibVLC.getAudioDelay();
            else if (mAction == ACTION_SPU_DELAY)
                delay = mLibVLC.getSpuDelay();
            if (delay != 0f)
                initTime(delay);
        }

        view.findViewById(R.id.jump_minutes_up).setOnClickListener(this);
        view.findViewById(R.id.jump_minutes_down).setOnClickListener(this);
        view.findViewById(R.id.jump_seconds_up).setOnClickListener(this);
        view.findViewById(R.id.jump_seconds_down).setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().setOnKeyListener(this);
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return view;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        ((EditText)v).setSelection(((EditText)v).getText().length());
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
            case R.id.jump_go:
                jumpToTime();
                break;
        }
    }

    private void updateViews(int keyCode){
        int delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
        int id = 0;
        if (mSeconds.hasFocus())
            id = mSeconds.getId();
        else if  (mMinutes.hasFocus())
            id = mMinutes.getId();
        else if (mHours != null && mHours.hasFocus())
            id = mHours.getId();
        updateValue(delta, id);
    }

    private void updateValue(int delta, int resId) {
        int max = 59, min = -59;
        long length = mLibVLC.getLength();
        EditText edit = null;
        switch(resId){
            case R.id.jump_hours:
                edit = mHours;
                if (length < 59 * HOURS_IN_MILLIS)
                    max = (int) (length/HOURS_IN_MILLIS);
                break;
            case R.id.jump_minutes:
                edit = mMinutes;
                if (mAction == ACTION_JUMP_TO_TIME) {
                    if (mHours != null)
                        length -= Long.decode(mHours.getText().toString()).longValue() * HOURS_IN_MILLIS;
                    if (length < 59 * MINUTES_IN_MILLIS)
                        max = (int) (length / MINUTES_IN_MILLIS);
                    min = 0;
                }
                break;
            case R.id.jump_seconds:
                if (mAction == ACTION_JUMP_TO_TIME) {
                    if (mHours != null)
                        length -= Long.decode(mHours.getText().toString()).longValue() * HOURS_IN_MILLIS;
                    length -= Long.decode(mMinutes.getText().toString()).longValue() * MINUTES_IN_MILLIS;
                    if (length < 59000)
                        max = (int) (length / 1000);
                    min = 0;
                }
                edit = mSeconds;
        }
        if (edit != null) {
            int value = Integer.parseInt(edit.getText().toString()) + delta;
            if (value < min)
                value = max;
            else if (value > max)
                value = min;
            edit.setText(String.format("%02d", value));

            if (mAction == ACTION_AUDIO_DELAY)
                setAudioDelay();
            else if (mAction == ACTION_SPU_DELAY)
                setSpuDelay();
        }
    }

    private void jumpToTime() {
        long hours = mHours != null ? Long.parseLong(mHours.getText().toString()) : 0l;
        long minutes = Long.parseLong(mMinutes.getText().toString());
        long seconds = Long.parseLong(mSeconds.getText().toString());
        LibVLC.getExistingInstance().setTime((hours * HOURS_IN_MILLIS +
                           minutes * MINUTES_IN_MILLIS +
                           seconds * 1000));
        dismiss();
    }

    private void setSpuDelay(){
        long minutes = Long.parseLong(mMinutes.getText().toString());
        long seconds = Long.parseLong(mSeconds.getText().toString());
        mLibVLC.setSpuDelay(minutes * MINUTES_IN_MILLIS + seconds * 1000);
        Log.d(TAG, "setting spu delay to: " + (minutes * MINUTES_IN_MILLIS + seconds * 1000));
    }

    private void setAudioDelay(){
        long minutes = Long.parseLong(mMinutes.getText().toString());
        long seconds = Long.parseLong(mSeconds.getText().toString());
        mLibVLC.setAudioDelay(minutes * MINUTES_IN_MILLIS + seconds * 1000);
        Log.d(TAG, "setting audio delay to: "+(minutes * MINUTES_IN_MILLIS + seconds * 1000));
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        jumpToTime();
        return true;
    }

    private void initTime(long delay) {
        long minutes = delay / MINUTES_IN_MILLIS;
        long seconds = (delay - minutes * MINUTES_IN_MILLIS)/ 1000;
        mMinutes.setText(String.format("%02d", minutes));
        mSeconds.setText(String.format("%02d", seconds));
    }

    private int getTitle() {
        if (mAction == ACTION_AUDIO_DELAY)
            return R.string.audio_delay;
        else if (mAction == ACTION_SPU_DELAY)
            return R.string.spu_delay;
        else
            return R.string.jump_to_time;
    }
}
