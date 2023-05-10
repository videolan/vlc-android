/*
 *****************************************************************************
 * MediaWrapperImpl.java
 *****************************************************************************
 * Copyright © 2011-2019 VLC authors and VideoLAN
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

package org.videolan.medialibrary.media;


import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;

import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.Bookmark;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

import java.util.Locale;

@SuppressWarnings("JniMissingFunction")
public class MediaWrapperImpl extends MediaWrapper {
    public final static String TAG = "VLC/MediaWrapperImpl";

    public MediaWrapperImpl(long id, String mrl, long time, float position, long length, int type, String title,
                            String filename, String artist, String genre, String album, String albumArtist,
                            int width, int height, String artworkURL, int audio, int spu, int trackNumber,
                            int discNumber, long lastModified, long seen, boolean isThumbnailGenerated,
                            boolean isFavorite, int releaseDate, boolean isPresent, long insertionDate) {
        super(id, mrl, time, position, length, type, title, filename, artist,
                genre, album, albumArtist, width, height, artworkURL,
                audio, spu, trackNumber, discNumber, lastModified,
                seen, isThumbnailGenerated, isFavorite, releaseDate, isPresent, insertionDate);
    }

    public MediaWrapperImpl(Uri uri, long time, float position, long length, int type,
                            Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                            int width, int height, String artworkURL, int audio, int spu, int trackNumber,
                            int discNumber, long lastModified, long seen, boolean isFavorite, long insertionDate) {
        super(uri, time, position, length, type, picture, title, artist,
                genre, album, albumArtist, width, height, artworkURL,
                audio, spu, trackNumber, discNumber, lastModified, seen, isFavorite, insertionDate);
    }

    public MediaWrapperImpl(Uri uri) { super(uri); }
    public MediaWrapperImpl(IMedia media) { super(media); }
    public MediaWrapperImpl(Parcel in) { super(in); }

    public void rename(String name) {
        final Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated()) nativeSetMediaTitle(ml, mId, name);
    }

    public boolean removeFromHistory() {
        if (mId != 0L) {
            final Medialibrary ml = Medialibrary.getInstance();
            if (ml.isInitiated()) return nativeRemoveFromHistory(ml, mId);
        }
        return false;
    }

    public void setArtist(String artist) {
        mArtist = artist;
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

    public long getMetaLong(int metaDataType) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? 0L : nativeGetMediaLongMetadata(ml, mId, metaDataType);
    }

    public String getMetaString(int metaDataType) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeGetMediaStringMetadata(ml, mId, metaDataType);
    }

    public Bookmark[] getBookmarks() {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeGetBookmarks(ml, mId);
    }

    public Bookmark addBookmark(long time) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeAddBookmark(ml, mId, time);
    }

    public boolean removeBookmark(long time) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeRemoveBookmark(ml, mId, time);
    }

    public boolean removeAllBookmarks() {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeRemoveAllBookmarks(ml, mId);
    }

    public boolean setLongMeta(int metaDataType, long metadataValue) {
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaLongMetadata(ml, mId, metaDataType, metadataValue);
        return mId != 0;
    }

    public boolean setStringMeta(int metaDataType, String metadataValue) {
        if (mId == 0L) return false;
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaStringMetadata(ml, mId, metaDataType, metadataValue);
        return true;
    }

    public void setThumbnail(String mrl) {
        if (mId == 0L) return;
        mArtworkURL = mrl;
        final Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated()) nativeSetMediaThumbnail(ml, mId, Tools.encodeVLCMrl(mrl));
    }

    @Override
    public boolean setPlayCount(long playCount) {
        if (mId == 0L) return false;
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetMediaPlayCount(ml, mId, playCount);
    }

    @Override
    public long getPlayCount() {
        if (mId == 0L) return -1;
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeGetMediaPlayCount(ml, mId);
    }

    public void removeThumbnail() {
        if (mId == 0L) return;
        final Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated()) nativeRemoveMediaThumbnail(ml, mId);
    }

    public void requestThumbnail(int width, float position) {
        if (mId == 0L) return;
        final Medialibrary ml = Medialibrary.getInstance();
        if (ml.isInitiated()) nativeRequestThumbnail(ml, mId, Medialibrary.ThumbnailSizeType.Thumbnail.ordinal(), width, 0, position);
    }

    public void requestBanner(int width, float position) {
        if (mId == 0L) return;
        final Medialibrary ml = Medialibrary.getInstance();
        if (ml.isInitiated()) nativeRequestThumbnail(ml, mId, Medialibrary.ThumbnailSizeType.Banner.ordinal(), width, 0, position);
    }

    public boolean markAsPlayed() {
        if (mId == 0L) return false;
        final Medialibrary ml = Medialibrary.getInstance();
        boolean ret = false;
        if (ml.isInitiated())
            ret = nativeMarkAsPlayed(ml, mId);
        return ret;
    }

    @Override
    public boolean setFavorite(boolean favorite) {
        if (mId == 0L) return false;
        final Medialibrary ml = Medialibrary.getInstance();
        boolean ret = false;
        if (ml.isInitiated())
            ret = nativeSetFavorite(ml, mId, favorite);
        return ret;
    }

    private native long nativeGetMediaLongMetadata(Medialibrary ml, long id, int metaDataType);
    private native String nativeGetMediaStringMetadata(Medialibrary ml, long id, int metaDataType);
    private native void nativeSetMediaStringMetadata(Medialibrary ml, long id, int metaDataType, String metadataValue);
    private native void nativeSetMediaLongMetadata(Medialibrary ml, long id, int metaDataType, long metadataValue);
    private native void nativeSetMediaTitle(Medialibrary ml, long id, String name);
    private native boolean nativeRemoveFromHistory(Medialibrary ml, long id);
    private native void nativeSetMediaThumbnail(Medialibrary ml, long id, String mrl);
    private native boolean nativeSetMediaPlayCount(Medialibrary ml, long id, long playCount);
    private native long nativeGetMediaPlayCount(Medialibrary ml, long id);
    private native boolean nativeRemoveMediaThumbnail(Medialibrary ml, long id);
    private native void nativeRequestThumbnail(Medialibrary ml, long mediaId, int type, int width, int height, float position);
    private native Bookmark[] nativeGetBookmarks(Medialibrary ml, long id);
    private native Bookmark nativeAddBookmark(Medialibrary ml, long id, long time);
    private native boolean nativeRemoveBookmark(Medialibrary ml, long id, long time);
    private native boolean nativeRemoveAllBookmarks(Medialibrary ml, long id);
    private native boolean nativeMarkAsPlayed(Medialibrary ml, long id);
    private native boolean nativeSetFavorite(Medialibrary ml, long id, boolean favorite);
}
