package vlc.android;

import android.app.Activity;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Button;

import android.util.Log;
import android.view.View;

public class VLC extends Activity {
    private static final String TAG = "VLC_Activity";

    private LibVLC mLibVlc;
    private Vout mVout;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.v(TAG, "Starting VLC");

        final Button button = (Button) findViewById(R.id.button);
        final GLSurfaceView surfaceView = (GLSurfaceView) findViewById(R.id.surface_view);

        mVout = new Vout(this);

        // For debug purpose.
        /* surfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR
                | GLSurfaceView.DEBUG_LOG_GL_CALLS);*/
        surfaceView.setRenderer(mVout);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mLibVlc = new LibVLC(surfaceView, mVout);
        
        try {
            mLibVlc.init();
        } catch (LibVlcException lve) {
            Log.e(TAG, "Could not load underlying libvlc library: " + lve);
            mLibVlc = null;
            /// FIXME Abort cleanly, alert user
            System.exit(1);
        }

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mLibVlc.readMedia("/sdcard/test.mp4");
            }
        });
    }

    /** Called when the activity is finally destroyed */
    @Override
    public void onDestroy() {
        Log.v(TAG, "VLC is exiting");
        if (mLibVlc != null) {
            mLibVlc.destroy();
        }

        super.onDestroy();
    }
}
