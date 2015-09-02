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
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.Calendar;

import static org.videolan.vlc.gui.dialogs.PickTimeFragment.ACTION_JUMP_TO_TIME;
import static org.videolan.vlc.gui.dialogs.PickTimeFragment.ACTION_SLEEP_TIMER;

public class AdvOptionsDialog extends DialogFragment implements View.OnClickListener, View.OnLongClickListener, PlaybackService.Client.Callback {

    public final static String TAG = "VLC/AdvOptionsDialog";
    public static final String MODE_KEY = "mode";
    public static final int MODE_VIDEO = 0;
    public static final int MODE_AUDIO = 1;

    public static final int ACTION_AUDIO_DELAY = 2 ;
    public static final int ACTION_SPU_DELAY = 3 ;

    private Activity mActivity;
    private int mTheme;
    private int mMode = -1;
    private ImageView mPlayAsAudio;
    private TextView mEqualizer;

    private TextView mPlaybackSpeed;
    private TextView mSleep;

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
        if (VLCApplication.sPlayerSleepTime != null && VLCApplication.sPlayerSleepTime.before(Calendar.getInstance()))
            VLCApplication.sPlayerSleepTime = null;
        if (getArguments() != null && getArguments().containsKey(MODE_KEY))
            mMode = getArguments().getInt(MODE_KEY);
        else
            mMode = MODE_VIDEO;

