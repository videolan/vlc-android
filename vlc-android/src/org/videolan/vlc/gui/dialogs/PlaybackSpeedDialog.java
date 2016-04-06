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
import org.videolan.vlc.gui.helpers.OnRepeatListener;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.util.Strings;

public class PlaybackSpeedDialog extends DialogFragment implements PlaybackService.Client.Callback {

    public final static String TAG = "VLC/PlaybackSpeedDialog";

    private TextView mSpeedValue;
    private SeekBar mSeekSpeed;
    private ImageView mPlaybackSpeedIcon;
    private ImageView mPlaybackSpeedPlus;
    private ImageView mPlaybackSpeedMinus;

    protected PlaybackService mService;
    protected int mTextColor;

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
        mPlaybackSpeedIcon = (ImageView) view.findViewById(R.id.playback_speed_icon);
        mPlaybackSpeedPlus = (ImageView) view.findViewById(R.id.playback_speed_plus);
        mPlaybackSpeedMinus = (ImageView) view.findViewById(R.id.playback_speed_minus);

        mSeekSpeed.setOnSeekBarChangeListener(mSeekBarListener);
        mPlaybackSpeedIcon.setOnClickListener(mResetListener);
        mPlaybackSpeedPlus.setOnClickListener(mSpeedUpListener);
        mPlaybackSpeedMinus.setOnClickListener(mSpeedDownListener);
        mSpeedValue.setOnClickListener(mResetListener);
        mPlaybackSpeedMinus.setOnTouchListener(new OnRepeatListener(mSpeedDownListener));
        mPlaybackSpeedPlus.setOnTouchListener(new OnRepeatListener(mSpeedUpListener));

        mTextColor = mSpeedValue.getCurrentTextColor();


        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        Window window = getDialog().getWindow();
        window.setBackgroundDrawableResource(UiTools.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        return view;
    }


    private void setRateProgress() {
        double speed = mService.getRate();
        speed = 100 * (1 + Math.log(speed) / Math.log(4));
        mSeekSpeed.setProgress((int) speed);
        updateInterface();
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (mService == null)
                return;
            if (fromUser) {
                float rate = (float) Math.pow(4, ((double) progress / (double) 100) - 1);
                mService.setRate(rate, mService.getCurrentMediaWrapper().getType() == MediaWrapper.TYPE_AUDIO);
                updateInterface();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    private View.OnClickListener mResetListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mService == null || mService.getRate() == 1.0d || mService.getCurrentMediaWrapper() == null)
                return;

            mService.setRate(1F, mService.getCurrentMediaWrapper().getType() == MediaWrapper.TYPE_AUDIO);
            setRateProgress();
        }
    };

    private View.OnClickListener mSpeedUpListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mService == null)
                return;
            changeSpeed(0.05f);
            setRateProgress();
        }
    };

    private View.OnClickListener mSpeedDownListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mService == null)
                return;
            changeSpeed(-0.05f);
            setRateProgress();
        }
    };

    public void changeSpeed(float delta){
        double initialRate = Math.round(mService.getRate() * 100d) / 100d;
        if (delta>0)
            initialRate = Math.floor((initialRate + 0.005d) / 0.05d) * 0.05d;
        else
            initialRate = Math.ceil((initialRate - 0.005d) / 0.05d) * 0.05d;
        float rate = Math.round((initialRate + delta) * 100f) / 100f;
        if (rate < 0.25f || rate > 4f || mService.getCurrentMediaWrapper() == null)
            return;
        mService.setRate(rate, mService.getCurrentMediaWrapper().getType() == MediaWrapper.TYPE_AUDIO);
    }

    private void updateInterface() {
        float rate = mService.getRate();
        mSpeedValue.setText(Strings.formatRateString(rate));
        if (rate != 1.0f) {
            mPlaybackSpeedIcon.setImageResource(R.drawable.ic_speed_reset);
            mSpeedValue.setTextColor(getResources().getColor(R.color.orange500));
        } else {
            mPlaybackSpeedIcon.setImageResource(UiTools.getResourceFromAttribute(getActivity(), R.attr.ic_speed_normal_style));
            mSpeedValue.setTextColor(mTextColor);
        }

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
        setRateProgress();
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
