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


public class MediaPlayer {

    private LibVLC mLibVLC;
    public MediaPlayer(LibVLC libVLC) {
        mLibVLC = libVLC;
    }

    // REMOVE ASAP
    public LibVLC getLibVLC() {
        return mLibVLC;
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
     * Play an mrl
     */
    public native void playMRL(String mrl, String[] mediaOptions);

    /**
     * Play an MRL directly.
     *
     * @param mrl MRL of the media to play.
     */
    public void playMRL(String mrl) {
        // index=-1 will return options from libvlc instance without relying on MediaList
        String[] options = mLibVLC.getMediaOptions(false, false);
        playMRL(mrl, options);
    }

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

    /**
     * Return true if there is a video track in the file
     */
    public native boolean hasVideoTrack(String mrl) throws java.io.IOException;

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
}
