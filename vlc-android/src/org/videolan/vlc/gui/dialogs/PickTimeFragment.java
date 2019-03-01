/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.BottomSheetBehavior;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

public abstract class PickTimeFragment extends VLCBottomSheetDialogFragment implements View.OnClickListener, View.OnFocusChangeListener,
        Observer<PlaybackService> {

    public final static String TAG = "VLC/PickTimeFragment";


    protected int mTextColor;

    protected static long MILLIS_IN_MICROS = 1000;
    protected static long SECONDS_IN_MICROS = 1000 * MILLIS_IN_MICROS;
    protected static long MINUTES_IN_MICROS = 60 * SECONDS_IN_MICROS;
    protected static long HOURS_IN_MICROS = 60 * MINUTES_IN_MICROS;

    protected String mHours = "", mMinutes = "", mSeconds = "", mFormatTime, mRawTime = "";
    protected int mMaxTimeSize = 6;
    protected TextView mTVTimeToJump;

    protected PlaybackService mService;

    @Override
    public int getDefaultState() {
        return BottomSheetBehavior.STATE_EXPANDED;
    }

    @Override
    public boolean needToManageOrientation() {
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_time_picker, container);
        mTVTimeToJump = (TextView) view.findViewById(R.id.tim_pic_timetojump);
        ((TextView)view.findViewById(R.id.tim_pic_title)).setText(getTitle());

        view.findViewById(R.id.tim_pic_1).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_1).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_2).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_2).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_3).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_3).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_4).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_4).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_5).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_5).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_6).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_6).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_7).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_7).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_8).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_8).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_9).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_9).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_0).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_0).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_00).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_00).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_30).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_30).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_delete).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_delete).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_ok).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_ok).setOnFocusChangeListener(this);

        mTextColor = mTVTimeToJump.getCurrentTextColor();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        PlaybackService.Companion.getService().observe(this, this);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v instanceof TextView) {
            ((TextView) v).setTextColor(hasFocus ? getResources().getColor(R.color.orange500) : mTextColor);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.tim_pic_1:
                updateValue("1");
                break;
            case R.id.tim_pic_2:
                updateValue("2");
                break;
            case R.id.tim_pic_3:
                updateValue("3");
                break;
            case R.id.tim_pic_4:
                updateValue("4");
                break;
            case R.id.tim_pic_5:
                updateValue("5");
                break;
            case R.id.tim_pic_6:
                updateValue("6");
                break;
            case R.id.tim_pic_7:
                updateValue("7");
                break;
            case R.id.tim_pic_8:
                updateValue("8");
                break;
            case R.id.tim_pic_9:
                updateValue("9");
                break;
            case R.id.tim_pic_0:
                updateValue("0");
                break;
            case R.id.tim_pic_00:
                updateValue("00");
                break;
            case R.id.tim_pic_30:
                updateValue("30");
                break;
            case R.id.tim_pic_delete:
                deleteLastNumber();
                break;
            case R.id.tim_pic_ok:
                executeAction();
                break;
        }
    }

    private String getLastNumbers(String rawTime){
        if (rawTime.length() == 0)
            return "";
        return (rawTime.length() == 1) ?
                rawTime:
                rawTime.substring(rawTime.length()-2);
    }

    private String removeLastNumbers(String rawTime){
        return rawTime.length() <= 1 ? "" : rawTime.substring(0, rawTime.length()-2);
    }

    private void deleteLastNumber(){
        if (mRawTime != "") {
            mRawTime = mRawTime.substring(0, mRawTime.length()-1);
            updateValue("");
        }
    }

    private void updateValue(String value) {
        if (mRawTime.length() >= mMaxTimeSize)
            return;
        mRawTime = mRawTime.concat(value);
        String tempRawTime = mRawTime;
        mFormatTime = "";

        if (mMaxTimeSize > 4) {
            mSeconds = getLastNumbers(tempRawTime);
            if (mSeconds != "")
                mFormatTime = mSeconds + "s";
            tempRawTime = removeLastNumbers(tempRawTime);
        } else
            mSeconds = "";

        mMinutes = getLastNumbers(tempRawTime);
        if (mMinutes != "")
            mFormatTime = mMinutes + "m " + mFormatTime;
        tempRawTime = removeLastNumbers(tempRawTime);

        mHours = getLastNumbers(tempRawTime);
        if (mHours != "")
            mFormatTime = mHours + "h " + mFormatTime;

        mTVTimeToJump.setText(mFormatTime);
    }

    @Override
    public void onChanged(PlaybackService service) {
        mService = service;
    }

    abstract protected int getTitle();
    abstract protected void executeAction();
}
