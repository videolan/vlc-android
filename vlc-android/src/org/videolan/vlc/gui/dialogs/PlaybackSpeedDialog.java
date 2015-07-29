/**
 * **************************************************************************
 * PickTimeFragment.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
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
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;

public class PlaybackSpeedDialog extends DialogFragment implements PlaybackService.Client.Callback {

    public final static String TAG = "VLC/PlaybackSpeedDialog";

    private TextView mSpeedValue;
    private SeekBar mSeekSpeed;
    private ImageView mResetSpeed;

    protected PlaybackService mService;

    public PlaybackSpeedDialog() {
    }

    public static PlaybackSpeedDialog newInstance(int theme) {
        PlaybackSpeedDialog myFragment = new PlaybackSpeedDialog();

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
        View view = inflater.inflate(R.layout.dialog_playback_speed, container);
        mSpeedValue = (TextView) view.findViewById(R.id.playback_speed_value);
        mSeekSpeed = (SeekBar) view.findViewById(R.id.playback_speed_seek);
        mResetSpeed = (ImageView) view.findViewById(R.id.playback_speed_reset);

        mSeekSpeed.setOnSeekBarChangeListener(mSeekBarListener);
        mResetSpeed.setOnClickListener(mResetListener);

        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        Window window = getDialog().getWindow();
        window.setBackgroundDrawableResource(Util.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        return view;
    }


    private void setRateProgress() {
        double speed = mService.getRate();
        if (speed != 1.0d) {
            speed = 100 * (1 + Math.log(speed) / Math.log(4));
            mSeekSpeed.setProgress((int) speed);
        }
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (mService == null)
                return;

            float rate = (float) Math.pow(4, ((double) progress / (double) 100) - 1);
            mSpeedValue.setText(Strings.formatRateString(rate));
            mService.setRate(rate);
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private View.OnClickListener mResetListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mService == null)
                return;

            mSeekSpeed.setProgress(100);
            mService.setRate(1);
        }
    };

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
        setRateProgress();
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

}
