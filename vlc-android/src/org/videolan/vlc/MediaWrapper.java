/*****************************************************************************
 * MediaWrapper.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
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

package org.videolan.vlc;

import java.util.Locale;

import org.videolan.libvlc.util.Extensions;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.Media.VideoTrack;
import org.videolan.libvlc.Media.Meta;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

public class MediaWrapper implements Parcelable {
    public final static String TAG = "VLC/MediaWrapper";

    public final static int TYPE_ALL = -1;
    public final static int TYPE_VIDEO = 0;
    public final static int TYPE_AUDIO = 1;
    public final static int TYPE_GROUP = 2;
    public final static int TYPE_DIR = 3;
    public final static int TYPE_SUBTITLE = 4;

    protected String mTitle;
    private String mArtist;
    private String mGenre;
    private String mCopyright;
    private String mAlbum;
    private int mTrackNumber;
    private int mDiscNumber;
    private String mAlbumArtist;
    private String mDescription;
    private String mRating;
    private String mDate;
    private String mSettings;
    private String mNowPlaying;
    private String mPublisher;
    private String mEncodedBy;
    private String mTrackID;
    private String mArtworkURL;

    private final String mLocation;
    private String mFilename;
    private long mTime = 0;
    private int mAudioTrack = -1;
    private int mSpuTrack = -2;
    private long mLength = 0;
    private int mType;
    private int mWidth = 0;
    private int mHeight = 0;
    private Bitmap mPicture;
    private boolean mIsPictureParsed;
    private int mFlags = 0;

    /**
     * Create a new MediaWrapper
     * @param mrl Should not be null.
     */
    public MediaWrapper(String mrl) {
        if (mrl == null)
            throw new NullPointerException("mrl was null");

        mLocation = mrl;
        init(null);
    }

    /**
     * Create a new MediaWrapper
     * @param media should be parsed and not NULL
     */
    public MediaWrapper(Media media) {
        if (media == null)
            throw new NullPointerException("media was null");

        mLocation = media.getMrl();
        init(media);
    }

    private void init(Media media) {
        mType = TYPE_ALL;

        if (media != null) {
            if (media.isParsed()) {
                mLength = media.getDuration();

                for (int i = 0; i < media.getTrackCount(); ++i) {
                    final Media.Track track = media.getTrack(i);
                    if (track == null)
                        continue;
                    if (track.type == Media.Track.Type.Video) {
                        final Media.VideoTrack videoTrack = (VideoTrack) track;
                        mType = TYPE_VIDEO;
                        mWidth = videoTrack.width;
                        mHeight = videoTrack.height;
                    } else if (mType == TYPE_ALL && track.type == Media.Track.Type.Audio){
                        mType = TYPE_AUDIO;
                    }
                }
            }
            updateMeta(media);
        }

        if (mType == TYPE_ALL) {
            int dotIndex = mLocation.lastIndexOf(".");
            if (dotIndex != -1) {
                String fileExt = mLocation.substring(dotIndex).toLowerCase(Locale.ENGLISH);
                if( Extensions.VIDEO.contains(fileExt) ) {
                    mType = TYPE_VIDEO;
                } else if (Extensions.AUDIO.contains(fileExt)) {
                    mType = TYPE_AUDIO;
                } else if (Extensions.SUBTITLES.contains(fileExt)) {
                    mType = TYPE_SUBTITLE;
                }
            }
            if (mType == TYPE_ALL) {
                /*
                 * TODO: add something in libvlc to retrieve media type
                 * In the meantime, assume media is a directory.
                 */
                mType = TYPE_DIR;
            }
        }
    }

    private void init(long time, long length, int type,
                      Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                      int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber) {
        mFilename = null;
        mTime = time;
        mAudioTrack = audio;
        mSpuTrack = spu;
        mLength = length;
        mType = type;
        mPicture = picture;
        mWidth = width;
        mHeight = height;

        mTitle = title;
        mArtist = artist;
        mGenre = genre;
        mAlbum = album;
        mAlbumArtist = albumArtist;
        mArtworkURL = artworkURL;
        mTrackNumber = trackNumber;
        mDiscNumber = discNumber;
    }

    public MediaWrapper(String location, long time, long length, int type,
                 Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                 int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber) {
        mLocation = location;
        init(time, length, type, picture, title, artist, genre, album, albumArtist,
             width, height, artworkURL, audio, spu, trackNumber, discNumber);
    }

    public String getLocation() {
        return mLocation;
    }

    private static String getMetaId(Media media, int id, boolean trim) {
        String meta = media.getMeta(id);
        return meta != null ? trim ? meta.trim() : meta : null;
    }

    public void updateMeta(Media media) {
        if (!media.isParsed())
            return;
        mTitle = getMetaId(media, Meta.Title, true);
        mArtist = getMetaId(media, Meta.Artist, true);
        mAlbum = getMetaId(media, Meta.Album, true);
        mGenre = getMetaId(media, Meta.Genre, true);
        mAlbumArtist = getMetaId(media, Meta.AlbumArtist, true);
        mArtworkURL = getMetaId(media, Meta.ArtworkURL, false);
        mNowPlaying = getMetaId(media, Meta.NowPlaying, false);
        final String trackNumber = getMetaId(media, Meta.TrackNumber, false);
        if (!TextUtils.isEmpty(trackNumber)) {
            try {
                mTrackNumber = Integer.parseInt(trackNumber);
            } catch (NumberFormatException ignored) {}
        }
        final String discNumber = getMetaId(media, Meta.DiscNumber, false);
        if (!TextUtils.isEmpty(discNumber)) {
            try {
                mDiscNumber = Integer.parseInt(discNumber);
            } catch (NumberFormatException ignored) {}
        }
        Log.d(TAG, "Title " + mTitle);
        Log.d(TAG, "Artist " + mArtist);
        Log.d(TAG, "Genre " + mGenre);
        Log.d(TAG, "Album " + mAlbum);
    }

    /*
     * XXX to remove
     */
    public void updateMeta(LibVLC libVLC) {
        mTitle = libVLC.getMeta(Meta.Title);
        mArtist = libVLC.getMeta(Meta.Artist);
        mGenre = libVLC.getMeta(Meta.Genre);
        mAlbum = libVLC.getMeta(Meta.Album);
        mAlbumArtist = libVLC.getMeta(Meta.AlbumArtist);
        mNowPlaying = libVLC.getMeta(Meta.NowPlaying);
        mArtworkURL = libVLC.getMeta(Meta.ArtworkURL);
    }

    public String getFileName() {
        if (mFilename == null) {
            mFilename = LibVlcUtil.URItoFileName(mLocation);
        }
        return mFilename;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long time) {
        mTime = time;
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

    public int getType() {
        return mType;
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
     * Use {@link org.videolan.vlc.util.BitmapUtil#getPictureFromCache(MediaWrapper)} instead.
     *
     * @return The raw picture or NULL
     */
    public Bitmap getPicture() {
        return mPicture;
    }

    /**
     * Sets the raw picture object.
     *
     * In VLC for Android, use {@link org.videolan.vlc.MediaDatabase#setPicture(MediaWrapper, Bitmap)} instead.
     *
     * @param p
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

    public String getTitle() {
        if (mTitle != null && mType != TYPE_VIDEO)
            return mTitle;
        else {
            String fileName = getFileName();
            if (fileName == null)
                return "";
            int end = fileName.lastIndexOf(".");
            if (end <= 0)
                return fileName;
            return fileName.substring(0, end);
        }
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

    public String getDescription() {
        return mDescription;
    }

    public String getRating() {
        return mRating;
    }

    public String getDate() {
        return mDate;
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

    public void addFlags(int flags) {
        mFlags |= flags;
    }
    public void setFlags(int flags) {
        mFlags = flags;
    }
    public int getFlags() {
        return mFlags;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public MediaWrapper(Parcel in) {
        mLocation = in.readString();
        init(in.readLong(),
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
                in.readInt());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(getLocation());
        dest.writeLong(getTime());
        dest.writeLong(getLength());
        dest.writeInt(getType());
        dest.writeParcelable(getPicture(), flags);
        dest.writeValue(getTitle());
        dest.writeValue(getArtist());
        dest.writeValue(getGenre());
        dest.writeValue(getAlbum());
        dest.writeValue(getAlbumArtist());
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        dest.writeValue(getArtworkURL());
        dest.writeInt(getAudioTrack());
        dest.writeInt(getSpuTrack());
        dest.writeInt(getTrackNumber());
        dest.writeInt(getDiscNumber());
    }

    public static final Parcelable.Creator<MediaWrapper> CREATOR = new Parcelable.Creator<MediaWrapper>() {
        public MediaWrapper createFromParcel(Parcel in) {
            return new MediaWrapper(in);
        }
        public MediaWrapper[] newArray(int size) {
            return new MediaWrapper[size];
        }
    };
}