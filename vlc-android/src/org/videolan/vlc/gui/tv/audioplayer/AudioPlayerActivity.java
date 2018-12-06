/*****************************************************************************
 * AudioPlayerActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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
 *****************************************************************************/
package org.videolan.vlc.gui.tv.audioplayer;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.TvAudioPlayerBinding;
import org.videolan.vlc.gui.PlaybackServiceActivity;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.media.MediaUtils;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Settings;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.PlayerState;
import org.videolan.vlc.viewmodels.PlaylistModel;

import java.util.Collections;
import java.util.List;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class AudioPlayerActivity extends BaseTvActivity {
    public static final String TAG = "VLC/AudioPlayerActivity";

    public static final String MEDIA_LIST = "media_list";
    public static final String MEDIA_POSITION = "media_position";
    private PlaybackServiceActivity.Helper mHelper;

    private TvAudioPlayerBinding mBinding;
    private PlaylistAdapter mAdapter;
    private final Handler mHandler = new Handler();

    //PAD navigation
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;
    private boolean mShuffling = false;
    private String mCurrentCoverArt;
    private PlaylistModel model;
    private SharedPreferences mSettings;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.tv_audio_player);
        mSettings = Settings.INSTANCE.getInstance(this);

        mBinding.playlist.setLayoutManager(new LinearLayoutManager(this));
        mBinding.playlist.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mAdapter = new PlaylistAdapter(this);
        mBinding.playlist.setAdapter(mAdapter);
        mBinding.setLifecycleOwner(this);
        model = ViewModelProviders.of(this).get(PlaylistModel.class);
        mBinding.setProgress(model.getProgress());
        mHelper = new PlaybackServiceActivity.Helper(this, model);
        model.getDataset().observe(this, new Observer<List<MediaWrapper>>() {
            @Override
            public void onChanged(List<MediaWrapper> mediaWrappers) {
                if (mediaWrappers != null) {
                    mAdapter.setSelection(-1);
                    mAdapter.update(mediaWrappers);
                }
            }
        });
        model.getPlayerState().observe(this, new Observer<PlayerState>() {
            @Override
            public void onChanged(PlayerState playerState) {
                update(playerState);
            }
        });
        final List<MediaWrapper> medialist = getIntent().getParcelableArrayListExtra(MEDIA_LIST);
        final int position = getIntent().getIntExtra(MEDIA_POSITION, 0);
        if (medialist != null) MediaUtils.INSTANCE.openList(this, medialist, position);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mHelper.onStart();
    }

    @Override
    protected void onStop() {
        mHelper.onStop();
        super.onStop();
    }

    @Override
    protected void refresh() {}


    public void update(PlayerState state) {
        if (state == null) return;
        mBinding.buttonPlay.setImageResource(state.getPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
        final MediaWrapper mw = model.getCurrentMediaWrapper();
        if (mw != null && !mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) && model.canSwitchToVideo()) {
            model.switchToVideo();
            finish();
            return;
        }
        mBinding.mediaTitle.setText(state.getTitle());
        mBinding.mediaArtist.setText(state.getArtist());
        mBinding.buttonShuffle.setImageResource(mShuffling ? R.drawable.ic_shuffle_on :
                R.drawable.ic_shuffle_w);
        if (mw == null || TextUtils.equals(mCurrentCoverArt, mw.getArtworkMrl())) return;
        mCurrentCoverArt = mw.getArtworkMrl();
        updateBackground();
    }

    private void updateBackground() {
        WorkersKt.runIO(new Runnable() {
            @Override
            public void run() {
                final Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(mCurrentCoverArt), mBinding.albumCover.getWidth());
                final Bitmap blurredCover = cover != null ? UiTools.blurBitmap(cover) : null;
                WorkersKt.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cover == null) {
                            mBinding.albumCover.setImageResource(R.drawable.ic_no_artwork_big);
                            mBinding.background.clearColorFilter();
                            mBinding.background.setImageResource(0);
                        } else {
                            mBinding.albumCover.setImageBitmap(cover);
                            mBinding.background.setColorFilter(UiTools.getColorFromAttribute(mBinding.background.getContext(), R.attr.audio_player_background_tint));
                            mBinding.background.setImageBitmap(blurredCover);
                        }
                    }
                });
            }
        });
    }

    public boolean onKeyDown(int keyCode, KeyEvent event){
        switch (keyCode){
            /*
             * Playback control
             */
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                togglePlayPause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                model.stop();
                finish();
                return true;
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_BUTTON_R1:
                goNext();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mBinding.mediaProgress.hasFocus()) {
                    seek(10000);
                    return true;
                } else
                    return false;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mBinding.mediaProgress.hasFocus()) {
                    seek(-10000);
                    return true;
                } else
                    return false;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(10000);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_BUTTON_L1:
                goPrevious();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    public void playSelection() {
        model.play(mAdapter.getSelectedItem());
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event){
        //Check for a joystick event
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) !=
                InputDevice.SOURCE_JOYSTICK ||
                event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        final InputDevice inputDevice = event.getDevice();

        float dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (inputDevice == null || Math.abs(dpadx) == 1.0f || Math.abs(dpady) == 1.0f) return false;

        float x = AndroidDevices.getCenteredAxis(event, inputDevice,
                MotionEvent.AXIS_X);

        if (Math.abs(x) > 0.3 && System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY) {
            seek(x > 0.0f ? 10000 : -10000);
            mLastMove = System.currentTimeMillis();
            return true;
        }
        return true;
    }

    private void seek(int delta) {
        int time = (int) model.getTime()+delta;
        if (time < 0 || time > model.getLength()) return;
        model.setTime(time);
    }

    public void onClick(View v){
        switch (v.getId()){
            case R.id.button_play:
                togglePlayPause();
                break;
            case R.id.button_next:
                goNext();
                break;
            case R.id.button_previous:
                goPrevious();
                break;
            case R.id.button_repeat:
                updateRepeatMode();
                break;
            case R.id.button_shuffle:
                setShuffleMode(!mShuffling);
                break;
        }
    }

    private void setShuffleMode(boolean shuffle) {
        mShuffling = shuffle;
        final List<MediaWrapper> medias = model.getMedias();
        if (medias == null) return;
        if (shuffle) Collections.shuffle(medias);
        else Collections.sort(medias, MediaComparators.byTrackNumber);
        model.load(medias, 0);
    }

    private void updateRepeatMode() {
        int type = model.getRepeatType();
        if (type == Constants.REPEAT_NONE){
            model.setRepeatType(Constants.REPEAT_ALL);
            mBinding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all);
        } else if (type == Constants.REPEAT_ALL) {
            model.setRepeatType(Constants.REPEAT_ONE);
            mBinding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one);
        } else if (type == Constants.REPEAT_ONE) {
            model.setRepeatType(Constants.REPEAT_NONE);
            mBinding.buttonRepeat.setImageResource(R.drawable.ic_repeat_w);
        }
    }

    private void goPrevious() {
        model.previous(false);
    }

    private void goNext() {
        model.next();
    }

    private void togglePlayPause() {
        model.togglePlayPause();
    }

    public void onUpdateFinished() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final int position = model.getCurrentMediaPosition();
                mAdapter.setSelection(position);
                int first = ((LinearLayoutManager)mBinding.playlist.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
                int last = ((LinearLayoutManager)mBinding.playlist.getLayoutManager()).findLastCompletelyVisibleItemPosition();
                if (position < first || position > last) mBinding.playlist.smoothScrollToPosition(position);
            }
        });
    }
}
