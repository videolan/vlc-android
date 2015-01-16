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

public class JumpToTimeFragment extends DialogFragment implements DialogInterface.OnKeyListener, View.OnClickListener, View.OnFocusChangeListener, TextView.OnEditorActionListener {

    private static long HOURS_IN_MILLIS = 60*60*1000;
    private static long MINUTES_IN_MILLIS = 60*1000;

    LibVLC mLibVLC = null;
    EditText mHours, mMinutes, mSeconds;
    public JumpToTimeFragment(){}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        try {
            mLibVLC = LibVLC.getInstance();
        } catch (LibVlcException e) {
            getDialog().dismiss();
        }
        View view = inflater.inflate(R.layout.jump_to_time, container);
        mHours = (EditText) view.findViewById(R.id.jump_hours);
        mMinutes = (EditText) view.findViewById(R.id.jump_minutes);
        mSeconds = (EditText) view.findViewById(R.id.jump_seconds);
        if (mLibVLC.getLength() < HOURS_IN_MILLIS) {
            view.findViewById(R.id.jump_hours_text).setVisibility(View.GONE);
            view.findViewById(R.id.jump_hours_container).setVisibility(View.GONE);
        }

        mHours.setOnFocusChangeListener(this);
        mMinutes.setOnFocusChangeListener(this);
        mSeconds.setOnFocusChangeListener(this);

        mHours.setOnEditorActionListener(this);
        mMinutes.setOnEditorActionListener(this);
        mSeconds.setOnEditorActionListener(this);

        view.findViewById(R.id.jump_hours_up).setOnClickListener(this);
        view.findViewById(R.id.jump_hours_down).setOnClickListener(this);
        view.findViewById(R.id.jump_minutes_up).setOnClickListener(this);
        view.findViewById(R.id.jump_minutes_down).setOnClickListener(this);
        view.findViewById(R.id.jump_seconds_up).setOnClickListener(this);
        view.findViewById(R.id.jump_seconds_down).setOnClickListener(this);
        view.findViewById(R.id.jump_go).setOnClickListener(this);

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
                go();
                break;
        }
    }

    private void updateViews(int keyCode){
        int delta = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1;
        int id = 0;
        if (mHours.hasFocus())
            id = mHours.getId();
        else if  (mMinutes.hasFocus())
            id = mMinutes.getId();
         else if (mSeconds.hasFocus())
            id = mSeconds.getId();
        updateValue(delta, id);
    }

    private void updateValue(int delta, int resId) {
        int max = 59;
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
                length -= Long.decode(mHours.getText().toString()).longValue() * HOURS_IN_MILLIS;
                if (length < 59 * MINUTES_IN_MILLIS)
                    max = (int) (length/MINUTES_IN_MILLIS);
                break;
            case R.id.jump_seconds:
                length -= Long.decode(mHours.getText().toString()).longValue() * HOURS_IN_MILLIS;
                length -= Long.decode(mMinutes.getText().toString()).longValue() * MINUTES_IN_MILLIS;
                if (length < 59000)
                    max = (int) (length/1000);
                edit = mSeconds;
        }
        if (edit != null) {
            int value = Integer.parseInt(edit.getText().toString()) + delta;
            if (value < 0)
                value = max;
            else if (value > max)
                value = 0;
            edit.setText(String.format("%02d", value));
        }
    }

    private void go() {
        long hours = Long.parseLong(mHours.getText().toString());
        long minutes = Long.parseLong(mMinutes.getText().toString());
        long seconds = Long.parseLong(mSeconds.getText().toString());
        LibVLC.getExistingInstance().setTime((hours * HOURS_IN_MILLIS +
                           minutes * MINUTES_IN_MILLIS +
                           seconds * 1000));
        dismiss();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        go();
        return true;
    }
}
