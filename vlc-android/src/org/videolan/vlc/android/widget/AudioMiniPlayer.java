package org.videolan.vlc.android.widget;

import org.videolan.vlc.android.AudioPlayer.AudioPlayerControl;
import org.videolan.vlc.android.MainActivity;
import org.videolan.vlc.android.R;
import org.videolan.vlc.android.Util;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class AudioMiniPlayer extends LinearLayout {
	public static final String TAG = "VLC/AudioMiniPlayer";

	
	private AudioPlayerControl mAudioPlayerControl;
	
	private TextView mTitle;
	private TextView mArtist;
	private ImageButton mPlayPause;
	private ImageView mCover;
	private SeekBar mSeekbar;
	
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

	
	public AudioMiniPlayer(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public AudioMiniPlayer(Context context) {
		super(context);
		init();
	}
	
	private void init() {
		// get inflater and create the new view
		LayoutInflater layoutInflater = 
			(LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View mMiniPlayerView = layoutInflater.inflate(R.layout.audio_player_mini, this, false);
		
		addView(mMiniPlayerView);
		
		// Initialize the children
		mCover = (ImageView) findViewById(R.id.cover);
		mTitle = (TextView) findViewById(R.id.title);
		mArtist = (TextView) findViewById(R.id.artist);
		mPlayPause = (ImageButton) findViewById(R.id.play_pause);
		mPlayPause.setOnClickListener(onPlayPauseClickListener);
		mSeekbar = (SeekBar) findViewById(R.id.timeline);
		
		this.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {
				// Start audio player
				Util.toaster("Start Audio Player");
			}
			
		});
		
		this.setOnLongClickListener(new OnLongClickListener() {
			
			@Override
			public boolean onLongClick(View arg0) {
				showContextMenu();
				return true;
			}
		});
	}
	
	@Override
	protected void onCreateContextMenu(ContextMenu menu) {
		MenuInflater inflater = MainActivity.getInstance().getMenuInflater();
		inflater.inflate(R.menu.audio_player_mini, menu);
		MenuItem hmi = menu.findItem(R.id.hide_mini_player);
		MenuItem pp = menu.findItem(R.id.play_pause);
		if (mAudioPlayerControl.isPlaying()) {
			hmi.setVisible(false);
			pp.setTitle(R.string.pause);
		} else {
			pp.setTitle(R.string.play);
		}
		
		super.onCreateContextMenu(menu);
	}
	
	public void setAudioPlayerControl(AudioPlayerControl control) {
		mAudioPlayerControl = control;
	}
	
	public void update() {
		if (mAudioPlayerControl != null) {
			
			if (mAudioPlayerControl.hasMedia()) {
				this.setVisibility(LinearLayout.VISIBLE);
			} else {
				this.setVisibility(LinearLayout.GONE);
				return;
			}
			
			Bitmap cover = mAudioPlayerControl.getCover();
			if (cover != null) {
				mCover.setVisibility(ImageView.VISIBLE);
				mCover.setImageBitmap(cover);
			} else {
				mCover.setVisibility(ImageView.GONE);
			}
	
			mTitle.setText(mAudioPlayerControl.getTitle());
			mArtist.setText(mAudioPlayerControl.getArtist());
			if (mAudioPlayerControl.isPlaying()) {
				mPlayPause.setImageResource(R.drawable.ic_pause);
			} else {
				mPlayPause.setImageResource(R.drawable.ic_play);
			}
			int time = (int)mAudioPlayerControl.getTime();
			int length = (int)mAudioPlayerControl.getLength();
			// Update all view elements

			mSeekbar.setMax(length);
			mSeekbar.setProgress(time);
		}
		
		
	}
	
}
