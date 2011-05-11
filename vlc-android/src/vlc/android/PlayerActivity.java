package vlc.android;

import java.text.DecimalFormat;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PlayerActivity extends Activity {

	public final static String TAG = "VLC/PlayerActivity";
	private LibVLC mLibVLC;
	private Vout mVout;
	private View mOverlay;
	private SeekBar mSeekbar;
	private TextView mTime;
	private TextView mLength;
	private GLSurfaceView mSurface;
	
	
	private Handler mHandler = new Handler();
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);
		
		mOverlay = (View)findViewById(R.id.player_overlay_play);
		mTime = (TextView)findViewById(R.id.player_overlay_time);
		mLength = (TextView)findViewById(R.id.player_overlay_length);
		mSeekbar = (SeekBar)findViewById(R.id.player_overlay_seekbar);
		mSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			private boolean mWasPlaying = false;
			
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (mWasPlaying) {
					mLibVLC.play();
					mWasPlaying = false;
				}
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
				if (mLibVLC.isPlaying()) {
					mLibVLC.pause();
					mWasPlaying = true;
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
		mOverlay.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v) {
				if (mLibVLC.isPlaying()) {
					mLibVLC.pause();
				} else {
					mLibVLC.play();
				}
				
			}
		});
		mSurface = (GLSurfaceView) findViewById(R.id.player_surface);
		mVout = new Vout(this);

        // For debug purpose.
        /* surfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR
                | GLSurfaceView.DEBUG_LOG_GL_CALLS);*/
		mSurface.setRenderer(mVout);
		mSurface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        try {
			mLibVLC = LibVLC.getInstance(mSurface, mVout);
		} catch (LibVLCException e) {
			e.printStackTrace();
		}		
	}
	



	@Override
	protected void onStart() {
		mLibVLC.readMedia(getIntent().getExtras().getString("filePath"));
		
		new Thread(new Runnable() {
			public void run() {
				while (true) {
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
			mSeekbar.setMax((int) mLibVLC.getLength());
			mLength.setText(millisToString(mLibVLC.getLength()));
			mSeekbar.setProgress((int) mLibVLC.getTime());
			mTime.setText(millisToString(mLibVLC.getTime()));
		}
	};
	
	private String millisToString(long millis) {
		millis /= 1000;
		int sec = (int) (millis % 60);
		millis /= 60;
		int min = (int) (millis % 60);
		millis /= 60;
		int hours = (int) millis;
		
		String time;
		DecimalFormat format = new DecimalFormat("00"); 
		if (millis > 0) {
			time = hours + ":" + format.format(min) + ":" + format.format(sec);
		} else {
			time = min + ":" + format.format(sec);
		}
		return time;
	}
	
	



	@Override
	protected void onPause() {
		mLibVLC.stopMedia();
		super.onPause();
	}
	
	

}
