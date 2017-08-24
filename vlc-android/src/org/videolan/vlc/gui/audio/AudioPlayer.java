/*****************************************************************************
 * AudioPlayer.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 *****************************************************************************/

package org.videolan.vlc.gui.audio;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.constraint.ConstraintSet;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.Snackbar;
import android.support.transition.TransitionManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.AudioPlayerBinding;
import org.videolan.vlc.gui.AudioPlayerContainerActivity;
import org.videolan.vlc.gui.PlaybackServiceFragment;
import org.videolan.vlc.gui.dialogs.AdvOptionsDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.SwipeDragItemTouchHelperCallback;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.view.AudioMediaSwitcher.AudioMediaSwitcherListener;
import org.videolan.vlc.util.AndroidDevices;

public class AudioPlayer extends PlaybackServiceFragment implements PlaybackService.Callback, PlaylistAdapter.IPlayer, TextWatcher {
    public static final String TAG = "VLC/AudioPlayer";

    private static int DEFAULT_BACKGROUND_DARKER_ID;
    private static int DEFAULT_BACKGROUND_ID;
    public static final int SEARCH_TIMEOUT_MILLIS = 5000;

    private AudioPlayerBinding mBinding;

    private boolean mShowRemainingTime = false;
    private boolean mPreviewingSeek = false;

    private PlaylistAdapter mPlaylistAdapter;

    private boolean mAdvFuncVisible;
    private boolean mPlaylistSwitchVisible;
    private boolean mSearchVisible;
    private boolean mHeaderPlayPauseVisible;
    private boolean mProgressBarVisible;
    private boolean mHeaderTimeVisible;
    private int mPlayerState;
    private String mCurrentCoverArt;
    private final ConstraintSet coverConstraintSet = AndroidUtil.isICSOrLater ? new ConstraintSet() : null;
    private final ConstraintSet playlistConstraintSet = AndroidUtil.isICSOrLater ? new ConstraintSet() : null;

    SharedPreferences mSettings;

