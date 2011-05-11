package vlc.android;

import java.util.concurrent.BrokenBarrierException;

import android.opengl.GLSurfaceView;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Surface;

public class LibVLC {
    private static final String TAG = "VLC/LibVLC";

    private static LibVLC mInstance;
    
    /** libVLC instance C pointer */
    private int mLibVlcInstance      = 0; // Read-only, reserved for JNI
    private int mMediaPlayerInstance = 0; // Read-only, reserved for JNI

    private GLSurfaceView mSurfaceView;
    private Vout mVout;
    private Aout mAout;
    
    /** Keep screen bright */
    private WakeLock mWakeLock;
    
    /** Check in libVLC already initialized otherwise crash */
    private boolean mIsInitialized = false;

    /* Load library before object instantiation */
    static {
        try {
            System.loadLibrary("vlcjni");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Can't load vlcjni library: " + ule);
            /// FIXME Alert user
            System.exit(1);
        } catch (SecurityException se) {
            Log.e(TAG, "Encountered a security issue when loading vlcjni library: " + se);
            /// FIXME Alert user
            System.exit(1);
        }
    }

    /**
     * Singleton constructors
     * @throws LibVLCException 
     */
    public static LibVLC getInstance(GLSurfaceView s, Vout v) throws LibVLCException
    {
    	/* First call */
    	mInstance = getInstance();     
        mInstance.setGLSerfaceView(s);
        mInstance.setVout(v);      
        return mInstance;
    }
    
    /**
     * Singleton constructor
     * Without surface and vout to create the thumbnail and get information
     * e.g. on the MediaLibraryAcitvity
     * @return
     * @throws LibVLCException
     */
    public static LibVLC getInstance() throws LibVLCException
    {
        if (mInstance == null) {
        	/* First call without surface and Vout */
        	mInstance = new LibVLC();
        	mInstance.init();
        }
        
    	return mInstance;
    }
    
    /**
     * Set Surface
     * @param s
     */
    public void setGLSerfaceView(GLSurfaceView s) {
    	mSurfaceView = s;
    }
    
    /**
     * Set Vout
     * @param v
     */
    public void setVout(Vout v) {
    	mVout = v;
    }
    
    /**
     * Constructor
     * It is private because this class is a singleton.
     */
    private LibVLC() {
    	mAout = new Aout();
    };
    

    /**
     * Destructor:
     * It is bad practice to rely on them, so please don't forget to call
     * destroy() before exiting.
     */
    public void finalize()
    {
        if (mLibVlcInstance != 0) {
            Log.d(TAG, "LibVLC is was destroyed yet before finalize()");
            destroy();
        }
    }

    /**
     * Give to LibVLC the surface to draw the video.
     * @param f the surface to draw
     */
    public native void setSurface(Surface f);

    /**
     * Initialize the libVLC class
     */
    private void init() throws LibVLCException
    {
        Log.v(TAG, "Initializing LibVLC");
        if (!mIsInitialized) {
        	nativeInit();
        	mIsInitialized = true;
        }
    }

    /**
     * Destroy this libVLC instance
     * @note You must call it before exiting
     */
    public void destroy()
    {
        Log.v(TAG, "Destroying LibVLC instance");
        nativeDestroy();
        mIsInitialized = false;
    }

    /**
     * Transmit to the renderer the size of the video.
     * This function is called by the native code.
     * @param frameWidth
     * @param frameHeight
     */
    public void setVoutSize(int frameWidth, int frameHeight)
    {
        mVout.frameWidth = frameWidth;
        mVout.frameHeight = frameHeight;
        mVout.mustInit = true;
    }

    /**
     * Transmit the image given by VLC to the renderer.
     * This function is called by the native code.
     * @param image the image data.
     */
    public void displayCallback(byte[] image)
    {
        mVout.image = image;
        mVout.hasReceivedFrame = true;
        mSurfaceView.requestRender();
        try {
            mVout.mBarrier.await();
        } catch (InterruptedException e) {
        } catch (BrokenBarrierException e) {
        }
    }

    /**
     * Open the Java audio output.
     * This function is called by the native code
     */
    public void initAout(int sampleRateInHz, int channels, int samples)
    {
        Log.d(TAG, "Opening the java audio output");
        mAout.init(sampleRateInHz, channels, samples);
    }

    /**
     * Play an audio buffer taken from the native code
     * This function is called by the native code
     */
    public void playAudio(byte[] audioData, int bufferSize, int nbSamples)
    {
        mAout.playBuffer(audioData, bufferSize, nbSamples);
    }

    /**
     * Close the Java audio output
     * This function is called by the native code
     */
    public void closeAout()
    {
        Log.d(TAG, "Closing the java audio output");
        mAout.release();
    }

    /**
     * Read a media.
     */
    public void readMedia(String mrl)
    {
        Log.v(TAG, "Reading " + mrl);
        readMedia(mLibVlcInstance, mrl);
    }
    
    /**
     * Get a media thumbnail.
     */
    public byte[] getThumbnail(String filePath, int i_width, int i_height)
    {
        return getThumbnail(mLibVlcInstance, filePath, i_width, i_height);
    }
    
    /**
     * Stop the video AND audio
     */
    public void stopMedia() {
    	stop();
    	mAout.release();	
    }
    

    /**
     * Initialize the libvlc C library
     * @return a pointer to the libvlc instance
     */
    private native void nativeInit() throws LibVLCException;

    /**
     * Close the libvlc C library
     * @note mLibVlcInstance should be 0 after a call to destroy()
     */
    private native void nativeDestroy();

    /**
     * Read a media
     * @param instance: the instance of libVLC
     * @param mrl: the media mrl
     */
    private native void readMedia(int instance, String mrl);

    /**
     * Returns true if any media is playing
     */
    public native boolean isPlaying();

    /**
     * Returns true if any media is seekable
     */
    public native boolean isSeekable();

    /**
     * Plays any loaded media
     */
    public native void play();

    /**
     * Pauses any playing media
     */
    public native void pause();

    /**
     * Stops any playing media
     */
    private native void stop();

    /**
     * Gets volume as integer
     */
    public native int getVolume();

    /**
     * Gets volume as integer
     * @param volume: Volume level passed as integer
     */
    public native int setVolume(int volume);

    /**
     * Gets the current movie time (in ms).
     * @return the movie time (in ms), or -1 if there is no media.
     */
    public native long getTime();

    /**
     * Sets the movie time (in ms), if any media is being played.
     * @param time: Time in ms.
     * @return the movie time (in ms), or -1 if there is no media.
     */
    public native long setTime(long time);

    /**
     * Gets the movie position.
     * @return the movie position, or -1 for any error.
     */
    public native float getPosition();

    /**
     * Sets the movie position.
     * @param pos: movie position.
     */
    public native void setPosition(float pos);

    /**
     * Gets current movie's length in ms.
     * @return the movie length (in ms), or -1 if there is no media.
     */
    public native long getLength();

    /**
     * Get the libVLC version
     * @return the libVLC version string
     */
    public native String version();

    /**
     * Get the libVLC compiler
     * @return the libVLC compiler string
     */
    public native String compiler();

    /**
     * Get the libVLC changeset
     * @return the libVLC changeset string
     */
    public native String changeset();
    
    /**
     * Get a media thumbnail.
     * @return a bytearray with the RGBA thumbnail data inside.
     */
    private native byte[] getThumbnail(int instance, String filePath, int i_width, int i_height);
}
