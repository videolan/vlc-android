package org.videolan.vlc.android;

import android.util.Log;
import android.view.Surface;

public class LibVLC {
    private static final String TAG = "LibVLC";

    private static LibVLC instance;
    
    /** libVLC instance C pointer */
    private int mLibVlcInstance      = 0; // Read-only, reserved for JNI
    private int mMediaPlayerInstance = 0; // Read-only, reserved for JNI

    private Aout mAout;

    public native void attachSurface(Surface surface, VLC gui, int width, int height);
    public native void detachSurface();

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
     */
    public static LibVLC getInstance()
    {
        if (instance == null) {
            /* First call */
            instance = new LibVLC();
        }
        return instance;
    }
    
    /**
     * Constructor
     * It is private because this class is a singleton.
     */
    private LibVLC() {
        mAout = new Aout();
    }

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
    public void init() throws LibVlcException
    {
        Log.v(TAG, "Initializing LibVLC");
        nativeInit();
    }

    /**
     * Destroy this libVLC instance
     * @note You must call it before exiting
     */
    public void destroy()
    {
        Log.v(TAG, "Destroying LibVLC instance");
        nativeDestroy();
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
     * Initialize the libvlc C library
     * @return a pointer to the libvlc instance
     */
    private native void nativeInit() throws LibVlcException;

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
     * Return true if there is currently a running media player.
     */
    public native boolean hasMediaPlayer();
    
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
    public native void stop();

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

    public native void setEventManager(EventManager eventManager);
}
