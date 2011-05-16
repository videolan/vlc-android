package vlc.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PlayerActivity extends Activity {

	public final static String TAG = "VLC/PlayerActivity";
	private LibVLC mLibVLC;
	private View mOverlay;
	private SeekBar mSeekbar;
	private TextView mTime;
	private TextView mLength;
	private SurfaceView mSurface;
	private SurfaceHolder mSurfaceHolder;
	
	// stop screen from dimming
	private WakeLock mWakeLock;
	
	// handle view update from other Thread.
	private Handler mHandler = new Handler();
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);
		
		// stop screen from dimming
		PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, TAG);
		
		/** initialize Views an their Events */
		mTime = (TextView)findViewById(R.id.player_overlay_time);
		mLength = (TextView)findViewById(R.id.player_overlay_length);

		mSurface = (SurfaceView) findViewById(R.id.player_surface);
		mSurfaceHolder = mSurface.getHolder();	
		mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
			
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				mLibVLC.attachSurface(holder.getSurface(), width, height);
				
			}

			public void surfaceCreated(SurfaceHolder holder) {
				// TODO Auto-generated method stub
				
			}

			public void surfaceDestroyed(SurfaceHolder holder) {
				mLibVLC.detachSurface();
			}
		});

		mSeekbar = (SeekBar)findViewById(R.id.player_overlay_seekbar);	
		mSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			private boolean wasPlaying = false;
			
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (wasPlaying) {
					play();
					wasPlaying = false;
				}
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
				if (mLibVLC.isPlaying()) {
					pause();
					wasPlaying = true;
				} 
			}
			
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser) {
					mLibVLC.setTime(progress);
					mHandler.post(updateOverlay);
				}
				
			}
		});

		mOverlay = (View)findViewById(R.id.player_overlay_play);	
		mOverlay.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v) {
				if (mLibVLC.isPlaying()) {
					pause();
				} else {
					play();
				}
				
			}
		});
		

        try {
			mLibVLC = LibVLC.getInstance();
		} catch (LibVLCException e) {
			e.printStackTrace();
		}		
		
		
		
	}
	



	@Override
	protected void onStart() {
		load();
		
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					mSeekbar.setMax((int) mLibVLC.getLength());
					mSeekbar.setProgress((int) mLibVLC.getTime());
					mHandler.post(updateOverlay);
					try {
						Thread.sleep(400);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}
			}
		}).start();
		
		super.onStart();
	}
	
	private Runnable updateOverlay = new Runnable() {
		
		public void run() {
			mLength.setText(Util.millisToString(mLibVLC.getLength()));
			mTime.setText(Util.millisToString(mLibVLC.getTime()));
		}
	};
	

	


	@Override
	protected void onPause() {
		stop();
		super.onPause();
	}
	
	private void play() {
		mLibVLC.play();
		mWakeLock.acquire();
	}
	
	private void stop() {
		mLibVLC.stop();
		mLibVLC.closeAout();
		mWakeLock.release();
	}
	
	private void pause() {
		mLibVLC.pause();
		mWakeLock.release();
	}
	
	private void load() {
		mLibVLC.readMedia(getIntent().getExtras().getString("filePath"));
		mWakeLock.acquire();
	}
	
	

}
