/*****************************************************************************
 * Media.java
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
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

import android.net.Uri;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.libvlc.util.HWDecoderUtil;

import java.io.FileDescriptor;

public class Media extends VLCObject<Media.Event> {
    private final static String TAG = "LibVLC/Media";

    public static class Event extends VLCEvent {
        public static final int MetaChanged = 0;
        public static final int SubItemAdded = 1;
        public static final int DurationChanged = 2;
        public static final int ParsedChanged = 3;
        //public static final int Freed                      = 4;
        public static final int StateChanged = 5;
        public static final int SubItemTreeAdded = 6;

        protected Event(int type) {
            super(type);
        }
    }

    public interface EventListener extends VLCEvent.Listener<Media.Event> {}

    /**
     * libvlc_media_type_t
     */
    public static class Type {
        public static final int Unknown = 0;
        public static final int File = 1;
        public static final int Directory = 2;
        public static final int Disc = 3;
        public static final int Stream = 4;
        public static final int Playlist = 5;
    }

    /**
     * see libvlc_meta_t
     */
    public static class Meta {
        public static final int Title = 0;
        public static final int Artist = 1;
        public static final int Genre = 2;
        public static final int Copyright = 3;
        public static final int Album = 4;
        public static final int TrackNumber = 5;
        public static final int Description = 6;
        public static final int Rating = 7;
        public static final int Date = 8;
        public static final int Setting = 9;
        public static final int URL = 10;
        public static final int Language = 11;
        public static final int NowPlaying = 12;
        public static final int Publisher = 13;
        public static final int EncodedBy = 14;
        public static final int ArtworkURL = 15;
        public static final int TrackID = 16;
        public static final int TrackTotal = 17;
        public static final int Director = 18;
        public static final int Season = 19;
        public static final int Episode = 20;
        public static final int ShowName = 21;
        public static final int Actors = 22;
        public static final int AlbumArtist = 23;
        public static final int DiscNumber = 24;
        public static final int MAX = 25;
    }

    /**
     * see libvlc_state_t
     */
    public static class State {
        public static final int NothingSpecial = 0;
        public static final int Opening = 1;
        public static final int Buffering = 2;
        public static final int Playing = 3;
        public static final int Paused = 4;
        public static final int Stopped = 5;
        public static final int Ended = 6;
        public static final int Error = 7;
        public static final int MAX = 8;
    }

    /**
     * see libvlc_media_parse_flag_t
     */
    public static class Parse {
        public static final int ParseLocal   = 0;
        public static final int ParseNetwork = 0x01;
        public static final int FetchLocal   = 0x02;
        public static final int FetchNetwork = 0x04;
    }

    /**
     * see libvlc_media_track_t
     */
    public static abstract class Track {
        public static class Type {
            public static final int Unknown = -1;
            public static final int Audio = 0;
            public static final int Video = 1;
            public static final int Text = 2;
        }

        public final int type;
        public final String codec;
        public final String originalCodec;
        public final int id;
        public final int profile;
        public final int level;
        public final int bitrate;
        public final String language;
        public final String description;

        private Track(int type, String codec, String originalCodec, int id, int profile,
                int level, int bitrate, String language, String description) {
            this.type = type;
            this.codec = codec;
            this.originalCodec = originalCodec;
            this.id = id;
            this.profile = profile;
            this.level = level;
            this.bitrate = bitrate;
            this.language = language;
            this.description = description;
        }
    }

    /**
     * see libvlc_audio_track_t
     */
    public static class AudioTrack extends Track {
        public final int channels;
        public final int rate;

        private AudioTrack(String codec, String originalCodec, int id, int profile,
                int level, int bitrate, String language, String description,
                int channels, int rate) {
            super(Type.Audio, codec, originalCodec, id, profile, level, bitrate, language, description);
            this.channels = channels;
            this.rate = rate;
        }
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static Track createAudioTrackFromNative(String codec, String originalCodec, int id, int profile,
            int level, int bitrate, String language, String description,
            int channels, int rate) {
        return new AudioTrack(codec, originalCodec, id, profile,
                level, bitrate, language, description,
                channels, rate);
    }

    /**
     * see libvlc_video_track_t
     */
    public static class VideoTrack extends Track {
        public final int height;
        public final int width;
        public final int sarNum;
        public final int sarDen;
        public final int frameRateNum;
        public final int frameRateDen;

        private VideoTrack(String codec, String originalCodec, int id, int profile,
                int level, int bitrate, String language, String description,
                int height, int width, int sarNum, int sarDen, int frameRateNum, int frameRateDen) {
            super(Type.Video, codec, originalCodec, id, profile, level, bitrate, language, description);
            this.height = height;
            this.width = width;
            this.sarNum = sarNum;
            this.sarDen = sarDen;
            this.frameRateNum = frameRateNum;
            this.frameRateDen = frameRateDen;
        }
    }

    /* Used from JNI */
    private static Track createVideoTrackFromNative(String codec, String originalCodec, int id, int profile,
            int level, int bitrate, String language, String description,
            int height, int width, int sarNum, int sarDen, int frameRateNum, int frameRateDen) {
        return new VideoTrack(codec, originalCodec, id, profile,
                level, bitrate, language, description,
                height, width, sarNum, sarDen, frameRateNum, frameRateDen);
    }

    /**
     * see libvlc_subtitle_track_t
     */
    public static class SubtitleTrack extends Track {
        public final String encoding;

        private SubtitleTrack(String codec, String originalCodec, int id, int profile,
                int level, int bitrate, String language, String description,
                String encoding) {
            super(Type.Text, codec, originalCodec, id, profile, level, bitrate, language, description);
            this.encoding = encoding;
        }
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static Track createSubtitleTrackFromNative(String codec, String originalCodec, int id, int profile,
            int level, int bitrate, String language, String description,
            String encoding) {
        return new SubtitleTrack(codec, originalCodec, id, profile,
                level, bitrate, language, description,
                encoding);
    }

    private static final int PARSE_STATUS_INIT = 0x00;
    private static final int PARSE_STATUS_PARSING = 0x01;
    private static final int PARSE_STATUS_PARSED = 0x02;

    private Uri mUri = null;
    private MediaList mSubItems = null;
    private int mParseStatus = PARSE_STATUS_INIT;
    private String mNativeMetas[] = null;
    private Track mNativeTracks[] = null;
    private long mDuration;
    private int mState = State.NothingSpecial;
    private int mType = Type.Unknown;
    private boolean mCodecOptionSet = false;

    /**
     * Create a Media from libVLC and a local path starting with '/'.
     *
     * @param libVLC
     * @param path
     */
    public Media(LibVLC libVLC, String path) {
        nativeNewFromPath(libVLC, path);
        mUri = UriFromMrl(nativeGetMrl());
        mType = nativeGetType();
    }

    /**
     * Create a Media from libVLC and a Uri
     *
     * @param libVLC
     * @param uri
     */
    public Media(LibVLC libVLC, Uri uri) {
        nativeNewFromLocation(libVLC, locationFromUri(uri));
        mUri = uri;
        mType = nativeGetType();
    }

    /**
     * Create a Media from libVLC and a FileDescriptor
     *
     * @param libVLC
     * @param fd
     */
    public Media(LibVLC libVLC, FileDescriptor fd) {
        nativeNewFromFD(libVLC, fd);
        mUri = UriFromMrl(nativeGetMrl());
        mType = nativeGetType();
    }

    /**
     *
     * @param ml Should not be released and locked
     * @param index
     */
    protected Media(MediaList ml, int index) {
        if (ml == null || ml.isReleased())
            throw new IllegalArgumentException("MediaList is null or released");
        if (!ml.isLocked())
            throw new IllegalStateException("MediaList should be locked");
        nativeNewFromMediaList(ml, index);
        mUri = UriFromMrl(nativeGetMrl());
        mNativeMetas = nativeGetMetas();
        mType = nativeGetType();
    }

    private static final String URI_AUTHORIZED_CHARS = "!'()*";

    /**
     * VLC authorize only "-._~" in Mrl format, android Uri authorize "_-!.~'()*".
     * Therefore, decode the characters authorized by Android Uri when creating an Uri from VLC.
     * @param mrl
     * @return
     */
    private static Uri UriFromMrl(String mrl) {
        final char array[] = mrl.toCharArray();
        final StringBuilder sb = new StringBuilder(array.length);

        for (int i = 0; i < array.length; ++i) {
            final char c = array[i];
            if (c == '%') {
                if (array.length - i < 3)
                    throw new IllegalArgumentException("bad mrl format");

                final int hex = Integer.parseInt(new String(array, i + 1, 2), 16);
                if (URI_AUTHORIZED_CHARS.indexOf(hex) != -1) {
                    sb.append((char)hex);
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }

        return Uri.parse(sb.toString());
    }

    /**
     * VLC authorize only "-._~" in Mrl format, android Uri authorize "_-!.~'()*".
     * Therefore, encode the characters authorized by Android Uri when creating a mrl from an Uri.
     * @param uri
     * @return
     */
    private static String locationFromUri(Uri uri) {
        final char array[] = uri.toString().toCharArray();
        final StringBuilder sb = new StringBuilder(array.length * 2);

        for (final char c : array) {
            if (URI_AUTHORIZED_CHARS.indexOf(c) != -1)
                sb.append("%").append(Integer.toHexString(c));
            else
                sb.append(c);
        }

        return sb.toString();
    }

    public void setEventListener(EventListener listener) {
        super.setEventListener(listener);
    }

    @Override
    protected synchronized Event onEventNative(int eventType, long arg1, long arg2) {
        switch (eventType) {
        case Event.MetaChanged:
            // either we update all metas (if first call) or we update a specific meta
            if (mNativeMetas == null) {
                mNativeMetas = nativeGetMetas();
            } else {
                int id = (int) arg1;
                if (id >= 0 && id < Meta.MAX)
                    mNativeMetas[id] = nativeGetMeta(id);
            }
            break;
        case Event.DurationChanged:
            mDuration = nativeGetDuration();
            break;
        case Event.ParsedChanged:
            postParse();
            break;
        case Event.StateChanged:
            mState = nativeGetState();
            break;
        }
        return new Event(eventType);
    }

    /**
     * Get the MRL associated with the Media.
     */
    public synchronized Uri getUri() {
        return mUri;
    }

    /**
     * Get the duration of the media.
     */
    public synchronized long getDuration() {
        return mDuration;
    }

    /**
     * Get the state of the media.
     *
     * @see State
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Get the subItems MediaList associated with the Media. This Media should be alive (not released).
     *
     * @return subItems as a MediaList. This MediaList should be released with {@link #release()}.
     */
    public synchronized MediaList subItems() {
        if (isReleased())
            throw new IllegalStateException("Media is released");
        if (mSubItems == null)
            mSubItems = new MediaList(this);
        mSubItems.retain();
        return mSubItems;
    }

    private synchronized void postParse() {
        if (isReleased())
            throw new IllegalStateException("Media is released");
        // fetch if parsed and not fetched
        if ((mParseStatus & PARSE_STATUS_PARSING) != 0
                && (mParseStatus & PARSE_STATUS_PARSED) == 0) {
            mParseStatus &= ~PARSE_STATUS_PARSING;
            mParseStatus |= PARSE_STATUS_PARSED;
            mNativeTracks = nativeGetTracks();
            mNativeMetas = nativeGetMetas();
            if (mNativeMetas != null && mNativeMetas.length != Meta.MAX)
                throw new IllegalStateException("native metas size doesn't match");
            mDuration = nativeGetDuration();
            mState = nativeGetState();
            mType = nativeGetType();
        }
    }

    /**
     * Parse the media synchronously with a flag. This Media should be alive (not released).
     *
     * @param flags see {@link Parse}
     * @return true in case of success, false otherwise.
     */
    public synchronized boolean parse(int flags) {
        if ((mParseStatus & (PARSE_STATUS_PARSED|PARSE_STATUS_PARSING)) == 0) {
            mParseStatus |= PARSE_STATUS_PARSING;
            if (nativeParse(flags)) {
                postParse();
                return true;
            }
        }
        return false;
    }

    /**
     * Parse the media and local art synchronously. This Media should be alive (not released).
     *
     * @return true in case of success, false otherwise.
     */
    public synchronized boolean parse() {
        return parse(Parse.FetchLocal);
    }

    /**
     * Parse the media asynchronously with a flag. This Media should be alive (not released).
     *
     * To track when this is over you can listen to {@link Event#ParsedChanged}
     * event (only if this methods returned true).
     *
     * @param flags see {@link Parse}
     * @return true in case of success, false otherwise.
     */
    public synchronized boolean parseAsync(int flags) {
        if ((mParseStatus & (PARSE_STATUS_PARSED|PARSE_STATUS_PARSING)) == 0) {
            mParseStatus |= PARSE_STATUS_PARSING;
            return nativeParseAsync(flags);
        } else
            return false;
    }

    /**
     * Parse the media and local art asynchronously. This Media should be alive (not released).
     *
     * @see #parseAsync(int)
     */
    public synchronized boolean parseAsync() {
        return parseAsync(Parse.FetchLocal);
    }

    /**
     * Returns true if the media is parsed This Media should be alive (not released).
     */
    public synchronized boolean isParsed() {
        return (mParseStatus & PARSE_STATUS_PARSED) != 0;
    }

    /**
     * Get the type of the media
     *
     * @see {@link Type}
     */
    public synchronized int getType() {
        return mType;
    }

    /**
     * Get the Track count.
     */
    public synchronized int getTrackCount() {
        return mNativeTracks != null ? mNativeTracks.length : 0;
    }

    /**
     * Get a Track
     * The Track can be casted to {@link AudioTrack}, {@link VideoTrack} or {@link SubtitleTrack} in function of the {@link Track.Type}.
     *
     * @param idx
     * @return Track or null if not idx is not valid
     * @see #getTrackCount()
     */
    public synchronized Track getTrack(int idx) {
        if (mNativeTracks == null || idx < 0 || idx >= mNativeTracks.length)
            return null;
        return mNativeTracks[idx];
    }

    /**
     * Get a Meta.
     *
     * @param id see {@link Meta}
     * @return meta or null if not found
     */
    public synchronized String getMeta(int id) {
        if (id < 0 || id >= Meta.MAX)
            return null;

        return mNativeMetas != null ? mNativeMetas[id] : null;
    }

    private static String getMediaCodecModule() {
        return AndroidUtil.isLolliPopOrLater() ? "mediacodec_ndk" : "mediacodec_jni";
    }


    /**
     * Add or remove hw acceleration media options
     *
     * @param enabled
     * @param force force hw acceleration even for unknown devices
     */
    public void setHWDecoderEnabled(boolean enabled, boolean force) {
        final HWDecoderUtil.Decoder decoder = enabled ?
                HWDecoderUtil.getDecoderFromDevice() :
                HWDecoderUtil.Decoder.NONE;

        if (decoder == HWDecoderUtil.Decoder.NONE ||
                (decoder == HWDecoderUtil.Decoder.UNKNOWN && !force)) {
            addOption(":codec=all");
            return;
        }

        /*
         * Set higher caching values if using iomx decoding, since some omx
         * decoders have a very high latency, and if the preroll data isn't
         * enough to make the decoder output a frame, the playback timing gets
         * started too soon, and every decoded frame appears to be too late.
         * On Nexus One, the decoder latency seems to be 25 input packets
         * for 320x170 H.264, a few packets less on higher resolutions.
         * On Nexus S, the decoder latency seems to be about 7 packets.
         */
        addOption(":file-caching=1500");
        addOption(":network-caching=1500");

        final StringBuilder sb = new StringBuilder(":codec=");
        if (decoder == HWDecoderUtil.Decoder.MEDIACODEC)
            sb.append(getMediaCodecModule()).append(",");
        else if (decoder == HWDecoderUtil.Decoder.OMX)
            sb.append("iomx,");
        else
            sb.append(getMediaCodecModule()).append(",iomx,");
        sb.append("all");

        addOption(sb.toString());
    }

    /**
     * Enable HWDecoder options if not already set
     */
    protected void setDefaultMediaPlayerOptions() {
        if (!mCodecOptionSet)
            setHWDecoderEnabled(true, false);
    }

    /**
     * Add an option to this Media. This Media should be alive (not released).
     *
     * @param option ":option" or ":option=value"
     */
    public synchronized void addOption(String option) {
        if (!mCodecOptionSet && option.startsWith(":codec="))
            mCodecOptionSet = true;
        nativeAddOption(option);
    }

    @Override
    protected void onReleaseNative() {
        if (mSubItems != null)
            mSubItems.release();
        nativeRelease();
    }

    /* JNI */
    private native void nativeNewFromPath(LibVLC libVLC, String path);
    private native void nativeNewFromLocation(LibVLC libVLC, String location);
    private native void nativeNewFromFD(LibVLC libVLC, FileDescriptor fd);
    private native void nativeNewFromMediaList(MediaList ml, int index);
    private native void nativeRelease();
    private native boolean nativeParseAsync(int flags);
    private native boolean nativeParse(int flags);
    private native String nativeGetMrl();
    private native int nativeGetState();
    private native String nativeGetMeta(int id);
    private native String[] nativeGetMetas();
    private native Track[] nativeGetTracks();
    private native long nativeGetDuration();
    private native int nativeGetType();
    private native void nativeAddOption(String option);
}