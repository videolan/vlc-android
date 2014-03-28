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

import java.util.ArrayList;
import java.util.Map;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

public class LibVLC {
    private static final String TAG = "VLC/LibVLC";
    public static final int AOUT_AUDIOTRACK_JAVA = 0;
    public static final int AOUT_AUDIOTRACK = 1;
    public static final int AOUT_OPENSLES = 2;

    public static final int VOUT_ANDROID_SURFACE = 0;
    public static final int VOUT_OPEGLES2 = 1;

    public static final int HW_ACCELERATION_AUTOMATIC = -1;
    public static final int HW_ACCELERATION_DISABLED = 0;
    public static final int HW_ACCELERATION_DECODING = 1;
    public static final int HW_ACCELERATION_FULL = 2;

    private static LibVLC sInstance;

    /** libVLC instance C pointer */
    private long mLibVlcInstance = 0; // Read-only, reserved for JNI
    /** libvlc_media_player pointer and index */
    private int mInternalMediaPlayerIndex = 0; // Read-only, reserved for JNI
    private long mInternalMediaPlayerInstance = 0; // Read-only, reserved for JNI

    private MediaList mMediaList; // Pointer to media list being followed
    private MediaList mPrimaryList; // Primary/default media list; see getPrimaryMediaList()

    /** Buffer for VLC messages */
    private StringBuffer mDebugLogBuffer;
    private boolean mIsBufferingLog = false;

    private Aout mAout;

    /** Keep screen bright */
    //private WakeLock mWakeLock;

    /** Settings */
    private int hardwareAcceleration = HW_ACCELERATION_AUTOMATIC;
    private String subtitlesEncoding = "";
    private int aout = LibVlcUtil.isGingerbreadOrLater() ? AOUT_OPENSLES : AOUT_AUDIOTRACK_JAVA;
    private int vout = VOUT_ANDROID_SURFACE;
    private boolean timeStretching = false;
    private int deblocking = -1;
    private String chroma = "";
    private boolean verboseMode = true;
    private float[] equalizer = null;
    private boolean frameSkip = false;
    private int networkCaching = 0;

    /** Check in libVLC already initialized otherwise crash */
    private boolean mIsInitialized = false;
    public native void attachSurface(Surface surface, IVideoPlayer player);

    public native void detachSurface();

    public native void attachSubtitlesSurface(Surface surface);
    public native void detachSubtitlesSurface();

    public native void eventVideoPlayerActivityCreated(boolean created);

