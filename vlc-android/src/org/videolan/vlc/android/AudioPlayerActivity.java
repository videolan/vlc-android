package org.videolan.vlc.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class AudioPlayerActivity extends Activity implements AudioPlayer {
	public final static String TAG = "VLC/AudioPlayerActiviy";

	private ImageView mCover;
	private TextView mTitle;
	private TextView mArtist;
	private TextView mAlbum;
	private TextView mTime;
	private TextView mLength;
	private ImageButton mPlayPause;
	private ImageButton mNext;
	private ImageButton mPrevious;
	private ImageButton mShuffle;
	private ImageButton mRepeat;
	private SeekBar mTimeline;

	private AudioServiceController mAudioController;
	private boolean mIsTracking = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.audio_player);

		mCover = (ImageView) findViewById(R.id.cover);
		mTitle = (TextView) findViewById(R.id.title);
		mArtist = (TextView) findViewById(R.id.artist);
		mAlbum = (TextView) findViewById(R.id.album);
		mTime = (TextView) findViewById(R.id.time);
		mLength = (TextView) findViewById(R.id.length);
		mPlayPause = (ImageButton) findViewById(R.id.play_pause);
		mNext = (ImageButton) findViewById(R.id.next);
		mPrevious = (ImageButton) findViewById(R.id.previous);
		mShuffle = (ImageButton) findViewById(R.id.shuffle);
		mRepeat = (ImageButton) findViewById(R.id.repeat);
		mTimeline = (SeekBar) findViewById(R.id.timeline);

		mAudioController = AudioServiceController.getInstance();

	}

	@Override
	protected void onStart() {
		mAudioController.addAudioPlayer(this);
		update();
		super.onStart();
	}

	@Override
	protected void onStop() {
		mAudioController.removeAudioPlayer(this);
		super.onStop();
	}

	@Override
	public void update() {
		// Exit the player when there is no media
		if (!mAudioController.hasMedia())
			finish();

		// mCover....
		mTitle.setText(mAudioController.getTitle());
		mArtist.setText(mAudioController.getArtist());
		mAlbum.setText(mAudioController.getAlbum());
		int time = (int) mAudioController.getTime();
		int length = (int) mAudioController.getLength();
		mTime.setText(Util.millisToString(time));
		mLength.setText(Util.millisToString(length));
		mTimeline.setMax(length);
		if (!mIsTracking)
			mTimeline.setProgress(time);
		if (mAudioController.isPlaying()) {
			mPlayPause.setBackgroundResource(R.drawable.ic_pause);
		} else {
			mPlayPause.setBackgroundResource(R.drawable.ic_play);
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
				mTime.setText(Util.millisToString(prog))
;			}
		}
	};

	public void onPlayPauseClick(View view) {
		if (mAudioController.isPlaying()) {
			mAudioController.pause();
		} else {
			mAudioController.play();
		}
	}

	public void onNextClick(View view) {
		mAudioController.next();
	}

	public void onPreviousClick(View view) {
		mAudioController.previous();
	}

	public void onRepeatClick(View view) {
		// mAudioController.repeat();
		Util.toaster("not implemented :(");
	}

	public void onShuffleClick(View view) {
		// mAudioController.shuffle();
		Util.toaster("not implemented :(");
	}




}
