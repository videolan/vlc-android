package org.videolan.vlc.android;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.MediaController;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class VLC extends Activity {
    private static final String TAG = "VLC_Activity";

    private static VLC sInstance;
    private LibVLC mLibVlc;
    private SurfaceView mSurfaceViewVideo;
    private SurfaceHolder mSurfaceHolderVideo;
    
    private MediaController controller;

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

        mSurfaceViewVideo = (SurfaceView) findViewById(R.id.surface_view);
        mSurfaceHolderVideo = mSurfaceViewVideo.getHolder();
        mSurfaceHolderVideo.setFormat(PixelFormat.RGBX_8888);
        mSurfaceViewVideo.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.show(10000);
            }
        });

        mLibVlc = LibVLC.getInstance();
        
        controller = new MediaController(this);
        controller.setMediaPlayer(playerInterface);
        controller.setAnchorView(mSurfaceViewVideo);

        mSurfaceHolderVideo.addCallback(new SurfaceHolder.Callback() {

        	@Override
        	public void surfaceCreated(SurfaceHolder holder) {
        	}

        	@Override
        	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        		mLibVlc.attachSurface(holder.getSurface(), width, height);
        	}

        	@Override
        	public void surfaceDestroyed(SurfaceHolder holder) {
        		mLibVlc.detachSurface();
        	}

        });
        
        try {
            mLibVlc.init();
        } catch (LibVlcException lve) {
            Log.e(TAG, "Could not load underlying libvlc library: " + lve);
            mLibVlc = null;
            /// FIXME Abort cleanly, alert user
            System.exit(1);
        }
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
        case R.id.menuOpen:
            // FIXME showAboutBox
            Intent i = new Intent(this, SimpleFileBrowser.class);
            startActivityForResult(i, 0);
            return true;
        
        case R.id.menuAbout:
            // FIXME showAboutBox
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
    
    /** Activity result callback */
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        if (data == null)
            return;
        Bundle extras = data.getExtras();

        if (requestCode == 0 && resultCode == RESULT_OK) {
            controller.hide();
            String filePath = extras.getString("filePath");
            mLibVlc.readMedia(filePath);
        }
    }
    
    // Implement the MediaController.MediaPlayerControl interface
    private MediaController.MediaPlayerControl playerInterface = new MediaController.MediaPlayerControl()
    {
        public int getBufferPercentage() {
            return 0;
        }

        public int getCurrentPosition() {
            float pos = mLibVlc.getPosition();
            return (int)(pos * getDuration());
        }

        public int getDuration() {
            return (int)mLibVlc.getLength();
        }

        public boolean isPlaying() {
            return mLibVlc.isPlaying();
        }

        public void pause() {
            mLibVlc.pause();
        }

        public void seekTo(int pos) {
            mLibVlc.setPosition((float)pos / getDuration());
        }

        public void start() {
            if (mLibVlc.hasMediaPlayer())
                mLibVlc.play();
            else {
                Intent i = new Intent(VLC.this, SimpleFileBrowser.class);
                startActivityForResult(i, 0);
            }
        }

        public boolean canPause() {
            return true;
        }

        public boolean canSeekBackward() {
            return true;
        }

        public boolean canSeekForward() {
            return true;
        }
    };
}
