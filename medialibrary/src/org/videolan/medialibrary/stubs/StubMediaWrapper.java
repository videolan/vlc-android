package org.videolan.medialibrary.stubs;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.util.SparseArray;

import org.videolan.libvlc.Media;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;

public class StubMediaWrapper extends AbstractMediaWrapper {
    public StubMediaWrapper(long id, String mrl, long time, long length, int type, String title,
                        String filename, String artist, String genre, String album, String albumArtist,
                        int width, int height, String artworkURL, int audio, int spu, int trackNumber,
                        int discNumber, long lastModified, long seen, boolean isThumbnailGenerated, int releaseDate) {
        super(id, mrl, time, length, type, title, filename, artist,
                genre, album, albumArtist, width, height, artworkURL,
                audio, spu, trackNumber, discNumber, lastModified,
                seen, isThumbnailGenerated, releaseDate);
    }

    public StubMediaWrapper(Uri uri, long time, long length, int type,
                        Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                        int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified, long seen) {
        super(uri, time, length, type, picture, title, artist,
                genre, album, albumArtist, width, height, artworkURL,
                audio, spu, trackNumber, discNumber, lastModified, seen);
    }

    public StubMediaWrapper(Uri uri) { super(uri); }
    public StubMediaWrapper(Media media) { super(media); }
    public StubMediaWrapper(Parcel in) { super(in); }

    private SparseArray<Long> mMetaLong = new SparseArray<>();
    private SparseArray<String> mMetaString = new SparseArray<>();

    public void rename(String name) {
        mTitle = name;
    }

    public long getMetaLong(int metaDataType) {
        return mMetaLong.get(metaDataType);
    }

    public String getMetaString(int metaDataType) {
        return mMetaString.get(metaDataType);
    }

    public boolean setLongMeta(int metaDataType, long metadataValue) {
        mMetaLong.setValueAt(metaDataType, metadataValue);
        return true;
    }

    public boolean setStringMeta(int metaDataType, String metadataValue) {
        mMetaString.setValueAt(metaDataType, metadataValue);
        return true;
    }

    public void setThumbnail(String mrl) {}

    @Override
    public void requestThumbnail(int width, float position) {}

    @Override
    public void requestBanner(int width, float position) {

    }

}
