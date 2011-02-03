package vlc.android;

import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;

public class LibVLC {
    private static final String TAG = "LibVLC";

    /** libVLC instance C pointer */
    private int mLibVlcInstance      = 0; // Read-only, reserved for JNI
    private int mMediaPlayerInstance = 0; // Read-only, reserved for JNI

    private GLSurfaceView mSurfaceView;
    private Vout mVout;

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
     * Constructor
     */
    public LibVLC(GLSurfaceView s, Vout v)
    {
        mSurfaceView = s;
        mVout = v;
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
}
