/*****************************************************************************
 * MediaPlayer.java
 *****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 *
 * Authors  Jean-Baptiste Kempf <jb@videolan.org>
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

import java.util.Map;

@SuppressWarnings("unused")
public class MediaPlayer extends VLCObject<MediaPlayer.Event> implements AWindow.SurfaceCallback {

    public static class Event extends VLCEvent {
        //public static final int MediaChanged        = 0x100;
        //public static final int NothingSpecial      = 0x101;
        //public static final int Opening             = 0x102;
        //public static final int Buffering           = 0x103;
        public static final int Playing             = 0x104;
        public static final int Paused              = 0x105;
        public static final int Stopped             = 0x106;
        //public static final int Forward             = 0x107;
        //public static final int Backward            = 0x108;
        public static final int EndReached          = 0x109;
        public static final int EncounteredError   = 0x10a;
        public static final int TimeChanged         = 0x10b;
        public static final int PositionChanged     = 0x10c;
        //public static final int SeekableChanged     = 0x10d;
        //public static final int PausableChanged     = 0x10e;
        //public static final int TitleChanged        = 0x10f;
        //public static final int SnapshotTaken       = 0x110;
        //public static final int LengthChanged       = 0x111;
        public static final int Vout                = 0x112;
        //public static final int ScrambledChanged    = 0x113;
        public static final int ESAdded             = 0x114;
        public static final int ESDeleted           = 0x115;
        //public static final int ESSelected          = 0x116;

        private final long arg1;
        private final float arg2;
        protected Event(int type) {
            super(type);
            this.arg1 = 0;
            this.arg2 = 0;
        }
        protected Event(int type, long arg1) {
            super(type);
            this.arg1 = arg1;
            this.arg2 = 0;
        }
        protected Event(int type, float arg2) {
            super(type);
            this.arg1 = 0;
            this.arg2 = arg2;
        }
        public long getTimeChanged() {
            return arg1;
        }
        public float getPositionChanged() {
            return arg2;
        }
        public int getVoutCount() {
            return (int) arg1;
        }
        public int getEsChangedType() {
            return (int) arg1;
        }
    }

    public interface EventListener extends VLCEvent.Listener<MediaPlayer.Event> {}

    public static class Position {
        public static final int Disable = -1;
        public static final int Center = 0;
        public static final int Left = 1;
        public static final int Right = 2;
        public static final int Top = 3;
        public static final int TopLeft = 4;
        public static final int TopRight = 5;
        public static final int Bottom = 6;
        public static final int BottomLeft = 7;
        public static final int BottomRight = 8;
    }

    public static class Navigate {
        public static final int Activate = 0;
        public static final int Up = 1;
        public static final int Down = 2;
        public static final int Left = 3;
        public static final int Right = 4;
    }

    public static class Title {
        /**
         * duration in milliseconds
         */
        public final long duration;

        /**
         * title name
         */
        public final String name;

        /**
         * true if the title is a menu
         */
        public final boolean menu;

        public Title(long duration, String name, boolean menu) {
            this.duration = duration;
            this.name = name;
            this.menu = menu;
        }
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static Title createTitleFromNative(long duration, String name, boolean menu) {
        return new Title(duration, name, menu);
    }

    public static class Chapter {
        /**
         * time-offset of the chapter in milliseconds
         */
        public final long timeOffset;

        /**
         * duration of the chapter in milliseconds
         */
        public final long duration;

        /**
         * chapter name
         */
        public final String name;

        private Chapter(long timeOffset, long duration, String name) {
            this.timeOffset = timeOffset;
            this.duration = duration;
            this.name = name;
        }
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static Chapter createChapterFromNative(long timeOffset, long duration, String name) {
        return new Chapter(timeOffset, duration, name);
    }

    private Media mMedia = null;
    private boolean mPlaying = false;
    private boolean mPlayRequested = false;
    private final AWindow mWindow = new AWindow(this);
    private int mVoutCount = 0;

    /**
     * Create an empty MediaPlayer
     *
     * @param libVLC a valid libVLC
     */
    public MediaPlayer(LibVLC libVLC) {
        nativeNewFromLibVlc(libVLC, mWindow);
    }

    /**
     * Create a MediaPlayer from a Media
     *
     * @param media a valid  Media
     */
    public MediaPlayer(Media media) {
        if (media == null || media.isReleased())
            throw new IllegalArgumentException("Media is null or released");
        mMedia = media;
        mMedia.retain();
        nativeNewFromMedia(mMedia, mWindow);
    }

    /**
     * Get the IVLCVout helper.
     */
    public IVLCVout getVLCVout() {
        return mWindow;
    }

    /**
     * Set a Media
     *
     * @param media a valid libVLC
     */
    public synchronized void setMedia(Media media) {
        if (mMedia != null)
            mMedia.release();
        if (media != null) {
            if (media.isReleased())
                throw new IllegalArgumentException("Media is released");
            media.retain();
            media.setDefaultMediaPlayerOptions();
        }
        mMedia = media;
        nativeSetMedia(mMedia);
    }

    /**
     * Get the Media used by this MediaPlayer. This Media should be released with {@link #release()}.
     */
    public synchronized Media getMedia() {
        if (mMedia != null)
            mMedia.retain();
        return mMedia;
    }

    /**
     * Play the media
     *
     */
    public synchronized void play() {
        if (!mPlaying) {
            mPlayRequested = true;
            if (mWindow.areSurfacesWaiting())
                return;
        }
        mPlaying = true;
        nativePlay();
    }

    /**
     * Stops the playing media
     *
     */
    public synchronized void stop() {
        mPlayRequested = false;
        mPlaying = false;
        nativeStop();
    }


    @Override
    public synchronized void onSurfacesCreated(AWindow vout) {
        if (!mPlaying && mPlayRequested)
            play();
    }

    @Override
    public synchronized void onSurfacesDestroyed(AWindow vout) {
        if (mVoutCount > 0)
            setVideoTrackEnabled(false);
        /* Wait for Vout destruction (mVoutCount = 0) in order to be sure that the surface is not
         * used after leaving this callback. This shouldn't be needed when using MediaCodec or
         * AndroidWindow (i.e. after Android 2.3) since the surface is ref-counted */
        while (mVoutCount > 0) {
            try {
                wait();
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Set if, and how, the video title will be shown when media is played
     *
     * @param position see {@link Position}
     * @param timeout
     */
    public synchronized void setVideoTitleDisplay(int position, int timeout) {
        nativeSetVideoTitleDisplay(position, timeout);
    }

    public synchronized boolean setAudioOutput(String aout) {
        return nativeSetAudioOutput(aout);
    }

    /**
     * Get the full description of available titles.
     *
     * @return the list of titles
     */
    public synchronized Title[] getTitles() {
        return nativeGetTitles();
    }

    /**
     * Get the full description of available chapters.
     *
     * @param title index of the title
     * @return the list of Chapters for the title
     */
    public synchronized Chapter[] getChapters(int title) {
        return nativeGetChapters(title);
    }

    /**
     * TODO: this doesn't respect API
     *
     * @param bands
     */
    public synchronized void setEqualizer(float[] bands) {
        nativeSetEqualizer(bands);
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
     * Returns true if any media is playing
     */
    public native boolean isPlaying();

    /**
     * Returns true if any media is seekable
     */
    public native boolean isSeekable();

    /**
     * Pauses any playing media
     */
    public native void pause();

    /**
     * Get player state.
     */
    public native int getPlayerState();

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

    public native int getTitle();
    public native void setTitle(int title);
    public native int getChapter();
    public native int previousChapter();
    public native int nextChapter();
    public native void setChapter(int chapter);
    public native void navigate(int navigate);

    public native int getAudioTracksCount();

    public native Map<Integer,String> getAudioTrackDescription();

    public native Map<String, Object> getStats();

    public native int getAudioTrack();

    public native int setAudioTrack(int index);

    public native int getVideoTracksCount();

    public native int setVideoTrackEnabled(boolean enabled);

    public native int addSubtitleTrack(String path);

    public native Map<Integer,String> getSpuTrackDescription();

    public native int getSpuTrack();

    public native int setSpuTrack(int index);

    public native int getSpuTracksCount();

    public native int setAudioDelay(long delay);

    public native long getAudioDelay();

    public native int setSpuDelay(long delay);

    public native long getSpuDelay();

    public native float[] getBands();
    public native String[] getPresets();
    public native float[] getPreset(int index);

    public synchronized void setEventListener(EventListener listener) {
        super.setEventListener(listener);
    }

    @Override
    protected Event onEventNative(int eventType, long arg1, float arg2) {
        switch (eventType) {
            case Event.Stopped:
            case Event.EndReached:
            case Event.EncounteredError:
                mVoutCount = 0;
                notify();
            case Event.Playing:
            case Event.Paused:
                return new Event(eventType);
            case Event.TimeChanged:
                return new Event(eventType, arg1);
            case Event.PositionChanged:
                return new Event(eventType, arg2);
            case Event.Vout:
                mVoutCount = (int) arg1;
                notify();
                return new Event(eventType, arg1);
            case Event.ESAdded:
            case Event.ESDeleted:
                return new Event(eventType, arg1);
        }
        return null;
    }

    @Override
    protected void onReleaseNative() {
        if (mMedia != null)
            mMedia.release();
        nativeRelease();
    }

    /* JNI */
    private native void nativeNewFromLibVlc(LibVLC libVLC, IAWindowNativeHandler window);
    private native void nativeNewFromMedia(Media media, IAWindowNativeHandler window);
    private native void nativeRelease();
    private native void nativeSetMedia(Media media);
    private native void nativePlay();
    private native void nativeStop();
    private native void nativeSetVideoTitleDisplay(int position, int timeout);
    private native void nativeSetEqualizer(float[] bands);
    private native boolean nativeSetAudioOutput(String aout);
    private native Title[] nativeGetTitles();
    private native Chapter[] nativeGetChapters(int title);
}
