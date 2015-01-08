/**
 * **************************************************************************
 * AdvOptionsDialog.java
 * ****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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
package org.videolan.vlc.gui.video;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.TimePickerDialogFragment;
import org.videolan.vlc.util.Strings;

import java.util.Calendar;

public class AdvOptionsDialog extends DialogFragment{

    public final static String TAG = "VLC/AdvOptionsDialog";
    public static final int SPEED_TEXT = 0;
    public static final int SLEEP_TEXT = 1;
    public static final int TOGGLE_CANCEL = 2;
    public static final int DIALOG_LISTENER = 3;
    public static final int RESET_RETRY = 4;

    private TextView mSpeedTv;
    private SeekBar mSeek;
    private Button mReset;

    private ImageView mSleepIcon;
    private TextView mSleepTitle;
    private TextView mSleepTime;
    private TextView mSleepCancel;

    private ImageView mJumpIcon;
    private TextView mJumpTitle;
    private static AdvOptionsDialog sInstance;

    public AdvOptionsDialog() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        setStyle(STYLE_NO_FRAME, R.style.Base_Theme_AppCompat_Dialog);
        if (VLCApplication.sPlayerSleepTime != null && VLCApplication.sPlayerSleepTime.before(Calendar.getInstance()))
            VLCApplication.sPlayerSleepTime = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_advanced_options, container, false);

        mSeek = (SeekBar) root.findViewById(R.id.playback_speed_seek);
        mSpeedTv = (TextView) root.findViewById(R.id.playback_speed_value);
        mReset = (Button) root.findViewById(R.id.playback_speed_reset);

        mSeek.setOnSeekBarChangeListener(mSeekBarListener);
        mReset.setOnClickListener(mResetListener);

        mSleepIcon = (ImageView) root.findViewById(R.id.sleep_timer_icon);
        mSleepTitle = (TextView) root.findViewById(R.id.sleep_timer_title);
        mSleepTime = (TextView) root.findViewById(R.id.sleep_timer_value);
        mSleepCancel = (TextView) root.findViewById(R.id.sleep_timer_cancel);

        mSleepIcon.setOnClickListener(mSleepListener);
        mSleepTitle.setOnClickListener(mSleepListener);
        mSleepTime.setOnClickListener(mSleepListener);
        mSleepCancel.setOnClickListener(mSleepCancelListener);

        mJumpIcon = (ImageView) root.findViewById(R.id.jump_icon);
        mJumpTitle = (TextView) root.findViewById(R.id.jump_title);

        mJumpIcon.setOnClickListener(mJumpListener);
        mJumpTitle.setOnClickListener(mJumpListener);


        getDialog().setCancelable(true);
        mHandler.sendEmptyMessage(TOGGLE_CANCEL);
        return root;
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float rate = (float) Math.pow(4, ((double) progress / (double) 100) - 1);
            mHandler.obtainMessage(SPEED_TEXT, Strings.formatRateString(rate)).sendToTarget();
            LibVLC.getExistingInstance().setRate(rate);
        }

        public void onStartTrackingTouch(SeekBar seekBar) {}
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    private View.OnClickListener mResetListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mSeek.setProgress(100);
            LibVLC.getExistingInstance().setRate(1);
        }
    };

    View.OnClickListener mSleepListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            showTimePicker(TimePickerDialogFragment.ACTION_SLEEP);
        }
    };

    View.OnClickListener mSleepCancelListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            setSleep(v.getContext(), null);
            mHandler.sendEmptyMessage(TOGGLE_CANCEL);
        }
    };

    View.OnClickListener mJumpListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            showTimePicker(TimePickerDialogFragment.ACTION_JUMP);
        }
    };

    private void showTimePicker(int action) {
        DialogFragment newFragment = new TimePickerDialogFragment();
        Bundle args = new Bundle();
        args.putInt("action", action);
        newFragment.setArguments(args);
        newFragment.show(getActivity().getSupportFragmentManager(), "timePicker");
        mHandler.sendEmptyMessage(RESET_RETRY);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(DIALOG_LISTENER, newFragment), 100);
    }

    public static void setSleep(Context context, Calendar time) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(VLCApplication.SLEEP_INTENT);
        PendingIntent sleepPendingIntent = PendingIntent.getBroadcast(VLCApplication.getAppContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (time != null) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), sleepPendingIntent);
        }
        else {
            alarmMgr.cancel(sleepPendingIntent);
        }
        VLCApplication.sPlayerSleepTime = time;
    }

    private final static Handler mHandler = new Handler(){

        public boolean retry = true;

        @Override
        public void handleMessage(Message msg) {
            String text = null;
            switch (msg.what) {
                case SPEED_TEXT:
                    text = (String) msg.obj;
                    sInstance.mSpeedTv.setText(text);
                    break;
                case TOGGLE_CANCEL:
                    sInstance.mSleepCancel.setVisibility(VLCApplication.sPlayerSleepTime == null ? View.GONE : View.VISIBLE);
                case SLEEP_TEXT:
                    if (VLCApplication.sPlayerSleepTime != null)
                        text = DateFormat.getTimeFormat(sInstance.mSleepTime.getContext()).format(VLCApplication.sPlayerSleepTime.getTime());
                    if (text == null)
                        text = "none set";
                    sInstance.mSleepTime.setText(text);
                    break;
                case DIALOG_LISTENER:
                    DialogFragment newFragment = (DialogFragment) msg.obj;
                    if (newFragment.getShowsDialog()) {
                        newFragment.getDialog().setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                mHandler.obtainMessage(TOGGLE_CANCEL).sendToTarget();
                            }
                        });
                    } else if (retry) {
                        retry = false;
                        sendMessageDelayed(msg, 300);
                    }
                    break;
                case RESET_RETRY:
                    retry = true;
                    break;

            }
        }
    };
}
