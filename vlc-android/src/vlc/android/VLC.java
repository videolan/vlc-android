package vlc.android;

import android.app.Activity;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Button;

import android.view.View;


public class VLC extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Button button = (Button)findViewById(R.id.button);
        final GLSurfaceView surfaceView = (GLSurfaceView)findViewById(R.id.surface_view);
        
        vout = new Vout(this);
        
        // For debug purpose.
        /* surfaceView.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR
        		| GLSurfaceView.DEBUG_LOG_GL_CALLS);*/
        surfaceView.setRenderer(vout);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        
        libvlc = new LibVLC(surfaceView, vout);

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	libvlc.Init();
            	libvlc.readMedia("/sdcard/test.mp4");
            }
        });
    }

    LibVLC libvlc;
    Vout vout;
}
