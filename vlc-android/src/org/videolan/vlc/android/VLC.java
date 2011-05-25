package org.videolan.vlc.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.MediaController;

public class VLC extends Activity {
    private static final String TAG = "VLC_Activity";

    private static VLC sInstance;
    private LibVLC mLibVlc;
    private SurfaceView mSurfaceViewVideo;
    private SurfaceHolder mSurfaceHolderVideo;
    
    private MediaController controller;

	Handler sizeHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			//video size
			int vw = msg.arg1;
			int vh = msg.arg2;
			float ar = (float)vw / (float)vh;

			//screen size
			int dw = getWindowManager().getDefaultDisplay().getWidth();
			int dh = getWindowManager().getDefaultDisplay().getHeight();

			//fix ar
			if (vw > vh)
				dh = (int)(dw / ar);
			else
				dw = (int)(dh * ar);

			Log.i(TAG, "video size changed to "+vw+"x"+vh+", displaying at "+dw+"x"+dh);
			mSurfaceHolderVideo.setFixedSize(vw, vh);
			LayoutParams lp = mSurfaceViewVideo.getLayoutParams();
			lp.width = dw;
			lp.height = dh;
			mSurfaceViewVideo.setLayoutParams(lp);
			mSurfaceViewVideo.invalidate();
		}
	};

    public void setSurfaceSize(int width, int height) {
		Message msg = Message.obtain();
		msg.arg1 = width;
		msg.arg2 = height;
		sizeHandler.sendMessage(msg);
    }

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
        mSurfaceHolderVideo.setKeepScreenOn(true);
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
        		mLibVlc.attachSurface(holder.getSurface(), sInstance, width, height);
        	}

        	@Override
        	public void surfaceDestroyed(SurfaceHolder holder) {
        		mLibVlc.detachSurface();
        	}

        });
        
        EventManager em = new EventManager(eventHandler);
        mLibVlc.setEventManager(em);

        try {
            mLibVlc.init();
        } catch (LibVlcException lve) {
            Log.e(TAG, "Could not load underlying libvlc library: " + lve);
            mLibVlc = null;
            /// FIXME Abort cleanly, alert user
            System.exit(1);
        }
    }

    /** Handle libvlc asynchronous events */
    private Handler eventHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.getData().getInt("event")) {
                case EventManager.MediaPlayerPlaying:
                    Log.e(TAG, "MediaPlayerPlaying");
                    break;
                case EventManager.MediaPlayerPaused:
                    Log.e(TAG, "MediaPlayerPaused");
                    break;
                case EventManager.MediaPlayerStopped:
                    Log.e(TAG, "MediaPlayerStopped");
                    break;
                case EventManager.MediaPlayerEndReached:
                    Log.e(TAG, "MediaPlayerEndReached");
                    break;
                default:
                    Log.e(TAG, "Event not handled");
                    break;
            }
        }
    };

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
        
        if (mLibVlc.hasMediaPlayer())
            mLibVlc.play();
    }
    
    /** Called when the activity is no more in the foreground. */
    public void onPause()
    {
        if (mLibVlc.hasMediaPlayer())
            mLibVlc.pause();
        super.onPause();
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
