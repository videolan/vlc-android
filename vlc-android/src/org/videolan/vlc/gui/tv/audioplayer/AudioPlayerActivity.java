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

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.browser.BaseTvActivity;
import org.videolan.vlc.gui.view.DividerItemDecoration;
import org.videolan.vlc.media.MediaLibrary;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Collections;

public class AudioPlayerActivity extends BaseTvActivity implements PlaybackService.Client.Callback,
        PlaybackService.Callback, View.OnFocusChangeListener {
    public static final String TAG = "VLC/AudioPlayerActivity";

    public static final String MEDIA_LIST = "media_list";
    public static final String MEDIA_POSITION = "media_position";

    private RecyclerView mRecyclerView;
    private PlaylistAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private ArrayList<MediaWrapper> mMediaList;

    //PAD navigation
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;
    private int mCurrentlyPlaying, mPositionSaved = 0;
    private boolean mShuffling = false;

    private TextView mTitleTv, mArtistTv;
    private ImageView mPlayPauseButton, mCover, mNext, mShuffle, mRepeat;
    private ProgressBar mProgressBar;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_audio_player);

        mMediaList = getIntent().getParcelableArrayListExtra(MEDIA_LIST);
        mCurrentlyPlaying = getIntent().getIntExtra(MEDIA_POSITION, 0);
        mRecyclerView = (RecyclerView) findViewById(R.id.playlist);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        mRecyclerView.setOnFocusChangeListener(this);
        if (mMediaList == null)
            mMediaList = new ArrayList<MediaWrapper>();
