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
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesUi;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.gui.view.AutoFitRecyclerView;
import org.videolan.vlc.interfaces.IPlaybackSettingsController;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Permissions;
import org.videolan.vlc.util.Strings;

import java.util.ArrayList;
import java.util.Calendar;

public class AdvOptionsDialog extends DialogFragment implements View.OnClickListener, PlaybackService.Client.Callback, View.OnFocusChangeListener, DialogInterface.OnKeyListener {

    public final static String TAG = "VLC/AdvOptionsDialog";
    public static final String MODE_KEY = "mode";
    public static final int MODE_VIDEO = 0;
    public static final int MODE_AUDIO = 1;

    private static final int SPAN_COUNT = 3;

    public static final int ACTION_AUDIO_DELAY = 2 ;
    public static final int ACTION_SPU_DELAY = 3 ;
    public static final int ACTION_PLAYBACK_SPEED = 4 ;

    private static final int ID_PLAY_AS_AUDIO = 0 ;
    private static final int ID_SLEEP = 1 ;
    private static final int ID_JUMP_TO = 2 ;
    private static final int ID_AUDIO_DELAY = 3 ;
    private static final int ID_SPU_DELAY = 4 ;
    private static final int ID_CHAPTER_TITLE = 5 ;
    private static final int ID_PLAYBACK_SPEED = 6 ;
    private static final int ID_EQUALIZER = 7 ;
    private static final int ID_SAVE_PLAYLIST = 8 ;
    private static final int ID_POPUP_VIDEO = 9 ;
    private static final int ID_REPEAT = 10 ;
    private static final int ID_SHUFFLE = 11 ;

    private Activity mActivity;
    private int mTheme;
    private int mMode = -1;

    AutoFitRecyclerView mRecyclerView;
    private AdvOptionsAdapter mAdapter;

    private TextView mPlaybackSpeed;
    private TextView mSleep;

    private TextView mJumpTitle;

    private TextView mAudioDelay;
    private TextView mSpuDelay;

    private TextView mRepeat;
    private TextView mShuffle;

    private TextView mChaptersTitle;
    private int mTextColor;
    private static final int FOCUSED_TEXT_COLOR = ContextCompat.getColor(VLCApplication.getAppContext(), R.color.orange300);
    private PlaybackService mService;

    private IPlaybackSettingsController mPlaybackController;

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

