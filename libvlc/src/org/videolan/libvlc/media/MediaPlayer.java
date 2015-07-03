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

package org.videolan.libvlc.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaFormat;
import android.media.TimedText;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;

public class MediaPlayer
{
    public static final int MEDIA_ERROR_UNKNOWN = 1;
    public static final int MEDIA_ERROR_SERVER_DIED = 100;
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;
    public static final int MEDIA_ERROR_IO = -1004;
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    public static final int MEDIA_ERROR_UNSUPPORTED = -1010;
    public static final int MEDIA_ERROR_TIMED_OUT = -110;

    public static final int MEDIA_INFO_UNKNOWN = 1;
    public static final int MEDIA_INFO_STARTED_AS_NEXT = 2;
    public static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;
    public static final int MEDIA_INFO_BUFFERING_START = 701;
    public static final int MEDIA_INFO_BUFFERING_END = 702;
    public static final int MEDIA_INFO_BAD_INTERLEAVING = 800;
    public static final int MEDIA_INFO_NOT_SEEKABLE = 801;
    public static final int MEDIA_INFO_METADATA_UPDATE = 802;
    public static final int MEDIA_INFO_EXTERNAL_METADATA_UPDATE = 803;
    public static final int MEDIA_INFO_TIMED_TEXT_ERROR = 900;
    public static final int MEDIA_INFO_UNSUPPORTED_SUBTITLE = 901;
    public static final int MEDIA_INFO_SUBTITLE_TIMED_OUT = 902;

    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT = 1;
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 2;

    private Media mCurrentMedia = null;
    private final LibVLC mLibVLC;
    private org.videolan.libvlc.MediaPlayer mMediaPlayer;

    public MediaPlayer() {
        mLibVLC = new LibVLC(); //FIXME, this is wrong
        mMediaPlayer = new org.videolan.libvlc.MediaPlayer(mLibVLC);
    }

    public static MediaPlayer create(Context context, Uri uri) {
        return create (context, uri, null);
    }

    public static MediaPlayer create(Context context, Uri uri, SurfaceHolder holder) {
        return create(context, uri, holder, null, 0);
    }

    public static MediaPlayer create(Context context, Uri uri, SurfaceHolder holder,
                                     AudioAttributes audioAttributes, int audioSessionId) {
        MediaPlayer player = new MediaPlayer();
        //player.setDataSource(context, uri); This throws exception, but not this create()
        return player;
    }

    public static MediaPlayer create(Context context, int resid) {
        return create(context, resid, null, 0);
    }

    public static MediaPlayer create(Context context, int resid,
                                     AudioAttributes audioAttributes, int audioSessionId) {
        return null;
    }