//        if (getIntent().getData() != null)
//            mMediaList.add(getIntent().getDataString());
        mAdapter = new PlaylistAdapter(this, mMediaList);
        mRecyclerView.setAdapter(mAdapter);

        mTitleTv = (TextView)findViewById(R.id.media_title);
        mArtistTv = (TextView)findViewById(R.id.media_artist);
        mNext = (ImageView)findViewById(R.id.button_next);
        mPlayPauseButton = (ImageView)findViewById(R.id.button_play);
        mShuffle = (ImageView)findViewById(R.id.button_shuffle);
        mRepeat = (ImageView)findViewById(R.id.button_repeat);
        mProgressBar = (ProgressBar)findViewById(R.id.media_progress);
        mCover = (ImageView)findViewById(R.id.album_cover);
    }


    @Override
    protected void onStop() {
        /* unregister before super.onStop() since mService is set to null from this call */
        if (mService != null)
            mService.removeCallback(this);
        super.onStop();
    }

    protected void onResume() {
        super.onResume();
        mRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    };

    @Override
    public void onConnected(PlaybackService service) {
        super.onConnected(service);

        mService.addCallback(this);
        ArrayList<MediaWrapper> medias = (ArrayList<MediaWrapper>) mService.getMedias();
        if (!mMediaList.isEmpty() && !mMediaList.equals(medias)) {
            mService.load(mMediaList, mCurrentlyPlaying);
        } else {
            mMediaList = medias;
            update();
            mAdapter = new PlaylistAdapter(this, mMediaList);
            mRecyclerView.setAdapter(mAdapter);
        }

    }

    @Override
    protected void refresh() {
        update();
    }

    @Override
    protected void onNetworkUpdated() {
        update();
    }

    @Override
    public void update() {
        if (mService == null)
            return;
        mPlayPauseButton.setImageResource(mService.isPlaying() ? R.drawable.ic_pause_w : R.drawable.ic_play_w);
        if (mService.hasMedia()) {
            SharedPreferences mSettings= PreferenceManager.getDefaultSharedPreferences(this);
            if (mSettings.getBoolean(PreferencesActivity.VIDEO_RESTORE, false)){
                Util.commitPreferences(mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, false));
                mService.switchToVideo();
                finish();
                return;
            }
            mTitleTv.setText(mService.getTitle());
            mArtistTv.setText(mService.getArtist());
            mProgressBar.setMax((int) mService.getLength());
            MediaWrapper MediaWrapper = MediaLibrary.getInstance().getMediaItem(mService.getCurrentMediaLocation());
            Bitmap cover = AudioUtil.getCover(this, MediaWrapper, mCover.getWidth());
            if (cover == null)
                cover = mService.getCover();
            if (cover == null)
                mCover.setImageResource(R.drawable.ic_tv_icon_big);
            else
                mCover.setImageBitmap(cover);

            mCurrentlyPlaying=mService.getCurrentMediaPosition();
            selectItem(mCurrentlyPlaying);
        }
    }

    @Override
    public void updateProgress() {
        if (mService != null)
            mProgressBar.setProgress((int)mService.getTime());
    }

    @Override
    public void onMediaEvent(Media.Event event) {

    }

    @Override
    public void onMediaPlayerEvent(MediaPlayer.Event event) {

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
                mService.stop();
                finish();
                return true;
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_BUTTON_R1:
                goNext();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mProgressBar.hasFocus()) {
                    seek(10000);
                    return true;
                } else
                    return false;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (mProgressBar.hasFocus()) {
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
            /*
             * Playlist navigation
             */
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mRecyclerView.hasFocus()) {
                    selectPrevious();
                    return true;
                } else
                    return false;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mRecyclerView.hasFocus()) {
                    selectNext();
                    return true;
                } else
                    return false;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mRecyclerView.hasFocus()) {
                    playSelection();
                    return true;
                } else
                    return false;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    public void playSelection() {
        if (mService == null)
            return;
        mService.playIndex(mAdapter.getmSelectedItem());
        mCurrentlyPlaying = mAdapter.getmSelectedItem();
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event){
        //Check for a joystick event
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) !=
                InputDevice.SOURCE_JOYSTICK ||
                event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        InputDevice inputDevice = event.getDevice();

        float dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (inputDevice == null || Math.abs(dpadx) == 1.0f || Math.abs(dpady) == 1.0f)
            return false;

        float x = AndroidDevices.getCenteredAxis(event, inputDevice,
                MotionEvent.AXIS_X);

        if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY){
            if (Math.abs(x) > 0.3){
                seek(x > 0.0f ? 10000 : -10000);
                mLastMove = System.currentTimeMillis();
                return true;
            }
        }
        return true;
    }

    private void seek(int delta) {
        if (mService == null)
            return;
        int time = (int) mService.getTime()+delta;
        if (time < 0 || time > mService.getLength())
            return;
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
        if (mService == null)
            return;
        mShuffling = shuffle;
        mShuffle.setImageResource(shuffle ? R.drawable.ic_shuffle_on :
                R.drawable.ic_shuffle_w);
        ArrayList<MediaWrapper> medias = (ArrayList<MediaWrapper>) mService.getMedias();
        if (shuffle){
            Collections.shuffle(medias);
        } else {
            Collections.sort(medias, MediaComparators.byTrackNumber);
        }
        mService.load(medias, 0);
        mAdapter.updateList(medias);
        update();
    }

    private void updateRepeatMode() {
        if (mService == null)
            return;
        int type = mService.getRepeatType();
        if (type == PlaybackService.REPEAT_NONE){
            mService.setRepeatType(PlaybackService.REPEAT_ALL);
            mRepeat.setImageResource(R.drawable.ic_repeat_all);
        } else if (type == PlaybackService.REPEAT_ALL) {
            mService.setRepeatType(PlaybackService.REPEAT_ONE);
            mRepeat.setImageResource(R.drawable.ic_repeat_one);
        } else if (type == PlaybackService.REPEAT_ONE) {
            mService.setRepeatType(PlaybackService.REPEAT_NONE);
            mRepeat.setImageResource(R.drawable.ic_repeat_w);
        }
    }

    private void goPrevious() {
        if (mService != null && mService.hasPrevious()) {
            mService.previous();
        }
    }

    private void goNext() {
        if (mService != null && mService.hasNext()){
            mService.next();
        }
    }

    private void togglePlayPause() {
        if (mService == null)
            return;
        if (mService.isPlaying())
            mService.pause();
        else if (mService.hasMedia())
            mService.play();
    }

    private void selectNext() {
        if (mAdapter.getmSelectedItem() >= mAdapter.getItemCount()-1) {
            mProgressBar.requestFocus();
            selectItem(-1);
            return;
        }
        selectItem(mAdapter.getmSelectedItem()+1);
    }

    private void selectPrevious() {
        if (mAdapter.getmSelectedItem() < 1){
            mPlayPauseButton.requestFocus();
            selectItem(-1);
            return;
        }
        selectItem(mAdapter.getmSelectedItem()-1);
    }

    private void selectItem(final int position){
        if (position >= mMediaList.size())
            return;
        mRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (position != -1 && (position > mLayoutManager.findLastCompletelyVisibleItemPosition()
                        || position < mLayoutManager.findFirstCompletelyVisibleItemPosition())) {
                    mRecyclerView.stopScroll();
                    mRecyclerView.smoothScrollToPosition(position);
                }
                mAdapter.setSelection(position);
            }
        });
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus)
            selectItem(mPositionSaved);
        else {
            if (mAdapter.getmSelectedItem() != -1)
                mPositionSaved = mAdapter.getmSelectedItem();
            selectItem(-1);
        }
    }
}
