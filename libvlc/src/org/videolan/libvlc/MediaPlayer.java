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

import java.util.ArrayList;
import java.util.Map;


public class MediaPlayer extends VLCObject {

    public static class Position {
        public static final int disable = -1;
        public static final int center = 0;
        public static final int left = 1;
        public static final int right = 2;
        public static final int top = 3;
        public static final int top_left = 4;
        public static final int top_right = 5;
        public static final int bottom = 6;
        public static final int bottom_left = 7;
        public static final int bottom_right = 8;
    }

    private Media mMedia = null;

    /**
     * Create an empty MediaPlayer
     *
     * @param libVLC
     */
    public MediaPlayer(LibVLC libVLC) {
        nativeNewFromLibVlc(libVLC);
    }

    /**
     * Create a MediaPlayer from a Media
     *
     * @param media
     */
    public MediaPlayer(Media media) {
        if (media == null || media.isReleased())
            throw new IllegalArgumentException("Media is null or released");
        mMedia = media;
        mMedia.retain();
        nativeNewFromMedia(mMedia);
    }

    /**
     * Set a Media
     *
     * @param media
     */
    public synchronized void setMedia(Media media) {
        if (mMedia != null)
            mMedia.release();
        if (media != null) {
            if (media.isReleased())
                throw new IllegalArgumentException("Media is released");
            media.retain();
        }
        mMedia = media;
        nativeSetMedia(mMedia);
    }

    /**
     * Get the Media used by this MediaPlayer.
     * This Media is owned by the MediaPlayer, it shouldn't be released.
     *
     * @return
     */
    public synchronized Media getMedia() {
        return mMedia;
    }

    /**
     * Play the media
     *
     */
    public synchronized void play() {
        nativePlay();
    }

    /**
     * Stops the playing media
     *
     */
    public synchronized void stop() {
        nativeStop();
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

    public native String getMeta(int meta);
    public native int getTitle();
    public native void setTitle(int title);
    public native int getChapterCountForTitle(int title);
    public native int getChapterCount();
    public native int getChapter();
    public native String getChapterDescription(int title);
    public native int previousChapter();
    public native int nextChapter();
    public native void setChapter(int chapter);
    public native int getTitleCount();
    public native void playerNavigate(int navigate);

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

    /* MediaList */
    public native int expandMedia(ArrayList<String> children);


    public native float[] getBands();
    public native String[] getPresets();
    public native float[] getPreset(int index);

    @Override
    protected Event onEventNative(int eventType, long arg1, long arg2) {
        /* TODO */
        return null;
    }

    @Override
    protected void onReleaseNative() {
        if (mMedia != null)
            mMedia.release();
        nativeRelease();
    }

    /* JNI */
    private native void nativeNewFromLibVlc(LibVLC libVLC);
    private native void nativeNewFromMedia(Media media);
    private native void nativeRelease();
    private native void nativeSetMedia(Media media);
    private native void nativePlay();
    private native void nativeStop();
    private native void nativeSetVideoTitleDisplay(int position, int timeout);
    private native void nativeSetEqualizer(float[] bands);
}
