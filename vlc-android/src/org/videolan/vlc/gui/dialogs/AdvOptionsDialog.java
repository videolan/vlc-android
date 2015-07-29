/**
 * **************************************************************************
 * AdvOptionsDialog.java
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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.interfaces.IDelayController;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WeakHandler;

import java.util.Calendar;

import static org.videolan.vlc.gui.dialogs.PickTimeFragment.ACTION_JUMP_TO_TIME;
import static org.videolan.vlc.gui.dialogs.PickTimeFragment.ACTION_SLEEP_TIMER;

public class AdvOptionsDialog extends DialogFragment implements View.OnClickListener, PlaybackService.Client.Callback {

    public final static String TAG = "VLC/AdvOptionsDialog";
    public static final String MODE_KEY = "mode";
    public static final int MODE_VIDEO = 0;
    public static final int MODE_AUDIO = 1;

    public static final int SPEED_TEXT = 0;
    public static final int SLEEP_TEXT = 1;
    public static final int TOGGLE_CANCEL = 2;
    public static final int DIALOG_LISTENER = 3;
    public static final int RESET_RETRY = 4;

    public static final int ACTION_AUDIO_DELAY = 2 ;
    public static final int ACTION_SPU_DELAY = 3 ;

    private int mMode = -1;
    private TextView mAudioMode;
    private TextView mEqualizer;

    private TextView mPlaybackSpeedValue;
    private ImageView mPlaybackSpeed;

    private TextView mSleepTitle;
    private TextView mSleepTime;
    private TextView mSleepCancel;

    private TextView mJumpTitle;

    private TextView mAudioDelay;
    private TextView mSpuDelay;

    private TextView mChaptersTitle;
    private int mTextColor;
    private PlaybackService mService;

    private IDelayController mDelayController;
    public AdvOptionsDialog() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.attr.advanced_options_style);
        if (VLCApplication.sPlayerSleepTime != null && VLCApplication.sPlayerSleepTime.before(Calendar.getInstance()))
            VLCApplication.sPlayerSleepTime = null;
        if (getArguments() != null && getArguments().containsKey(MODE_KEY))
            mMode = getArguments().getInt(MODE_KEY);
        else
            mMode = MODE_VIDEO;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mMode == MODE_VIDEO) {
            mDelayController = (IDelayController) activity;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_advanced_options, container, false);
        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);


        mPlaybackSpeedValue = (TextView) root.findViewById(R.id.playback_speed_value);
        mPlaybackSpeed = (ImageView) root.findViewById(R.id.playback_speed_icon);
        mPlaybackSpeed.setOnClickListener(this);

        mSleepTitle = (TextView) root.findViewById(R.id.sleep_timer_title);
        mSleepTime = (TextView) root.findViewById(R.id.sleep_timer_value);
        mSleepCancel = (TextView) root.findViewById(R.id.sleep_timer_cancel);
        mJumpTitle = (TextView) root.findViewById(R.id.jump_title);

        mJumpTitle.setOnClickListener(this);

        mSleepTitle.setOnClickListener(this);
        mSleepTime.setOnClickListener(this);
        mSleepCancel.setOnClickListener(this);

        mSleepTime.setOnFocusChangeListener(mFocusListener);
        mSleepCancel.setOnFocusChangeListener(mFocusListener);
        mJumpTitle.setOnFocusChangeListener(mFocusListener);

        if (mMode == MODE_VIDEO) {
            mAudioMode = (TextView) root.findViewById(R.id.playback_switch_audio);
            mAudioMode.setOnClickListener(this);
            mAudioMode.setOnFocusChangeListener(mFocusListener);

            mChaptersTitle = (TextView) root.findViewById(R.id.jump_chapter_title);
            mChaptersTitle.setOnClickListener(this);

            mAudioDelay = (TextView) root.findViewById(R.id.audio_delay);
            mSpuDelay = (TextView) root.findViewById(R.id.spu_delay);

            mSpuDelay.setOnClickListener(this);
            mSpuDelay.setOnFocusChangeListener(mFocusListener);
            mAudioDelay.setOnClickListener(this);
            mAudioDelay.setOnFocusChangeListener(mFocusListener);
        } else {
            root.findViewById(R.id.audio_delay).setVisibility(View.GONE);
            root.findViewById(R.id.spu_delay).setVisibility(View.GONE);
            root.findViewById(R.id.jump_chapter_title).setVisibility(View.GONE);
            root.findViewById(R.id.playback_switch_audio).setVisibility(View.GONE);
        }

        if (mMode == MODE_AUDIO){
            mEqualizer = (TextView) root.findViewById(R.id.opt_equalizer);
            mEqualizer.setOnClickListener(this);
            mEqualizer.setOnFocusChangeListener(mFocusListener);
        } else
            root.findViewById(R.id.opt_equalizer).setVisibility(View.GONE);
        mHandler.sendEmptyMessage(TOGGLE_CANCEL);
        mTextColor = mSleepTitle.getCurrentTextColor();

        Window window = getDialog().getWindow();
        window.setBackgroundDrawableResource(Util.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        return root;
    }

    private void showTimePickerFragment(int action) {
        DialogFragment newFragment = null;
        switch (action){
            case PickTimeFragment.ACTION_JUMP_TO_TIME:
                newFragment = new JumpToTimeDialog();
                break;
            case PickTimeFragment.ACTION_SLEEP_TIMER:
                newFragment = new SleepTimerDialog();
                break;
            default:
                return;
        }
        if (newFragment != null)
            newFragment.show(getActivity().getSupportFragmentManager(), "time");
        dismiss();
    }

    private void showPlayBackSpeedDialog() {
        DialogFragment newFragment = null;
        newFragment = new PlaybackSpeedDialog();
        if (newFragment != null)
            newFragment.show(getActivity().getSupportFragmentManager(), "playback_speed");
        dismiss();
    }

    private void showSelectChapterDialog() {
        DialogFragment newFragment = null;
        newFragment = new SelectChapterDialog();
        if (newFragment != null)
            newFragment.show(getActivity().getSupportFragmentManager(), "select_chapter");
        dismiss();
    }

    private void showAudioSpuDelayControls(int action) {
        if (mDelayController == null && getActivity() instanceof IDelayController)
            mDelayController = (IDelayController) getActivity();
        switch (action){
            case ACTION_AUDIO_DELAY:
                if (mDelayController != null)
                    mDelayController.showAudioDelaySetting();
                break;
            case ACTION_SPU_DELAY:
                if (mDelayController != null)
                    mDelayController.showSubsDelaySetting();
                break;
            default:
                return;
        }
        dismiss();
    }

    View.OnFocusChangeListener mFocusListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (v instanceof TextView)
                ((TextView) v).setTextColor(v.hasFocus() ?
                        getResources().getColor(R.color.orange500) : mTextColor);
        }
    };

    public static void setSleep(Calendar time) {
        AlarmManager alarmMgr = (AlarmManager) VLCApplication.getAppContext().getSystemService(Context.ALARM_SERVICE);
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

    private final Handler mHandler = new AdvOptionsDialogHandler(this);

    private static class AdvOptionsDialogHandler extends WeakHandler<AdvOptionsDialog> {

        public boolean retry = true;

        public AdvOptionsDialogHandler(AdvOptionsDialog owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            String text = null;
            AdvOptionsDialog owner = getOwner();
            if (owner == null || owner.isDetached())
                return;
            switch (msg.what) {
                case SPEED_TEXT:
                    text = (String) msg.obj;
                    owner.mPlaybackSpeedValue.setText(text);
                    break;
                case TOGGLE_CANCEL:
                    owner.mSleepCancel.setVisibility(VLCApplication.sPlayerSleepTime == null ? View.GONE : View.VISIBLE);
                case SLEEP_TEXT:
                    if (VLCApplication.sPlayerSleepTime != null)
                        text = DateFormat.getTimeFormat(owner.mSleepTime.getContext()).format(VLCApplication.sPlayerSleepTime.getTime());
                    if (text == null)
                        text = VLCApplication.getAppResources().getString(R.string.sleep_time_not_set);
                    owner.mSleepTime.setText(text);
                    break;
                case DIALOG_LISTENER:
                    DialogFragment newFragment = (DialogFragment) msg.obj;
                    if (newFragment.getShowsDialog()) {
                        newFragment.getDialog().setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                obtainMessage(TOGGLE_CANCEL).sendToTarget();
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
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.playback_speed_icon:
                showPlayBackSpeedDialog();
                break;
            case R.id.jump_chapter_title:
                showSelectChapterDialog();
                break;
            case R.id.audio_delay:
                showAudioSpuDelayControls(ACTION_AUDIO_DELAY);
                break;
            case R.id.spu_delay:
                showAudioSpuDelayControls(ACTION_SPU_DELAY);
                break;
            case R.id.jump_title:
                showTimePickerFragment(ACTION_JUMP_TO_TIME);
                break;
            case R.id.sleep_timer_title:
            case R.id.sleep_timer_value:
                showTimePickerFragment(ACTION_SLEEP_TIMER);
                break;
            case R.id.sleep_timer_cancel:
                setSleep(null);
                mHandler.sendEmptyMessage(TOGGLE_CANCEL);
                break;
            case R.id.playback_switch_audio:
                ((VideoPlayerActivity)getActivity()).switchToAudioMode(true);
                break;
            case R.id.opt_equalizer:
                Intent i = new Intent(getActivity(), SecondaryActivity.class);
                i.putExtra("fragment", SecondaryActivity.EQUALIZER);
                startActivity(i);
                dismiss();
                break;
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

        mPlaybackSpeedValue.setText(Strings.formatRateString(mService.getRate()));

        if (mMode == MODE_VIDEO) {
            final MediaPlayer.Chapter[] chapters = mService.getChapters(-1);
            if (chapters != null) {
                mChaptersTitle.setText(chapters[mService.getChapterIdx()].name);
            } else
                mChaptersTitle.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
