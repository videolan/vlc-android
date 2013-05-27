/*****************************************************************************
 * AudioPlayerActivity.java
 *****************************************************************************
 * Copyright © 2011-2013 VLC authors and VideoLAN
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

import org.videolan.vlc.AudioService;
import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.R;
import org.videolan.vlc.RepeatType;
import org.videolan.vlc.Util;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.interfaces.IAudioPlayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class AudioPlayerActivity extends Activity implements IAudioPlayer {
    public final static String TAG = "VLC/AudioPlayerActivity";

    private ImageView mCover;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mAlbum;
    private TextView mTime;
    private TextView mLength;
    private ImageButton mPlayPause;
    private ImageButton mStop;
    private ImageButton mNext;
    private ImageButton mPrevious;
    private ImageButton mShuffle;
    private ImageButton mRepeat;
    private ImageButton mAdvFunc;
    private SeekBar mTimeline;

    private AudioServiceController mAudioController;
    private boolean mShowRemainingTime = false;
    private String lastTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics screen = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(screen);
        Log.v(TAG, "width = " + screen.widthPixels + " : height = " + screen.heightPixels);
        if(screen.widthPixels == 240 && screen.heightPixels == 320) /* QVGA 2.7in */
        	setContentView(R.layout.audio_player_qvga);
        else
        	setContentView(R.layout.audio_player);

        mCover = (ImageView) findViewById(R.id.cover);
        mTitle = (TextView) findViewById(R.id.title);
        mArtist = (TextView) findViewById(R.id.artist);
        mAlbum = (TextView) findViewById(R.id.album);
        mTime = (TextView) findViewById(R.id.time);
        mLength = (TextView) findViewById(R.id.length);
        mPlayPause = (ImageButton) findViewById(R.id.play_pause);
        mStop = (ImageButton) findViewById(R.id.stop);
        mNext = (ImageButton) findViewById(R.id.next);
        mPrevious = (ImageButton) findViewById(R.id.previous);
        mShuffle = (ImageButton) findViewById(R.id.shuffle);
        mRepeat = (ImageButton) findViewById(R.id.repeat);
        mAdvFunc = (ImageButton) findViewById(R.id.adv_function);
        mTimeline = (SeekBar) findViewById(R.id.timeline);

        View.OnFocusChangeListener listener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus)
                    v.setBackgroundColor(Color.parseColor("#FFBA6F"));
                else
                    v.setBackgroundColor(Color.TRANSPARENT);
            }
        };
        mShuffle.setOnFocusChangeListener(listener);
        mRepeat.setOnFocusChangeListener(listener);
        mAdvFunc.setOnFocusChangeListener(listener);
        mTimeline.setOnFocusChangeListener(listener);
        mPrevious.setOnFocusChangeListener(listener);
        mPlayPause.setOnFocusChangeListener(listener);
        mStop.setOnFocusChangeListener(listener);
        mNext.setOnFocusChangeListener(listener);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mAudioController = AudioServiceController.getInstance();
        lastTitle = "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioServiceController.getInstance().bindAudioService(this);
        mAudioController.addAudioPlayer(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAudioController.removeAudioPlayer(this);
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    public static void start(Context context) {
        start(context, false);
    }

    public static void start(Context context, Boolean dontParse) {
        if (context == null) {
            Log.e(TAG, "No context when starting AudioPlayerActivity");
            return;
        }
        Intent intent = new Intent(context, AudioPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (dontParse)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        Bundle extras = getIntent().getExtras();

        if (extras != null && extras.containsKey(AudioService.START_FROM_NOTIFICATION)) {
            // Launched from notification (adding the MainActivity to the backstack)
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void update() {
        // Exit the player and return to the main menu when there is no media
        if (!mAudioController.hasMedia()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            finish();
            return;
        }

        String title = mAudioController.getTitle();
        if (title != null && !title.equals(lastTitle)) {
            Bitmap cover = mAudioController.getCover();
            if (cover != null)
                mCover.setImageBitmap(cover);
            else
                mCover.setImageResource(R.drawable.cone);
        }
        lastTitle = title;
        mTitle.setText(lastTitle);
        mArtist.setText(mAudioController.getArtist());
        mAlbum.setText(mAudioController.getAlbum());
        int time = mAudioController.getTime();
        int length = mAudioController.getLength();
        mTime.setText(Util.millisToString(mShowRemainingTime ? time-length : time));
        mLength.setText(Util.millisToString(length));
        mTimeline.setMax(length);
        mTimeline.setProgress(time);
        if (mAudioController.isPlaying()) {
            mPlayPause.setImageResource(R.drawable.ic_pause);
            mPlayPause.setContentDescription(getString(R.string.pause));
        } else {
            mPlayPause.setImageResource(R.drawable.ic_play);
            mPlayPause.setContentDescription(getString(R.string.play));
        }
        if (mAudioController.isShuffling()) {
            mShuffle.setImageResource(R.drawable.ic_shuffle_glow);
        } else {
            mShuffle.setImageResource(R.drawable.ic_shuffle);
        }
        switch(mAudioController.getRepeatType()) {
        case None:
            mRepeat.setImageResource(R.drawable.ic_repeat);
            break;
        case Once:
            mRepeat.setImageResource(R.drawable.ic_repeat_one);
            break;
        default:
        case All:
            mRepeat.setImageResource(R.drawable.ic_repeat_glow);
            break;
        }
        if (mAudioController.hasNext())
            mNext.setVisibility(ImageButton.VISIBLE);
        else
            mNext.setVisibility(ImageButton.INVISIBLE);
        if (mAudioController.hasPrevious())
            mPrevious.setVisibility(ImageButton.VISIBLE);
        else
            mPrevious.setVisibility(ImageButton.INVISIBLE);
        mTimeline.setOnSeekBarChangeListener(mTimelineListner);
    }

    OnSeekBarChangeListener mTimelineListner = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onStartTrackingTouch(SeekBar arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
            if (fromUser) {
                mAudioController.setTime(prog);
                mTime.setText(Util.millisToString(mShowRemainingTime ? prog-mAudioController.getLength() : prog))
            ;
        }
    }
    };

    public void onTimeLabelClick(View view) {
        mShowRemainingTime = !mShowRemainingTime;
        update();
    }

    public void onTextClick(View view) {
        //FIXME disabled until AudioPlayerActivity is converted to a fragment
        // it's not possible to call startActivity on a Fragment.

        //Intent intent = new Intent(this, AudioListFragment.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //startActivity(intent);
    }

    public void onPlayPauseClick(View view) {
        if (mAudioController.isPlaying()) {
            mAudioController.pause();
        } else {
            mAudioController.play();
        }
    }

    public void onStopClick(View view) {
    	mAudioController.stop();
    	Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    	finish();
    }

    public void onNextClick(View view) {
        mAudioController.next();
    }

    public void onPreviousClick(View view) {
        mAudioController.previous();
    }

    public void onRepeatClick(View view) {
        switch (mAudioController.getRepeatType()) {
            case None:
                mAudioController.setRepeatType(RepeatType.All);
                break;
            case All:
                mAudioController.setRepeatType(RepeatType.Once);
                break;
            default:
            case Once:
                mAudioController.setRepeatType(RepeatType.None);
                break;
        }
        update();
    }

    public void onShuffleClick(View view) {
        mAudioController.shuffle();
        update();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	/* Stop the controller if we are going home */
    	if(keyCode == KeyEvent.KEYCODE_HOME) {
    		mAudioController.stop();
    	}
    	return super.onKeyDown(keyCode, event);
    }

    public void showAdvanceFunction(View v) {
        CommonDialogs.advancedOptions(this, v);
    }
}
