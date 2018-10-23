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

import android.content.Context;
import android.util.Log;

import org.videolan.libvlc.util.HWDecoderUtil;

import java.util.ArrayList;

@SuppressWarnings("unused, JniMissingFunction")
public class LibVLC extends VLCObject<LibVLC.Event> {
    private static final String TAG = "VLC/LibVLC";
    final Context mAppContext;

    public static class Event extends VLCEvent {
        protected Event(int type) {
            super(type);
        }
    }

    /**
     * Create a LibVLC withs options
     *
     * @param options
     */
    public LibVLC(Context context, ArrayList<String> options) {
        mAppContext = context.getApplicationContext();
        loadLibraries();

        if (options == null)
            options = new ArrayList<String>();
        boolean setAout = true, setChroma = true;
        // check if aout/vout options are already set
        for (String option : options) {
            if (option.startsWith("--aout="))
                setAout = false;
            if (option.startsWith("--android-display-chroma"))
                setChroma = false;
            if (!setAout && !setChroma)
                break;
        }

        // set aout/vout options if they are not set
        if (setAout || setChroma) {
            if (setAout) {
                final HWDecoderUtil.AudioOutput hwAout = HWDecoderUtil.getAudioOutputFromDevice();
                if (hwAout == HWDecoderUtil.AudioOutput.OPENSLES)
                    options.add("--aout=opensles");
                else
                    options.add("--aout=android_audiotrack");
            }
            if (setChroma) {
                options.add("--android-display-chroma");
                options.add("RV16");
            }
        }
        nativeNew(options.toArray(new String[options.size()]), context.getDir("vlc", Context.MODE_PRIVATE).getAbsolutePath());
    }

    /**
     * Create a LibVLC
     */
    public LibVLC(Context context) {
        this(context, null);
    }

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

    @Override
    protected Event onEventNative(int eventType, long arg1, long arg2, float argf1) {
        return null;
    }

    @Override
    protected void onReleaseNative() {
        nativeRelease();
    }

    /**
     * Sets the application name. LibVLC passes this as the user agent string
     * when a protocol requires it.
     *
     * @param name human-readable application name, e.g. "FooBar player 1.2.3"
     * @param http HTTP User Agent, e.g. "FooBar/1.2.3 Python/2.6.0"
     */
    public void setUserAgent(String name, String http){
        nativeSetUserAgent(name, http);
    }

    /* JNI */
    private native void nativeNew(String[] options, String homePath);
    private native void nativeRelease();
    private native void nativeSetUserAgent(String name, String http);

    private static boolean sLoaded = false;

    public static synchronized void loadLibraries() {
        if (sLoaded)
            return;
        sLoaded = true;

        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("vlc");
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
}
