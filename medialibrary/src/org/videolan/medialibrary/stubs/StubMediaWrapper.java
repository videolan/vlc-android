package org.videolan.medialibrary.stubs;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;

import org.videolan.libvlc.Media;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;

public class StubMediaWrapper extends AMediaWrapper {
    public StubMediaWrapper(long id, String mrl, long time, long length, int type, String title,
                        String filename, String artist, String genre, String album, String albumArtist,
                        int width, int height, String artworkURL, int audio, int spu, int trackNumber,
                        int discNumber, long lastModified, long seen, boolean isThumbnailGenerated) {
        super(id, mrl, time, length, type, title, filename, artist,
                genre, album, albumArtist, width, height, artworkURL,
                audio, spu, trackNumber, discNumber, lastModified,
                seen, isThumbnailGenerated);
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

    public void rename(String name) {

    }

    public long getMetaLong(int metaDataType) {
//        Medialibrary ml = Medialibrary.getInstance();
//        return mId == 0 || !ml.isInitiated() ? 0L : nativeGetMediaLongMetadata(ml, mId, metaDataType);
        return 0L;
    }

    public String getMetaString(int metaDataType) {
//        Medialibrary ml = Medialibrary.getInstance();
//        return mId == 0 || !ml.isInitiated() ? null : nativeGetMediaStringMetadata(ml, mId, metaDataType);
        return "";
    }

    public boolean setLongMeta(int metaDataType, long metadataValue) {
//        Medialibrary ml = Medialibrary.getInstance();
//        if (mId != 0 && ml.isInitiated())
//            nativeSetMediaLongMetadata(ml, mId, metaDataType, metadataValue);
//        return mId != 0;
        return true;
    }

    public boolean setStringMeta(int metaDataType, String metadataValue) {
//        Medialibrary ml = Medialibrary.getInstance();
//        if (mId != 0 && ml.isInitiated())
//            nativeSetMediaStringMetadata(ml, mId, metaDataType, metadataValue);
//        return mId != 0;
        return true;
    }

    public void setThumbnail(String mrl) {
//        mArtworkURL = mrl;
//        final Medialibrary ml = Medialibrary.getInstance();
//        if (mId != 0 && ml.isInitiated()) nativeSetMediaThumbnail(ml, mId, Tools.encodeVLCMrl(mrl));
    }

}
