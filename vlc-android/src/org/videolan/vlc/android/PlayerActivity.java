package org.videolan.vlc.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PlayerActivity extends Activity {

	public final static String TAG = "VLC/PlayerActivity";

	private SurfaceView mSurface;
	private SurfaceHolder mSurfaceHolder;
	private LibVLC mLibVLC;
	private Context mContext;
	
	/** Overlay */
	private FrameLayout mOverlay;
	private LinearLayout mDecor;
	private View mSpacer;
	private static final int OVERLAY_TIMEOUT = 4000;
	private static final int FADE_OUT = 1;
	private static final int SHOW_PROGRESS = 2;
	private boolean mDragging;
	private boolean mShowing;
	private SeekBar mSeekbar;
	private TextView mTime;
	private TextView mLength;
	private ImageButton mPause;

	// stop screen from dimming
	private WakeLock mWakeLock;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);
		mContext = MediaLibraryActivity.getInstance();
		
		// stop screen from dimming
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
		

		
		/** initialize Views an their Events */
		mDecor = (LinearLayout)findViewById(R.id.player_overlay_decor);
		mSpacer = (View)findViewById(R.id.player_overlay_spacer);
		mSpacer.setOnTouchListener(mTouchListener);
		
		LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);
		mOverlay = (FrameLayout)inflater.inflate(R.layout.player_overlay, null);
		
		mTime = (TextView) mOverlay.findViewById(R.id.player_overlay_time);
		mLength = (TextView) mOverlay.findViewById(R.id.player_overlay_length);
		
		mPause = (ImageButton) mOverlay.findViewById(R.id.player_overlay_play);
		mPause.setOnClickListener(mPauseListener);
		
		mSurface = (SurfaceView) findViewById(R.id.player_surface);
		mSurfaceHolder = mSurface.getHolder();	
		mSurfaceHolder.addCallback(mSurfaceCallback);

		mSeekbar = (SeekBar)mOverlay.findViewById(R.id.player_overlay_seekbar);	
		mSeekbar.setOnSeekBarChangeListener(mSeekListener);
		
        try {
			mLibVLC = LibVLC.getInstance();
		} catch (LibVlcException e) {
			e.printStackTrace();
		}		

	}
	
	
	@Override
	protected void onStart() {
		super.onStart();
		// load and start the selected movie
		load();
	}
	
	
	@Override
	protected void onPause() {
		stop();
		super.onPause();
	}

	
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		
		return super.onTrackballEvent(event);
	}
	
	/**
	 * 
	 */
	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case FADE_OUT:
					hideOverlay();
					break;
				case SHOW_PROGRESS:
					int pos = setOverlayProgress();
					if (!mDragging && mShowing && mLibVLC.isPlaying()) {
						msg = obtainMessage(SHOW_PROGRESS);
						sendMessageDelayed(msg, 1000 - (pos % 1000));
					}
					break;
			}
		}
	};

	/**
	 * 
	 */
    private OnTouchListener mTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!mShowing) {
                    showOverlay();
                } else {
                	hideOverlay();
                }
            }
            return false;
        }
    };
    
    
    /**
     * 
     */
    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
    	boolean wasPlaying;
    	
		public void onStartTrackingTouch(SeekBar seekBar) {
			mDragging = true;
			showOverlay(3600000);
			if (mLibVLC.isPlaying()) {
				wasPlaying = true;
				pause();
			}
		}
		
		public void onStopTrackingTouch(SeekBar seekBar) {
			mDragging = false;
			showOverlay();
			if (wasPlaying) {
				play();
			}
		}
		
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (fromUser) {
				mLibVLC.setTime(progress);
				setOverlayProgress();	
				mTime.setText(Util.millisToString(progress));
			}
			
		}
	};
	
	
	/**
	 * 
	 */
	private OnClickListener mPauseListener = new OnClickListener() {		
		public void onClick(View v) {
			doPausePlay();
			showOverlay();
		}
	};

	
	/**
	 * 
	 */
	private SurfaceHolder.Callback mSurfaceCallback = new Callback() {		
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			mLibVLC.attachSurface(holder.getSurface(), width, height);
		}

		public void surfaceCreated(SurfaceHolder holder) { }

		public void surfaceDestroyed(SurfaceHolder holder) {
			mLibVLC.detachSurface();
		}
	};

	
	/**
	 * 
	 */
	private void showOverlay() {
		showOverlay(OVERLAY_TIMEOUT);
	}
	
	
	/**
	 * 
	 */
	private void showOverlay(int timeout) {
		mHandler.sendEmptyMessage(SHOW_PROGRESS);
		if (!mShowing) {
			Log.i(TAG, "add View!");
			mShowing = true;
			mDecor.addView(mOverlay);
		}
		Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
	}
	
	
	/**
	 * 
	 */
	private void hideOverlay() {
		if (mShowing) {
			mHandler.removeMessages(SHOW_PROGRESS);
        	Log.i(TAG, "remove View!");
			mDecor.removeView(mOverlay);
			mShowing = false;
		}
	}

	
	/*
	private void updateOverlayPausePlay() {
		if (mLibVLC == null) {
			return;
		}
		
		Log.i(TAG, "update play/pause button. Playing: " + mLibVLC.isPlaying());
		if (mLibVLC.isPlaying()) {
			mPause.setBackgroundResource(android.R.drawable.ic_media_pause);
		} else {
			mPause.setBackgroundResource(android.R.drawable.ic_media_play);
		}
	}
	*/
	
	
	/**
	 * 
	 */
	private void doPausePlay() {
		// FIXME: the libVLC is to slow to use updateOverlayPausePlay()
		if (mLibVLC.isPlaying()) {
			pause();
			mPause.setBackgroundResource(android.R.drawable.ic_media_play);
		} else {
			play();
			mPause.setBackgroundResource(android.R.drawable.ic_media_pause);
		}
	}
	
	
	/**
	 * 
	 */
	private int setOverlayProgress() {
		if (mLibVLC == null) {
			return 0;
		}
		int time = (int)mLibVLC.getTime();
		int length = (int)mLibVLC.getLength();
		// Update all view elements

		mSeekbar.setMax(length);
		mSeekbar.setProgress(time);
		mTime.setText(Util.millisToString(time));
		mLength.setText(Util.millisToString(length));
		return time;
	}

	/**
	 * 
	 */
	private void play() {
		mLibVLC.play();
		mWakeLock.acquire();
	}
	
	
	/**
	 * 
	 */
	private void stop() {
		mLibVLC.stop();
		if(mWakeLock.isHeld())
			mWakeLock.release();
	}
	
	
	/**
	 * 
	 */
	private void pause() {
		mLibVLC.pause();
		mWakeLock.release();
	}
	
	
	/**
	 * 
	 */
	private void load() {
		mLibVLC.readMedia(getIntent().getExtras().getString("filePath"));
		mWakeLock.acquire();
	}
}