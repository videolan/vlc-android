package org.videolan.libvlc.interfaces;

import android.net.Uri;

public interface IMedia extends IVLCObject<IMedia.Event> {
    class Event extends AbstractVLCEvent {
        public static final int MetaChanged = 0;
        public static final int SubItemAdded = 1;
        public static final int DurationChanged = 2;
        public static final int ParsedChanged = 3;
        //public static final int Freed                      = 4;
        public static final int StateChanged = 5;
        public static final int SubItemTreeAdded = 6;

        public Event(int type) {
            super(type);
        }

        public Event(int type, long arg1) {
            super(type, arg1);
        }

        public int getMetaId() {
            return (int) arg1;
        }

        /**
         * Get the ParsedStatus in case of {@link Event#ParsedChanged} event
         *
         * @return {@link ParsedStatus}
         */
        public int getParsedStatus() {
            return (int) arg1;
        }
    }

    interface EventListener extends AbstractVLCEvent.Listener<Event> {
    }

    /**
     * libvlc_media_type_t
     */
    class Type {
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
    class Meta {
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
    class State {
        public static final int NothingSpecial = 0;
        public static final int Opening = 1;
        /* deprecated public static final int Buffering = 2; */
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
    class Parse {
        public static final int ParseLocal = 0;
        public static final int ParseNetwork = 0x01;
        public static final int FetchLocal = 0x02;
        public static final int FetchNetwork = 0x04;
        public static final int DoInteract = 0x08;
    }

    /*
     * see libvlc_media_parsed_status_t
     */
    class ParsedStatus {
        public static final int Skipped = 1;
        public static final int Failed = 2;
        public static final int Timeout = 3;
        public static final int Done = 4;
    }

    /**
     * see libvlc_media_track_t
     */
    abstract class Track {
        public static class Type {
            public static final int Unknown = -1;
            public static final int Audio = 0;
            public static final int Video = 1;
            public static final int Text = 2;
        }

        public final int type;
        public final String codec;
        public final String originalCodec;
        public final int fourcc;
        public final int id;
        public final int profile;
        public final int level;
        public final int bitrate;
        public final String language;
        public final String description;

