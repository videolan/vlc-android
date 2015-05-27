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
import java.util.ArrayList;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import org.videolan.libvlc.util.HWDecoderUtil;

public class LibVLC {
    private static final String TAG = "VLC/LibVLC";
    public static final int AOUT_AUDIOTRACK = 0;
    public static final int AOUT_OPENSLES = 1;

    public static final int VOUT_ANDROID_SURFACE = 0;
    public static final int VOUT_OPEGLES2 = 1;
    public static final int VOUT_ANDROID_WINDOW = 2;

    public static final int HW_ACCELERATION_AUTOMATIC = -1;
    public static final int HW_ACCELERATION_DISABLED = 0;
    public static final int HW_ACCELERATION_DECODING = 1;
    public static final int HW_ACCELERATION_FULL = 2;

    public static final int DEV_HW_DECODER_AUTOMATIC = -1;
    public static final int DEV_HW_DECODER_OMX = 0;
    public static final int DEV_HW_DECODER_OMX_DR = 1;
    public static final int DEV_HW_DECODER_MEDIACODEC = 2;
    public static final int DEV_HW_DECODER_MEDIACODEC_DR = 3;

    public static final int INPUT_NAV_ACTIVATE = 0;
    public static final int INPUT_NAV_UP = 1;
    public static final int INPUT_NAV_DOWN = 2;
    public static final int INPUT_NAV_LEFT = 3;
    public static final int INPUT_NAV_RIGHT = 4;

    public final static int MEDIA_NO_VIDEO   = 0x01;
    public final static int MEDIA_NO_HWACCEL = 0x02;
    public final static int MEDIA_PAUSED = 0x4;

    private static final String DEFAULT_CODEC_LIST = "mediacodec_ndk,mediacodec_jni,iomx,all";
    private static final boolean HAS_WINDOW_VOUT = LibVlcUtil.isGingerbreadOrLater();

    /** libVLC instance C pointer */
    private long mLibVlcInstance = 0; // Read-only, reserved for JNI

    private MediaPlayer mMediaPlayer;

    /** Keep screen bright */
    //private WakeLock mWakeLock;

    /** Settings */
    private int hardwareAcceleration = HW_ACCELERATION_AUTOMATIC;
    private int devHardwareDecoder = DEV_HW_DECODER_AUTOMATIC;
    private String codecList = DEFAULT_CODEC_LIST;
    private String devCodecList = null;
    private String subtitlesEncoding = "";
    private int aout = AOUT_AUDIOTRACK;
    private int vout = VOUT_ANDROID_SURFACE;
    private boolean timeStretching = false;
    private int deblocking = -1;
    private String chroma = "";
    private boolean verboseMode = true;
    private boolean frameSkip = false;
    private int networkCaching = 0;
    private boolean httpReconnect = false;
    private boolean hdmiAudioEnabled = false;

    /** Path of application-specific cache */
    private String mCachePath = "";

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
     * those get/is* are called from native code to get settings values.
     */

    public int getHardwareAcceleration() {
        return this.hardwareAcceleration;
    }

    public void setHardwareAcceleration(int hardwareAcceleration) {

        if (hardwareAcceleration == HW_ACCELERATION_DISABLED) {
            Log.d(TAG, "HWDec disabled: by user");
            this.hardwareAcceleration = HW_ACCELERATION_DISABLED;
            this.codecList = "all";
        } else {
            // Automatic or forced
            HWDecoderUtil.Decoder decoder = HWDecoderUtil.getDecoderFromDevice();

            if (decoder == HWDecoderUtil.Decoder.NONE) {
                // NONE
                this.hardwareAcceleration = HW_ACCELERATION_DISABLED;
                this.codecList = "all";
                Log.d(TAG, "HWDec disabled: device not working with mediacodec,iomx");
            } else if (decoder == HWDecoderUtil.Decoder.UNKNOWN) {
                // UNKNOWN
                if (hardwareAcceleration < 0) {
                    this.hardwareAcceleration = HW_ACCELERATION_DISABLED;
                    this.codecList = "all";
                    Log.d(TAG, "HWDec disabled: automatic and (unknown device or android version < 4.3)");
                } else {
                    this.hardwareAcceleration = hardwareAcceleration;
                    this.codecList = DEFAULT_CODEC_LIST;
                    Log.d(TAG, "HWDec enabled: forced by user and unknown device");
                }
            } else {
                // OMX, MEDIACODEC or ALL
                this.hardwareAcceleration = hardwareAcceleration < 0 ?
                        HW_ACCELERATION_FULL : hardwareAcceleration;
                if (decoder == HWDecoderUtil.Decoder.ALL)
                    this.codecList = DEFAULT_CODEC_LIST;
                else {
                    final StringBuilder sb = new StringBuilder();
                    if (decoder == HWDecoderUtil.Decoder.MEDIACODEC)
                        sb.append("mediacodec_ndk,mediacodec_jni,");
                    else if (decoder == HWDecoderUtil.Decoder.OMX)
                        sb.append("iomx,");
                    sb.append("all");
                    this.codecList = sb.toString();
                }
                Log.d(TAG, "HWDec enabled: device working with: " + this.codecList);
            }
        }
    }

