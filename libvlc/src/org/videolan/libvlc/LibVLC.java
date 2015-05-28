/*****************************************************************************
 * LibVLC.java
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.libvlc;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import org.videolan.libvlc.util.HWDecoderUtil;

public class LibVLC {
    private static final String TAG = "VLC/LibVLC";

    public static final int INPUT_NAV_ACTIVATE = 0;
    public static final int INPUT_NAV_UP = 1;
    public static final int INPUT_NAV_DOWN = 2;
    public static final int INPUT_NAV_LEFT = 3;
    public static final int INPUT_NAV_RIGHT = 4;

    public static final boolean HAS_WINDOW_VOUT = LibVlcUtil.isGingerbreadOrLater();

    /** libVLC instance C pointer */
    private long mLibVlcInstance = 0; // Read-only, reserved for JNI

    /** Native crash handler */
    private static OnNativeCrashListener sOnNativeCrashListener;

    /** Check in libVLC already initialized otherwise crash */
    private boolean mIsInitialized = false;
    public native void attachSurface(Surface surface, IVideoPlayer player);

    public native void detachSurface();

    public native void attachSubtitlesSurface(Surface surface);
    public native void detachSubtitlesSurface();

    /* Load library before object instantiation */
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR1)
                    System.loadLibrary("anw.10");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2)
                    System.loadLibrary("anw.13");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    System.loadLibrary("anw.14");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT_WATCH)
                    System.loadLibrary("anw.18");
                else
                    System.loadLibrary("anw.21");
            } catch (Throwable t) {
                Log.w(TAG, "Unable to load the anw library: " + t);
            }

            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1)
                    System.loadLibrary("iomx.10");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2)
                    System.loadLibrary("iomx.13");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    System.loadLibrary("iomx.14");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2)
                    System.loadLibrary("iomx.18");
                else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)
                    System.loadLibrary("iomx.19");
            } catch (Throwable t) {
                // No need to warn if it isn't found, when we intentionally don't build these except for debug
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                    Log.w(TAG, "Unable to load the iomx library: " + t);
            }
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
     * Constructor
     * It is private because this class is a singleton.
     */
    public LibVLC() {
    }

    /**
     * Destructor:
     * It is bad practice to rely on them, so please don't forget to call
     * destroy() before exiting.
     */
    @Override
    protected void finalize() {
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
     * Initialize the libVLC class.
     *
     * This function must be called before using any libVLC functions.
     *
     * @throws LibVlcException
     */
    public void init(Context context, String options[]) throws LibVlcException {
        Log.v(TAG, "Initializing LibVLC");
        if (!mIsInitialized) {
            if(!LibVlcUtil.hasCompatibleCPU(context)) {
                Log.e(TAG, LibVlcUtil.getErrorMsg());
                throw new LibVlcException();
            }

            nativeInit(options);
            setEventHandler(EventHandler.getInstance());
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
        detachEventHandler();
        mIsInitialized = false;
    }

    /**
     * Initialize the libvlc C library
     * @return a pointer to the libvlc instance
     */
    private native void nativeInit(String options[]) throws LibVlcException;

    /**
     * Close the libvlc C library
     * @note mLibVlcInstance should be 0 after a call to destroy()
     */
    private native void nativeDestroy();

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
    public native byte[] getThumbnail(String mrl, int i_width, int i_height);
    
    public native static void sendMouseEvent( int action, int button, int x, int y);

    /**
     * Quickly converts path to URIs, which are mandatory in libVLC.
     *
     * @param path
     *            The path to be converted.
     * @return A URI representation of path
     */
    public static String PathToURI(String path) {
        if(path == null) {
            throw new NullPointerException("Cannot convert null path!");
        }

        File f = new File(path);
        return f.toURI().toString();
    }

    public static native void nativeReadDirectory(String path, ArrayList<String> res);

    public native static boolean nativeIsPathDirectory(String path);

    private native void setEventHandler(EventHandler eventHandler);

    private native void detachEventHandler();

    public static interface OnNativeCrashListener {
        public void onNativeCrash();
    }

    public static void setOnNativeCrashListener(OnNativeCrashListener l) {
        sOnNativeCrashListener = l;
    }

    private static void onNativeCrash() {
        if (sOnNativeCrashListener != null)
            sOnNativeCrashListener.onNativeCrash();
    }

    public native int setWindowSize(int width, int height);
}
