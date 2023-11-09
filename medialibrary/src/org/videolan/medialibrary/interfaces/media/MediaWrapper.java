/*
 *****************************************************************************
 * MediaWrapper.java
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.medialibrary.interfaces.media;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.Extensions;
import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.VlcMigrationHelper;

import java.util.List;
import java.util.Locale;

public abstract class MediaWrapper extends MediaLibraryItem implements Parcelable {
    public final static int TYPE_ALL = -1;
    public final static int TYPE_VIDEO = 0;
    public final static int TYPE_AUDIO = 1;
    public final static int TYPE_GROUP = 2;
    public final static int TYPE_DIR = 3;
    public final static int TYPE_SUBTITLE = 4;
    public final static int TYPE_PLAYLIST = 5;
    public final static int TYPE_STREAM = 6;

    public final static int MEDIA_VIDEO = 0x01;
    public final static int MEDIA_NO_HWACCEL = 0x02;
    public final static int MEDIA_PAUSED = 0x4;
    public final static int MEDIA_FORCE_AUDIO = 0x8;
    public final static int MEDIA_BENCHMARK = 0x10;
    public final static int MEDIA_FROM_START = 0x20;

    //MetaData flags
    public final static int META_RATING = 1;
    //Playback
    public final static int META_SPEED = 51;
    public final static int META_TITLE = 52;
    public final static int META_CHAPTER = 53;
    public final static int META_PROGRAM = 54;
    //video
    public final static int META_VIDEOTRACK = 100;
    public final static int META_ASPECT_RATIO = 101;
    public final static int META_ZOOM = 102;
    public final static int META_CROP = 103;
    public final static int META_DEINTERLACE = 104;
    public final static int META_VIDEOFILTER = 105;
    public final static int META_AB_REPEAT_START = 106;
    public final static int META_AB_REPEAT_STOP = 107;
    //Audio
    public final static int META_AUDIOTRACK = 150;
    public final static int META_GAIN = 151;
    public final static int META_AUDIODELAY = 152;
    //Spu
    public final static int META_SUBTITLE_TRACK = 200;
    public final static int META_SUBTITLE_DELAY = 201;
    //Various
    public final static int META_APPLICATION_SPECIFIC = 250;
    public final static int META_METADATA_RETRIEVED = 251;

    // threshold lentgh between song and podcast ep, set to 15 minutes
    protected static final long PODCAST_THRESHOLD = 900000L;
    protected static final long PODCAST_ABSOLUTE = 3600000L;

    protected String mDisplayTitle;
    protected String mArtist;
    protected String mGenre;
    protected String mCopyright;
    protected String mAlbum;
    protected int mTrackNumber;
    protected int mDiscNumber;
    protected String mAlbumArtist;
    protected String mRating;
    protected String mDate;
    protected int mReleaseYear;
    protected String mSettings;
    protected String mNowPlaying;
    protected String mPublisher;
    protected String mEncodedBy;
    protected String mTrackID;
    protected String mArtworkURL;
    protected boolean mThumbnailGenerated;
    private boolean mIsPresent = true;
    protected long mInsertionDate;

    protected final Uri mUri;
    protected String mFilename;
    protected long mTime = -1;
    protected float mPosition = -1;
    protected long mDisplayTime = 0;
    /* -1 is a valid track (Disabled) */
    protected int mAudioTrack = -2;
    protected int mSpuTrack = -2;
    protected long mLength = 0;
    protected int mType;
    protected int mWidth = 0;
    protected int mHeight = 0;
    protected Bitmap mPicture;
    protected boolean mIsPictureParsed;
    protected int mFlags = 0;
    protected long mLastModified = 0L;
    protected IMedia.Slave[] mSlaves = null;

    protected long mSeen = 0L;

    public abstract void rename(String name);
    public abstract long getMetaLong(int metaDataType);
    public abstract String getMetaString(int metaDataType);
    public abstract boolean setLongMeta(int metaDataType, long metaDataValue);
    public abstract boolean setStringMeta(int metaDataType, String metaDataValue);
    public abstract void setThumbnail(String mrl);
    public abstract boolean setPlayCount(long playCount);
    public abstract long getPlayCount();
    public abstract void removeThumbnail();
    public abstract void requestThumbnail(int width, float position);
    public abstract void requestBanner(int width, float position);
    public abstract boolean removeFromHistory();
    public abstract Bookmark[] getBookmarks();
    public abstract Bookmark addBookmark(long time);
    public abstract boolean removeBookmark(long time);
    public abstract boolean removeAllBookmarks();
    public abstract boolean markAsPlayed();

    /**
     * Create a new MediaWrapper
     *
     * @param mrl Should not be null.
     */
    public MediaWrapper(long id, String mrl, long time, float position, long length, int type, String title,
                        String filename, String artist, String genre, String album, String albumArtist,
                        int width, int height, String artworkURL, int audio, int spu, int trackNumber,
                        int discNumber, long lastModified, long seen, boolean isThumbnailGenerated,
                        boolean isFavorite, int releaseDate, boolean isPresent, long insertionDate) {
        super();
        if (TextUtils.isEmpty(mrl)) throw new IllegalArgumentException("uri was empty");

        mUri = Uri.parse(manageVLCMrl(mrl));
        mId = id;
        mFilename = filename;
        mReleaseYear = releaseDate;
        mIsPresent = isPresent;
        init(time, position, length, type, null, title, artist, genre, album, albumArtist, width, height,
                artworkURL != null ? VLCUtil.UriFromMrl(artworkURL).getPath() : null, audio, spu,
                trackNumber, discNumber, lastModified, seen, isPresent, null, isFavorite, insertionDate);
        final StringBuilder sb = new StringBuilder();
        if (type == TYPE_AUDIO) {
            boolean hasArtistMeta = !TextUtils.isEmpty(artist);
            boolean hasAlbumMeta = !TextUtils.isEmpty(album);
            if (hasArtistMeta) {
                sb.append(artist);
                if (hasAlbumMeta)
                    sb.append(" - ");
            }
            if (hasAlbumMeta)
                sb.append(album);
        } else if (type == TYPE_VIDEO) {
            Tools.setMediaDescription(this);
        }

        if (sb.length() > 0)
            mDescription = sb.toString();
        defineType();
        mThumbnailGenerated = isThumbnailGenerated;
        mFavorite = isFavorite;
    }

    private String manageVLCMrl(String mrl) {
        if (!mrl.isEmpty() && mrl.charAt(0) == '/') {
            mrl = "file://" + mrl;
        } else if (mrl.toLowerCase().startsWith("vlc://")) {
            mrl = mrl.substring(6);
            if (Uri.parse(mrl).getScheme() == null) {
                mrl = "http://" + mrl;
            }
        }
        return mrl;
    }

    /**
     * Create a new MediaWrapper
     *
     * @param uri Should not be null.
     */
    public MediaWrapper(Uri uri) {
        super();
        if (uri == null) throw new NullPointerException("uri was null");

        uri = Uri.parse(manageVLCMrl(uri.toString()));
        mUri = uri;
        init(null);
    }

    /**
     * Create a new MediaWrapper
     *
     * @param media should be parsed and not NULL
     */
    public MediaWrapper(IMedia media) {
        super();
        if (media == null)
            throw new NullPointerException("media was null");

        mUri = Uri.parse(VLCUtil.encodeVLCUri(media.getUri()));
        init(media);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MediaLibraryItem) || ((MediaLibraryItem) obj).getItemType() != TYPE_MEDIA)
            return false;
        return equals((MediaWrapper) obj);
    }

    public boolean equals(MediaWrapper obj) {
        long otherId = obj.getId();
        if (otherId != 0L && getId() != 0L && otherId == getId()) return true;
        final Uri otherUri = obj.getUri();
        return !(mUri == null || otherUri == null) && (mUri == otherUri || mUri.equals(otherUri));
    }

    private void init(IMedia media) {
        mType = TYPE_ALL;

        if (media != null) {
            if (media.isParsed()) {
                mLength = media.getDuration();

                List<IMedia.Track> tracks = VlcMigrationHelper.getMediaTracks(media);
                for (int i = 0; i < tracks.size(); ++i) {
                    final IMedia.Track track = tracks.get(i);
                    if (track.type == Media.Track.Type.Video) {
                        final IMedia.VideoTrack videoTrack = (IMedia.VideoTrack) track;
                        mType = TYPE_VIDEO;
                        mWidth = videoTrack.width;
                        mHeight = videoTrack.height;
                    } else if (mType == TYPE_ALL && track.type == Media.Track.Type.Audio) {
                        mType = TYPE_AUDIO;
                    }
                }
            }
            updateMeta(media);
            if (mType == TYPE_ALL)
                switch (media.getType()) {
                    case Media.Type.Directory:
                        mType = TYPE_DIR;
                        break;
                    case Media.Type.Playlist:
                        mType = TYPE_PLAYLIST;
                        break;
                }
            mSlaves = media.getSlaves();
        }
        defineType();
    }

    private void defineType() {
        if (mType != TYPE_ALL)
            return;

        String fileExt = null, filename = mUri.getLastPathSegment();
        if (TextUtils.isEmpty(filename))
            filename = mTitle;
        if (TextUtils.isEmpty(filename))
            return;
        int index = filename.indexOf('?');
        if (index != -1)
            filename = filename.substring(0, index);

        index = filename.lastIndexOf(".");

        if (index != -1)
            fileExt = filename.substring(index).toLowerCase(Locale.ENGLISH);

        if (!TextUtils.isEmpty(fileExt)) {
            if (Extensions.VIDEO.contains(fileExt)) {
                mType = TYPE_VIDEO;
            } else if (Extensions.AUDIO.contains(fileExt)) {
                mType = TYPE_AUDIO;
            } else if (Extensions.SUBTITLES.contains(fileExt)) {
                mType = TYPE_SUBTITLE;
            } else if (Extensions.PLAYLIST.contains(fileExt)) {
                mType = TYPE_PLAYLIST;
            }
        }
    }

    private void init(long time, float position, long length, int type,
                      Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                      int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified,
                      long seen, boolean isPresent, IMedia.Slave[] slaves, boolean isFavorite, long insertionDate) {
        mFilename = null;
        mTime = time;
        mPosition = position;
        mDisplayTime = time;
        mAudioTrack = audio;
        mSpuTrack = spu;
        mLength = length;
        mType = type;
        mPicture = picture;
        mWidth = width;
        mHeight = height;
        mInsertionDate = insertionDate;

        mTitle = title != null ? title.trim() : null;
        mArtist = artist != null ? artist.trim() : null;
        mGenre = genre != null ? genre.trim() : null;
        mAlbum = album != null ? album.trim() : null;
        mAlbumArtist = albumArtist != null ? albumArtist.trim() : null;
        mArtworkURL = artworkURL;
        mTrackNumber = trackNumber;
        mDiscNumber = discNumber;
        mLastModified = lastModified;
        mSeen = seen;
        mSlaves = slaves;
        mIsPresent = isPresent;
        mFavorite = isFavorite;
    }

    public MediaWrapper(Uri uri, long time, float position, long length, int type,
                        Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                        int width, int height, String artworkURL, int audio, int spu, int trackNumber,
                        int discNumber, long lastModified, long seen, boolean isFavorite, long insertionDate) {
        mUri = uri;
        init(time, position, length, type, picture, title, artist, genre, album, albumArtist,
                width, height, artworkURL, audio, spu, trackNumber, discNumber, lastModified, seen, true, null, isFavorite, insertionDate);
    }

    @Override
    public MediaWrapper[] getTracks() {
        return new MediaWrapper[]{this};
    }

    @Override
    public int getTracksCount() {
        return 1;
    }

    @Override
    public int getItemType() {
        return TYPE_MEDIA;
    }

    @Override
    public long getId() {
        return mId;
    }

    public String getLocation() {
        return mUri.toString();
    }

    public Uri getUri() {
        return mUri;
    }

    private static String getMetaId(IMedia media, String defaultMeta, int id, boolean trim) {
        String meta = media.getMeta(id, true);
        return meta != null ? trim ? meta.trim() : meta : defaultMeta;
    }

    /**
     * This returns the updated title meta for the media. Though it checks that the title media in
     * libvlc isn't just the filename as this would mean that libvlc doesn't have any title meta.
     * This is to ensure that medialibrary titles don't get overriden by default filename.
     * An example of this would be for a stream in a m3u8 file with and #EXTINF: title parameter.
     * libvlc won't have access to this meta, and if the stream doesn't send updated metas, then it
     * will be replaced with the filename even though the medialibrary has a correct title.
     * This won't work for network shares as they will return null titles, so
     * they are exempted as directories
     * This also won't work for media streams (ex.: udp://@... multicast streams) as libvlc will
     * return the url (ex.: udp://...) as title, so on streams we look for :// in libvlc title and
     * ignore those title updates.     
     * @param media media to update
     * @return title string
     */
    private String getMetaTitle(IMedia media) {
        String libvlcTitle = getMetaId(media, mTitle, Media.Meta.Title, true);
        String fileName = getFileName();
        if (media.getType() == Media.Type.Directory ||
                (!TextUtils.isEmpty(libvlcTitle) &&
                        !libvlcTitle.equals(fileName) &&
                        !(media.getType() == Media.Type.Stream && libvlcTitle.toLowerCase().contains("://"))
                )
        )
            return libvlcTitle;
        return getTitle();
    }

    private void  updateMeta(IMedia media) {
        mTitle = getMetaTitle(media);
        mArtist = getMetaId(media, mArtist, Media.Meta.Artist, true);
        mAlbum = getMetaId(media, mAlbum, Media.Meta.Album, true);
        mGenre = getMetaId(media, mGenre, Media.Meta.Genre, true);
        mAlbumArtist = getMetaId(media, mAlbumArtist, Media.Meta.AlbumArtist, true);
        mArtworkURL = getMetaId(media, mArtworkURL, Media.Meta.ArtworkURL, false);
        mNowPlaying = getMetaId(media, mNowPlaying, Media.Meta.NowPlaying, false);
        final String trackNumber = getMetaId(media, null, Media.Meta.TrackNumber, false);
        if (!TextUtils.isEmpty(trackNumber)) {
            try {
                mTrackNumber = Integer.parseInt(trackNumber);
            } catch (NumberFormatException ignored) {}
        }
        final String discNumber = getMetaId(media, null, Media.Meta.DiscNumber, false);
        if (!TextUtils.isEmpty(discNumber)) {
            try {
                mDiscNumber = Integer.parseInt(discNumber);
            } catch (NumberFormatException ignored) {}
        }
    }

    public void updateMeta(MediaPlayer mediaPlayer) {
        if ((!TextUtils.isEmpty(mTitle) && TextUtils.isEmpty(mDisplayTitle)) || (mDisplayTitle != null && !mDisplayTitle.equals(mTitle)))
            mDisplayTitle = mTitle;
        final IMedia media = mediaPlayer.getMedia();
        if (media == null)
            return;
        updateMeta(media);
        media.release();
    }

    public String getFileName() {
        if (mFilename == null) {
            if (mUri == null)
                mFilename = "";
            else if (mUri.getLastPathSegment() != null) {
                mFilename = mUri.getLastPathSegment();
            } else {
                mFilename = mUri.toString();
            }
        }
        return mFilename;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long time) {
        mTime = time;
    }

    public float getPosition() {
        return mPosition;
    }

    public void setPosition(float mPosition) {
        this.mPosition = mPosition;
    }

    public long getDisplayTime() {
        return mDisplayTime;
    }

    public void setDisplayTime(long time) {
        mDisplayTime = time;
    }

    public int getAudioTrack() {
        return mAudioTrack;
    }

    public void setAudioTrack(int track) {
        mAudioTrack = track;
    }

    public int getSpuTrack() {
        return mSpuTrack;
    }

    public void setSpuTrack(int track) {
        mSpuTrack = track;
    }

    public long getLength() {
        return mLength;
    }

    public void setLength(long length) {
        mLength = length;
    }

    public int getType() {
        return mType;
    }

    public boolean isPodcast() {
        return mType == TYPE_AUDIO && (mLength > PODCAST_ABSOLUTE
                || TextUtils.isEmpty(mAlbum) && mLength > PODCAST_THRESHOLD
                || "podcast".equalsIgnoreCase(mGenre)
                || "audiobooks".equalsIgnoreCase(mGenre)
                || "audiobook".equalsIgnoreCase(mGenre)
                || "speech".equalsIgnoreCase(mGenre)
                || "vocal".equalsIgnoreCase(mGenre));
    }

    public void setType(int type) {
        mType = type;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /**
     * Returns the raw picture object. Likely to be NULL in VLC for Android
     * due to lazy-loading.
     *
     * @return The raw picture or NULL
     */
    public Bitmap getPicture() {
        return mPicture;
    }

    /**
     * Sets the raw picture object.
     *
     * @param p Bitmap picture
     */
    public void setPicture(Bitmap p) {
        mPicture = p;
    }

    public boolean isPictureParsed() {
        return mIsPictureParsed;
    }

    public void setPictureParsed(boolean isParsed) {
        mIsPictureParsed = isParsed;
    }

    public void setDisplayTitle(String title) {
        mDisplayTitle = title;
    }

    @Override
    public void setTitle(String title) {
        mTitle = title;
    }


    public void setArtist(String artist) {
        mArtist = artist;
    }

    @Override
    public String getTitle() {
        String displayTitle = mDisplayTitle;
        if (!TextUtils.isEmpty(displayTitle))
            return displayTitle;
        String title = mTitle;
        if (!TextUtils.isEmpty(title))
            return title;
        String fileName = getFileName();
        if (fileName == null)
            return "";
        int end = fileName.lastIndexOf(".");
        if (end <= 0)
            return fileName;
        return fileName.substring(0, end);
    }

    public long getInsertionDate() {
        return mInsertionDate;
    }

    public String getReferenceArtist() {
        return mAlbumArtist == null ? mArtist : mAlbumArtist;
    }

    public String getArtist() {
        return mArtist;
    }

    public Boolean isArtistUnknown() {
        return mArtist == null;
    }

    public String getGenre() {
        if (mGenre == null)
            return null;
        else if (mGenre.length() > 1)/* Make genres case insensitive via normalisation */
            return Character.toUpperCase(mGenre.charAt(0)) + mGenre.substring(1).toLowerCase(Locale.getDefault());
        else
            return mGenre;
    }

    public String getCopyright() {
        return mCopyright;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public String getAlbumArtist() {
        return mAlbumArtist;
    }

    public Boolean isAlbumUnknown() {
        return mAlbum == null;
    }

    public int getTrackNumber() {
        return mTrackNumber;
    }

    public int getDiscNumber() {
        return mDiscNumber;
    }

    public String getRating() {
        return mRating;
    }

    public String getDate() {
        return mDate;
    }

    public int getReleaseYear() {
        return mReleaseYear;
    }

    public String getSettings() {
        return mSettings;
    }

    public String getNowPlaying() {
        return mNowPlaying;
    }

    public String getPublisher() {
        return mPublisher;
    }

    public String getEncodedBy() {
        return mEncodedBy;
    }

    public String getTrackID() {
        return mTrackID;
    }

    public String getArtworkURL() {
        return mArtworkURL;
    }

    public boolean isThumbnailGenerated() {
        return mThumbnailGenerated;
    }

    @Override
    public String getArtworkMrl() {
        return mArtworkURL;
    }

    public void setArtworkURL(String url) {
        mArtworkURL = url;
    }

    public long getLastModified() {
        return mLastModified;
    }

    public void setLastModified(long mLastModified) {
        this.mLastModified = mLastModified;
    }

    public long getSeen() {
        return mSeen;
    }

    public void setSeen(long seen) {
        mSeen = seen;
    }

    public void addFlags(int flags) {
        mFlags |= flags;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    public int getFlags() {
        return mFlags;
    }

    public boolean hasFlag(int flag) {
        return (mFlags & flag) != 0;
    }

    public void removeFlags(int flags) {
        mFlags &= ~flags;
    }

    public boolean isPresent() {
        return mIsPresent;
    }

    @Nullable
    public IMedia.Slave[] getSlaves() {
        return mSlaves;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public MediaWrapper(Parcel in) {
        super(in);
        mUri = in.readParcelable(Uri.class.getClassLoader());
        init(in.readLong(),
                in.readFloat(),
                in.readLong(),
                in.readInt(),
                (Bitmap) in.readParcelable(Bitmap.class.getClassLoader()),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readLong(),
                in.readLong(),
                in.readInt() == 1,
                in.createTypedArray(PSlave.CREATOR),
                in.readInt() == 1,
                in.readLong());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(mUri, flags);
        dest.writeLong(getTime());
        dest.writeFloat(getPosition());
        dest.writeLong(getLength());
        dest.writeInt(getType());
        dest.writeParcelable(getPicture(), flags);
        dest.writeString(getTitle());
        dest.writeString(getArtist());
        dest.writeString(getGenre());
        dest.writeString(getAlbum());
        dest.writeString(getAlbumArtist());
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        dest.writeString(getArtworkURL());
        dest.writeInt(getAudioTrack());
        dest.writeInt(getSpuTrack());
        dest.writeInt(getTrackNumber());
        dest.writeInt(getDiscNumber());
        dest.writeLong(getLastModified());
        dest.writeLong(getSeen());
        dest.writeInt(isPresent() ? 1 : 0);

        if (mSlaves != null) {
            PSlave[] pslaves = new PSlave[mSlaves.length];
            for (int i = 0; i < mSlaves.length; ++i) {
                pslaves[i] = new PSlave(mSlaves[i]);
            }
            dest.writeTypedArray(pslaves, flags);
        } else
            dest.writeTypedArray(null, flags);
        dest.writeInt(mFavorite ? 1 : 0);
        dest.writeLong(mInsertionDate);
    }

    public static final Parcelable.Creator<MediaWrapper> CREATOR = new Parcelable.Creator<MediaWrapper>() {
        @Override
        public MediaWrapper createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractMediaWrapper(in);
        }

        @Override
        public MediaWrapper[] newArray(int size) {
            return new MediaWrapper[size];
        }
    };

    protected static class PSlave extends IMedia.Slave implements Parcelable {

        PSlave(IMedia.Slave slave) {
            super(slave.type, slave.priority, slave.uri);
        }

        PSlave(Parcel in) {
            super(in.readInt(), in.readInt(), in.readString());
        }

        public static final Creator<PSlave> CREATOR = new Creator<PSlave>() {
            @Override
            public PSlave createFromParcel(Parcel in) {
                return new PSlave(in);
            }

            @Override
            public PSlave[] newArray(int size) {
                return new PSlave[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(type);
            parcel.writeInt(priority);
            parcel.writeString(uri);
        }
    }
}
