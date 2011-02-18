package vlc.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

        /* Force orientation... */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(VlcPreferences.ORIENTATION_MODE, true)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        /* ... and then load the layout */
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

    /** Resume the application */
    public void onResume() {
        super.onResume();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // FIXME Make sure we have the requested orientation
        boolean preferredMode  = !prefs.getBoolean(VlcPreferences.ORIENTATION_MODE, true);
        boolean currentMode    = this.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (currentMode != preferredMode) {
            // This will probably recreate the activity
            if (preferredMode) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }

        // FIXME
        // if (prefs.getBoolean("hud lock", false))
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
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stop_menu, menu);
        return true;
    }

    /** Handle menu item selection */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Main menu entries
        case R.id.menuAbout:
            // FIXME showAboutBox
            Util.toaster("About VLC media player:\nNot implemented yet");
            return true;

        case R.id.menuQuit:
            finish();
            return true;

        // Options menu
        case R.id.menuOptions:
            startActivity(new Intent(this, VlcPreferences.class));
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