    public int getDevHardwareDecoder() {
        return this.devHardwareDecoder;
    }

    public void setDevHardwareDecoder(int devHardwareDecoder) {
        if (devHardwareDecoder != DEV_HW_DECODER_AUTOMATIC) {
            this.devHardwareDecoder = devHardwareDecoder;
            if (this.devHardwareDecoder == DEV_HW_DECODER_OMX ||
                    this.devHardwareDecoder == DEV_HW_DECODER_OMX_DR)
                this.devCodecList = "iomx";
            else
                this.devCodecList = "mediacodec_ndk,mediacodec_jni";

            Log.d(TAG, "HWDec forced: " + this.devCodecList +
                (isDirectRendering() ? "-dr" : ""));
            this.devCodecList += ",none";
        } else {
            this.devHardwareDecoder = DEV_HW_DECODER_AUTOMATIC;
            this.devCodecList = null;
        }
    }

    public boolean isDirectRendering() {
        if (!HAS_WINDOW_VOUT)
            return false;
        if (devHardwareDecoder != DEV_HW_DECODER_AUTOMATIC) {
            return (this.devHardwareDecoder == DEV_HW_DECODER_OMX_DR ||
                    this.devHardwareDecoder == DEV_HW_DECODER_MEDIACODEC_DR);
        } else {
            return this.hardwareAcceleration == HW_ACCELERATION_FULL;
        }
    }

    public String[] getMediaOptions(boolean noHardwareAcceleration, boolean noVideo) {
        final int flag = (noHardwareAcceleration ? MEDIA_NO_HWACCEL : 0) |
                         (noVideo ? MEDIA_NO_VIDEO : 0);
        return getMediaOptions(flag);
    }

    public String[] getMediaOptions(int flags) {
        boolean noHardwareAcceleration = (flags & MEDIA_NO_HWACCEL) != 0;
        boolean noVideo = (flags & MEDIA_NO_VIDEO) != 0;
        final boolean paused = (flags & MEDIA_PAUSED) != 0;

        if (this.devHardwareDecoder != DEV_HW_DECODER_AUTOMATIC)
            noHardwareAcceleration = noVideo = false;
        else if (!noHardwareAcceleration)
            noHardwareAcceleration = getHardwareAcceleration() == HW_ACCELERATION_DISABLED;

        ArrayList<String> options = new ArrayList<String>();

        if (!noHardwareAcceleration) {
            /*
             * Set higher caching values if using iomx decoding, since some omx
             * decoders have a very high latency, and if the preroll data isn't
             * enough to make the decoder output a frame, the playback timing gets
             * started too soon, and every decoded frame appears to be too late.
             * On Nexus One, the decoder latency seems to be 25 input packets
             * for 320x170 H.264, a few packets less on higher resolutions.
             * On Nexus S, the decoder latency seems to be about 7 packets.
             */
            options.add(":file-caching=1500");
            options.add(":network-caching=1500");
            options.add(":codec="+ (this.devCodecList != null ? this.devCodecList : this.codecList));
        }
        if (noVideo)
            options.add(":no-video");
        if (paused)
            options.add(":start-paused");
        return options.toArray(new String[options.size()]);
    }

    public String getSubtitlesEncoding() {
        return subtitlesEncoding;
    }

    public void setSubtitlesEncoding(String subtitlesEncoding) {
        this.subtitlesEncoding = subtitlesEncoding;
    }