    /* Load library before object instantiation */
    static {
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.GINGERBREAD_MR1)
                System.loadLibrary("iomx-gingerbread");
            else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB_MR2)
                System.loadLibrary("iomx-hc");
            else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2)
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
     * Singleton constructor of libVLC Without surface and vout to create the
     * thumbnail and get information e.g. on the MediaLibraryActivity
     *
     * @return libVLC instance
     * @throws LibVlcException
     */
    public static LibVLC getInstance() throws LibVlcException {
        synchronized (LibVLC.class) {
            if (sInstance == null) {
                /* First call */
                sInstance = new LibVLC();
            }
        }

        return sInstance;
    }

    /**
     * Return an existing instance of libVLC Call it when it is NOT important
     * that this fails
     *
     * @return libVLC instance OR null
     */
    public static LibVLC getExistingInstance() {
        synchronized (LibVLC.class) {
            return sInstance;
        }
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
    @Override
    public void finalize() {
        if (mLibVlcInstance != 0) {
            Log.d(TAG, "LibVLC is was destroyed yet before finalize()");
            destroy();
        }
    }

    /**
     * Get the media list that LibVLC is following right now.
     *
     * @return The media list object being followed
     */
    public MediaList getMediaList() {
        return mMediaList;
    }

    /**
     * Set the media list for LibVLC to follow.
     *
     * @param mediaList The media list object to follow
     */
    public void setMediaList(MediaList mediaList) {
        mMediaList = mediaList;
    }

    /**
     * Sets LibVLC to follow the default media list (see below)
     */
    public void setMediaList() {
        mMediaList = mPrimaryList;
    }

    /**
     * Gets the primary media list, or the "currently playing" list.
     * Not to be confused with the media list pointer from above, which
     * refers the the MediaList object that libVLC is currently following.
     * This list is just one out of many lists that it can be pointed towards.
     *
     * This list will be used for lists of songs that are not user-defined.
     * For example: selecting a song from the Songs list, or from the list
     * displayed after selecting an album.
     *
     * It is loaded as the default list.
     *
     * @return The primary media list
     */
    public MediaList getPrimaryMediaList() {
        return mPrimaryList;
    }

    /**
     * Give to LibVLC the surface to draw the video.
     * @param f the surface to draw
     */
    public native void setSurface(Surface f);

    public static synchronized void restart(Context context) {
        if (sInstance != null) {
            try {
                sInstance.destroy();
                sInstance.init(context);
            } catch (LibVlcException lve) {
                Log.e(TAG, "Unable to reinit libvlc: " + lve);
            }
        }
    }

    /**
     * those get/is* are called from native code to get settings values.
     */

    public int getHardwareAcceleration() {
        return this.hardwareAcceleration;
    }

    public void setHardwareAcceleration(int hardwareAcceleration) {
        if (hardwareAcceleration < 0) {
            // Automatic mode: activate MediaCodec opaque direct rendering for 4.3 and above.
            if (LibVlcUtil.isJellyBeanMR2OrLater())
                this.hardwareAcceleration = HW_ACCELERATION_FULL;
            else
                this.hardwareAcceleration = HW_ACCELERATION_DISABLED;
        }
        else
            this.hardwareAcceleration = hardwareAcceleration;
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
        if (aout < 0)
            this.aout = LibVlcUtil.isICSOrLater() ? AOUT_OPENSLES : AOUT_AUDIOTRACK_JAVA;
        else
            this.aout = aout;
    }

    public int getVout() {
        return vout;
    }

    public void setVout(int vout) {
        if (vout < 0)
            this.vout = VOUT_ANDROID_SURFACE;
        else
            this.vout = vout;
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
            if( (m.hasArmV6 && !(m.hasArmV7)) || m.hasMips )
                ret = 4;
            else if(m.bogoMIPS > 1200 && m.processors > 2)
                ret = 1;
            else
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

    public float[] getEqualizer()
    {
        return equalizer;
    }

    public void setEqualizer(float[] equalizer)
    {
        this.equalizer = equalizer;
        applyEqualizer();
    }

    private void applyEqualizer()
    {
        setNativeEqualizer(mInternalMediaPlayerInstance, this.equalizer);
    }
    private native int setNativeEqualizer(long mediaPlayer, float[] bands);

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

    /**
     * Initialize the libVLC class.
     *
     * This function must be called before using any libVLC functions.
     *
     * @throws LibVlcException
     */
    public void init(Context context) throws LibVlcException {
        Log.v(TAG, "Initializing LibVLC");
        mDebugLogBuffer = new StringBuffer();
        if (!mIsInitialized) {
            if(!LibVlcUtil.hasCompatibleCPU(context)) {
                Log.e(TAG, LibVlcUtil.getErrorMsg());
                throw new LibVlcException();
            }
            nativeInit();
            mMediaList = mPrimaryList = new MediaList(this);
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
     * Pause the Java audio output
     * This function is called by the native code
     */
    public void pauseAout() {
        Log.d(TAG, "Pausing the java audio output");
        mAout.pause();
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
     * Play a media from the media list (playlist)
     *
     * @param position The index of the media
     */
    public void playIndex(int position) {
        String mrl = mMediaList.getMRL(position);
        if (mrl == null)
            return;
        String[] options = mMediaList.getMediaOptions(position);
        mInternalMediaPlayerIndex = position;
        playMRL(mLibVlcInstance, mrl, options);
    }

    /**
     * Play an MRL directly.
     *
     * @param mrl MRL of the media to play.
     */
    public void playMRL(String mrl) {
        // index=-1 will return options from libvlc instance without relying on MediaList
        String[] options = mMediaList.getMediaOptions(-1);
        mInternalMediaPlayerIndex = 0;
        playMRL(mLibVlcInstance, mrl, options);
    }

    public TrackInfo[] readTracksInfo(String mrl) {
        return readTracksInfo(mLibVlcInstance, mrl);
    }

    /**
     * Get a media thumbnail.
     */
    public byte[] getThumbnail(String mrl, int i_width, int i_height) {
        return getThumbnail(mLibVlcInstance, mrl, i_width, i_height);
    }

    /**
     * Return true if there is a video track in the file
     */
    public boolean hasVideoTrack(String mrl) throws java.io.IOException {
        return hasVideoTrack(mLibVlcInstance, mrl);
    }

    /**
     * Sets the speed of playback (1 being normal speed, 2 being twice as fast)
     *
     * @param rate
     */
    public native void setRate(float rate);

    /**
     * Get the current playback speed
     */
    public native float getRate();

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
     * Start buffering to the mDebugLogBuffer.
     */
    public native void startDebugBuffer();
    public native void stopDebugBuffer();
    public String getBufferContent() {
        return mDebugLogBuffer.toString();
    }

    public void clearBuffer() {
        mDebugLogBuffer.setLength(0);
    }

    public boolean isDebugBuffering() {
        return mIsBufferingLog;
    }

    /**
     * Play an mrl
     */
    private native void playMRL(long instance, String mrl, String[] mediaOptions);

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
     * Sets volume as integer
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
    private native byte[] getThumbnail(long instance, String mrl, int i_width, int i_height);

    /**
     * Return true if there is a video track in the file
     */
    private native boolean hasVideoTrack(long instance, String mrl);

    private native TrackInfo[] readTracksInfo(long instance, String mrl);

    public native TrackInfo[] readTracksInfoInternal();

    public native int getAudioTracksCount();

    public native Map<Integer,String> getAudioTrackDescription();

    public native int getAudioTrack();

    public native int setAudioTrack(int index);

    public native int getVideoTracksCount();

    public native int addSubtitleTrack(String path);

    public native Map<Integer,String> getSpuTrackDescription();

    public native int getSpuTrack();

    public native int setSpuTrack(int index);

    public native int getSpuTracksCount();

    public static native String nativeToURI(String path);

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

     /**
      * Expand and continue playing the current media.
      *
      * @return the index of the media was expanded, and -1 if no media was expanded
      */
    public int expandAndPlay() {
        int r = mMediaList.expandMedia(mInternalMediaPlayerIndex);
        if(r == 0)
            this.playIndex(mInternalMediaPlayerIndex);
        return r;
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    public int expand() {
        return mMediaList.expandMedia(mInternalMediaPlayerIndex);
    }

    private native void setEventHandler(EventHandler eventHandler);

    private native void detachEventHandler();

    public native float[] getBands();

    public native String[] getPresets();

    public native float[] getPreset(int index);
}
