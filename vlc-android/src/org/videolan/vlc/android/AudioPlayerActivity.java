package org.videolan.vlc.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class AudioPlayerActivity extends Activity implements AudioPlayer {
	public final static String TAG = "VLC/AudioPlayerActiviy";
	
	private ImageView mCover;
	private TextView mTitle;
	private TextView mArtist;
	private TextView mAlbum;
	private TextView mGenre;
	private ImageButton mPlayPause;
	private ImageButton mNext;
	private ImageButton mPrevious;
	private ImageButton mShuffle;
	private ImageButton mRepeat;
	private SeekBar mTimeline;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.audio_player);
		
		mCover = (ImageView) findViewById(R.id.cover);
		mTitle = (TextView) findViewById(R.id.title);
		mArtist = (TextView) findViewById(R.id.artist);
//		mAlbum = (TextView) findViewById(R.id.album);
//		mGenre = (TextView) findViewById(R.id.genre);
//		mPlayPause = (ImageButton) findViewById(R.id.play_pause);
//		mNext = (ImageButton) findViewById(R.id.next);
//		mPrevious = (ImageButton) findViewById(R.id.previous);
//		mShuffle = (ImageButton) findViewById(R.id.shuffle);
//		mRepeat = (ImageButton) findViewById(R.id.repeat);
		mTimeline = (SeekBar) findViewById(R.id.timeline);
		
	}

	@Override
	public void update() {
		
	}
	

	
	
}
