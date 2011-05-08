package vlc.android;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

public class PlayerActivity extends Activity {

	public final static String TAG = "VLC/PlayerActivity";
	private LibVLC mLibVLC;
	private Vout mVout;
	

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);
		
		final GLSurfaceView surfaceView = 
        	(GLSurfaceView) findViewById(R.id.surface_view);
		mVout = new Vout(this);

        // For debug purpose.
        /* surfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR
                | GLSurfaceView.DEBUG_LOG_GL_CALLS);*/
        surfaceView.setRenderer(mVout);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        try {
			mLibVLC = LibVLC.getInstance(surfaceView, mVout);
		} catch (LibVLCException e) {
			e.printStackTrace();
		}

		
	}


	@Override
	protected void onStart() {
		mLibVLC.readMedia(getIntent().getExtras().getString("filePath"));
		super.onStart();
	}


	@Override
	protected void onPause() {
		mLibVLC.stopMedia();
		super.onPause();
	}
	
	

}
