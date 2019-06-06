package org.videolan.medialibrary;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;

import org.videolan.libvlc.Media;
import org.videolan.medialibrary.interfaces.media.AAlbum;
import org.videolan.medialibrary.interfaces.media.AArtist;
import org.videolan.medialibrary.interfaces.media.AFolder;
import org.videolan.medialibrary.interfaces.media.AGenre;
import org.videolan.medialibrary.interfaces.media.AMediaWrapper;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Folder;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.stubs.StubAlbum;
import org.videolan.medialibrary.stubs.StubArtist;
import org.videolan.medialibrary.stubs.StubFolder;
import org.videolan.medialibrary.stubs.StubGenre;
import org.videolan.medialibrary.stubs.StubMediaWrapper;

public class ServiceLocator {

    private static ServiceLocator mServiceLocator;
    private static LocatorMode mMode = LocatorMode.VLC_ANDROID;

    public static void setLocatorMode(LocatorMode mode) {
        ServiceLocator.mMode = mode;
    }

    enum LocatorMode {
        VLC_ANDROID,
        TESTS,
    }

    public static ServiceLocator getInstance() {
        return mServiceLocator;
    }

    // AMediaWrapper
    public static AMediaWrapper getAMediaWrapper(long id, String mrl, long time, long length,
                                                 int type, String title, String filename,
                                                 String artist, String genre, String album,
                                                 String albumArtist, int width, int height,
                                                 String artworkURL, int audio, int spu,
                                                 int trackNumber, int discNumber, long lastModified,
                                                 long seen, boolean isThumbnailGenerated) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new MediaWrapper(id, mrl, time, length, type, title,
                    filename, artist, genre, album, albumArtist, width, height, artworkURL,
                    audio, spu, trackNumber, discNumber, lastModified, seen, isThumbnailGenerated);
        } else {
            return new StubMediaWrapper(id, mrl, time, length, type, title,
                    filename, artist, genre, album, albumArtist, width, height, artworkURL,
                    audio, spu, trackNumber, discNumber, lastModified, seen, isThumbnailGenerated);
        }
    }

    public static AMediaWrapper getAMediaWrapper(Uri uri, long time, long length, int type,
                                                 Bitmap picture, String title, String artist,
                                                 String genre, String album, String albumArtist,
                                                 int width, int height, String artworkURL,
                                                 int audio, int spu, int trackNumber,
                                                 int discNumber, long lastModified, long seen) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new MediaWrapper(uri, time, length, type, picture, title, artist, genre,
                    album, albumArtist, width, height, artworkURL, audio, spu, trackNumber,
                    discNumber, lastModified, seen);
        } else {
            return new StubMediaWrapper(uri, time, length, type, picture, title, artist, genre,
                    album, albumArtist, width, height, artworkURL, audio, spu, trackNumber,
                    discNumber, lastModified, seen);
        }
    }

    public static AMediaWrapper getAMediaWrapper(Uri uri) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new MediaWrapper(uri);
        } else {
            return new StubMediaWrapper(uri);
        }
    }

    public static AMediaWrapper getAMediaWrapper(Media media) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new MediaWrapper(media);
        } else {
            return new StubMediaWrapper(media);
        }
    }

    public static AMediaWrapper getAMediaWrapper(Parcel in) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new MediaWrapper(in);
        } else {
            return new StubMediaWrapper(in);
        }
    }

    //Artist
    public static AArtist getAArtist(long id, String name, String shortBio, String artworkMrl, String musicBrainzId) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Artist(id, name, shortBio, artworkMrl, musicBrainzId);
        } else {
            return new StubArtist(id, name, shortBio, artworkMrl, musicBrainzId);
        }
    }

    public static AArtist getAArtist(Parcel in) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Artist(in);
        } else {
            return new StubArtist(in);
        }
    }

    //Genre
    public static AGenre getAGenre(long id, String title) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Genre(id, title);
        } else {
            return new StubGenre(id, title);
        }
    }

    public static AGenre getAGenre(Parcel in) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Genre(in);
        } else {
            return new StubGenre(in);
        }
    }

    //Album
    public static AAlbum getAAlbum(long id, String title, int releaseYear, String artworkMrl,
                                   String albumArtist, long albumArtistId, int nbTracks,
                                   int duration) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Album(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId,
                    nbTracks, duration);
        } else {
            return new StubAlbum(id, title, releaseYear, artworkMrl, albumArtist, albumArtistId,
                    nbTracks, duration);
        }
    }

    public static AAlbum getAAlbum(Parcel in) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Album(in);
        } else {
            return new StubAlbum(in);
        }
    }

    //Folder
    public static AFolder getAFolder(long id, String name, String mrl) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Folder(id, name, mrl);
        } else {
            return new StubFolder(id, name, mrl);
        }
    }

    public static AFolder getAFolder(Parcel in) {
        if (mMode == LocatorMode.VLC_ANDROID) {
            return new Folder(in);
        } else {
            return new StubFolder(in);
        }
    }
}