    public int getAout() {
        return aout;
    }

    public void setAout(int aout) {
        final HWDecoderUtil.AudioOutput hwaout = HWDecoderUtil.getAudioOutputFromDevice();
        if (hwaout == HWDecoderUtil.AudioOutput.AUDIOTRACK || hwaout == HWDecoderUtil.AudioOutput.OPENSLES)
            aout = hwaout == HWDecoderUtil.AudioOutput.OPENSLES ? AOUT_OPENSLES : AOUT_AUDIOTRACK;

        this.aout = aout == AOUT_OPENSLES ? AOUT_OPENSLES : AOUT_AUDIOTRACK;
    }

    public int getVout() {
        return vout;
    }

    public void setVout(int vout) {
        if (vout < 0)
            this.vout = VOUT_ANDROID_SURFACE;
        else
            this.vout = vout;
        if (this.vout == VOUT_ANDROID_SURFACE && HAS_WINDOW_VOUT)
            this.vout = VOUT_ANDROID_WINDOW;
    }

    public void setHdmiAudioEnabled(boolean enable) {
        this.hdmiAudioEnabled = enable;
    }

    public boolean isHdmiAudioEnabled() {
        return this.hdmiAudioEnabled;
    }

    public boolean useCompatSurface() {
        return this.vout != VOUT_ANDROID_WINDOW;
    }

    public boolean timeStretchingEnabled() {
        return timeStretching;
    }

    public void setTimeStretching(boolean timeStretching) {
        this.timeStretching = timeStretching;
    }

    public int getDeblocking() {
        int ret = deblocking;
        if(deblocking < 0) {
            /**
             * Set some reasonable deblocking defaults:
             *
             * Skip all (4) for armv6 and MIPS by default
             * Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
             * Skip non-key (3) for all devices that don't meet anything above
             */
            LibVlcUtil.MachineSpecs m = LibVlcUtil.getMachineSpecs();
            if (m == null)
                return ret;
            if( (m.hasArmV6 && !(m.hasArmV7)) || m.hasMips )
                ret = 4;
            else if(m.frequency >= 1200 && m.processors > 2)
                ret = 1;
            else if(m.bogoMIPS >= 1200 && m.processors > 2) {
                ret = 1;
                Log.d(TAG, "Used bogoMIPS due to lack of frequency info");
            } else
                ret = 3;
        } else if(deblocking > 4) { // sanity check
            ret = 3;
        }
        return ret;
    }

    public void setDeblocking(int deblocking) {
        this.deblocking = deblocking;
    }

    public String getChroma() {
        return chroma;
    }

    public void setChroma(String chroma) {
        this.chroma = chroma.equals("YV12") && !LibVlcUtil.isGingerbreadOrLater() ? "" : chroma;
    }

    public boolean isVerboseMode() {
        return verboseMode;
    }

    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    public boolean frameSkipEnabled() {
        return frameSkip;
    }

    public void setFrameSkip(boolean frameskip) {
        this.frameSkip = frameskip;
    }

    public int getNetworkCaching() {
        return this.networkCaching;
    }

    public void setNetworkCaching(int networkcaching) {
        this.networkCaching = networkcaching;
    }

    public boolean getHttpReconnect() {
        return httpReconnect;
    }

    public void setHttpReconnect(boolean httpReconnect) {
        this.httpReconnect = httpReconnect;
    }

    /**
     * Initialize the libVLC class.
     *
     * This function must be called before using any libVLC functions.
     *
     * @throws LibVlcException
     */
    public void init(Context context) throws LibVlcException {
        Log.v(TAG, "Initializing LibVLC");
        if (!mIsInitialized) {
            if(!LibVlcUtil.hasCompatibleCPU(context)) {
                Log.e(TAG, LibVlcUtil.getErrorMsg());
                throw new LibVlcException();
            }

            File cacheDir = context.getCacheDir();
            mCachePath = (cacheDir != null) ? cacheDir.getAbsolutePath() : null;
            nativeInit();
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
    private native void nativeInit() throws LibVlcException;

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

    public static native String nativeToURI(String path);
    
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
        return LibVLC.nativeToURI(path);
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

    public String getCachePath() {
        return mCachePath;
    }

    public native int setWindowSize(int width, int height);

}
