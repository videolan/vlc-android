/*****************************************************************************
 * LibVLC.java
 *****************************************************************************
 * Copyright © 2010-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc;

import org.videolan.vlc.LibVlcException;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import android.util.Log;
import android.view.Surface;
import android.preference.PreferenceManager;
import android.content.Context;
import android.os.Build;

public class LibVLC {
    private static final String TAG = "VLC/LibVLC";

    private static LibVLC sInstance;
    private static boolean sUseIomx = false;

    /** libVLC instance C pointer */
    private int mLibVlcInstance = 0; // Read-only, reserved for JNI
    private int mMediaPlayerInstance = 0; // Read-only, reserved for JNI

    private Aout mAout;

    /** Keep screen bright */
    //private WakeLock mWakeLock;

    /** Check in libVLC already initialized otherwise crash */
    private boolean mIsInitialized = false;

    public native void attachSurface(Surface surface, VideoPlayerActivity player, int width, int height);

    public native void detachSurface();

    /* Load library before object instantiation */
    static {
        try {
            if (Build.VERSION.SDK_INT <= 10)
                System.loadLibrary("iomx-gingerbread");
            else /* No honeycomb build for now */
                System.loadLibrary("iomx-ics");
        } catch (Throwable t) {
            Log.w(TAG, "Unable to load the iomx library: " + t);
        }
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
     * Singleton constructor
     * Without surface and vout to create the thumbnail and get information
     * e.g. on the MediaLibraryAcitvity
     * @return
     * @throws LibVlcException
     */
    public static LibVLC getInstance() throws LibVlcException {
        if (sInstance == null) {
            /* First call */
            sInstance = new LibVLC();
            sInstance.init();
        }

        return sInstance;
    }

    public static LibVLC getExistingInstance() {
        return sInstance;
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
    public void finalize() {
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
     *
     */
    public boolean useIOMX() {
        return sUseIomx;
    }

    public static synchronized void useIOMX(boolean enable) {
        sUseIomx = enable;
    }

    public static synchronized void useIOMX(Context context) {
        sUseIomx = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("enable_iomx", false);
    }

    /**
     * Initialize the libVLC class
     */
    private void init() throws LibVlcException {
        Log.v(TAG, "Initializing LibVLC");
        if (!mIsInitialized) {
            nativeInit();
            setEventManager(EventManager.getIntance());
            mIsInitialized = true;
        }
    }

    /**
     * Destroy this libVLC instance
     * @note You must call it before exiting
     */
    public void destroy() {
        Log.v(TAG, "Destroying LibVLC instance");
        nativeDestroy();
        detachEventManager();
        mIsInitialized = false;
    }

    /**
     * Open the Java audio output.
     * This function is called by the native code
     */
    public void initAout(int sampleRateInHz, int channels, int samples) {
        Log.d(TAG, "Opening the java audio output");
        mAout.init(sampleRateInHz, channels, samples);
    }

    /**
     * Play an audio buffer taken from the native code
     * This function is called by the native code
     */
    public void playAudio(byte[] audioData, int bufferSize) {
        mAout.playBuffer(audioData, bufferSize);
    }

    /**
     * Close the Java audio output
     * This function is called by the native code
     */
    public void closeAout() {
        Log.d(TAG, "Closing the java audio output");
        mAout.release();
    }

    /**
     * Read a media.
     */
    public void readMedia(String mrl) {
        Log.v(TAG, "Reading " + mrl);
        readMedia(mLibVlcInstance, mrl);
    }

    public String[] readMediaMeta(String mrl) {
        return readMediaMeta(mLibVlcInstance, mrl);
    }

    /**
     * Get a media thumbnail.
     */
    public byte[] getThumbnail(String filePath, int i_width, int i_height) {
        return getThumbnail(mLibVlcInstance, filePath, i_width, i_height);
    }

    /**
     * Return true if there is a video track in the file
     */
    public boolean hasVideoTrack(String filePath) {
        return hasVideoTrack(mLibVlcInstance, filePath);
    }

    /**
     * Return true if there is a video track in the file
     */
    public long getLengthFromFile(String filePath) {
        return getLengthFromFile(mLibVlcInstance, filePath);
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

    /**
     * Return true if there is a video track in the file
     */
    private native boolean hasVideoTrack(int instance, String filePath);

    private native String[] readMediaMeta(int instance, String mrl);

    /**
     * Return true if there is a video track in the file
     */
    private native long getLengthFromFile(int instance, String filePath);

    private native void setEventManager(EventManager eventManager);

    private native void detachEventManager();
}