        mTheme = (mMode == MODE_VIDEO || Util.isBlackThemeEnabled()) ?
                R.style.Theme_VLC_Black :
                R.style.Theme_VLC;
        setStyle(DialogFragment.STYLE_NO_FRAME, mTheme);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mMode == MODE_VIDEO) {
            mDelayController = (IDelayController) activity;
        }
        mActivity = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mDelayController = null;
        mActivity = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_advanced_options, container, false);
        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);


        mPlaybackSpeed = (TextView) root.findViewById(R.id.playback_speed);
        mPlaybackSpeed.setOnFocusChangeListener(mFocusListener);
        mPlaybackSpeed.setOnClickListener(this);
        mPlaybackSpeed.setOnLongClickListener(this);

        mSleep = (TextView) root.findViewById(R.id.sleep);
        mSleep.setOnClickListener(this);
        mSleep.setOnFocusChangeListener(mFocusListener);

        mJumpTitle = (TextView) root.findViewById(R.id.jump_title);
        mJumpTitle.setOnClickListener(this);

        if (mMode == MODE_VIDEO) {
            mPlayAsAudio = (ImageView) root.findViewById(R.id.play_as_audio_icon);
            mPlayAsAudio.setOnClickListener(this);

            mChaptersTitle = (TextView) root.findViewById(R.id.jump_chapter_title);
            mChaptersTitle.setOnFocusChangeListener(mFocusListener);
            mChaptersTitle.setOnClickListener(this);

            mAudioDelay = (TextView) root.findViewById(R.id.audio_delay);
            mAudioDelay.setOnFocusChangeListener(mFocusListener);
            mAudioDelay.setOnClickListener(this);

            mSpuDelay = (TextView) root.findViewById(R.id.spu_delay);
            mSpuDelay.setOnFocusChangeListener(mFocusListener);
            mSpuDelay.setOnClickListener(this);
        } else {
            root.findViewById(R.id.audio_delay).setVisibility(View.GONE);
            root.findViewById(R.id.spu_delay).setVisibility(View.GONE);
            root.findViewById(R.id.jump_chapter_title).setVisibility(View.GONE);
            root.findViewById(R.id.play_as_audio_icon).setVisibility(View.GONE);
        }

        if (mMode == MODE_AUDIO){
            mEqualizer = (TextView) root.findViewById(R.id.opt_equalizer);
            mEqualizer.setOnClickListener(this);
        } else
            root.findViewById(R.id.opt_equalizer).setVisibility(View.GONE);

        mTextColor = mSleep.getCurrentTextColor();

        if (getDialog() != null) {
            int dialogWidth = getResources().getDimensionPixelSize(mMode == MODE_VIDEO ?
                    R.dimen.adv_options_video_width:
                    R.dimen.adv_options_music_width);
            int dialogHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(dialogWidth, dialogHeight);
            getDialog().getWindow().setBackgroundDrawableResource(Util.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));
        }
        return root;
    }

    private void showTimePickerFragment(int action) {
        DialogFragment newFragment = null;
        switch (action){
            case PickTimeFragment.ACTION_JUMP_TO_TIME:
                newFragment = JumpToTimeDialog.newInstance(mTheme);
                break;
            case PickTimeFragment.ACTION_SLEEP_TIMER:
                newFragment = SleepTimerDialog.newInstance(mTheme);
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
        newFragment = PlaybackSpeedDialog.newInstance(mTheme);
        if (newFragment != null)
            newFragment.show(getActivity().getSupportFragmentManager(), "playback_speed");
        dismiss();
    }

    private void showSelectChapterDialog() {
        DialogFragment newFragment = null;
        newFragment = SelectChapterDialog.newInstance(mTheme);
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
                        getResources().getColor(R.color.orange300) : mTextColor);
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

    public void initPlaybackSpeed () {
        if (mService.getRate() == 1.0f) {
            mPlaybackSpeed.setText(null);
            mPlaybackSpeed.setCompoundDrawablesWithIntrinsicBounds(0,
                    Util.getResourceFromAttribute(mActivity, R.attr.ic_speed_normal_style),
                    0, 0);
        } else {
            mPlaybackSpeed.setText(Strings.formatRateString(mService.getRate()));
            mPlaybackSpeed.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_speed_on, 0, 0);
        }
    }

    public void initSleep () {
        String text = null;
        if (VLCApplication.sPlayerSleepTime == null) {
            mSleep.setCompoundDrawablesWithIntrinsicBounds(0,
                    Util.getResourceFromAttribute(mActivity, R.attr.ic_sleep_normal_style),
                    0, 0);
        } else {
            mSleep.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_sleep_on, 0, 0);
            text = DateFormat.getTimeFormat(mActivity).format(VLCApplication.sPlayerSleepTime.getTime());
        }
        mSleep.setText(text);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.sleep:
                if (VLCApplication.sPlayerSleepTime == null)
                    showTimePickerFragment(ACTION_SLEEP_TIMER);
                else {
                    setSleep(null);
                    initSleep();
                }
                break;
            case R.id.playback_speed:
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
            case R.id.play_as_audio_icon:
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

    public boolean onLongClick (View v) {
        switch (v.getId()) {
            case R.id.playback_speed:
                mService.setRate(1);
                initPlaybackSpeed();
                return true;
        }
        return false;
    }


    private DialogInterface.OnDismissListener onDismissListener;

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
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

        initSleep();
        initPlaybackSpeed();

        if (mMode == MODE_VIDEO) {
            // Init Chapter
            final MediaPlayer.Chapter[] chapters = mService.getChapters(-1);
            final int chaptersCount = chapters != null ? chapters.length : 0;

            if (chaptersCount > 1) {
                mChaptersTitle.setText(chapters[mService.getChapterIdx()].name);
            } else
                mChaptersTitle.setVisibility(View.GONE);

            //Init Audio Delay
            long audiodelay = mService.getAudioDelay() / 1000l;
            if (audiodelay == 0l) {
                mAudioDelay.setText(null);
                mAudioDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                        Util.getResourceFromAttribute(mActivity, R.attr.ic_audiodelay),
                        0, 0);
            } else {
                mAudioDelay.setText(Long.toString(audiodelay) + " ms");
                mAudioDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                        R.drawable.ic_audiodelay_on,
                        0, 0);
            }

            //Init Subtitle Delay
            long spudelay = mService.getSpuDelay() / 1000l;
            if (spudelay == 0l) {
                mSpuDelay.setText(null);
                mSpuDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                        Util.getResourceFromAttribute(mActivity, R.attr.ic_subtitledelay),
                        0, 0);
            } else {
                mSpuDelay.setText(Long.toString(spudelay) + " ms");
                mSpuDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                        R.drawable.ic_subtitledelay_on,
                        0, 0);
            }
        }
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }
}
