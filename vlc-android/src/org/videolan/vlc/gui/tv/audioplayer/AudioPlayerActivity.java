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
import android.arch.lifecycle.ViewModelProviders;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.databinding.TvAudioPlayerBinding;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.WorkersKt;
import org.videolan.vlc.viewmodels.PlaylistModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class AudioPlayerActivity extends BaseTvActivity implements PlaybackService.Client.Callback,
        PlaybackService.Callback {
    public static final String TAG = "VLC/AudioPlayerActivity";

    public static final String MEDIA_LIST = "media_list";
    public static final String MEDIA_POSITION = "media_position";

    private TvAudioPlayerBinding mBinding;
    private PlaylistAdapter mAdapter;
    private List<MediaWrapper> mMediaList;

    //PAD navigation
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;
    private int mCurrentlyPlaying;
    private boolean mShuffling = false;
    private String mCurrentCoverArt;
    private PlaylistModel model;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.tv_audio_player);

        mMediaList = getIntent().getParcelableArrayListExtra(MEDIA_LIST);
        if (mMediaList == null) mMediaList = new ArrayList<>();
        mCurrentlyPlaying = getIntent().getIntExtra(MEDIA_POSITION, 0);
        mBinding.playlist.setLayoutManager(new LinearLayoutManager(this));
        mBinding.playlist.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mAdapter = new PlaylistAdapter(this, mMediaList);
        mBinding.playlist.setAdapter(mAdapter);
        mBinding.setLifecycleOwner(this);
    }

    @Override
    protected void onStop() {
        /* unregister before super.onStop() since mService is set to null from this call */
        if (mService != null) mService.removeCallback(this);
        super.onStop();
    }

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);

        mService.addCallback(this);
        final List<MediaWrapper> medias = mService.getMedias();
        if (!mMediaList.isEmpty() && !mMediaList.equals(medias)) {
            mService.load(mMediaList, mCurrentlyPlaying);
        } else {
            mMediaList = medias;
            if (mCurrentlyPlaying != mService.getCurrentMediaPosition())
                mService.playIndex(mCurrentlyPlaying);
            update();
            mAdapter.updateList(mMediaList);
        }
        model = ViewModelProviders.of(this, new PlaylistModel.Factory(service)).get(PlaylistModel.class);
        model.setup();
        mBinding.setProgress(model.getProgress());
    }

    @Override
    public void onDisconnected() {
        mBinding.setProgress(null);
        super.onDisconnected();
    }

    @Override
    protected void refresh() {
        update();
    }

    @Override
    public void onNetworkConnectionChanged(boolean connected) {
        update();
    }

    @Override
    public void update() {
        if (mService == null || !mService.hasMedia()) return;
        mBinding.buttonPlay.setImageResource(mService.isPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
        final SharedPreferences mSettings= PreferenceManager.getDefaultSharedPreferences(this);
        if (mSettings.getBoolean(PreferencesActivity.VIDEO_RESTORE, false)) {
            mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false).apply();
            mService.getCurrentMediaWrapper().removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);
            mService.switchToVideo();
            finish();
            return;
        }
        mBinding.mediaTitle.setText(mService.getTitle());
        mBinding.mediaArtist.setText(mService.getArtist());
        mCurrentlyPlaying = mService.getCurrentMediaPosition();
        mAdapter.setSelection(mCurrentlyPlaying);
        final MediaWrapper mw = mService.getCurrentMediaWrapper();
        if (TextUtils.equals(mCurrentCoverArt, mw.getArtworkMrl())) return;
        mCurrentCoverArt = mw.getArtworkMrl();
        updateBackground();
    }

    private void updateBackground() {
        WorkersKt.runBackground(new Runnable() {
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

    @Override
    public void onMediaEvent(Media.Event event) {}

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {}

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
                mService.stop();
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
        if (mService == null) return;
        mService.playIndex(mAdapter.getSelectedItem());
        mCurrentlyPlaying = mAdapter.getSelectedItem();
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
        if (mService == null) return;
        int time = (int) mService.getTime()+delta;
        if (time < 0 || time > mService.getLength()) return;
        mService.setTime(time);
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
        if (mService == null) return;
        mShuffling = shuffle;
        mBinding.buttonShuffle.setImageResource(shuffle ? R.drawable.ic_shuffle_on :
                R.drawable.ic_shuffle_w);
        final List<MediaWrapper> medias = mService.getMedias();
        if (shuffle) Collections.shuffle(medias);
        else Collections.sort(medias, MediaComparators.byTrackNumber);
        mService.load(medias, 0);
        mAdapter.updateList(medias);
        update();
    }

    private void updateRepeatMode() {
        if (mService == null) return;
        int type = mService.getRepeatType();
        if (type == Constants.REPEAT_NONE){
            mService.setRepeatType(Constants.REPEAT_ALL);
            mBinding.buttonRepeat.setImageResource(R.drawable.ic_repeat_all);
        } else if (type == Constants.REPEAT_ALL) {
            mService.setRepeatType(Constants.REPEAT_ONE);
            mBinding.buttonRepeat.setImageResource(R.drawable.ic_repeat_one);
        } else if (type == Constants.REPEAT_ONE) {
            mService.setRepeatType(Constants.REPEAT_NONE);
            mBinding.buttonRepeat.setImageResource(R.drawable.ic_repeat_w);
        }
    }

    private void goPrevious() {
        if (mService != null && mService.hasPrevious()) mService.previous(false);
    }

    private void goNext() {
        if (mService != null && mService.hasNext()) mService.next();
    }

    private void togglePlayPause() {
        if (mService == null) return;
        if (mService.isPlaying()) mService.pause();
        else if (mService.hasMedia()) mService.play();
    }
}