    // Tips
    private static final String PREF_PLAYLIST_TIPS_SHOWN = "playlist_tips_shown";
    public static final String PREF_AUDIOPLAYER_TIPS_SHOWN = "audioplayer_tips_shown";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            mPlayerState = savedInstanceState.getInt("player_state");
        mSettings = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = AudioPlayerBinding.inflate(inflater);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            DEFAULT_BACKGROUND_DARKER_ID = UiTools.getResourceFromAttribute(view.getContext(), R.attr.background_default_darker);
            DEFAULT_BACKGROUND_ID = UiTools.getResourceFromAttribute(view.getContext(), R.attr.background_default);
        }
        mPlaylistAdapter = new PlaylistAdapter(this);
        mBinding.songsList.setLayoutManager(new LinearLayoutManager(mBinding.getRoot().getContext()));
        mBinding.songsList.setAdapter(mPlaylistAdapter);
        mBinding.audioMediaSwitcher.setAudioMediaSwitcherListener(mHeaderMediaSwitcherListener);
        mBinding.coverMediaSwitcher.setAudioMediaSwitcherListener(mCoverMediaSwitcherListener);
        mBinding.playlistSearchText.getEditText().addTextChangedListener(this);

        ItemTouchHelper.Callback callback =  new SwipeDragItemTouchHelperCallback(mPlaylistAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(mBinding.songsList);

        setHeaderVisibilities(false, false, true, false, true, false);
        mBinding.setFragment(this);

        mBinding.next.setOnTouchListener(new LongSeekListener(true,
                UiTools.getResourceFromAttribute(view.getContext(), R.attr.ic_next),
                R.drawable.ic_next_pressed));
        mBinding.previous.setOnTouchListener(new LongSeekListener(false,
                UiTools.getResourceFromAttribute(view.getContext(), R.attr.ic_previous),
                R.drawable.ic_previous_pressed));

        registerForContextMenu(mBinding.songsList);
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setUserVisibleHint(true);
        if (playlistConstraintSet != null) {
            playlistConstraintSet.clone(mBinding.contentLayout);
            coverConstraintSet.clone(mBinding.contentLayout);
            coverConstraintSet.setVisibility(R.id.songs_list, View.GONE);
            coverConstraintSet.setVisibility(R.id.cover_media_switcher, View.VISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("player_state", mPlayerState);
    }

    public void onPopupMenu(View anchor, final int position) {
        final Activity activity = getActivity();
        if (activity == null || position >= mPlaylistAdapter.getItemCount())
            return;
        final MediaWrapper mw = mPlaylistAdapter.getItem(position);
        final PopupMenu popupMenu = new PopupMenu(activity, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audio_player, popupMenu.getMenu());

        popupMenu.getMenu().setGroupVisible(R.id.phone_only, mw.getType() != MediaWrapper.TYPE_VIDEO
                && TextUtils.equals(mw.getUri().getScheme(), "file")
                && AndroidDevices.isPhone());

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.audio_player_mini_remove) {
                    if (mService != null) {
                        mService.remove(position);
                        return true;
                    }
                } else if (item.getItemId() == R.id.audio_player_set_song) {
                    AudioUtil.setRingtone(mw, activity);
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    /**
     * Show the audio player from an intent
     *
     * @param context The context of the activity
     */
    public static void start(Context context) {
        Intent intent = new Intent();
        intent.setAction(AudioPlayerContainerActivity.ACTION_SHOW_PLAYER);
        context.getApplicationContext().sendBroadcast(intent);
    }

    public void update() {
        mHandler.removeMessages(UPDATE);
        mHandler.sendEmptyMessage(UPDATE);
    }

    public void doUpdate() {
        if (mService == null || getActivity() == null)
            return;
        if (mService.hasMedia() && !mService.isVideoPlaying()) {
            //Check fragment resumed to not restore video on device turning off
            if (isVisible() && mSettings.getBoolean(PreferencesActivity.VIDEO_RESTORE, false)) {
                mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply();
                mService.getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
                mService.switchToVideo();
                return;
            } else
                show();
        } else {
            hide();
            return;
        }

        mBinding.audioMediaSwitcher.updateMedia(mService);
        mBinding.coverMediaSwitcher.updateMedia(mService);

        FragmentActivity act = getActivity();
        mBinding.playlistPlayasaudioOff.setVisibility(mService.getVideoTracksCount() > 0 ? View.VISIBLE : View.GONE);

        boolean playing = mService.isPlaying();
        int imageResId = UiTools.getResourceFromAttribute(act, playing ? R.attr.ic_pause : R.attr.ic_play);
        String text = getString(playing ? R.string.pause : R.string.play);
        mBinding.playPause.setImageResource(imageResId);
        mBinding.playPause.setContentDescription(text);
        mBinding.headerPlayPause.setImageResource(imageResId);
        mBinding.headerPlayPause.setContentDescription(text);
        mBinding.shuffle.setImageResource(UiTools.getResourceFromAttribute(act, mService.isShuffling() ? R.attr.ic_shuffle_on : R.attr.ic_shuffle));
        mBinding.shuffle.setContentDescription(getResources().getString(mService.isShuffling() ? R.string.shuffle_on : R.string.shuffle));
        switch(mService.getRepeatType()) {
            case PlaybackService.REPEAT_NONE:
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(act, R.attr.ic_repeat));
                mBinding.repeat.setContentDescription(getResources().getString(R.string.repeat));
                break;
            case PlaybackService.REPEAT_ONE:
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(act, R.attr.ic_repeat_one));
                mBinding.repeat.setContentDescription(getResources().getString(R.string.repeat_single));
                break;
            default:
            case PlaybackService.REPEAT_ALL:
                mBinding.repeat.setImageResource(UiTools.getResourceFromAttribute(act, R.attr.ic_repeat_all));
                mBinding.repeat.setContentDescription(getResources().getString(R.string.repeat_all));
                break;
        }
        mBinding.shuffle.setVisibility(mService.canShuffle() ? View.VISIBLE : View.INVISIBLE);
        mBinding.timeline.setOnSeekBarChangeListener(mTimelineListner);
        updateList();
        updateBackground();
    }

    @Override
    public void updateProgress() {
        if (mService == null)
            return;
        int time = (int) mService.getTime();
        int length = (int) mService.getLength();

        mBinding.headerTime.setText(Tools.millisToString(time));
        mBinding.length.setText(Tools.millisToString(length));
        mBinding.timeline.setMax(length);
        mBinding.progressBar.setMax(length);

        if (!mPreviewingSeek) {
            mBinding.time.setText(Tools.millisToString(mShowRemainingTime ? time-length : time));
            mBinding.timeline.setProgress(time);
            mBinding.progressBar.setProgress(time);
        }
    }

    @Override
    public void onMediaEvent(Media.Event event) {}

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                hideSearchField();
                break;
            case MediaPlayer.Event.Stopped:
                hide();
                break;
        }
    }

    private void updateBackground() {
        if (AndroidUtil.isJellyBeanMR1OrLater) {
            final MediaWrapper mw = mService.getCurrentMediaWrapper();
            if (mw == null || TextUtils.equals(mCurrentCoverArt, mw.getArtworkMrl()))
                return;
            mCurrentCoverArt = mw.getArtworkMrl();
            if (TextUtils.isEmpty(mw.getArtworkMrl())) {
                setDefaultBackground();
            } else {
                VLCApplication.runBackground(new Runnable() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
                    @Override
                    public void run() {
                        final Bitmap blurredCover = UiTools.blurBitmap(AudioUtil.readCoverBitmap(Uri.decode(mw.getArtworkMrl()), mBinding.contentLayout.getWidth()));
                        if (blurredCover != null)
                            VLCApplication.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity) getActivity();
                                    if (activity == null)
                                        return;
                                    mBinding.backgroundView.setColorFilter(UiTools.getColorFromAttribute(activity, R.attr.audio_player_background_tint));
                                    mBinding.backgroundView.setImageBitmap(blurredCover);
                                    mBinding.backgroundView.setVisibility(View.VISIBLE);
                                    mBinding.songsList.setBackgroundResource(0);
                                    if (mPlayerState == BottomSheetBehavior.STATE_EXPANDED)
                                        mBinding.header.setBackgroundResource(0);
                                }
                            });
                        else {
                            VLCApplication.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    setDefaultBackground();
                                }
                            });
                        }
                    }
                });
            }
        }
        if (((AudioPlayerContainerActivity)getActivity()).isAudioPlayerExpanded())
            setHeaderVisibilities(true, true, false, false, false, true);

    }

    @MainThread
    private void setDefaultBackground() {
        mBinding.songsList.setBackgroundResource(DEFAULT_BACKGROUND_ID);
        mBinding.header.setBackgroundResource(DEFAULT_BACKGROUND_ID);
        mBinding.backgroundView.setVisibility(View.INVISIBLE);
    }

    public void updateList() {
        hideSearchField();
        if (mService != null)
            mPlaylistAdapter.update(mService.getMedias());
    }

    @Override
    public void onSelectionSet(int position) {
        if (mPlayerState != BottomSheetBehavior.STATE_COLLAPSED && mPlayerState != BottomSheetBehavior.STATE_HIDDEN) {
            mBinding.songsList.scrollToPosition(position);
        }
    }

    OnSeekBarChangeListener mTimelineListner = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
            if (fromUser && mService != null) {
                mService.setTime(progress);
                mBinding.time.setText(Tools.millisToString(mShowRemainingTime ? progress-mService.getLength() : progress));
                mBinding.headerTime.setText(Tools.millisToString(progress));
            }
        }
    };

    public void onTimeLabelClick(View view) {
        mShowRemainingTime = !mShowRemainingTime;
        update();
    }

    public void onPlayPauseClick(View view) {
        if (mService == null)
            return;
        if (mService.isPlaying()) {
            mService.pause();
        } else {
            mService.play();
        }
    }

    public boolean onStopClick(View view) {
        if (mService == null)
            return false;
        mService.stop();
        return true;
    }

    public void onNextClick(View view) {
        if (mService == null)
            return;
        if (mService.hasNext())
            mService.next();
        else
            Snackbar.make(mBinding.getRoot(), R.string.lastsong, Snackbar.LENGTH_SHORT).show();
    }

    public void onPreviousClick(View view) {
        if (mService == null)
            return;
        if (mService.hasPrevious() || mService.isSeekable())
            mService.previous(false);
        else
            Snackbar.make(mBinding.getRoot(), R.string.firstsong, Snackbar.LENGTH_SHORT).show();
    }

    public void onRepeatClick(View view) {
        if (mService == null)
            return;

        switch (mService.getRepeatType()) {
            case PlaybackService.REPEAT_NONE:
                mService.setRepeatType(PlaybackService.REPEAT_ALL);
                break;
            case PlaybackService.REPEAT_ALL:
                mService.setRepeatType(PlaybackService.REPEAT_ONE);
                break;
            default:
            case PlaybackService.REPEAT_ONE:
                mService.setRepeatType(PlaybackService.REPEAT_NONE);
                break;
        }
        update();
    }

    public void onPlaylistSwitchClick(View view) {
        boolean showCover = mBinding.songsList.getVisibility() == View.VISIBLE;
        if (playlistConstraintSet != null) {
            TransitionManager.beginDelayedTransition(mBinding.contentLayout);
            ConstraintSet cs = showCover ? coverConstraintSet : playlistConstraintSet;
            cs.applyTo(mBinding.contentLayout);
            mBinding.playlistSwitch.setImageResource(UiTools.getResourceFromAttribute(view.getContext(),
                    showCover ? R.attr.ic_playlist : R.attr.ic_playlist_on));
        } else {
            mBinding.songsList.setVisibility(showCover ? View.GONE : View.VISIBLE);
            mBinding.coverMediaSwitcher.setVisibility(showCover ? View.VISIBLE : View.GONE);
        }
    }

    public void onShuffleClick(View view) {
        if (mService != null)
            mService.shuffle();
        update();
    }

    public void onResumeToVideoClick(View v) {
        if (mService != null && mService.hasMedia()) {
            mService.getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            mService.switchToVideo();
        }
    }


    public void showAdvancedOptions(View v) {
        if (!isVisible())
            return;
        FragmentManager fm = getActivity().getSupportFragmentManager();
        AdvOptionsDialog advOptionsDialog = new AdvOptionsDialog();
        Bundle args = new Bundle();
        args.putInt(AdvOptionsDialog.MODE_KEY, AdvOptionsDialog.MODE_AUDIO);
        advOptionsDialog.setArguments(args);
        advOptionsDialog.show(fm, "fragment_adv_options");
    }

    public void show() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null && activity.isAudioPlayerReady())
            activity.showAudioPlayer();
    }

    public void hide() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null)
            activity.hideAudioPlayer();
    }

    public void setHeaderVisibilities(boolean advFuncVisible, boolean playlistSwitchVisible,
                                      boolean headerPlayPauseVisible, boolean progressBarVisible,
                                      boolean headerTimeVisible, boolean searchVisible) {
        mAdvFuncVisible = advFuncVisible;
        mPlaylistSwitchVisible = playlistSwitchVisible;
        mHeaderPlayPauseVisible = headerPlayPauseVisible;
        mProgressBarVisible = progressBarVisible;
        mHeaderTimeVisible = headerTimeVisible;
        mSearchVisible = searchVisible;
        restoreHeaderButtonVisibilities();
    }

    private void restoreHeaderButtonVisibilities() {
        mBinding.advFunction.setVisibility(mAdvFuncVisible ? View.VISIBLE : View.GONE);
        mBinding.playlistSwitch.setVisibility(mPlaylistSwitchVisible ? View.VISIBLE : View.GONE);
        mBinding.playlistSearch.setVisibility(mSearchVisible ? View.VISIBLE : View.GONE);
        mBinding.headerPlayPause.setVisibility(mHeaderPlayPauseVisible ? View.VISIBLE : View.GONE);
        mBinding.progressBar.setVisibility(mProgressBarVisible ? View.VISIBLE : View.GONE);
        mBinding.headerTime.setVisibility(mHeaderTimeVisible ? View.VISIBLE : View.GONE);
    }

    private void hideHeaderButtons() {
        mBinding.advFunction.setVisibility(View.GONE);
        mBinding.playlistSwitch.setVisibility(View.GONE);
        mBinding.playlistSearch.setVisibility(View.GONE);
        mBinding.headerPlayPause.setVisibility(View.GONE);
        mBinding.progressBar.setVisibility(View.GONE);
        mBinding.headerTime.setVisibility(View.GONE);
    }

    private final AudioMediaSwitcherListener mHeaderMediaSwitcherListener = new AudioMediaSwitcherListener() {

        @Override
        public void onMediaSwitching() {}

        @Override
        public void onMediaSwitched(int position) {
            if (mService == null)
                return;
            if (position == AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                mService.previous(true);
            else if (position == AudioMediaSwitcherListener.NEXT_MEDIA)
                mService.next();
        }

        @Override
        public void onTouchDown() {
            hideHeaderButtons();
        }

        @Override
        public void onTouchUp() {
            restoreHeaderButtonVisibilities();
        }

        @Override
        public void onTouchClick() {
            AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
            activity.slideUpOrDownAudioPlayer();
        }
    };

    private final AudioMediaSwitcherListener mCoverMediaSwitcherListener = new AudioMediaSwitcherListener() {

        @Override
        public void onMediaSwitching() {}

        @Override
        public void onMediaSwitched(int position) {
            if (mService == null)
                return;
            if (position == AudioMediaSwitcherListener.PREVIOUS_MEDIA)
                mService.previous(true);
            else if (position == AudioMediaSwitcherListener.NEXT_MEDIA)
                mService.next();
        }

        @Override
        public void onTouchDown() {}

        @Override
        public void onTouchUp() {}

        @Override
        public void onTouchClick() {}
    };

    public void onSearchClick(View v) {
        mBinding.playlistSearch.setVisibility(View.GONE);
        mBinding.playlistSearchText.setVisibility(View.VISIBLE);
        if (mBinding.playlistSearchText.getEditText() != null)
            mBinding.playlistSearchText.getEditText().requestFocus();
        InputMethodManager imm = (InputMethodManager) VLCApplication.getAppContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mBinding.playlistSearchText.getEditText(), InputMethodManager.SHOW_IMPLICIT);
        mHandler.postDelayed(hideSearchRunnable, SEARCH_TIMEOUT_MILLIS);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {}

    public boolean clearSearch() {
        mPlaylistAdapter.restoreList();
        return hideSearchField();
    }

    public boolean hideSearchField() {
        if (mBinding.playlistSearchText.getVisibility() != View.VISIBLE)
            return false;
        if (mBinding.playlistSearchText.getEditText() != null) {
            mBinding.playlistSearchText.getEditText().removeTextChangedListener(this);
            mBinding.playlistSearchText.getEditText().setText("");
            mBinding.playlistSearchText.getEditText().addTextChangedListener(this);
        }
        UiTools.setKeyboardVisibility(mBinding.playlistSearchText, false);
        mBinding.playlistSearch.setVisibility(View.VISIBLE);
        mBinding.playlistSearchText.setVisibility(View.GONE);
        return true;
    }

    Runnable hideSearchRunnable = new Runnable() {
        @Override
        public void run() {
            hideSearchField();
            mPlaylistAdapter.restoreList();
        }
    };

    @Override
    public void onTextChanged(CharSequence charSequence,  int start, int before, int count) {
        int length = charSequence.length();
        if (length > 1) {
            mPlaylistAdapter.getFilter().filter(charSequence);
            mHandler.removeCallbacks(hideSearchRunnable);
        } else if (length == 0) {
            mPlaylistAdapter.restoreList();
            hideSearchField();
        }
    }

    @Override
    public void afterTextChanged(Editable editable) {}

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);
        mService.addCallback(this);
        mPlaylistAdapter.setService(service);
        update();
    }

    @Override
    public void onStop() {
        /* unregister before super.onStop() since mService is set to null from this call */
        if (mService != null)
            mService.removeCallback(this);
        super.onStop();
    }

    private class LongSeekListener implements View.OnTouchListener {
        boolean forward;
        int normal, pressed;
        long length;

        private LongSeekListener(boolean forwards, int normalRes, int pressedRes) {
            this.forward = forwards;
            this.normal = normalRes;
            this.pressed = pressedRes;
            this.length = -1;
        }

        int possibleSeek;
        boolean vibrated;

        @RequiresPermission(Manifest.permission.VIBRATE)
        Runnable seekRunnable = new Runnable() {
            @Override
            public void run() {
                if(!vibrated) {
                    ((android.os.Vibrator) VLCApplication.getAppContext().getSystemService(Context.VIBRATOR_SERVICE))
                            .vibrate(80);
                    vibrated = true;
                }

                if(forward) {
                    if(length <= 0 || possibleSeek < length)
                        possibleSeek += 4000;
                } else {
                    if(possibleSeek > 4000)
                        possibleSeek -= 4000;
                    else if(possibleSeek <= 4000)
                        possibleSeek = 0;
                }

                mBinding.time.setText(Tools.millisToString(mShowRemainingTime ? possibleSeek-length : possibleSeek));
                mBinding.timeline.setProgress(possibleSeek);
                mBinding.progressBar.setProgress(possibleSeek);
                mHandler.postDelayed(seekRunnable, 50);
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mService == null)
                return false;
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    (forward ? mBinding.next : mBinding.previous).setImageResource(this.pressed);

                    possibleSeek = (int) mService.getTime();

                    mPreviewingSeek = true;
                    vibrated = false;
                    length = mService.getLength();

                    mHandler.postDelayed(seekRunnable, 1000);
                    return true;

                case MotionEvent.ACTION_UP:
                    (forward ? mBinding.next : mBinding.previous).setImageResource(this.normal);
                    mHandler.removeCallbacks(seekRunnable);
                    mPreviewingSeek = false;

                    if(event.getEventTime()-event.getDownTime() < 1000) {
                        if(forward)
                            onNextClick(v);
                        else
                            onPreviousClick(v);
                    } else {
                        if(forward) {
                            if(possibleSeek < mService.getLength())
                                mService.setTime(possibleSeek);
                            else
                                onNextClick(v);
                        } else {
                            if(possibleSeek > 0)
                                mService.setTime(possibleSeek);
                            else
                                onPreviousClick(v);
                        }
                    }
                    return true;
            }
            return false;
        }
    }

    public void showPlaylistTips() {
        AudioPlayerContainerActivity activity = (AudioPlayerContainerActivity)getActivity();
        if (activity != null)
            activity.showTipViewIfNeeded(R.id.audio_playlist_tips, PREF_PLAYLIST_TIPS_SHOWN);
    }

    public void onStateChanged(int newState) {
        mPlayerState = newState;
        switch (newState) {
            case BottomSheetBehavior.STATE_COLLAPSED:
                mBinding.header.setBackgroundResource(DEFAULT_BACKGROUND_DARKER_ID);
                setHeaderVisibilities(false, false, true, true, true, false);
                break;
            case BottomSheetBehavior.STATE_EXPANDED:
                mBinding.header.setBackgroundResource(0);
                setHeaderVisibilities(true, true, false, false, false, true);
                showPlaylistTips();
                if (mService != null)
                    mPlaylistAdapter.setCurrentIndex(mService.getCurrentMediaPosition());
                break;
            default:
                mBinding.header.setBackgroundResource(0);
        }
    }

    static final int UPDATE = 0;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE:
                    doUpdate();
                default:
                    super.handleMessage(msg);
            }
        }
    };
}
