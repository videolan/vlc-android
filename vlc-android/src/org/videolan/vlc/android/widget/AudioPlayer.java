package org.videolan.vlc.android.widget;

import org.videolan.vlc.android.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AudioPlayer extends LinearLayout {
	public static final String TAG = "VLC/AudioPlayer";
	
	private static final int STATE_MINI_PLAYER = 0;
	private static final int STATE_PLAYER = 1;

	
	private AudioPlayerControl mAudioPlayerControl;
	
	private TextView mTitle;
	private TextView mArtist;
	private ImageButton mPlayPause;

	private int mCurrentState = STATE_MINI_PLAYER;
	
	// Listener for the play and pause buttons
	private OnClickListener onPlayPauseClickListener = new OnClickListener() {		
		@Override
		public void onClick(View v) {
			if (mAudioPlayerControl != null) {
				if (mAudioPlayerControl.isPlaying()) {
					mAudioPlayerControl.pause();
				} else {
					mAudioPlayerControl.play();
				}
			}
			update();
		}
	};

	
	public AudioPlayer(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AudioPlayer(Context context) {
		super(context);
	}

	public void showMiniPlayer() {
		// remove old views
		removeAllViews();
		// get inflater and create the new view
		LayoutInflater layoutInflater = 
			(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View mMiniPlayerView = layoutInflater.inflate(R.layout.audio_player_mini, this, false);
		
		addView(mMiniPlayerView);
		
		// Initialize the children
		mTitle = (TextView) findViewById(R.id.title);
		mArtist = (TextView) findViewById(R.id.artist);
		mPlayPause = (ImageButton) findViewById(R.id.play_pause);
		mPlayPause.setOnClickListener(onPlayPauseClickListener);
		
		mCurrentState = STATE_MINI_PLAYER;
	}
	
	public void showPlayer() {
		// remove old views
		removeAllViews();
		// get inflater and create the new view
		LayoutInflater layoutInflater = 
			(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View mPlayerView = layoutInflater.inflate(R.layout.audio_player, this, false);
		
		// Initialize the children
		
		addView(mPlayerView);
		mCurrentState = STATE_PLAYER;
	}
	
	public void setAudioPlayerControl(AudioPlayerControl control) {
		mAudioPlayerControl = control;
	}
	
	public void update() {
		if (mAudioPlayerControl != null) {
			if (mCurrentState == STATE_MINI_PLAYER) {
				if (mAudioPlayerControl.hasMedia()) {
					this.setVisibility(LinearLayout.VISIBLE);
				} else {
					this.setVisibility(LinearLayout.INVISIBLE);
					return;
				}
			}
			mTitle.setText(mAudioPlayerControl.getTitle());
			mArtist.setText(mAudioPlayerControl.getArtist());
			if (mAudioPlayerControl.isPlaying()) {
				mPlayPause.setBackgroundResource(R.drawable.ic_pause);
			} else {
				mPlayPause.setBackgroundResource(R.drawable.ic_play);
			}
			if (mCurrentState == STATE_PLAYER) {
				
			}
		}
		
		
	}
	
	public interface AudioPlayerControl {
		String getTitle();
		boolean hasMedia();
		String getArtist();
		void play();
		void pause();
		boolean isPlaying();
	}
}