        protected Track(int type, String codec, String originalCodec, int fourcc, int id, int profile,
                        int level, int bitrate, String language, String description) {
            this.type = type;
            this.codec = codec;
            this.originalCodec = originalCodec;
            this.fourcc = fourcc;
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
    class AudioTrack extends Track {
        public final int channels;
        public final int rate;

        public AudioTrack(String codec, String originalCodec, int fourcc, int id, int profile,
                          int level, int bitrate, String language, String description,
                          int channels, int rate) {
            super(Type.Audio, codec, originalCodec, fourcc, id, profile, level, bitrate, language, description);
            this.channels = channels;
            this.rate = rate;
        }
    }

    /**
     * see libvlc_video_track_t
     */
    class VideoTrack extends Track {
        public static final class Orientation {
            /**
             * Top line represents top, left column left
             */
            public static final int TopLeft = 0;
            /**
             * Flipped horizontally
             */
            public static final int TopRight = 1;
            /**
             * Flipped vertically
             */
            public static final int BottomLeft = 2;
            /**
             * Rotated 180 degrees
             */
            public static final int BottomRight = 3;
            /**
             * Transposed
             */
            public static final int LeftTop = 4;
            /**
             * Rotated 90 degrees clockwise (or 270 anti-clockwise)
             */
            public static final int LeftBottom = 5;
            /**
             * Rotated 90 degrees anti-clockwise
             */
            public static final int RightTop = 6;
            /**
             * Anti-transposed
             */
            public static final int RightBottom = 7;
        }

        public static final class Projection {
            public static final int Rectangular = 0;
            /**
             * 360 spherical
             */
            public static final int EquiRectangular = 1;
            public static final int CubemapLayoutStandard = 0x100;
        }

        public final int height;
        public final int width;
        public final int sarNum;
        public final int sarDen;
        public final int frameRateNum;
        public final int frameRateDen;
        public final int orientation;
        public final int projection;

        public VideoTrack(String codec, String originalCodec, int fourcc, int id, int profile,
                          int level, int bitrate, String language, String description,
                          int height, int width, int sarNum, int sarDen, int frameRateNum, int frameRateDen,
                          int orientation, int projection) {
            super(Type.Video, codec, originalCodec, fourcc, id, profile, level, bitrate, language, description);
            this.height = height;
            this.width = width;
            this.sarNum = sarNum;
            this.sarDen = sarDen;
            this.frameRateNum = frameRateNum;
            this.frameRateDen = frameRateDen;
            this.orientation = orientation;
            this.projection = projection;
        }
    }

    /**
     * see libvlc_subtitle_track_t
     */
    class SubtitleTrack extends Track {
        public final String encoding;

        public SubtitleTrack(String codec, String originalCodec, int fourcc, int id, int profile,
                             int level, int bitrate, String language, String description,
                             String encoding) {
            super(Type.Text, codec, originalCodec, fourcc, id, profile, level, bitrate, language, description);
            this.encoding = encoding;
        }
    }

    /**
     * see libvlc_subtitle_track_t
     */
    class UnknownTrack extends Track {
        public UnknownTrack(String codec, String originalCodec, int fourcc, int id, int profile,
                            int level, int bitrate, String language, String description) {
            super(Type.Unknown, codec, originalCodec, fourcc, id, profile, level, bitrate, language, description);
        }
    }

    /**
     * see libvlc_media_slave_t
     */
    class Slave {
        public static class Type {
            public static final int Subtitle = 0;
            public static final int Audio = 1;
        }

        /**
         * @see Type
         */
        public final int type;
        /**
         * From 0 (low priority) to 4 (high priority)
         */
        public final int priority;
        public final String uri;

        public Slave(int type, int priority, String uri) {
            this.type = type;
            this.priority = priority;
            this.uri = uri;
        }
    }

    /**
     * see libvlc_media_stats_t
     */
    class Stats {

        public final int readBytes;
        public final float inputBitrate;
        public final int demuxReadBytes;
        public final float demuxBitrate;
        public final int demuxCorrupted;
        public final int demuxDiscontinuity;
        public final int decodedVideo;
        public final int decodedAudio;
        public final int displayedPictures;
        public final int lostPictures;
        public final int playedAbuffers;
        public final int lostAbuffers;
        public final int sentPackets;
        public final int sentBytes;
        public final float sendBitrate;

        public Stats(int readBytes, float inputBitrate, int demuxReadBytes,
                     float demuxBitrate, int demuxCorrupted,
                     int demuxDiscontinuity, int decodedVideo, int decodedAudio,
                     int displayedPictures, int lostPictures, int playedAbuffers,
                     int lostAbuffers, int sentPackets, int sentBytes,
                     float sendBitrate) {
            this.readBytes = readBytes;
            this.inputBitrate = inputBitrate;
            this.demuxReadBytes = demuxReadBytes;
            this.demuxBitrate = demuxBitrate;
            this.demuxCorrupted = demuxCorrupted;
            this.demuxDiscontinuity = demuxDiscontinuity;
            this.decodedVideo = decodedVideo;
            this.decodedAudio = decodedAudio;
            this.displayedPictures = displayedPictures;
            this.lostPictures = lostPictures;
            this.playedAbuffers = playedAbuffers;
            this.lostAbuffers = lostAbuffers;
            this.sentPackets = sentPackets;
            this.sentBytes = sentBytes;
            this.sendBitrate = sendBitrate;
        }
    }

    long getDuration();

    int getState();

    IMediaList subItems();

    boolean parse(int flags);

    boolean parse();

    boolean parseAsync(int flags, int timeout);

    boolean parseAsync(int flags);

    boolean parseAsync();

    int getType();

    int getTrackCount();

    Track getTrack(int idx);

    String getMeta(int id);

    String getMeta(int id, boolean force);

    void setHWDecoderEnabled(boolean enabled, boolean force);

    void setEventListener(EventListener listener);

    void addOption(String option);

    void addSlave(Slave slave);

    void clearSlaves();

    Slave[] getSlaves();

    Uri getUri();

    boolean isParsed();

    Stats getStats();

    /**
     * Enable HWDecoder options if not already set
     */
    void setDefaultMediaPlayerOptions();
}