    public void setDataSource(Context context, Uri uri)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(context, uri, null);
    }

    // FIXME, this is INCORRECT, @headers are ignored
    public void setDataSource(Context context, Uri uri, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        mCurrentMedia = new Media(mLibVLC, uri);
        mMediaPlayer.setMedia(mCurrentMedia);
    }

    public void setDataSource(String path)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        mCurrentMedia = new Media(mLibVLC, path);
        mMediaPlayer.setMedia(mCurrentMedia);
    }

    public void setDataSource(FileDescriptor fd)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mCurrentMedia = new Media(mLibVLC, fd);
        mMediaPlayer.setMedia(mCurrentMedia);
    }

    // FIXME, this is INCORRECT, @offset and @length are ignored
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException {
        setDataSource(fd);
    }

    public void prepare() throws IOException, IllegalStateException {
    }

    public void prepareAsync() {
        mCurrentMedia.addOption(":video-paused");
        mMediaPlayer.play();
    }

    public void setDisplay(SurfaceHolder sh) {
        mMediaPlayer.getVLCVout().setVideoSurface(sh.getSurface(), sh);
    }

    public void setSurface(Surface surface) {
        mMediaPlayer.getVLCVout().setVideoSurface(surface, null);
    }

    public void setVideoScalingMode(int mode) {
    }

    public void start() throws IllegalStateException {
        mMediaPlayer.play();
    }

    public void stop() throws IllegalStateException {
        mMediaPlayer.stop();
    }

    public void pause() throws IllegalStateException {
        // FIXME, this is toggling for now.
        mMediaPlayer.pause();
    }

    public void setWakeMode(Context context, int mode) {
    }

    public void setScreenOnWhilePlaying(boolean screenOn) {
    }

    public int getVideoWidth() {
        return -1;
    }

    public int getVideoHeight() {
        return -1;
    }

    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }

    public void seekTo(int msec) throws IllegalStateException {
    }

    // This is of course, less precise than VLC
    public int getCurrentPosition() {
        return (int)mMediaPlayer.getTime();
    }

    // This is of course, less precise than VLC
    public int getDuration() {
        return (int)mMediaPlayer.getLength();
    }

    public void setNextMediaPlayer(MediaPlayer next) {
    }

    public void release() {
        mMediaPlayer.release();
    }

    public void reset() {
    }

    public void setAudioStreamType(int streamtype) {
    }

    public void setAudioAttributes(AudioAttributes attributes) throws IllegalArgumentException {
    }

    public void setLooping(boolean looping) {
    }

    public boolean isLooping() {
        return false;
    }

    public void setVolume(float leftVolume, float rightVolume) {
        mMediaPlayer.setVolume( (int)((leftVolume + rightVolume) * 100/2));
    }

    public void setAudioSessionId(int sessionId)  throws IllegalArgumentException, IllegalStateException {
    }

    public int getAudioSessionId() {
        return 0;
    }

    public void attachAuxEffect(int effectId) {
    }

    public void setAuxEffectSendLevel(float level) {
    }

    static public class TrackInfo implements Parcelable {

        public static final int MEDIA_TRACK_TYPE_UNKNOWN = 0;
        public static final int MEDIA_TRACK_TYPE_VIDEO = 1;
        public static final int MEDIA_TRACK_TYPE_AUDIO = 2;
        public static final int MEDIA_TRACK_TYPE_TIMEDTEXT = 3;
        public static final int MEDIA_TRACK_TYPE_SUBTITLE = 4;

        TrackInfo(Parcel in) {
        }

        public int getTrackType() {
            return MEDIA_TRACK_TYPE_UNKNOWN;
        }

        public String getLanguage() {
            return  "und";
        }

        public MediaFormat getFormat() {
            return null;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }

        @Override
        public String toString() {
            return "";
        }
    }

    public TrackInfo[] getTrackInfo() throws IllegalStateException {
        //FIXME
        TrackInfo trackInfo[] = new TrackInfo[1];
        return trackInfo;
    }

    public static final String MEDIA_MIMETYPE_TEXT_SUBRIP = "application/x-subrip";

    public void addTimedTextSource(String path, String mimeType) {
        mMediaPlayer.setSubtitleFile(path);
    }

    // FIXME: This is incorrect, since libVLC can only add local subtitles
    public void addTimedTextSource(Context context, Uri uri, String mimeType) {
        mMediaPlayer.setSubtitleFile(uri.getPath());
    }

    public void addTimedTextSource(FileDescriptor fd, String mimeType)
            throws IllegalArgumentException, IllegalStateException {
    }

    public void addTimedTextSource(FileDescriptor fd, long offset, long length, String mime)
            throws IllegalArgumentException, IllegalStateException {
    }

    public int getSelectedTrack(int trackType) throws IllegalStateException {
        return 0;
    }

    public void selectTrack(int index) throws IllegalStateException {
    }

    public void deselectTrack(int index) throws IllegalStateException {
    }

    @Override
    protected void finalize() {}

    public interface OnPreparedListener
    {
        void onPrepared(MediaPlayer mp);
    }

    public void setOnPreparedListener(OnPreparedListener listener) {
    }

    public interface OnCompletionListener
    {
        void onCompletion(MediaPlayer mp);
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
    }

    public interface OnBufferingUpdateListener
    {
        void onBufferingUpdate(MediaPlayer mp, int percent);
    }

    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
    }

    public interface OnSeekCompleteListener
    {
        public void onSeekComplete(MediaPlayer mp);
    }

    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
    }

    public interface OnVideoSizeChangedListener
    {
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height);
    }

    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) {
    }

    public interface OnTimedTextListener
    {
        public void onTimedText(MediaPlayer mp, TimedText text);
    }

    public void setOnTimedTextListener(OnTimedTextListener listener) {
    }

    public interface OnErrorListener
    {
        boolean onError(MediaPlayer mp, int what, int extra);
    }

    public void setOnErrorListener(OnErrorListener listener) {
    }

    public interface OnInfoListener
    {
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    public void setOnInfoListener(OnInfoListener listener) {
    }
}
