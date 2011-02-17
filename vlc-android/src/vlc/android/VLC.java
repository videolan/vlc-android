package vlc.android;

import android.app.Activity;
import android.content.Context;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.Button;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class VLC extends Activity {
    private static final String TAG = "VLC_Activity";

    private static VLC sInstance;
    private LibVLC mLibVlc;
    private Vout mVout;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;

        Log.v(TAG, "Starting VLC media player...");

        /* Define layout */
        setContentView(R.layout.main);

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
        Log.v (TAG, "VLC is exiting");
        if (mLibVlc != null) {
            mLibVlc.destroy();
        }

        super.onDestroy();
    }

    /** Handle menu key
     * Note: this is called only once
     * Implement onPrepareOptionsMenu() to recreate the menu every time it is
     * displayed.
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater ();
        inflater.inflate(R.menu.stop_menu, menu);
        return true;
    }

    /** Handle menu item selection */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Main menu entries
        case R.id.menuAbout:
            // FIXME showAboutBox
            return true;

        case R.id.menuQuit:
            // FIXME For debugging only. Remove before the release.
            Log.i(TAG, "Forcefully quitting VLC...");
            System.exit(0);
            return true; // Will not be called

        // Options menu
        case R.id.menuLandscapeMode:
            Log.d (TAG, "Toggled LandscapeMode");
            item.setChecked(!item.isChecked());
            mVout.setOrientation(item.isChecked() ?
                                 Vout.Orientation.HORIZONTAL :
                                 Vout.Orientation.VERTICAL);
            return true;

        default:
            // Handle submenus...
            return super.onOptionsItemSelected(item);
        }
    }

    /** Get the global Activity context for use elsewhere */
    public static Context getActivityContext() {
        // The Activity is a context itself
        return sInstance;
    }
}
