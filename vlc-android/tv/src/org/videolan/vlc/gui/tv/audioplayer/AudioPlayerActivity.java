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

import java.util.ArrayList;
import java.util.Collections;

import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.PlaybackServiceClient;
import org.videolan.vlc.R;
import org.videolan.vlc.gui.DividerItemDecoration;
import org.videolan.vlc.gui.audio.AudioUtil;
import org.videolan.vlc.gui.audio.MediaComparators;
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

public class AudioPlayerActivity extends Activity implements PlaybackServiceClient.Callback, View.OnFocusChangeListener {
    public static final String TAG = "VLC/AudioPlayerActivity";

    public static final String MEDIA_LIST = "media_list";

    private PlaybackServiceClient mClient;
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
        mRecyclerView = (RecyclerView) findViewById(R.id.playlist);
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        if (mMediaList == null)
            mMediaList = new ArrayList<MediaWrapper>();
//        if (getIntent().getData() != null)
//            mMediaList.add(getIntent().getDataString());
        mAdapter = new PlaylistAdapter(this, mMediaList);
        mRecyclerView.setAdapter(mAdapter);

        mClient = new PlaybackServiceClient(this, this);

        mClient.getRepeatType();
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

    @Override
    protected void onStart() {
        super.onStart();
        mClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mClient.disconnect();
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
    public void onConnected() {
        ArrayList<MediaWrapper> medias = (ArrayList<MediaWrapper>) mClient.getMedias();
        if (!mMediaList.isEmpty() && !mMediaList.equals(medias)) {
            mClient.load(mMediaList, 0);
        } else {
            mMediaList = medias;
            update();
            mAdapter = new PlaylistAdapter(this, mMediaList);
            mRecyclerView.setAdapter(mAdapter);
        }
    }

    @Override
    public void onDisconnected() {
    }

    @Override
    public void update() {
        if (!mClient.isConnected())
            return;
        mPlayPauseButton.setImageResource(mClient.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        if (mClient.hasMedia()) {
            mTitleTv.setText(mClient.getTitle());
            mArtistTv.setText(mClient.getArtist());
            mProgressBar.setMax(mClient.getLength());
            MediaWrapper MediaWrapper = MediaLibrary.getInstance().getMediaItem(mClient.getCurrentMediaLocation());
            Bitmap cover = AudioUtil.getCover(this, MediaWrapper, mCover.getWidth());
            if (cover == null)
                cover = mClient.getCover();
            if (cover == null)
                mCover.setImageResource(R.drawable.background_cone);
            else
                mCover.setImageBitmap(cover);
        }
    }

    @Override
    public void updateProgress() {
        mProgressBar.setProgress(mClient.getTime());
    }

    @Override
    public void onMediaPlayedAdded(MediaWrapper media, int index) {

    }

    @Override
    public void onMediaPlayedRemoved(int index) {

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
        mClient.playIndex(mAdapter.getmSelectedItem());
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
        int time = mClient.getTime()+delta;
        if (time < 0 || time > mClient.getLength())
            return;
        mClient.setTime(time);
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
        if (!mClient.isConnected())
            return;
        mShuffling = shuffle;
        mShuffle.setImageResource(shuffle ? R.drawable.ic_shuffle_on :
                R.drawable.ic_shuffle);
        ArrayList<MediaWrapper> medias = (ArrayList<MediaWrapper>) mClient.getMedias();
        if (shuffle){
            Collections.shuffle(medias);
        } else {
            Collections.sort(medias, MediaComparators.byTrackNumber);
        }
        mClient.load(medias, 0);
        mAdapter.updateList(medias);
        update();
    }

    private void updateRepeatMode() {
        PlaybackService.RepeatType type = mClient.getRepeatType();
        if (type == PlaybackService.RepeatType.None){
            mClient.setRepeatType(PlaybackService.RepeatType.All);
            mRepeat.setImageResource(R.drawable.ic_repeat_on);
        } else if (type == PlaybackService.RepeatType.All) {
            mClient.setRepeatType(PlaybackService.RepeatType.Once);
            mRepeat.setImageResource(R.drawable.ic_repeat_one);
        } else if (type == PlaybackService.RepeatType.Once) {
            mClient.setRepeatType(PlaybackService.RepeatType.None);
            mRepeat.setImageResource(R.drawable.ic_repeat);
        }
    }

    private void goPrevious() {
        if (mClient.hasPrevious()) {
            mClient.previous();
            selectItem(--mCurrentlyPlaying);
        }
    }

    private void goNext() {
        if (mClient.hasNext()){
            mClient.next();
            selectItem(++mCurrentlyPlaying);
        }
    }

    private void togglePlayPause() {
        if (mClient.isPlaying())
            mClient.pause();
        else if (mClient.hasMedia())
            mClient.play();
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
        if (hasFocus) {
            if (mAdapter.getmSelectedItem() != -1)
                mPositionSaved = mAdapter.getmSelectedItem();
            selectItem(-1);
        } else if (!mNext.hasFocus())
            selectItem(mPositionSaved);
    }
}
