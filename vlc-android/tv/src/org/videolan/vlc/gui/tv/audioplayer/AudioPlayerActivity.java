/*****************************************************************************
 * AudioPlayerActivity.java
 *****************************************************************************
 * Copyright © 2014-2015 VLC authors, VideoLAN and VideoLabs
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

import java.util.ArrayList;
import java.util.Collections;

import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.audio.RepeatType;
import org.videolan.vlc.gui.DividerItemDecoration;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.interfaces.IAudioPlayer;
import org.videolan.vlc.util.AndroidDevices;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class AudioPlayerActivity extends Activity implements AudioServiceController.AudioServiceConnectionListener, IAudioPlayer, View.OnFocusChangeListener {
    public static final String TAG = "VLC/AudioPlayerActivity";

    private AudioServiceController mAudioController;
    private RecyclerView mRecyclerView;
    private PlaylistAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;
    private ArrayList<String> mLocations;

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

        mLocations = getIntent().getStringArrayListExtra("locations");
        mRecyclerView = (RecyclerView) findViewById(R.id.playlist);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        if (mLocations == null)
            mLocations = new ArrayList<String>();
        if (getIntent().getData() != null)
            mLocations.add(getIntent().getDataString());
        mAdapter = new PlaylistAdapter(this, mLocations);
        mRecyclerView.setAdapter(mAdapter);

        mAudioController = AudioServiceController.getInstance();

        mAudioController.getRepeatType();
        mTitleTv = (TextView)findViewById(R.id.media_title);
        mArtistTv = (TextView)findViewById(R.id.media_artist);
        mNext = (ImageView)findViewById(R.id.button_next);
        mPlayPauseButton = (ImageView)findViewById(R.id.button_play);
        mShuffle = (ImageView)findViewById(R.id.button_shuffle);
        mRepeat = (ImageView)findViewById(R.id.button_repeat);
        mProgressBar = (ProgressBar)findViewById(R.id.media_progress);
        mCover = (ImageView)findViewById(R.id.album_cover);
        findViewById(R.id.button_shuffle).setOnFocusChangeListener(this);
    }

    public void onStart(){
        super.onStart();
        mAudioController.bindAudioService(this, this);
        mAudioController.addAudioPlayer(this);
    }

    public void onStop(){
        super.onStop();
        mAudioController.removeAudioPlayer(this);
        mAudioController.unbindAudioService(this);
        mLocations.clear();
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
    public void onConnectionSuccess() {
        ArrayList<String> medialocations = (ArrayList<String>) mAudioController.getMediaLocations();
        if (!mLocations.isEmpty() && !mLocations.equals(medialocations)) {
            mAudioController.load(mLocations, 0, true);
        } else {
            mLocations = medialocations;
            update();
            mAdapter = new PlaylistAdapter(this, mLocations);
            mRecyclerView.setAdapter(mAdapter);
        }
    }

    @Override
    public void onConnectionFailed() {}

    @Override
    public void update() {
        mPlayPauseButton.setImageResource(mAudioController.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        if (mAudioController.hasMedia()) {
            mTitleTv.setText(mAudioController.getTitle());
            mArtistTv.setText(mAudioController.getArtist());
            mProgressBar.setMax(mAudioController.getLength());
            MediaWrapper MediaWrapper = MediaLibrary.getInstance().getMediaItem(mAudioController.getCurrentMediaLocation());
            Bitmap cover = AudioUtil.getCover(this, MediaWrapper, mCover.getWidth());
            if (cover == null)
                cover = mAudioController.getCover();
            if (cover == null)
                mCover.setImageResource(R.drawable.background_cone);
            else
                mCover.setImageBitmap(cover);
        }
    }

    @Override
    public void updateProgress() {
        mProgressBar.setProgress(mAudioController.getTime());
    }

    public boolean onKeyDown(int keyCode, KeyEvent event){
        switch (keyCode){
            /*
             * Playback control
             */
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                togglePlayPause();
                return true;
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_BUTTON_R1:
                goNext();
                return true;
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
                selectPrevious();
                mRecyclerView.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                selectNext();
                mRecyclerView.requestFocus();
                return true;
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
        mAudioController.playIndex(mAdapter.getmSelectedItem());
        mCurrentlyPlaying = mAdapter.getmSelectedItem();
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event){
        //Check for a joystick event
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) !=
                InputDevice.SOURCE_JOYSTICK ||
                event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        InputDevice inputDevice = event.getDevice();

        if (inputDevice == null)
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
        int time = mAudioController.getTime()+delta;
        if (time < 0 || time > mAudioController.getLength())
            return;
        mAudioController.setTime(time);
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
        mShuffle.setImageResource(shuffle ? R.drawable.ic_shuffle_white_36dp :
                R.drawable.ic_shuffle_grey600_36dp);
        ArrayList<String> medialocations = (ArrayList<String>) mAudioController.getMediaLocations();
        if (shuffle){
            Collections.shuffle(medialocations);
        } else {
            ArrayList<MediaWrapper> mediaList = new ArrayList<MediaWrapper>(medialocations.size());
            MediaLibrary mediaLibrary = MediaLibrary.getInstance();
            for (String location : medialocations)
                mediaList.add(mediaLibrary.getMediaItem(location));
            Collections.sort(mediaList, MediaComparators.byTrackNumber);
            medialocations.clear();
            for (MediaWrapper media : mediaList)
                medialocations.add(media.getLocation());
        }
        mAudioController.load(medialocations, 0);
        mAdapter.updateList(medialocations);
        update();
    }

    private void updateRepeatMode() {
        RepeatType type = mAudioController.getRepeatType();
        if (type == RepeatType.None){
            mAudioController.setRepeatType(RepeatType.All);
            mRepeat.setImageResource(R.drawable.ic_repeat_white_36dp);
        } else if (type == RepeatType.All) {
            mAudioController.setRepeatType(RepeatType.Once);
            mRepeat.setImageResource(R.drawable.ic_repeat_one_white_36dp);
        } else if (type == RepeatType.Once) {
            mAudioController.setRepeatType(RepeatType.None);
            mRepeat.setImageResource(R.drawable.ic_repeat_grey600_36dp);
        }
    }

    private void goPrevious() {
        if (mAudioController.hasPrevious()) {
            mAudioController.previous();
            selectItem(--mCurrentlyPlaying);
        }
    }

    private void goNext() {
        if (mAudioController.hasNext()){
            mAudioController.next();
            selectItem(++mCurrentlyPlaying);
        }
    }

    private void togglePlayPause() {
        if (mAudioController.isPlaying())
            mAudioController.pause();
        else if (mAudioController.hasMedia())
            mAudioController.play();
    }

    private void selectNext() {
        if (mAdapter.getmSelectedItem() >= mAdapter.getItemCount()-1)
            return;
        selectItem(mAdapter.getmSelectedItem()+1);
    }

    private void selectPrevious() {
        if (mAdapter.getmSelectedItem() < 1)
            return;
        selectItem(mAdapter.getmSelectedItem()-1);
    }

    private void selectItem(final int position){
        if (position >= mLocations.size())
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
        if (hasFocus) {
            if (mAdapter.getmSelectedItem() != -1)
                mPositionSaved = mAdapter.getmSelectedItem();
            selectItem(-1);
        } else if (!mNext.hasFocus())
            selectItem(mPositionSaved);
    }
}