        mTheme = (mMode == MODE_VIDEO || UiTools.isBlackThemeEnabled()) ?
                R.style.Theme_VLC_Black :
                R.style.Theme_VLC;
        setStyle(DialogFragment.STYLE_NO_FRAME, mTheme);
    }

    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (mMode == MODE_VIDEO) {
            mPlaybackController = (IPlaybackSettingsController) activity;
        }
        mActivity = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mPlaybackController = null;
        mActivity = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);

        mRecyclerView = (AutoFitRecyclerView) inflater.inflate(R.layout.fragment_advanced_options, container, false);
        mRecyclerView.setNumColumns(SPAN_COUNT);
        mRecyclerView.setSpanSizeLookup(mSpanSizeLookup);
        mRecyclerView.setColumnWidth(getResources().getDimensionPixelSize(R.dimen.option_width));
        mAdapter = new AdvOptionsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        //Get default color
        int[] attrs = new int[] { android.R.attr.textColorSecondary };
        TypedArray a = getActivity().getTheme().obtainStyledAttributes(R.style.Theme_VLC, attrs);
        mTextColor = a.getColor(0, Color.LTGRAY);
        a.recycle();

        getDialog().getWindow().setBackgroundDrawableResource(UiTools.getResourceFromAttribute(getActivity(), R.attr.rounded_bg));

        return mRecyclerView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getDialog().setOnKeyListener(this);
    }

    private void setDialogDimensions(int offset) {
        if (getDialog() == null)
            return;
        int dialogWidth = getResources().getDimensionPixelSize(R.dimen.option_width) * SPAN_COUNT + mRecyclerView.getPaddingLeft()+ mRecyclerView.getRight();

        int count = mAdapter.getItemCount()-offset;
        int rows = offset + count / SPAN_COUNT;
        if (count % SPAN_COUNT != 0)
            rows++;

        int dialogHeight = getResources().getDimensionPixelSize(R.dimen.option_height) * rows + mRecyclerView.getPaddingBottom()+ mRecyclerView.getPaddingTop();

        getDialog().getWindow().setLayout(dialogWidth, dialogHeight);
    }

    private void showFragment(int id) {
        DialogFragment newFragment;
        String tag;
        switch (id) {
            case ID_PLAYBACK_SPEED:
                newFragment = PlaybackSpeedDialog.newInstance(mTheme);
                tag = "playback_speed";
                break;
            case ID_JUMP_TO:
                newFragment = JumpToTimeDialog.newInstance(mTheme);
                tag = "time";
                break;
            case ID_SLEEP:
                newFragment = SleepTimerDialog.newInstance(mTheme);
                tag = "time";
                break;
            case ID_CHAPTER_TITLE:
                newFragment = SelectChapterDialog.newInstance(mTheme);
                tag = "select_chapter";
                break;
            case ID_SAVE_PLAYLIST:
                newFragment = new SavePlaylistDialog();
                Bundle args = new Bundle();
                args.putParcelableArrayList(SavePlaylistDialog.KEY_TRACKS, (ArrayList<MediaWrapper>) mService.getMedias());
                newFragment.setArguments(args);
                tag = "fragment_save_playlist";
                break;
            default:
                return;
        }
        if (newFragment != null)
            newFragment.show(getActivity().getSupportFragmentManager(), tag);
        dismiss();
    }

    private void showValueControls(int action) {
        if (mPlaybackController == null && getActivity() instanceof IPlaybackSettingsController)
            mPlaybackController = (IPlaybackSettingsController) getActivity();
        switch (action){
            case ACTION_AUDIO_DELAY:
                if (mPlaybackController != null)
                    mPlaybackController.showAudioDelaySetting();
                break;
            case ACTION_SPU_DELAY:
                if (mPlaybackController != null)
                    mPlaybackController.showSubsDelaySetting();
                break;
            case ACTION_PLAYBACK_SPEED:
                if (mPlaybackController != null)
                    mPlaybackController.showPlaybackSpeedSetting();
                break;
            default:
                return;
        }
        dismiss();
    }

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
        if (!mService.isSeekable()) {
            mPlaybackSpeed.setEnabled(false);
            mPlaybackSpeed.setCompoundDrawablesWithIntrinsicBounds(0,R.drawable.ic_speed_disable, 0, 0);
            return;
        }
        if (mService.getRate() == 1.0f) {
            mPlaybackSpeed.setText(null);
            mPlaybackSpeed.setCompoundDrawablesWithIntrinsicBounds(0,
                    UiTools.getResourceFromAttribute(mActivity, R.attr.ic_speed_normal_style),
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
                    UiTools.getResourceFromAttribute(mActivity, R.attr.ic_sleep_normal_style),
                    0, 0);
        } else {
            mSleep.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_sleep_on, 0, 0);
            text = DateFormat.getTimeFormat(mActivity).format(VLCApplication.sPlayerSleepTime.getTime());
        }
        mSleep.setText(text);
    }

    private void initSpuDelay() {
        long spudelay = mService.getSpuDelay() / 1000L;
        if (spudelay == 0L) {
            mSpuDelay.setText(null);
            mSpuDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                    UiTools.getResourceFromAttribute(mActivity, R.attr.ic_subtitledelay),
                    0, 0);
        } else {
            mSpuDelay.setText(String.format("%s ms", Long.toString(spudelay)));
            mSpuDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                    R.drawable.ic_subtitledelay_on,
                    0, 0);
        }
    }

    private void initAudioDelay() {
        long audiodelay = mService.getAudioDelay() / 1000L;
        if (audiodelay == 0L) {
            mAudioDelay.setText(null);
            mAudioDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                    UiTools.getResourceFromAttribute(mActivity, R.attr.ic_audiodelay),
                    0, 0);
        } else {
            mAudioDelay.setText(String.format("%s ms", Long.toString(audiodelay)));
            mAudioDelay.setCompoundDrawablesWithIntrinsicBounds(0,
                    R.drawable.ic_audiodelay_on,
                    0, 0);
        }
    }

    public void initRepeat() {
        switch (mService.getRepeatType()) {
            case PlaybackService.REPEAT_NONE:
                mRepeat.setCompoundDrawablesWithIntrinsicBounds(0,
                        UiTools.getResourceFromAttribute(mActivity, R.attr.ic_repeat),
                        0, 0);
                break;
            case PlaybackService.REPEAT_ALL:
                mRepeat.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_repeat_all, 0, 0);
                break;
            case PlaybackService.REPEAT_ONE:
                mRepeat.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_repeat_one, 0, 0);
                    break;
        }
    }

    public void setRepeatMode() {
        switch (mService.getRepeatType()) {
            case PlaybackService.REPEAT_NONE:
                mRepeat.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_repeat_one, 0, 0);
                mService.setRepeatType(PlaybackService.REPEAT_ONE);
                break;
            case PlaybackService.REPEAT_ONE:
                if (mService.hasPlaylist()){
                    mRepeat.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_repeat_all, 0, 0);
                    mService.setRepeatType(PlaybackService.REPEAT_ALL);
                } else {
                    mRepeat.setCompoundDrawablesWithIntrinsicBounds(0,
                            UiTools.getResourceFromAttribute(mActivity, R.attr.ic_repeat),
                            0, 0);
                    mService.setRepeatType(PlaybackService.REPEAT_NONE);
                }
                break;
            case PlaybackService.REPEAT_ALL:
                mRepeat.setCompoundDrawablesWithIntrinsicBounds(0,
                        UiTools.getResourceFromAttribute(mActivity, R.attr.ic_repeat),
                        0, 0);
                mService.setRepeatType(PlaybackService.REPEAT_NONE);
                break;
        }
    }

    public void initShuffle(){
        mShuffle.setCompoundDrawablesWithIntrinsicBounds(0,
                mService.isShuffling()
                        ? R.drawable.ic_shuffle_on
                        : UiTools.getResourceFromAttribute(mActivity, R.attr.ic_shuffle),
                0, 0);

    }

    private void initChapters() {
        final MediaPlayer.Chapter[] chapters = mService.getChapters(-1);

        int index = mService.getChapterIdx();
        if (chapters[index].name == null || chapters[index].name.equals(""))
            mChaptersTitle.setText(String.format("%s %d", getResources().getString(R.string.chapter), index));
        else
            mChaptersTitle.setText(chapters[index].name);
    }

    private void initJumpTo() {
        mJumpTitle.setEnabled(mService.isSeekable());
        mJumpTitle.setCompoundDrawablesWithIntrinsicBounds(0,
                mService.isSeekable()
                        ? UiTools.getResourceFromAttribute(mActivity, R.attr.ic_jumpto_normal_style)
                        : R.drawable.ic_jumpto_disable,
                0, 0);
    }

    private void setViewReference(int id, TextView tv) {
        switch (id) {
            case ID_CHAPTER_TITLE:
                mChaptersTitle = tv;
                initChapters();
                break;
            case ID_PLAYBACK_SPEED:
                mPlaybackSpeed = tv;
                initPlaybackSpeed();
                break;
            case ID_AUDIO_DELAY:
                mAudioDelay = tv;
                initAudioDelay();
                break;
            case ID_JUMP_TO:
                mJumpTitle = tv;
                initJumpTo();
                break;
            case ID_SLEEP:
                mSleep = tv;
                initSleep();
                break;
            case ID_SPU_DELAY:
                mSpuDelay = tv;
                initSpuDelay();
                break;
            case ID_REPEAT:
                mRepeat = tv;
                initRepeat();
                break;
            case ID_SHUFFLE:
                mShuffle = tv;
                initShuffle();
                break;
        }
    }



    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case ID_SLEEP:
                if (VLCApplication.sPlayerSleepTime == null)
                    showFragment(ID_SLEEP);
                else {
                    setSleep(null);
                    initSleep();
                }
                break;
            case ID_PLAYBACK_SPEED:
                if (mMode == MODE_AUDIO)
                    showFragment(ID_PLAYBACK_SPEED);
                else
                    showValueControls(ACTION_PLAYBACK_SPEED);
                break;
            case ID_CHAPTER_TITLE:
                showFragment(ID_CHAPTER_TITLE);
                break;
            case ID_AUDIO_DELAY:
                showValueControls(ACTION_AUDIO_DELAY);
                break;
            case ID_SPU_DELAY:
                showValueControls(ACTION_SPU_DELAY);
                break;
            case ID_JUMP_TO:
                showFragment(ID_JUMP_TO);
                break;
            case ID_PLAY_AS_AUDIO:
                ((VideoPlayerActivity)getActivity()).switchToAudioMode(true);
                break;
            case ID_POPUP_VIDEO:
                if (Permissions.canDrawOverlays(mActivity))
                    ((VideoPlayerActivity)getActivity()).switchToPopupMode();
                else
                    Permissions.checkDrawOverlaysPermission(mActivity);
                break;
            case ID_EQUALIZER:
                Intent i = new Intent(getActivity(), SecondaryActivity.class);
                i.putExtra("fragment", SecondaryActivity.EQUALIZER);
                startActivity(i);
                dismiss();
                break;
            case ID_SAVE_PLAYLIST:
                showFragment(ID_SAVE_PLAYLIST);
                break;
            case ID_REPEAT:
                setRepeatMode();
                break;
            case ID_SHUFFLE:
                mService.shuffle();
                initShuffle();
                break;
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v instanceof TextView)
            ((TextView) v).setTextColor(v.hasFocus() ?
                    FOCUSED_TEXT_COLOR : mTextColor);
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
        mAdapter.clear();
        mService = service;
        int large_items = 0;
        boolean tvUi = VLCApplication.showTvUi();

        mAdapter.addOption(new Option(ID_SLEEP, R.attr.ic_sleep_normal_style, getString(R.string.sleep_title)));
        mAdapter.addOption(new Option(ID_PLAYBACK_SPEED, R.attr.ic_speed_normal_style, getString(R.string.playback_speed)));
        mAdapter.addOption(new Option(ID_JUMP_TO, R.attr.ic_jumpto_normal_style, getString(R.string.jump_to_time)));

        if (mMode == MODE_VIDEO) {
            if (!tvUi)
                mAdapter.addOption(new Option(ID_PLAY_AS_AUDIO, R.attr.ic_playasaudio_on, getString(R.string.play_as_audio)));
            mAdapter.addOption(new Option(ID_SPU_DELAY, R.attr.ic_subtitledelay, getString(R.string.spu_delay)));
            mAdapter.addOption(new Option(ID_AUDIO_DELAY, R.attr.ic_audiodelay, getString(R.string.audio_delay)));
            if (!tvUi)
                mAdapter.addOption(new Option(ID_POPUP_VIDEO, R.attr.ic_popup_dim, getString(R.string.popup_playback_title)));
            mAdapter.addOption(new Option(ID_REPEAT, R.attr.ic_repeat, getString(R.string.repeat_title)));
            if (mService.canShuffle())
                mAdapter.addOption(new Option(ID_SHUFFLE, R.attr.ic_shuffle, getString(R.string.shuffle_title)));

            final MediaPlayer.Chapter[] chapters = mService.getChapters(-1);
            final int chaptersCount = chapters != null ? chapters.length : 0;
            if (chaptersCount > 1) {
                mAdapter.addOption(new Option(ID_CHAPTER_TITLE, R.attr.ic_chapter_normal_style, getString(R.string.go_to_chapter)));
                large_items++;
            }
        } else {
            mAdapter.addOption(new Option(ID_EQUALIZER, R.attr.ic_equalizer_normal_style, getString(R.string.equalizer)));
            mAdapter.addOption(new Option(ID_SAVE_PLAYLIST, R.attr.ic_save, getString(R.string.playlist_save)));
        }
        setDialogDimensions(large_items);
    }

    @Override
    public void onDisconnected() {
        mService = null;
    }

    GridLayoutManager.SpanSizeLookup mSpanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize(int position) {
            switch (mAdapter.getItemViewType(position)) {
                case ID_CHAPTER_TITLE:
                    return SPAN_COUNT;
                default:
                    return 1;
            }
        }
    };

    @Override
    public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getAction() != KeyEvent.ACTION_DOWN)
            return false;
        if (mAdapter.getSelection() == -1)
            mAdapter.setSelection(0);
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                dismiss();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mAdapter.setSelection(mAdapter.getSelection() - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mAdapter.setSelection(mAdapter.getSelection() + 1);
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                onClick(mRecyclerView.getChildAt(mAdapter.getSelection()));
                return true;
        }
        return false;
    }

    private class AdvOptionsAdapter extends RecyclerView.Adapter<AdvOptionsAdapter.ViewHolder> {

        private ArrayList<Option> mList = new ArrayList<>();
        private int mSelection = 0;

        public AdvOptionsAdapter() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AdvOptionsDialog.this.getContext());
            if (TextUtils.equals(prefs.getString(PreferencesUi.KEY_ENABLE_TOUCH_PLAYER, AndroidDevices.hasTsp() ? "0" : "2"), "2"))
                mSelection = 0;
        }

        @Override
        public AdvOptionsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.adv_option_item, parent, false);
            v.setOnClickListener(AdvOptionsDialog.this);
            v.setOnFocusChangeListener(AdvOptionsDialog.this);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Option option = mList.get(position);
            TextView tv = (TextView) holder.itemView;
            if (mSelection == position)
                tv.requestFocus();
            tv.setId(option.id);
            int icon = UiTools.getResourceFromAttribute(mActivity, option.icon);
            if (option.id == ID_CHAPTER_TITLE)
                tv.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            else
                tv.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
            setViewReference(option.id, tv);
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }

        public void clear() {
            mList.clear();
        }

        @Override
        public int getItemViewType(int position) {
            return mList.get(position).id;
        }

        public void addOption(Option opt) {
            mList.add(opt);
            notifyItemInserted(mList.size()-1);
        }

        public void setSelection(int position) {
            if (mSelection == position || position < 0 || position >= mList.size())
                return;
            int formerSelection = mSelection;
            mSelection = position;
            notifyDataSetChanged();
        }

        public int getSelection() {
            return mSelection;
        }

        public void removeOption(Option opt) {
            mList.remove(opt);
        }

        public class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {

            public ViewHolder(View itemView) {
                super(itemView);
                itemView.setOnLongClickListener(this);
            }

            @Override
            public boolean onLongClick(View view) {
                Toast toast = Toast.makeText(VLCApplication.getAppContext(), mList.get(getAdapterPosition()).text, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.TOP,0,0);
                toast.show();
                return true;
            }
        }
    }

    private static class Option {
        int id, icon;
        String text;

        Option(int id, int icon, String text) {
            this.id = id;
            this.icon = icon;
            this.text = text;
        }
    }
}
