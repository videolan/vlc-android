package vlc.android;

import android.app.Activity;

import android.os.Bundle;
import android.widget.Button;

import android.view.SurfaceView;
import android.view.View;


public class vlc extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final Button button = (Button)findViewById(R.id.button);
        final SurfaceView surfaceView = (SurfaceView)findViewById(R.id.surface_view);
        
        libvlc = new LibVLC();
        
        surfaceView.getHolder().addCallback(new SurfaceCallback(libvlc));

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	libvlc.Init();
            	libvlc.readMedia("/sdcard/test.mp4");
            }
        });
    }

    LibVLC libvlc;
}
